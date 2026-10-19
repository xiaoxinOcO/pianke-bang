-- Appeal audit fields for delivery_task
-- Safe for existing databases (add columns only when missing)

SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'delivery_task'
    AND column_name = 'rider_no_fault'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE delivery_task ADD COLUMN rider_no_fault TINYINT(1) NULL DEFAULT 0',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'delivery_task'
    AND column_name = 'appeal_approved'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE delivery_task ADD COLUMN appeal_approved TINYINT(1) NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'delivery_task'
    AND column_name = 'appeal_service_completed'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE delivery_task ADD COLUMN appeal_service_completed TINYINT(1) NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'delivery_task'
    AND column_name = 'appeal_audited_by'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE delivery_task ADD COLUMN appeal_audited_by VARCHAR(50) NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'delivery_task'
    AND column_name = 'appeal_remark'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE delivery_task ADD COLUMN appeal_remark VARCHAR(300) NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'delivery_task'
    AND column_name = 'appeal_audited_at'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE delivery_task ADD COLUMN appeal_audited_at DATETIME(6) NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

