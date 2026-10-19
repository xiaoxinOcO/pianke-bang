-- Coupon templates, user coupons, and task bounty payable fields.

CREATE TABLE IF NOT EXISTS coupon_template (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    type VARCHAR(20) NOT NULL,
    discount_amount DECIMAL(12,2) NULL,
    min_spend DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    discount_percent DECIMAL(8,4) NULL,
    max_discount DECIMAL(12,2) NULL,
    valid_from DATETIME NULL,
    valid_to DATETIME NULL,
    total_stock INT NOT NULL DEFAULT 0,
    issued_count INT NOT NULL DEFAULT 0,
    per_user_limit INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_coupon_template_status (status, valid_to)
);

CREATE TABLE IF NOT EXISTS user_coupon (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNUSED',
    obtained_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at DATETIME NULL,
    used_at DATETIME NULL,
    used_task_id BIGINT NULL,
    INDEX idx_user_coupon_user_status (username, status),
    INDEX idx_user_coupon_template (template_id),
    UNIQUE KEY uk_user_coupon_used_task (used_task_id),
    CONSTRAINT fk_user_coupon_template FOREIGN KEY (template_id) REFERENCES coupon_template (id)
);

ALTER TABLE delivery_task
    ADD COLUMN user_coupon_id BIGINT NULL,
    ADD COLUMN coupon_discount DECIMAL(12,2) NULL,
    ADD COLUMN bounty_payable DECIMAL(12,2) NULL;
