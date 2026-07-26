-- Reset and validation tokens expire after 24 hours (M7 / IA-5 token lifetime).
-- account_token_expires: when set, findByAccountToken() rejects tokens past this time.
-- NULL means no expiry (pre-migration rows and admin-issued tokens remain valid until cleared).
ALTER TABLE users ADD COLUMN IF NOT EXISTS account_token_expires TIMESTAMP(3);
