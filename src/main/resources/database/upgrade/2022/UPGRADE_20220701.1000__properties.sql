-- E-learning

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (16, 'LRS Auth Header', 'elearning.lrs.authHeader', '', 'text');

-- BI

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (1, 'Enable bi?', 'bi.enabled', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Superset Url', 'bi.superset.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (12, 'Superset Id', 'bi.superset.id', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (14, 'Superset Secret', 'bi.superset.secret', '', 'text');
