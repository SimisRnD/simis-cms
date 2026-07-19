-- Multi-factor authentication recovery codes: one-time backup codes, stored as SHA-256 hashes.
-- Idempotent: on a fresh install the NEW_ script already created this table, so guard with IF NOT EXISTS.
CREATE TABLE IF NOT EXISTS user_mfa_recovery_codes (
  recovery_code_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(user_id) NOT NULL,
  code_hash VARCHAR(64) NOT NULL,
  used BOOLEAN DEFAULT false,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS user_mfa_recovery_codes_user_idx ON user_mfa_recovery_codes(user_id);
