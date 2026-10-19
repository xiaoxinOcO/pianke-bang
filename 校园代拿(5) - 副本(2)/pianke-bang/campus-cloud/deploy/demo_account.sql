-- 片刻帮小程序固定测试账号
-- 用法：mysql -u root -p campus_delivery < demo_account.sql
--
-- 账号：pianke
-- 密码：123456
-- 钱包余额：100 元（可用于发布任务测试）

USE campus_delivery;

-- 1) 创建用户（已存在则跳过）
INSERT INTO user_account (
  username, password, enabled, credit_score,
  real_name, student_id, gender, phone,
  rider_approved, rider_application_pending,
  avatar_pending, customer_service_staff,
  pending_penalty, created_at
)
SELECT
  'pianke', '123456', 1, 80,
  '测试用户', '20240001', '男', '13800138000',
  1, 0,
  0, 0,
  0.00, NOW(6)
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM user_account WHERE username = 'pianke'
);

-- 2) 初始化钱包（已存在则充值到 100）
INSERT INTO wallet_account (username, balance, total_income, total_withdrawn, withdrawing_amount)
SELECT 'pianke', 100.00, 100.00, 0.00, 0.00
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM wallet_account WHERE username = 'pianke'
);

UPDATE wallet_account
SET balance = 100.00,
    total_income = GREATEST(total_income, 100.00)
WHERE username = 'pianke';

-- 3) 确认结果
SELECT id, username, real_name, phone, rider_approved, credit_score
FROM user_account
WHERE username = 'pianke';

SELECT username, balance, total_income
FROM wallet_account
WHERE username = 'pianke';
