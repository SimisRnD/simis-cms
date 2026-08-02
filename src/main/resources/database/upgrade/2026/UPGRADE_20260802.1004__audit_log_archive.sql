-- Issue #558: cold storage for audit_log rows purged by AuditLogRepository.deleteOlderThan(). See
-- NEW_10000__new_database.sql for the full column-by-column rationale (mirrored here verbatim for
-- existing installs). Strictly cold storage: never read by AuditLogIntegrityCommand or the audit
-- log viewer.
CREATE TABLE IF NOT EXISTS audit_log_archive (
  audit_id BIGINT PRIMARY KEY,
  occurred TIMESTAMP(3) NOT NULL,
  event_category VARCHAR(50) NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  outcome VARCHAR(20) NOT NULL,
  actor_user_id BIGINT,
  actor_username VARCHAR(255),
  source_ip VARCHAR(200),
  target_type VARCHAR(50),
  target_id VARCHAR(255),
  target_label VARCHAR(255),
  details TEXT,
  session_id VARCHAR(255),
  schema_version INTEGER NOT NULL,
  previous_hash VARCHAR(64),
  record_hash VARCHAR(64),
  archived TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL
);
