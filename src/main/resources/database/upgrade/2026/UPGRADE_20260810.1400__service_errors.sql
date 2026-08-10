-- Recent service errors: one row per uncaught exception that reached ServiceErrorLoggingFilter
-- (issue #556). This is the "recent service errors" half of the Health Dashboard's acceptance
-- criteria -- errors previously only reached stdout/the application log, with nothing queryable
-- from inside the app itself.
CREATE TABLE service_errors (
  service_error_id BIGSERIAL PRIMARY KEY,
  request_uri VARCHAR(500),
  exception_class VARCHAR(255) NOT NULL,
  message VARCHAR(1000),
  stack_trace TEXT,
  occurred_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX service_errors_occurred_at_idx ON service_errors(occurred_at);
