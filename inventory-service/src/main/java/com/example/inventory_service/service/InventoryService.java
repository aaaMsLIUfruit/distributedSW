package com.example.inventory_service.service;

import com.example.inventory_service.dto.ApiResponse;
import com.example.inventory_service.dto.InventoryConfirmRequest;
import com.example.inventory_service.dto.InventoryPreDeductRequest;
import com.example.inventory_service.entity.InventoryStock;
import com.example.inventory_service.mapper.InventoryStockMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * 秒杀库存一致性说明：
 *   预扣（Redis）：Lua 原子扣减 + 用户维度累计件数限购（见 seckill.per-user-max-quantity）；无 key 时从 MySQL 读一次灌入 Redis。
 *   确认（MySQL）：带条件的 UPDATE（available_stock &gt;= quantity），与乐观 version 一并防止超卖。
 *   Redis 与 MySQL 非同事务：预扣成功、confirm 失败时 rollback 仅恢复 Redis，与「未扣 DB」一致。
 */
@Service
@SuppressWarnings("null")
public class InventoryService {

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    /** 活动期间该用户对某商品已通过预扣占用的总件数（confirm 成功保留；rollback 递减以释放限购额度） */
    private static final String USER_QTY_PREFIX = "seckill:user_qty:";
    /**
     * ARGV[1]=本次扣减件数，ARGV[2]=每人每商品限购上限（累计）。
     * 返回：1 成功；0 库存不足；2 超出限购；-1 未初始化库存 key。
     */
    private static final String LUA_PRE_DEDUCT = """
            local stock = redis.call('GET', KEYS[1])
            if (not stock) then
                return -1
            end
            local cur = redis.call('GET', KEYS[2])
            if (not cur) then
                cur = '0'
            end
            local q = tonumber(ARGV[1])
            local lim = tonumber(ARGV[2])
            if tonumber(cur) + q > lim then
                return 2
            end
            if tonumber(stock) < q then
                return 0
            end
            redis.call('DECRBY', KEYS[1], q)
            redis.call('INCRBY', KEYS[2], q)
            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final InventoryStockMapper inventoryStockMapper;
    private final int perUserMaxQuantity;

    public InventoryService(StringRedisTemplate redisTemplate,
                            InventoryStockMapper inventoryStockMapper,
                            @Value("${seckill.per-user-max-quantity:1}") int perUserMaxQuantity) {
        this.redisTemplate = redisTemplate;
        this.inventoryStockMapper = inventoryStockMapper;
        this.perUserMaxQuantity = perUserMaxQuantity;
    }

    /**
     * Redis 原子预扣：同脚本内校验库存与每人累计限购，避免超卖与超买；rollback 时递减用户累计以释放额度。
     */
    public ApiResponse<Map<String, Object>> preDeduct(InventoryPreDeductRequest request) {
        if (request.getUserId() == null || request.getProductId() == null) {
            return ApiResponse.fail("userId/productId 不能为空");
        }
        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        if (quantity <= 0) {
            return ApiResponse.fail("quantity 必须大于 0");
        }
        if (perUserMaxQuantity <= 0) {
            return ApiResponse.fail("限购配置非法");
        }
        if (quantity > perUserMaxQuantity) {
            return ApiResponse.fail("单次购买件数超过每人限购(" + perUserMaxQuantity + ")");
        }

        String stockKey = STOCK_KEY_PREFIX + request.getProductId();
        // 冷启动：从 DB 同步可用库存到 Redis，与后续 Lua 预扣衔接（非双写事务，仅初始化）
        if (Boolean.FALSE.equals(redisTemplate.hasKey(stockKey))) {
            InventoryStock stock = inventoryStockMapper.selectByProductId(request.getProductId());
            if (stock == null) {
                return ApiResponse.fail("商品库存不存在");
            }
            redisTemplate.opsForValue().set(Objects.requireNonNull(stockKey), String.valueOf(stock.getAvailableStock()));
        }

        String userQtyKey = USER_QTY_PREFIX + request.getUserId() + ":" + request.getProductId();
        String limitStr = String.valueOf(perUserMaxQuantity);
        Long executeResult = redisTemplate.execute(
                (connection) -> connection.scriptingCommands().eval(
                        LUA_PRE_DEDUCT.getBytes(StandardCharsets.UTF_8),
                        ReturnType.INTEGER,
                        2,
                        stockKey.getBytes(StandardCharsets.UTF_8),
                        userQtyKey.getBytes(StandardCharsets.UTF_8),
                        String.valueOf(quantity).getBytes(StandardCharsets.UTF_8),
                        limitStr.getBytes(StandardCharsets.UTF_8)
                ),
                true
        );
        long result = executeResult == null ? -2L : executeResult;
        if (result == 2L) {
            return ApiResponse.fail("超出每人限购(" + perUserMaxQuantity + "件)");
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
     * MySQL 真实扣减：UPDATE … WHERE available_stock &gt;= qty，扣减失败表示与 Redis 预扣或并发冲突，由订单侧决定是否 rollback Redis。
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

    /**
     * confirm 失败或订单放弃时：回补 Redis 库存并递减用户限购计数，与「未确认 DB 扣减」一致。
     */
    public ApiResponse<Map<String, Object>> rollback(Long userId, Long productId, Integer quantity) {
        int q = quantity == null ? 1 : quantity;
        redisTemplate.opsForValue().increment(STOCK_KEY_PREFIX + productId, q);
        String userQtyKey = USER_QTY_PREFIX + userId + ":" + productId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(userQtyKey))) {
            Long left = redisTemplate.opsForValue().increment(userQtyKey, -q);
            if (left != null && left <= 0) {
                redisTemplate.delete(userQtyKey);
            }
        }
        return ApiResponse.success(Map.of("status", "ROLLBACK_OK"));
    }

    public ApiResponse<InventoryStock> getStock(Long productId) {
        return ApiResponse.success(inventoryStockMapper.selectByProductId(productId));
    }
}
