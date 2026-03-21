package com.example.product_service.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.example.product_service.entity.Product;
import com.example.product_service.mapper.ProductMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 商品详情页缓存，处理：
 * - 缓存穿透：空值缓存
 * - 缓存击穿：分布式锁
 * - 缓存雪崩：TTL 随机偏移
 */
@Service
public class ProductService {

    private static final String CACHE_KEY_PREFIX = "product:";
    private static final String NULL_CACHE = "product:null:";
    private static final int BASE_TTL_SECONDS = 3600;
    private static final int NULL_TTL_SECONDS = 60;
    private static final String LOCK_KEY_PREFIX = "lock:product:";
    private static final int LOCK_EXPIRE_SECONDS = 10;

    private final ProductMapper productMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ProductService(ProductMapper productMapper, StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.productMapper = productMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Product getById(Long id) {
        String cacheKey = CACHE_KEY_PREFIX + id;

        String json = redisTemplate.opsForValue().get(cacheKey);
        if (json != null) {
            if ("".equals(json)) {
                return null;
            }
            try {
                return objectMapper.readValue(json, Product.class);
            } catch (JsonProcessingException e) {
                return selectFromDb(id);
            }
        }

        if (Boolean.TRUE.equals(redisTemplate.hasKey(NULL_CACHE + id))) {
            return null;
        }

        String lockKey = LOCK_KEY_PREFIX + id;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);

        try {
            if (Boolean.TRUE.equals(locked)) {
                Product product = productMapper.selectById(id);
                if (product == null) {
                    redisTemplate.opsForValue().set(NULL_CACHE + id, "1", NULL_TTL_SECONDS, TimeUnit.SECONDS);
                    return null;
                }
                try {
                    redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(product), ttlWithJitter(), TimeUnit.SECONDS);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                return product;
            } else {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return getById(id);
            }
        } finally {
            if (Boolean.TRUE.equals(locked)) {
                redisTemplate.delete(lockKey);
            }
        }
    }

    @DS("slave")
    public Product selectFromDb(Long id) {
        return productMapper.selectById(id);
    }

    private long ttlWithJitter() {
        return BASE_TTL_SECONDS + (long) (Math.random() * 300);
    }
}
