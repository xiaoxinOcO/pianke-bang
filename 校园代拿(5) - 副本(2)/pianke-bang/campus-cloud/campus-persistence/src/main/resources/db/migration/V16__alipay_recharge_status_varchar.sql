-- 兼容修复：status 若为 ENUM(PENDING,PAID,CLOSED)，写入 REFUNDED 会报 Data truncated
ALTER TABLE alipay_recharge_order
    MODIFY COLUMN status VARCHAR(20) NOT NULL;
