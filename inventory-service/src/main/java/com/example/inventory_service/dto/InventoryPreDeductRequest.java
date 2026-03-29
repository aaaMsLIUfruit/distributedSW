package com.example.inventory_service.dto;

import lombok.Data;

@Data
public class InventoryPreDeductRequest {
    private Long userId;
    private Long productId;
    private Integer quantity;
}
