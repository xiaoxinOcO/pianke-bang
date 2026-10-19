ALTER TABLE delivery_task
    ADD COLUMN completion_note VARCHAR(500) NULL COMMENT '接单员完成留言' AFTER image_urls,
    ADD COLUMN completion_proof_images LONGTEXT NULL COMMENT '完成凭证图片 JSON 数组' AFTER completion_note;
