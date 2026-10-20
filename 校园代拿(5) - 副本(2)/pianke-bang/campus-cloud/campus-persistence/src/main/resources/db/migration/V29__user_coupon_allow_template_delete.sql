-- 删除优惠券模板时保留用户已领记录，并允许模板行被删掉
ALTER TABLE user_coupon
    ADD COLUMN snapshot_name VARCHAR(80) NULL COMMENT '模板删除后仍显示的券名' AFTER template_id;

ALTER TABLE user_coupon
    DROP FOREIGN KEY fk_user_coupon_template;

ALTER TABLE user_coupon
    MODIFY COLUMN template_id BIGINT NULL;
