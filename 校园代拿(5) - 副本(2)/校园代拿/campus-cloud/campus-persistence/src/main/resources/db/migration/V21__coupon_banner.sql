ALTER TABLE coupon_template
    ADD COLUMN banner_image_url MEDIUMTEXT NULL COMMENT '活动海报图（URL 或 dataURL）' AFTER description,
    ADD COLUMN banner_theme VARCHAR(20) NULL COMMENT '默认主题 warm/green/cool/blue' AFTER banner_image_url;
