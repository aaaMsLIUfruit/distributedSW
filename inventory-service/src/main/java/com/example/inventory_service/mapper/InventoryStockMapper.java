package com.example.inventory_service.mapper;

import com.example.inventory_service.entity.InventoryStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryStockMapper {

    @Select("SELECT * FROM inventory_stock WHERE product_id = #{productId} LIMIT 1")
    InventoryStock selectByProductId(@Param("productId") Long productId);

    @Update("""
            UPDATE inventory_stock
            SET available_stock = available_stock - #{quantity},
                version = version + 1,
                updated_at = NOW()
            WHERE product_id = #{productId}
              AND available_stock >= #{quantity}
            """)
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
