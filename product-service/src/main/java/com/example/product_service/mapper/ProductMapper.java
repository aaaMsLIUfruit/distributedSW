package com.example.product_service.mapper;

import com.example.product_service.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductMapper {

    @Select("SELECT * FROM products WHERE id = #{id} AND status = 1")
    Product selectById(Long id);
}
