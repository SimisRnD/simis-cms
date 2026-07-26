-- Analytics consent gate (#366): the site-wide switch for withholding third-party analytics
-- scripts until the visitor accepts a consent banner. Default 'false' -- existing sites keep
-- loading analytics immediately, so this changes nothing on upgrade.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (9, 'Require visitor consent before loading analytics?', 'analytics.consentRequired', 'false', 'boolean')
ON CONFLICT (property_name) DO NOTHING;
