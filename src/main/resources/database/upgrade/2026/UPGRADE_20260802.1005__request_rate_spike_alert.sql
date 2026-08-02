-- Issue #569 slice 1: configurable alert threshold for the request-rate-per-IP spike dashboard
-- tile (peak hits from a single non-bot IP in the last hour), matching the
-- search.zeroResultAlertThreshold precedent (see UPGRADE_20260801.1002). Placed under the
-- "security" prefix so it surfaces on the existing /admin/security-properties settings page and
-- requires step-up auth to change, like the security.rateLimit.* properties (see
-- NEW_10140__new_security_properties.sql / UPGRADE_20260801.1006__security_rate_limit_properties.sql).
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (30, 'IP Request Rate Alert Threshold (hits/hour)', 'security.ipRequestRateAlertThreshold', '300', 'text')
ON CONFLICT (property_name) DO NOTHING;
