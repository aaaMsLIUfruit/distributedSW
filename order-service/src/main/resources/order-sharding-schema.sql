-- 订单分库分表：与 shardingsphere.yaml 一致
-- 2 个库：order_db_0、order_db_1（按 user_id % 2 路由）
-- 每库 4 张表：seckill_orders_0 .. seckill_orders_3（按 order_id % 4 路由）
-- 逻辑表名仍为 seckill_orders（MyBatis 中不变，由 ShardingSphere 改写路由）

CREATE DATABASE IF NOT EXISTS order_db_0;
CREATE DATABASE IF NOT EXISTS order_db_1;

-- 在 order_db_0 中执行以下整段（或连接串指定 order_db_0）
USE order_db_0;

CREATE TABLE IF NOT EXISTS seckill_orders_0 (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_id (order_id),
    UNIQUE KEY uk_user_product (user_id, product_id)
) COMMENT='秒杀订单分表_0';

CREATE TABLE IF NOT EXISTS seckill_orders_1 LIKE seckill_orders_0;
CREATE TABLE IF NOT EXISTS seckill_orders_2 LIKE seckill_orders_0;
CREATE TABLE IF NOT EXISTS seckill_orders_3 LIKE seckill_orders_0;

-- 在 order_db_1 中执行以下整段（或连接串指定 order_db_1）
USE order_db_1;

CREATE TABLE IF NOT EXISTS seckill_orders_0 (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_id (order_id),
    UNIQUE KEY uk_user_product (user_id, product_id)
) COMMENT='秒杀订单分表_0';

CREATE TABLE IF NOT EXISTS seckill_orders_1 LIKE seckill_orders_0;
CREATE TABLE IF NOT EXISTS seckill_orders_2 LIKE seckill_orders_0;
CREATE TABLE IF NOT EXISTS seckill_orders_3 LIKE seckill_orders_0;
