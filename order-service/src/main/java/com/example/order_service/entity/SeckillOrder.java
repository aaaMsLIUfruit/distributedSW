package com.example.order_service.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SeckillOrder {
    private Long id;
    private Long orderId;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private BigDecimal amount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
