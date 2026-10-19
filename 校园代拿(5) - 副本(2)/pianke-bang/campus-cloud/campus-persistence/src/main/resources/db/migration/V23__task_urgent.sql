-- 任务加急：费用拆分、加权窗口时间
ALTER TABLE delivery_task
    ADD COLUMN urgent_enabled BIT(1) NOT NULL DEFAULT 0 COMMENT '是否加急' AFTER bounty_payable,
    ADD COLUMN urgent_fee DECIMAL(12, 2) NULL COMMENT '加急费总额' AFTER urgent_enabled,
    ADD COLUMN urgent_rider_bonus DECIMAL(12, 2) NULL COMMENT '接单员加急赏(约80%)' AFTER urgent_fee,
    ADD COLUMN urgent_push_fee DECIMAL(12, 2) NULL COMMENT '平台推送费(约20%)' AFTER urgent_rider_bonus,
    ADD COLUMN urgent_at DATETIME(6) NULL COMMENT '加急生效时间(支付成功后写入，用于30分钟加权)' AFTER urgent_push_fee;

CREATE INDEX idx_delivery_task_urgent_at ON delivery_task (urgent_enabled, urgent_at, urgent_fee);
