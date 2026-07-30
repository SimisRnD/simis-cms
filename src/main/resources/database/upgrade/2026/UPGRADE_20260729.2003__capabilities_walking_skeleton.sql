CREATE TABLE capabilities (
  capability_id BIGSERIAL PRIMARY KEY,
  code VARCHAR(100) UNIQUE NOT NULL,
  category VARCHAR(50),
  description VARCHAR(500),
  created TIMESTAMP DEFAULT NOW()
);

CREATE TABLE role_capabilities (
  role_id INTEGER NOT NULL REFERENCES lookup_role (role_id),
  capability_id BIGINT NOT NULL REFERENCES capabilities (capability_id),
  PRIMARY KEY (role_id, capability_id)
);

CREATE INDEX idx_role_capabilities_capability_id ON role_capabilities (capability_id);

-- Seeded mechanically from the existing hasRole()/hasRole()-OR-chain survey (issue #701) --
-- one capability per (module, verb) actually observed, mapped onto the role(s) that already --
-- pass that check today. This is a read model over the status quo, not a policy change: no --
-- role gains or loses access to anything as a result of this migration.
--
-- Note: 'content-editor' (added UPGRADE_20260724.1001) deliberately gets no rows here - it is
-- not referenced by any hasRole() call site yet, so there is no observed behavior to derive a
-- capability set from. Seeding it here would invent access it doesn't currently grant.

INSERT INTO capabilities (code, category, description) VALUES
  ('content:manage', 'content', 'Create, edit, and publish web pages, blog posts, and wiki content'),
  ('community:manage', 'community', 'Manage mailing lists, users, and community/forum content'),
  ('data:manage', 'data', 'Manage structured data items and collections'),
  ('ecommerce:manage', 'ecommerce', 'Manage products and orders'),
  ('admin:manage', 'admin', 'Full administrative access to all site settings and configuration');

-- admin: the existing "admin OR X" pattern found at every one of the 191 hasRole() call sites
-- means admin implicitly has every capability - expressed here as explicit rows so the table
-- stays a complete, queryable matrix (nothing is implied at query time).
INSERT INTO role_capabilities (role_id, capability_id)
SELECT lr.role_id, c.capability_id
FROM lookup_role lr, capabilities c
WHERE lr.code = 'admin';

INSERT INTO role_capabilities (role_id, capability_id)
SELECT lr.role_id, c.capability_id
FROM lookup_role lr, capabilities c
WHERE lr.code = 'content-manager' AND c.code = 'content:manage';

INSERT INTO role_capabilities (role_id, capability_id)
SELECT lr.role_id, c.capability_id
FROM lookup_role lr, capabilities c
WHERE lr.code = 'community-manager' AND c.code = 'community:manage';

INSERT INTO role_capabilities (role_id, capability_id)
SELECT lr.role_id, c.capability_id
FROM lookup_role lr, capabilities c
WHERE lr.code = 'data-manager' AND c.code = 'data:manage';

INSERT INTO role_capabilities (role_id, capability_id)
SELECT lr.role_id, c.capability_id
FROM lookup_role lr, capabilities c
WHERE lr.code = 'ecommerce-manager' AND c.code = 'ecommerce:manage';

-- The wiki widget's 3-way admin/content-manager/community-manager OR-check (issue #701 survey)
-- means content-manager and community-manager both also need content:manage's wiki slice.
-- Kept coarse (whole content:manage) rather than splitting out a wiki-only capability in this
-- walking-skeleton PR - narrowing that is future work once a widget actually needs it.
INSERT INTO role_capabilities (role_id, capability_id)
SELECT lr.role_id, c.capability_id
FROM lookup_role lr, capabilities c
WHERE lr.code = 'community-manager' AND c.code = 'content:manage';
