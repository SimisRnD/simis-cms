-- Issue #408: database-backed URL redirect management. Mirrors NEW_10180__new_web_redirects.sql
-- exactly (install/ and upgrade/ must stay in sync -- see issue #431's precedent for what happens
-- when they drift, and DatabaseMigrationTest's
-- tablesThatOnlyExistedInUpgradeMigrationsAreOnTheInstallPath()/
-- columnsThatOnlyExistedInUpgradeMigrationsAreOnTheInstallPath() for the regression class this
-- guards against).

CREATE TABLE IF NOT EXISTS web_redirects (
  web_redirect_id BIGSERIAL PRIMARY KEY,
  from_path VARCHAR(500) NOT NULL,
  to_url VARCHAR(2000) NOT NULL,
  status_code INTEGER NOT NULL DEFAULT 301,
  enabled BOOLEAN DEFAULT true,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT REFERENCES users(user_id),
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id),
  CONSTRAINT web_redirects_status_code_check CHECK (status_code IN (301, 302))
);
CREATE UNIQUE INDEX IF NOT EXISTS web_redirects_from_path_idx ON web_redirects(from_path);
CREATE INDEX IF NOT EXISTS web_redirects_enabled_idx ON web_redirects(enabled);
