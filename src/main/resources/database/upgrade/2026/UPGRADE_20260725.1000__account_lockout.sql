-- Durable, auditable account lockout (#295, AC-7 / 800-171 3.1.8). Additive columns on users:
--   failed_attempt_count : consecutive failed login attempts since the last successful login
--   locked_until         : when set and in the future, the account is locked from logging in
-- Both are nullable/defaulted so existing rows migrate cleanly; the login flow maintains them.
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_attempt_count INTEGER DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP(3);
