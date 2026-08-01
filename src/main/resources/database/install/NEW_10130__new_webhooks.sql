-- Issue #418: outbound webhook delivery and retry infrastructure. A subscription targets one or
-- more of this application's existing domain events (see src/main/java/.../domain/events/) and is
-- delivered a signed HTTP POST when a matching event fires. The admin UI for managing
-- subscriptions is issue #453 -- this migration only adds the tables the delivery engine itself
-- needs. The signing secret is stored as a plain column, matching how this codebase already
-- stores comparable secrets (see site_properties rows such as 'bi.metabase.secret',
-- 'ecommerce.stripe.production.secret') -- a secrets-manager abstraction is issue #454.

CREATE TABLE webhook_subscription (
  webhook_subscription_id BIGSERIAL PRIMARY KEY,
  url VARCHAR(2000) NOT NULL,
  event_types VARCHAR(2000) NOT NULL,
  secret VARCHAR(255) NOT NULL,
  enabled BOOLEAN DEFAULT true,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT REFERENCES users(user_id),
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id)
);
CREATE INDEX webhook_subscription_enabled_idx ON webhook_subscription(enabled);

-- One row per delivery attempt-series (not per attempt -- attempt_count/status/next_retry_at
-- track a single delivery through its retry schedule). delivery_uuid is generated once and
-- carried in the outbound payload on every attempt/retry of this delivery, so a receiver can
-- de-duplicate a delivery that is retried after it actually succeeded but the response was lost
-- (issue #456's idempotency requirement).
CREATE TABLE webhook_delivery (
  webhook_delivery_id BIGSERIAL PRIMARY KEY,
  webhook_subscription_id BIGINT NOT NULL REFERENCES webhook_subscription(webhook_subscription_id),
  event_type VARCHAR(200) NOT NULL,
  delivery_uuid VARCHAR(36) NOT NULL UNIQUE,
  payload TEXT NOT NULL,
  attempt_count INTEGER DEFAULT 0,
  status VARCHAR(20) DEFAULT 'pending',
  last_attempted_at TIMESTAMP(3),
  next_retry_at TIMESTAMP(3),
  response_code INTEGER,
  response_snippet VARCHAR(1000),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX webhook_delivery_subscription_idx ON webhook_delivery(webhook_subscription_id);
CREATE INDEX webhook_delivery_status_idx ON webhook_delivery(status);
CREATE INDEX webhook_delivery_next_retry_idx ON webhook_delivery(next_retry_at);
