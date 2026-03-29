package com.example.order_service.service;

import com.example.order_service.dto.ApiResponse;
import com.example.order_service.dto.InventoryConfirmRequest;
import com.example.order_service.dto.InventoryPreDeductRequest;
import com.example.order_service.dto.SeckillOrderMessage;
import com.example.order_service.dto.SeckillOrderRequest;
import com.example.order_service.entity.SeckillOrder;
import com.example.order_service.mapper.SeckillOrderMapper;
import com.example.order_service.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@SuppressWarnings("null")
public class OrderService {

    private final SeckillOrderMapper seckillOrderMapper;
    private final KafkaTemplate<String, SeckillOrderMessage> kafkaTemplate;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final RestTemplate restTemplate;

    @Value("${seckill.kafka.topic}")
    private String seckillTopic;

    @Value("${inventory.service.base-url}")
    private String inventoryServiceBaseUrl;

    public OrderService(SeckillOrderMapper seckillOrderMapper,
                        KafkaTemplate<String, SeckillOrderMessage> kafkaTemplate,
                        SnowflakeIdGenerator snowflakeIdGenerator,
                        RestTemplate restTemplate) {
        this.seckillOrderMapper = seckillOrderMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.restTemplate = restTemplate;
    }

    /**
     * 秒杀入口：先调用库存服务预扣，再发 Kafka 异步创建订单。
     */
    public ApiResponse<Map<String, Object>> seckill(SeckillOrderRequest request) {
        if (request.getUserId() == null || request.getProductId() == null) {
            return ApiResponse.fail("userId/productId 不能为空");
        }
        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        if (quantity <= 0) {
            return ApiResponse.fail("quantity 必须大于 0");
        }

        InventoryPreDeductRequest preDeductRequest = new InventoryPreDeductRequest();
        preDeductRequest.setUserId(request.getUserId());
        preDeductRequest.setProductId(request.getProductId());
        preDeductRequest.setQuantity(quantity);

        ResponseEntity<ApiResponse<Map<String, Object>>> preDeductResp = restTemplate.exchange(
                inventoryServiceBaseUrl + "/api/inventories/seckill/pre-deduct",
                HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(preDeductRequest),
                new ParameterizedTypeReference<>() {
                }
        );
        ApiResponse<Map<String, Object>> body = preDeductResp.getBody();
        if (body == null || !Objects.equals(body.getCode(), 0)) {
            return ApiResponse.fail(body == null ? "库存服务不可用" : body.getMessage());
        }

        Long orderId = snowflakeIdGenerator.nextId();
        SeckillOrderMessage msg = new SeckillOrderMessage();
        msg.setOrderId(orderId);
        msg.setUserId(request.getUserId());
        msg.setProductId(request.getProductId());
        msg.setQuantity(quantity);
        kafkaTemplate.send(Objects.requireNonNull(seckillTopic), String.valueOf(orderId), msg);
        return ApiResponse.success(Map.of("orderId", orderId, "status", "PROCESSING"));
    }

    /**
     * 消费 MQ 后执行：确认数据库扣减库存并写订单，保证最终一致性。
     */
    @Transactional(rollbackFor = Exception.class)
    public void createOrderFromMessage(SeckillOrderMessage message) {
        SeckillOrder exists = seckillOrderMapper.selectByOrderId(message.getOrderId());
        if (exists != null) {
            return;
        }

        InventoryConfirmRequest confirmRequest = new InventoryConfirmRequest();
        confirmRequest.setProductId(message.getProductId());
        confirmRequest.setQuantity(message.getQuantity());
        ResponseEntity<ApiResponse<Map<String, Object>>> confirmResp = restTemplate.exchange(
                inventoryServiceBaseUrl + "/api/inventories/seckill/confirm-deduct",
                HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(confirmRequest),
                new ParameterizedTypeReference<>() {
                }
        );
        ApiResponse<Map<String, Object>> confirmBody = confirmResp.getBody();
        if (confirmBody == null || !Objects.equals(confirmBody.getCode(), 0)) {
            rollbackInventory(message);
            return;
        }

        SeckillOrder order = new SeckillOrder();
        order.setOrderId(message.getOrderId());
        order.setUserId(message.getUserId());
        order.setProductId(message.getProductId());
        order.setQuantity(message.getQuantity());
        order.setAmount(BigDecimal.ZERO);
        order.setStatus("CREATED");
        seckillOrderMapper.insert(order);
    }

    private void rollbackInventory(SeckillOrderMessage message) {
        String url = inventoryServiceBaseUrl
                + "/api/inventories/seckill/rollback?userId=" + message.getUserId()
                + "&productId=" + message.getProductId()
                + "&quantity=" + message.getQuantity();
        restTemplate.postForEntity(url, null, Object.class);
    }

    public SeckillOrder getByOrderId(Long orderId) {
        return seckillOrderMapper.selectByOrderId(orderId);
    }

    public List<SeckillOrder> listByUserId(Long userId) {
        return seckillOrderMapper.selectByUserId(userId);
    }
}
