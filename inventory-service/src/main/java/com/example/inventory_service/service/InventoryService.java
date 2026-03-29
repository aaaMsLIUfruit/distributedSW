package com.example.inventory_service.service;

import com.example.inventory_service.dto.ApiResponse;
import com.example.inventory_service.dto.InventoryConfirmRequest;
import com.example.inventory_service.dto.InventoryPreDeductRequest;
import com.example.inventory_service.entity.InventoryStock;
import com.example.inventory_service.mapper.InventoryStockMapper;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

@Service
@SuppressWarnings("null")
public class InventoryService {

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String USER_ORDERED_PREFIX = "seckill:ordered:";
    private static final String LUA_PRE_DEDUCT = """
            local stock = redis.call('GET', KEYS[1])
            if (not stock) then
                return -1
            end
            if redis.call('EXISTS', KEYS[2]) == 1 then
                return 2
            end
            if tonumber(stock) < tonumber(ARGV[1]) then
                return 0
            end
            redis.call('DECRBY', KEYS[1], ARGV[1])
            redis.call('SET', KEYS[2], '1')
            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final InventoryStockMapper inventoryStockMapper;

    public InventoryService(StringRedisTemplate redisTemplate, InventoryStockMapper inventoryStockMapper) {
        this.redisTemplate = redisTemplate;
        this.inventoryStockMapper = inventoryStockMapper;
    }

    /**
     * Redis 原子预扣库存，并标记用户已下单（幂等控制）。
     */
    public ApiResponse<Map<String, Object>> preDeduct(InventoryPreDeductRequest request) {
        if (request.getUserId() == null || request.getProductId() == null) {
            return ApiResponse.fail("userId/productId 不能为空");
        }
        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        if (quantity <= 0) {
            return ApiResponse.fail("quantity 必须大于 0");
        }

        String stockKey = STOCK_KEY_PREFIX + request.getProductId();
        if (Boolean.FALSE.equals(redisTemplate.hasKey(stockKey))) {
            InventoryStock stock = inventoryStockMapper.selectByProductId(request.getProductId());
            if (stock == null) {
                return ApiResponse.fail("商品库存不存在");
            }
            redisTemplate.opsForValue().set(Objects.requireNonNull(stockKey), String.valueOf(stock.getAvailableStock()));
        }

        String orderedKey = USER_ORDERED_PREFIX + request.getUserId() + ":" + request.getProductId();
        Long executeResult = redisTemplate.execute(
                (connection) -> connection.scriptingCommands().eval(
                        LUA_PRE_DEDUCT.getBytes(StandardCharsets.UTF_8),
                        ReturnType.INTEGER,
                        2,
                        stockKey.getBytes(StandardCharsets.UTF_8),
                        orderedKey.getBytes(StandardCharsets.UTF_8),
                        String.valueOf(quantity).getBytes(StandardCharsets.UTF_8)
                ),
                true
        );
        long result = executeResult == null ? -2L : executeResult;
        if (result == 2L) {
            return ApiResponse.fail("重复下单");
        }
        if (result == 0L) {
            return ApiResponse.fail("库存不足");
        }
        if (result < 0L) {
            return ApiResponse.fail("库存预扣失败");
        }
        return ApiResponse.success(Map.of("status", "PRE_DEDUCT_OK"));
    }

    /**
     * 订单服务消费 MQ 后调用该方法，完成数据库真实扣减。
     */
    public ApiResponse<Map<String, Object>> confirmDeduct(InventoryConfirmRequest request) {
        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        if (request.getProductId() == null || quantity <= 0) {
            return ApiResponse.fail("参数不合法");
        }
        int updated = inventoryStockMapper.deductStock(request.getProductId(), quantity);
        if (updated <= 0) {
            return ApiResponse.fail("数据库库存不足");
        }
        return ApiResponse.success(Map.of("status", "CONFIRM_OK"));
    }

    public ApiResponse<Map<String, Object>> rollback(Long userId, Long productId, Integer quantity) {
        int q = quantity == null ? 1 : quantity;
        redisTemplate.opsForValue().increment(STOCK_KEY_PREFIX + productId, q);
        redisTemplate.delete(USER_ORDERED_PREFIX + userId + ":" + productId);
        return ApiResponse.success(Map.of("status", "ROLLBACK_OK"));
    }

    public ApiResponse<InventoryStock> getStock(Long productId) {
        return ApiResponse.success(inventoryStockMapper.selectByProductId(productId));
    }
}
