-- /admin/users visibility improvements (#492).
--
-- last_password_changed_at: stamped by UserRepository.updatePassword() on every password change
-- (admin-initiated reset completion, self-service reset completion, and initial account
-- activation). NULL means "never tracked" -- an existing account whose password predates this
-- column -- and is treated as maximally stale by the UI, not silently excluded.
--
-- suspension_reason: free-text reason captured when an admin suspends an account, cleared on
-- restore. NULL when never suspended, or after a restore.
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_password_changed_at TIMESTAMP(3);
ALTER TABLE users ADD COLUMN IF NOT EXISTS suspension_reason VARCHAR(255);

-- Configurable "password age" warning threshold for the /admin/users UI, matching the
-- account.lockout.* / audit.retentionDays configurable-threshold precedent. The UI's "red/expired"
-- tier is computed as 2x this value, not separately stored.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (2, 'Password Age Warning Threshold (days)', 'password.maxAgeDays', '90', 'text')
ON CONFLICT (property_name) DO NOTHING;
