-- Grammar/consistency fix, no behavior change: "Url" -> "URL" (matches the same fix applied to the
-- BI Settings page's Superset/Metabase URL fields).
UPDATE site_properties SET property_label = 'Custom Map Tiles URL ({z}/{x}/{y} template)' WHERE property_name = 'maps.custom.tileserver.url';
