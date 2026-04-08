package com.example.order_service.service;

import com.example.order_service.dto.SeckillOrderMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka 至少投递一次：同一条消息可能重复到达，幂等与去重在 {@link OrderService#createOrderFromMessage}（按 orderId 查重）。
 */
@Component
public class OrderConsumer {

    private final OrderService orderService;

    public OrderConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 异步创建订单，削峰；与 Redis 预扣、DB confirm 组成最终一致链路。
     */
    @KafkaListener(topics = "${seckill.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(SeckillOrderMessage message) {
        orderService.createOrderFromMessage(message);
    }
}
