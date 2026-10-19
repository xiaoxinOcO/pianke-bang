-- 接单员工作台独立会话（与网页端互不踢下线）
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_account'
    AND COLUMN_NAME = 'active_rider_session_id'
);
SET @sql := IF(
  @exists = 0,
  'ALTER TABLE user_account ADD COLUMN active_rider_session_id VARCHAR(64) NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
