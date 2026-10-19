-- Claim modes + redeem codes for activity / newcomer coupons.

ALTER TABLE coupon_template
    ADD COLUMN claim_mode VARCHAR(20) NOT NULL DEFAULT 'ADMIN_ONLY',
    ADD COLUMN scene_code VARCHAR(40) NULL,
    ADD COLUMN description VARCHAR(200) NULL;

CREATE INDEX idx_coupon_template_claim ON coupon_template (claim_mode, status);
CREATE INDEX idx_coupon_template_scene ON coupon_template (scene_code);

CREATE TABLE IF NOT EXISTS coupon_redeem_code (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    template_id BIGINT NOT NULL,
    max_uses INT NOT NULL DEFAULT 1,
    used_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expire_at DATETIME NULL,
    remark VARCHAR(200) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_coupon_redeem_code (code),
    INDEX idx_coupon_redeem_template (template_id),
    CONSTRAINT fk_coupon_redeem_template FOREIGN KEY (template_id) REFERENCES coupon_template (id)
);

CREATE TABLE IF NOT EXISTS coupon_redeem_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    user_coupon_id BIGINT NULL,
    redeemed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_coupon_redeem_user (code_id, username),
    INDEX idx_coupon_redeem_log_user (username),
    CONSTRAINT fk_coupon_redeem_log_code FOREIGN KEY (code_id) REFERENCES coupon_redeem_code (id)
);
