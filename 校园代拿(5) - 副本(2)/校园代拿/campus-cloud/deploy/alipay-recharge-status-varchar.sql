-- 手动执行（Flyway 未启用时）：修复支付宝订单 status 无法写入 REFUNDED
USE campus_delivery;

ALTER TABLE alipay_recharge_order
    MODIFY COLUMN status VARCHAR(20) NOT NULL;
