-- Opt-in: when enabled, visitor and page-hit tracking is skipped for a request that sends the
-- Do-Not-Track (DNT: 1) or Global Privacy Control (Sec-GPC: 1) header. Default off for backward
-- compatibility (existing analytics behavior is unchanged until an operator enables it).
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (7, 'Honor Do-Not-Track / Global Privacy Control?', 'analytics.honorDnt', 'false', 'boolean')
ON CONFLICT (property_name) DO NOTHING;
