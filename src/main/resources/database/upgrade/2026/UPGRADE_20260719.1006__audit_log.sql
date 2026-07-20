-- Security audit log (Milestone #3 / Phase 1): a structured, append-only record of who did what, when,
-- and from where -- authentication events now, admin/data-change events in later phases. Idempotent so
-- it is safe on any existing install; fresh installs get the identical table from NEW_10000 instead.
-- No foreign key on actor_user_id so a record survives the deletion of the user it references, and the
-- full source IP is retained for forensics.
CREATE TABLE IF NOT EXISTS audit_log (
  audit_id BIGSERIAL PRIMARY KEY,
  occurred TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
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
  schema_version INTEGER DEFAULT 1 NOT NULL
);
CREATE INDEX IF NOT EXISTS audit_log_occurred_idx ON audit_log(occurred);
CREATE INDEX IF NOT EXISTS audit_log_category_type_idx ON audit_log(event_category, event_type);
CREATE INDEX IF NOT EXISTS audit_log_actor_idx ON audit_log(actor_user_id);
