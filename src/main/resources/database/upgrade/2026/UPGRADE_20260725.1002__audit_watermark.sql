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

-- Backfill: initialise the watermark from the current chain, but ONLY when a hashed record already
-- exists. If none do yet, leave the table empty rather than seeding a placeholder 0 -- the
-- application's own ON CONFLICT DO NOTHING insert (see AuditLogRepository.add()) can only ever set
-- the watermark once, on the row's first appearance; a pre-seeded 0 row would permanently block it
-- from ever recording the true first hashed audit_id once one is actually written.
INSERT INTO audit_log_watermark (id, lowest_hashed_audit_id)
SELECT 1, MIN(audit_id) FROM audit_log WHERE record_hash IS NOT NULL
HAVING MIN(audit_id) IS NOT NULL
ON CONFLICT (id) DO NOTHING;
