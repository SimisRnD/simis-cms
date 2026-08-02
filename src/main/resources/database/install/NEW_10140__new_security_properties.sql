-- Issue #487: makes the IP- and username-based rate limits in RateLimitCommand admin-configurable
-- instead of hardcoded. Defaults match the previous hardcoded values exactly (10 attempts / 30
-- minutes by IP, 5 attempts / 30 minutes by username), so installing this migration changes no
-- behavior until an admin actually edits these settings.

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Max attempts per IP address', 'security.rateLimit.ipMaxAttempts', '10', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (11, 'Time window (minutes) per IP address', 'security.rateLimit.ipWindowMinutes', '30', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'Max attempts per username', 'security.rateLimit.usernameMaxAttempts', '5', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (21, 'Time window (minutes) per username', 'security.rateLimit.usernameWindowMinutes', '30', 'text');
