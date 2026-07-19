-- Multi-factor authentication (TOTP): a per-user shared secret and an enabled flag
-- Guarded with IF NOT EXISTS: a fresh install already creates these columns in the
-- NEW_ baseline, so this upgrade must be a no-op there rather than failing on a re-add.
ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_secret VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN DEFAULT false;
