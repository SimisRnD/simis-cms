-- Audit logging Phase 4 (Milestone #3): tamper-evidence and retention. Maps to NIST 800-53 AU-9 and AU-11.
--
-- Tamper-evidence: two columns hold a per-row SHA-256 hash chain computed by the application on insert --
-- record_hash = SHA-256(previous_hash || canonical(row)), where previous_hash is the record_hash of the row
-- inserted immediately before (a genesis constant for the first row). Any edit, deletion, reorder, or
-- mid-chain insertion breaks the chain, which AuditLogIntegrityCommand.verify() detects. Nullable so rows
-- written before this migration (Phase 1/2) remain valid; the chain is verified from the oldest hashed row.
-- Idempotent so it is safe to re-run; fresh installs get the identical columns from NEW_10000 instead.
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS previous_hash VARCHAR(64);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS record_hash VARCHAR(64);

-- Retention: the daily AuditLogRetentionJob deletes records older than this many days (reusing the analytics
-- retention pattern). The value is parsed to a bounded integer in code (floor 90, cap 3650, default 2555 ~=
-- 7 years) before use, so it cannot inject SQL and cannot be set low enough to erase recent evidence. A long
-- default keeps the purge inert on a fresh install; long-term retention is the SIEM's job, not the database.
INSERT INTO site_properties (property_order, property_label, property_name, property_value)
VALUES (1, 'Audit log retention (days)', 'audit.retentionDays', '2555')
ON CONFLICT (property_name) DO NOTHING;
