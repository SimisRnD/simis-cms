-- Direct, individually-trackable capability grants (issue #702) - independent of role_capabilities.
-- A user can hold a capability two ways: through a role (role_capabilities, #701) or through a
-- direct grant here (e.g. a temporary contractor who shouldn't get a whole role). expires_at is
-- nullable - null means permanent, matching a direct grant with no time limit.
CREATE TABLE capability_grants (
  capability_grant_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users (user_id),
  capability_id BIGINT NOT NULL REFERENCES capabilities (capability_id),
  granted_by BIGINT REFERENCES users (user_id),
  granted TIMESTAMP DEFAULT NOW(),
  reason VARCHAR(500),
  expires_at TIMESTAMP,
  revoked_at TIMESTAMP,
  expiration_notified_at TIMESTAMP
);

CREATE INDEX idx_capability_grants_user_id ON capability_grants (user_id);
CREATE INDEX idx_capability_grants_capability_id ON capability_grants (capability_id);

-- Only one *active* grant of a given capability per user at a time - prevents silently stacking
-- duplicate grants; revoke (or let expire) the existing one before granting again.
CREATE UNIQUE INDEX idx_capability_grants_active_unique ON capability_grants (user_id, capability_id)
  WHERE revoked_at IS NULL;

-- Sweep queries (CapabilityGrantExpirationJob) filter on both columns together.
CREATE INDEX idx_capability_grants_expires_at ON capability_grants (expires_at) WHERE revoked_at IS NULL;
