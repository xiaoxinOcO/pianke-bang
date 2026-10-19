-- User online heartbeat timestamp
-- Safe for existing databases (add columns only when missing)

SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'user_account'
    AND column_name = 'last_seen_at'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE user_account ADD COLUMN last_seen_at DATETIME(6) NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

