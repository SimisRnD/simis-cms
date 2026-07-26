-- Audit log prefix-deletion watermark (#296, AU-9).
--
-- A single-row table that records the lowest audit_id that has ever held a record_hash on this
-- server. verify() compares the current minimum hashed audit_id against this value: if the
-- actual minimum is higher, oldest-prefix records have been deleted outside of the normal
-- retention job, which is the one case the in-database hash chain alone cannot detect.
--
-- The watermark is set atomically during the first hashed insert (ON CONFLICT DO NOTHING so it
-- never regresses) and is advanced by the retention purge after each successful delete.

CREATE TABLE IF NOT EXISTS audit_log_watermark (
  id                     INTEGER PRIMARY KEY DEFAULT 1,
  lowest_hashed_audit_id BIGINT  NOT NULL DEFAULT 0
);

-- Backfill: initialise the watermark from the current chain (0 if no hashed records exist yet).
INSERT INTO audit_log_watermark (id, lowest_hashed_audit_id)
SELECT 1, COALESCE(MIN(audit_id), 0) FROM audit_log WHERE record_hash IS NOT NULL
ON CONFLICT (id) DO NOTHING;
