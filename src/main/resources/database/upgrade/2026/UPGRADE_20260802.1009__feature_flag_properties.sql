-- Issue #410: mirrors NEW_10150__new_feature_flag_properties.sql for existing installs -- see that
-- file for the full rationale (FeatureFlagCommand, the cache-invalidation dependency, and why
-- features.layout-editor defaults to true).
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (10, 'Enable visual page layout editor', 'features.layout-editor', 'true', 'boolean')
ON CONFLICT (property_name) DO NOTHING;

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (20, 'Enable item tag facet search', 'features.item-tags-facet-search', 'false', 'boolean')
ON CONFLICT (property_name) DO NOTHING;
