-- 修复 Flyway V8 失败记录（在 campus_delivery 库执行）
USE campus_delivery;

-- 删除失败的迁移记录，避免 Validate failed
DELETE FROM flyway_schema_history WHERE success = 0;

-- 若 V8 的列尚未创建，可手动补齐（已存在则忽略报错）
-- ALTER TABLE delivery_task
--     ADD COLUMN task_type VARCHAR(32) NOT NULL DEFAULT 'EXPRESS';
