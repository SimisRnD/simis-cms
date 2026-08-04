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
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (30, 'IP Request Rate Alert Threshold (hits/hour)', 'security.ipRequestRateAlertThreshold', '300', 'text');

-- Issue #419: how long a generated draft-preview link stays valid before it stops showing the
-- draft and silently falls back to the live page. Under the "security" prefix (same as the
-- properties above) since it's a bearer-token TTL -- changing it requires step-up auth like
-- every other security-sensitive setting on this page.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (40, 'Draft Preview Link Expiry (hours)', 'security.previewLinkTtlHours', '24', 'text');
