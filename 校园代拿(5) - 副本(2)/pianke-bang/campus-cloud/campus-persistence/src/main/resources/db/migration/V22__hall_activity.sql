-- 大厅轮播通用活动（不限于优惠券）
CREATE TABLE IF NOT EXISTS hall_activity (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    title             VARCHAR(80)  NOT NULL,
    subtitle          VARCHAR(200) NULL,
    banner_image_url  MEDIUMTEXT   NULL,
    banner_theme      VARCHAR(20)  NULL DEFAULT 'warm',
    cta_text          VARCHAR(30)  NOT NULL DEFAULT '去看看',
    action_type       VARCHAR(40)  NOT NULL,
    action_value      VARCHAR(200) NULL COMMENT '自定义路径等',
    coupon_template_id BIGINT      NULL COMMENT '领券活动关联券模板',
    sort_order        INT          NOT NULL DEFAULT 0,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_hall_activity_status_sort (status, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
