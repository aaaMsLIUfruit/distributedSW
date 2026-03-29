package com.example.inventory_service.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryStock {
    private Long id;
    private Long productId;
    private Integer availableStock;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
