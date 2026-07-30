-- Issue #492 Phase 3: maker-checker approval for unsuspending elevated-role accounts (a single
-- administrator can no longer reactivate an admin/community-manager/etc. account acting alone).
-- No foreign key on target_user_id/requested_by/decided_by, matching audit_log's own precedent
-- (UPGRADE_20260719.1007__audit_log.sql) -- a request row is a governance/audit record and must
-- survive the deletion of any user it references, not block it. The partial unique index enforces
-- "at most one pending request per target" at the database level, independent of any
-- application-level check.
CREATE TABLE IF NOT EXISTS unsuspend_requests (
  request_id BIGSERIAL PRIMARY KEY,
  target_user_id BIGINT NOT NULL,
  target_email VARCHAR(255),
  target_role_snapshot VARCHAR(255),
  requested_by BIGINT NOT NULL,
  requested_by_email VARCHAR(255),
  reason VARCHAR(255) NOT NULL,
  requested_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
  status VARCHAR(20) DEFAULT 'pending' NOT NULL,
  decided_by BIGINT,
  decided_by_email VARCHAR(255),
  decided_at TIMESTAMP(3),
  decision_reason VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS unsuspend_requests_target_idx ON unsuspend_requests(target_user_id);
CREATE INDEX IF NOT EXISTS unsuspend_requests_status_idx ON unsuspend_requests(status, requested_at);
CREATE UNIQUE INDEX IF NOT EXISTS ux_unsuspend_requests_one_pending_per_target
  ON unsuspend_requests(target_user_id) WHERE status = 'pending';
