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

/**
 * 秒杀一致性说明（最终一致，非跨服务单事务）：
 * <ul>
 *   <li>同步阶段：Redis 预扣（库存服务）→ 发 Kafka；预扣失败则整条链路失败，与 DB 无关。</li>
 *   <li>异步阶段：消费消息 → 远程 confirm 扣 MySQL → 本地 insert 订单；@Transactional 仅保证订单库内 insert 等与本地库一致。</li>
 *   <li>confirm 与 insert 分属两服务/两库，不是分布式 ACID；confirm 失败会 rollback Redis 预扣，与「未落单」对齐。</li>
 *   <li>消费幂等：先按 orderId 查重，避免 Kafka 重复投递导致重复建单、重复扣库。</li>
 *   <li>边界：若 confirm 已成功而本方法后续 insert 抛错，本地事务回滚订单行，但库存服务已扣减，需运维/补偿任务对齐（当前未自动回补 DB）。</li>
 * </ul>
 */
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
     * 秒杀入口：先调用库存服务 Redis 预扣，再发 Kafka 异步落单。
     * 一致性：预扣成功才发消息，避免「消息已发但未占位」；削峰，DB 在消费阶段写入。
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
     * 消费 MQ：远程扣库存库 + 本地写订单。顺序为先扣 MySQL 再 insert，与「预扣在 Redis」形成最终一致。
     * @Transactional 仅作用于订单库；库存 HTTP 成功即已在库存服务提交，与本事务非同一原子单元。
     */
    @Transactional(rollbackFor = Exception.class)
    public void createOrderFromMessage(SeckillOrderMessage message) {
        // Kafka 可能重复投递：已存在则直接返回，保证幂等
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
        // DB 扣减失败：回补 Redis 库存与限购计数，避免长期占库存与限购额度
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

    /** confirm 失败时调用，回补 Redis 库存并释放限购额度（不回调 MySQL，因 confirm 未成功）。 */
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
