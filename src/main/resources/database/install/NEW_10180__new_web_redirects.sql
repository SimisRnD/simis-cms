-- Issue #408: database-backed URL redirect management, replacing the CMS_PATH/config/cms/redirects.csv
-- file that previously required file system access and a server restart to change. A row here is
-- checked by WebRequestFilter (via a Caffeine-cached lookup keyed by from_path) before the legacy
-- CSV map, so an admin-managed redirect can shadow a not-yet-migrated CSV entry for the same path.
-- The CSV file remains a legacy-import source during the transition (see
-- ImportLegacyRedirectsCommand) and continues to be read directly by LoadRedirectsCommand, which
-- now also logs a startup warning when the file is present.
-- from_path is unique because exactly one destination should ever apply to a given incoming path.
-- status_code is constrained to 301 (permanent) or 302 (temporary), matching the two redirect
-- kinds WebRequestFilter's do301()/do302() helpers already support.
CREATE TABLE web_redirects (
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
CREATE UNIQUE INDEX web_redirects_from_path_idx ON web_redirects(from_path);
CREATE INDEX web_redirects_enabled_idx ON web_redirects(enabled);
