-- Issue #487: makes the IP- and username-based rate limits in RateLimitCommand admin-configurable
-- instead of hardcoded. Defaults match the previous hardcoded values exactly (10 attempts / 30
-- minutes by IP, 5 attempts / 30 minutes by username), so installing this migration changes no
-- behavior until an admin actually edits these settings.

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Max attempts per IP address', 'security.rateLimit.ipMaxAttempts', '10', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (11, 'Time window (minutes) per IP address', 'security.rateLimit.ipWindowMinutes', '30', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'Max attempts per username', 'security.rateLimit.usernameMaxAttempts', '5', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (21, 'Time window (minutes) per username', 'security.rateLimit.usernameWindowMinutes', '30', 'text');

-- Issue #569 slice 1: configurable alert threshold for the request-rate-per-IP spike dashboard
-- tile, matching the search.zeroResultAlertThreshold precedent. Placed under the "security" prefix
-- so it surfaces on this same Security Settings page and requires step-up auth to change, like the
-- rate-limit properties above.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (30, 'IP request rate alert threshold (hits/hour)', 'security.ipRequestRateAlertThreshold', '300', 'text');

-- Issue #569 slice 2: configurable windows for the geographic-anomaly dashboard tile (a country
-- newly appearing in the top 5 by session count). Same "security" prefix / step-up-auth placement
-- as the properties above.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (40, 'Geo anomaly baseline window (days)', 'security.geoAnomalyBaselineDays', '30', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (41, 'Geo anomaly recent window (hours)', 'security.geoAnomalyRecentHours', '24', 'text');

-- Issue #419: how long a generated draft-preview link stays valid. Seeded on existing deployments
-- by UPGRADE_20260804.1002__web_page_preview_tokens.sql, then sentence-cased by
-- UPGRADE_20260814.1500, so the label here is the one an upgraded deployment ends on. Without this
-- row a fresh install falls back to GeneratePreviewLinkCommand's DEFAULT_TTL_HOURS and the setting
-- never appears on this page for an admin to change.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (40, 'Draft preview link expiry (hours)', 'security.previewLinkTtlHours', '24', 'text');

-- Issue #1430: the candidate Content-Security-Policy that PageServlet emits as a
-- Content-Security-Policy-Report-Only header, so the directives that cannot be written by reading
-- the source (a vendor SDK's runtime endpoints) can be collected from real traffic instead. Seeded
-- on existing deployments by UPGRADE_20260827.1100__csp_report_only_property.sql; mirrored here
-- because a fresh install never runs the upgrade scripts. Without this row there is no field on
-- /admin/security-properties -- SitePropertiesEditorWidget renders only the rows
-- SitePropertyRepository.findAllByPrefix("security") returns, and saving the page cannot create
-- one -- so report-only mode and the /csp-report collector are unreachable on a fresh install.
-- Blank keeps the header and that endpoint off until an administrator sets a policy to test.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (40, 'CSP report-only policy', 'security.csp.reportOnly', '', 'text');

-- Issue #295: the durable account-lockout thresholds AuthenticateLoginCommand enforces -- how many
-- consecutive failed logins lock an account, and for how long. PR #318 shipped the enforcement
-- reading these as site properties but never seeded a row in either install/ or upgrade/, so the
-- code-side defaults (5 attempts / 15 minutes) were the only values any site could ever have:
-- SitePropertiesEditorWidget renders and saves only the rows
-- SitePropertyRepository.findAllByPrefix(prefix) returns, and saving a page cannot create one.
-- Seeded under "security" rather than the original "account" prefix so they land on the existing
-- /admin/security-properties page beside the security.rateLimit.* settings they interact with, and
-- so editing them requires step-up auth like the other security-sensitive prefixes -- raising the
-- threshold weakens a control. There is no "account" prefix registered in admin-layout.xml, so the
-- original names had no page to appear on at all. property_order 22/23 places them immediately
-- after the per-username rate limit (20/21), the closest sibling behaviour. Values match the
-- code-side defaults exactly, so no site changes behaviour.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (22, 'Failed attempts before account lockout', 'security.lockout.threshold', '5', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (23, 'Account lockout duration (minutes)', 'security.lockout.durationMinutes', '15', 'text');

-- Issue #1701: requires a caller of GET /api/content/{uniqueId} to have access to at least one web
-- page that renders the content. Seeded on existing deployments by
-- UPGRADE_20260831.1600__content_api_page_access.sql; mirrored here because a fresh install never
-- runs the upgrade scripts, and SitePropertiesEditorWidget renders only rows that already exist.
-- 'true' is both the seeded value and the behaviour when the row is missing -- this closes a hole
-- rather than adding a restriction, so the default is the safe side.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (26, 'Require page access for the content API', 'security.contentApi.enforcePageAccess', 'true', 'boolean');
