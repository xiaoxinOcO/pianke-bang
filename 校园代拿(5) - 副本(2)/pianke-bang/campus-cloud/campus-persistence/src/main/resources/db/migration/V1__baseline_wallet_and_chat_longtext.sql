-- Wallet tables + chat_message.content LONGTEXT migration
-- Safe for existing databases (idempotent operations where possible)

CREATE TABLE IF NOT EXISTS wallet_account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  total_income DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  total_withdrawn DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  withdrawing_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE TABLE IF NOT EXISTS wallet_transaction (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  type VARCHAR(30) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  balance_after DECIMAL(12,2) NOT NULL,
  remark VARCHAR(300),
  task_id BIGINT NULL,
  withdraw_request_id BIGINT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE IF NOT EXISTS wallet_withdraw_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  account_name VARCHAR(100),
  account_no VARCHAR(100),
  remark VARCHAR(300),
  audited_by VARCHAR(50),
  audit_remark VARCHAR(300),
  audited_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

-- Ensure chat content can store base64 image payloads.
ALTER TABLE chat_message
  MODIFY COLUMN content LONGTEXT NOT NULL;

-- wallet_transaction indexes (create only when missing)
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'wallet_transaction'
    AND index_name = 'idx_wallet_tx_user_time'
);
SET @ddl := IF(@idx_exists = 0,
  'CREATE INDEX idx_wallet_tx_user_time ON wallet_transaction (username, created_at)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'wallet_transaction'
    AND index_name = 'idx_wallet_tx_task_user'
);
SET @ddl := IF(@idx_exists = 0,
  'CREATE INDEX idx_wallet_tx_task_user ON wallet_transaction (task_id, username)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- wallet_withdraw_request indexes (create only when missing)
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'wallet_withdraw_request'
    AND index_name = 'idx_wallet_wd_user_time'
);
SET @ddl := IF(@idx_exists = 0,
  'CREATE INDEX idx_wallet_wd_user_time ON wallet_withdraw_request (username, created_at)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'wallet_withdraw_request'
    AND index_name = 'idx_wallet_wd_status_time'
);
SET @ddl := IF(@idx_exists = 0,
  'CREATE INDEX idx_wallet_wd_status_time ON wallet_withdraw_request (status, created_at)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
