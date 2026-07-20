-- Makes the web-page-hit retention window configurable (was hardcoded to 365 days). The daily cleanup
-- job deletes hits older than this many days. Parsed to a bounded integer in code before use.
INSERT INTO site_properties (property_order, property_label, property_name, property_value)
VALUES (8, 'Analytics data retention (days)', 'analytics.retentionDays', '365')
ON CONFLICT (property_name) DO NOTHING;
