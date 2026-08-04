-- Issue #569 slice 2: configurable windows for the geographic-anomaly dashboard tile (a country
-- newly appearing in the top 5 by session count in a short recent window that wasn't in the top 5
-- during a longer baseline window). Placed under the "security" prefix so it surfaces on the
-- existing /admin/security-properties settings page and requires step-up auth to change, matching
-- security.ipRequestRateAlertThreshold's precedent (see UPGRADE_20260802.1005).
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (40, 'Geo Anomaly Baseline Window (days)', 'security.geoAnomalyBaselineDays', '30', 'text')
ON CONFLICT (property_name) DO NOTHING;
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (41, 'Geo Anomaly Recent Window (hours)', 'security.geoAnomalyRecentHours', '24', 'text')
ON CONFLICT (property_name) DO NOTHING;
