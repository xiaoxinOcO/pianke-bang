-- 兼容修复：
-- 历史环境中 wallet_transaction.type 可能被 Hibernate 建成 ENUM，
-- 新增 ALIPAY_RECHARGE 等类型时会报 Data truncated for column 'type'。
ALTER TABLE wallet_transaction
    MODIFY COLUMN type VARCHAR(50) NOT NULL;
