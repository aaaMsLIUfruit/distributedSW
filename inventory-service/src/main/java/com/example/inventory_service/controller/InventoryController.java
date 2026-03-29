package com.example.inventory_service.controller;

import com.example.inventory_service.dto.ApiResponse;
import com.example.inventory_service.dto.InventoryConfirmRequest;
import com.example.inventory_service.dto.InventoryPreDeductRequest;
import com.example.inventory_service.entity.InventoryStock;
import com.example.inventory_service.service.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/inventories")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/seckill/pre-deduct")
    public ApiResponse<Map<String, Object>> preDeduct(@RequestBody InventoryPreDeductRequest request) {
        return inventoryService.preDeduct(request);
    }

    @PostMapping("/seckill/confirm-deduct")
    public ApiResponse<Map<String, Object>> confirmDeduct(@RequestBody InventoryConfirmRequest request) {
        return inventoryService.confirmDeduct(request);
    }

    @PostMapping("/seckill/rollback")
    public ApiResponse<Map<String, Object>> rollback(@RequestParam Long userId,
                                                     @RequestParam Long productId,
                                                     @RequestParam Integer quantity) {
        return inventoryService.rollback(userId, productId, quantity);
    }

    @GetMapping("/{productId}")
    public ApiResponse<InventoryStock> getStock(@PathVariable Long productId) {
        return inventoryService.getStock(productId);
    }
}
