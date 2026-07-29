-- Issue #644: /admin/blocked-ip-list and /admin/allowed-ip-list gained an inline "History" link that
-- filters the audit log by target_type + target_label (e.g. a specific blocked IP). Without an index,
-- that lookup is a sequential scan over the whole audit_log table.
CREATE INDEX IF NOT EXISTS audit_log_target_idx ON audit_log(target_type, target_label);
