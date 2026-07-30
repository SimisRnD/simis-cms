-- System health check history (issue #466). HealthCommand already gates the /healthz readiness
-- probe on three checks ANDed together (startup flag, database reachability, file store
-- writability), but nothing persists the individual results, so admins have no way to see current
-- or historical per-service status.
--
-- Only 'database' and 'filesystem' are tracked here, not 'startup' -- the startup flag is a
-- one-time fact set once by the ContextListener and never flips back to false, so re-checking it
-- on a recurring schedule (SystemHealthJob, which only starts after startup already succeeded)
-- has no signal value. The dashboard shows application startup as a separate static fact instead
-- of a row in this history table.
--
-- One row per check run rather than a single upserted "current status" row, so the dashboard can
-- show a real uptime percentage / history, not just the latest sample. Pruned by
-- SystemHealthCheckCleanupJob after 30 days, mirroring the raw web_vitals retention window.
CREATE TABLE system_health_checks (
  system_health_check_id BIGSERIAL PRIMARY KEY,
  service_name VARCHAR(50) NOT NULL,
  status VARCHAR(10) NOT NULL,
  response_time_ms INTEGER,
  error_message VARCHAR(500),
  checked_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX system_health_checks_checked_at_idx ON system_health_checks(checked_at);
CREATE INDEX system_health_checks_service_name_idx ON system_health_checks(service_name);
