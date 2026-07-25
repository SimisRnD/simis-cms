-- Audit high-water floor for on-box oldest-prefix deletion detection (POA&M #9 / NIST AU-9).
-- Stored in site_properties so it persists across restarts and is accessible to the same
-- property-loading path as audit.retentionDays.
--
-- The floor ID is the minimum audit_id that is expected to exist in audit_log among records
-- that carry a hash. It advances monotonically:
--   - AuditLogIntegrityCommand.verify() sets it when it walks an intact chain (first-time
--     initialization and after each clean check).
--   - AuditLogRetentionJob advances it after a legitimate retention purge.
-- If verify() observes MIN(audit_id) > floor, it reports broken (oldest-prefix deletion
-- outside the retention path). A value of '0' means not yet established; the check is skipped
-- until the first intact verify walk sets a real floor.
INSERT INTO site_properties (property_order, property_label, property_name, property_value)
VALUES (0, 'Audit log high-water floor ID', 'audit.highwater.floor_id', '0')
ON CONFLICT (property_name) DO NOTHING;
