package com.example.order_service.controller;

import com.example.order_service.dto.ApiResponse;
import com.example.order_service.dto.SeckillOrderRequest;
import com.example.order_service.entity.SeckillOrder;
import com.example.order_service.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/seckill")
    public ApiResponse<Map<String, Object>> seckill(@RequestBody SeckillOrderRequest request) {
        return orderService.seckill(request);
    }

    @GetMapping("/{orderId}")
    public ApiResponse<SeckillOrder> getByOrderId(@PathVariable Long orderId) {
        SeckillOrder order = orderService.getByOrderId(orderId);
        if (order == null) {
            return ApiResponse.fail("订单不存在");
        }
        return ApiResponse.success(order);
    }

    @GetMapping
    public ApiResponse<List<SeckillOrder>> listByUserId(@RequestParam Long userId) {
        return ApiResponse.success(orderService.listByUserId(userId));
    }
}
