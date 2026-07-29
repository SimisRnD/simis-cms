-- Rejected form submissions (issue #563): deliberately lean, no field_values -- most of this volume is
-- bot/spam noise (captcha failures, rate-limited requests) not worth persisting PII for. A rejection here
-- never has a corresponding form_data row -- that table only ever contains successfully-saved submissions.
-- Idempotent so it is safe on any existing install; fresh installs get the identical table from NEW_10010.
CREATE TABLE IF NOT EXISTS form_submission_failures (
  failure_id BIGSERIAL PRIMARY KEY,
  form_unique_id VARCHAR(255),
  occurred TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
  reason VARCHAR(30) NOT NULL,
  ip_address VARCHAR(200),
  url VARCHAR(512)
);
CREATE INDEX IF NOT EXISTS form_sub_fail_form_idx ON form_submission_failures(form_unique_id);
CREATE INDEX IF NOT EXISTS form_sub_fail_occurred_idx ON form_submission_failures(occurred);
CREATE INDEX IF NOT EXISTS form_sub_fail_reason_idx ON form_submission_failures(reason);

-- Much shorter than audit_log's retention -- this is operational bot/spam-pressure telemetry, not a
-- compliance-grade evidentiary trail.
INSERT INTO site_properties (property_order, property_label, property_name, property_value)
VALUES (11, 'Form submission failure retention (days)', 'formData.failureRetentionDays', '90')
ON CONFLICT (property_name) DO NOTHING;
