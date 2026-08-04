-- Issue #417: same llms.* site properties as NEW_10170__new_llms_properties.sql, added here for
-- databases that already exist. See that file and LlmsTxtServlet for the full rationale.

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (10, 'Enable /llms.txt', 'llms.enabled', 'true', 'boolean')
ON CONFLICT (property_name) DO NOTHING;

INSERT INTO site_properties (property_order, property_label, property_name, property_value)
VALUES (20, 'Custom llms.txt description', 'llms.description', '')
ON CONFLICT (property_name) DO NOTHING;
