-- Adds an optional self-hosted map tile server url, used when maps.service.tiles is 'custom'
-- No property_type: the url validator rejects the required {z}/{x}/{y} placeholders; FindMapTilesCredentialsCommand validates instead
INSERT INTO site_properties (property_order, property_label, property_name, property_value)
VALUES (40, 'Custom Map Tiles Url ({z}/{x}/{y} template)', 'maps.custom.tileserver.url', '')
ON CONFLICT (property_name) DO NOTHING;
