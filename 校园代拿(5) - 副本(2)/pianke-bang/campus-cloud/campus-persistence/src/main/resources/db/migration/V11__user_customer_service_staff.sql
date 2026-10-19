SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'user_account'
    AND column_name = 'customer_service_staff'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE user_account ADD COLUMN customer_service_staff TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否人工客服账号''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
