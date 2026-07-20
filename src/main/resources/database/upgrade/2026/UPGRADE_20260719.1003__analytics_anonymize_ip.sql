-- Opt-in: when enabled, stored analytics IP addresses (visitor sessions and page hits) are masked to
-- the /24 (IPv4) or /48 (IPv6) network. Geo location is resolved from the full IP at session creation,
-- so the map is unaffected. Default off for backward compatibility.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (6, 'Anonymize analytics IP addresses?', 'analytics.anonymizeIp', 'false', 'boolean')
ON CONFLICT (property_name) DO NOTHING;
