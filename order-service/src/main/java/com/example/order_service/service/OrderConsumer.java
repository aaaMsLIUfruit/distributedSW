package com.example.order_service.service;

import com.example.order_service.dto.SeckillOrderMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderConsumer {

    private final OrderService orderService;

    public OrderConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Kafka 消费者：异步创建订单，削峰填谷。
     */
    @KafkaListener(topics = "${seckill.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(SeckillOrderMessage message) {
        orderService.createOrderFromMessage(message);
    }
}
