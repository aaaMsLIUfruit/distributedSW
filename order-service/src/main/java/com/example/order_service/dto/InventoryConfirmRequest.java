package com.example.order_service.dto;

import lombok.Data;

@Data
public class InventoryConfirmRequest {
    private Long productId;
    private Integer quantity;
}
