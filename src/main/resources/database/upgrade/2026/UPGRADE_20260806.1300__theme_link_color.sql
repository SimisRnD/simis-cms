-- theme.link.color is read by main.jsp to color <a> tags but was never seeded by any install or
-- upgrade script, so it could never appear on the Theme Settings page -- admins had no UI path to
-- ever set link color, and the CSS rule that depends on it never fired.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (16, 'Link Color', 'theme.link.color', '#0067ff', 'color')
ON CONFLICT (property_name) DO NOTHING;
