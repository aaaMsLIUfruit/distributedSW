package com.example.order_service.mapper;

import com.example.order_service.entity.SeckillOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 逻辑表名 seckill_orders；物理为 order_db_{user_id%2}.seckill_orders_{order_id%4}，由 ShardingSphere-JDBC 路由改写。
 */
@Mapper
public interface SeckillOrderMapper {

    @Insert("""
            INSERT INTO seckill_orders(order_id, user_id, product_id, quantity, amount, status, created_at, updated_at)
            VALUES(#{orderId}, #{userId}, #{productId}, #{quantity}, #{amount}, #{status}, NOW(), NOW())
            """)
    int insert(SeckillOrder order);

    @Select("SELECT * FROM seckill_orders WHERE order_id = #{orderId} LIMIT 1")
    SeckillOrder selectByOrderId(@Param("orderId") Long orderId);

    @Select("SELECT * FROM seckill_orders WHERE user_id = #{userId} ORDER BY id DESC")
    List<SeckillOrder> selectByUserId(@Param("userId") Long userId);
}
