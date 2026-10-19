-- 兼容修复：
-- 若历史环境中 task_type 被建成了 ENUM('EXPRESS') 或其他受限类型，
-- 会导致非快递分类发布失败。统一改为 VARCHAR 以支持全部分类。
ALTER TABLE delivery_task
    MODIFY COLUMN task_type VARCHAR(32) NOT NULL DEFAULT 'EXPRESS';

