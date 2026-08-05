-- Decouples search-log retention (web_searches, search_analytics) from the shared
-- analytics.retentionDays property (see UPGRADE_20260719.1005), which also governs
-- session/page-hit retention. The two search-logging tables back the Search Analytics
-- page and can reasonably be pruned on their own, shorter schedule. web_searches had no
-- cleanup job prior to this and grew unbounded -- see WebSearchCleanupJob.
INSERT INTO site_properties (property_label, property_name, property_value, property_type)
VALUES ('Search Log Retention (days)', 'search.retentionDays', '180', 'text')
ON CONFLICT (property_name) DO NOTHING;
