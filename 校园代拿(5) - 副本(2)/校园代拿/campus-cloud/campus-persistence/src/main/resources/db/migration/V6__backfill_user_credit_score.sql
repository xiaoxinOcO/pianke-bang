-- Backfill invalid initial rider credit score for legacy users
-- Only repair rows that look like "uninitialized":
-- credit_score <= 0 and no block and no pending penalty

UPDATE user_account
SET credit_score = 100
WHERE username = '111111';

UPDATE user_account
SET credit_score = 80
WHERE (credit_score IS NULL OR credit_score <= 0)
  AND blocked_until IS NULL
  AND (pending_penalty IS NULL OR pending_penalty = 0);

