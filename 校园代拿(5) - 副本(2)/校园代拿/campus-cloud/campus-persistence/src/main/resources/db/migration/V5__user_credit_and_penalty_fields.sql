-- Rider reputation and penalty fields
-- Safe for existing databases (add columns only when missing)

SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'user_account'
    AND column_name = 'credit_score'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE user_account ADD COLUMN credit_score INT NOT NULL DEFAULT 80',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'user_account'
    AND column_name = 'blocked_until'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE user_account ADD COLUMN blocked_until DATETIME(6) NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'user_account'
    AND column_name = 'pending_penalty'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE user_account ADD COLUMN pending_penalty DECIMAL(12,2) NOT NULL DEFAULT 0.00',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

