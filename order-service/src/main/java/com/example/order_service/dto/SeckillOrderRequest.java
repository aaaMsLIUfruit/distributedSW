package com.example.order_service.dto;

import lombok.Data;

@Data
public class SeckillOrderRequest {
    private Long userId;
    private Long productId;
    private Integer quantity;
}
