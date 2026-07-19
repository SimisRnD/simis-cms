-- Multi-factor authentication (TOTP): a per-user shared secret and an enabled flag
ALTER TABLE users ADD mfa_secret VARCHAR(64);
ALTER TABLE users ADD mfa_enabled BOOLEAN DEFAULT false;
