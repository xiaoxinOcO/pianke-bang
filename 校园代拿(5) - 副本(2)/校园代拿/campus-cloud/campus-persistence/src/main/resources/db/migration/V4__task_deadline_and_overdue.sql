-- Add deadline field for task overdue judgment
-- Safe for existing databases (add columns only when missing)

SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'delivery_task'
    AND column_name = 'latest_delivery_at'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE delivery_task ADD COLUMN latest_delivery_at DATETIME(6) NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

