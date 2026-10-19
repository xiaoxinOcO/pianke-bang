-- 大厅活动：网页端独立海报（与小程序 banner_image_url 尺寸不同）
ALTER TABLE hall_activity
    ADD COLUMN banner_image_url_web MEDIUMTEXT NULL COMMENT '网页端活动海报' AFTER banner_image_url;
