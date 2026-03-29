package com.example.order_service.dto;

import lombok.Data;

@Data
public class SeckillOrderMessage {
    private Long orderId;
    private Long userId;
    private Long productId;
    private Integer quantity;
}
