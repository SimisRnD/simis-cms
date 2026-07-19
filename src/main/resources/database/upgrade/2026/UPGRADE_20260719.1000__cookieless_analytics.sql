-- Register the cookieless-analytics toggle so existing installs get the setting.
-- Idempotent: a fresh install already seeds it via NEW_ (property_name is UNIQUE), so ON CONFLICT no-ops there.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (5, 'Cookieless analytics (no visitor cookie)?', 'analytics.cookieless', 'false', 'boolean')
ON CONFLICT (property_name) DO NOTHING;
