-- 大厅公告：可选点击跳转到小程序页面
ALTER TABLE notice
    ADD COLUMN link_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用点击跳转' AFTER pinned,
    ADD COLUMN link_path VARCHAR(200) NULL COMMENT '跳转小程序路径，如 /pages/wallet/wallet' AFTER link_enabled;
