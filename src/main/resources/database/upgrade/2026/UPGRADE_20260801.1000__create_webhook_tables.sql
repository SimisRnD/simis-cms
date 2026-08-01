-- Issue #418: outbound webhook delivery and retry infrastructure. Mirrors
-- NEW_10130__new_webhooks.sql exactly (install/ and upgrade/ must stay in sync -- see issue #431's
-- precedent for what happens when they drift, and DatabaseMigrationTest's
-- tablesThatOnlyExistedInUpgradeMigrationsAreOnTheInstallPath()/
-- columnsThatOnlyExistedInUpgradeMigrationsAreOnTheInstallPath() for the regression class this
-- guards against).

CREATE TABLE IF NOT EXISTS webhook_subscription (
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
CREATE INDEX IF NOT EXISTS webhook_subscription_enabled_idx ON webhook_subscription(enabled);

CREATE TABLE IF NOT EXISTS webhook_delivery (
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
CREATE INDEX IF NOT EXISTS webhook_delivery_subscription_idx ON webhook_delivery(webhook_subscription_id);
CREATE INDEX IF NOT EXISTS webhook_delivery_status_idx ON webhook_delivery(status);
CREATE INDEX IF NOT EXISTS webhook_delivery_next_retry_idx ON webhook_delivery(next_retry_at);
