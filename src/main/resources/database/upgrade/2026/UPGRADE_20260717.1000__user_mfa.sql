-- Multi-factor authentication (TOTP): a per-user shared secret and an enabled flag.
-- Idempotent: on a fresh install the NEW_ script already added these columns, so guard with IF NOT EXISTS.
ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_secret VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN DEFAULT false;
