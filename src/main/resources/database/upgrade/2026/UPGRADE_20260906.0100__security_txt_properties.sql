-- Same securitytxt.* site properties as NEW_10210__new_security_txt_properties.sql, added here for
-- databases that already exist. See that file and SecurityTxtServlet for the full rationale --
-- notably that securitytxt.enabled defaulting to 'true' does not start publishing anything: the
-- servlet 404s while securitytxt.contact is blank, so this upgrade is inert until an administrator
-- fills in a contact.

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (10, 'Enable /.well-known/security.txt', 'securitytxt.enabled', 'true', 'boolean')
ON CONFLICT (property_name) DO NOTHING;

INSERT INTO site_properties (property_order, property_label, property_name, property_value)
VALUES (20, 'Security contact (email or URL)', 'securitytxt.contact', '')
ON CONFLICT (property_name) DO NOTHING;

INSERT INTO site_properties (property_order, property_label, property_name, property_value)
VALUES (30, 'Vulnerability disclosure policy URL', 'securitytxt.policy', '')
ON CONFLICT (property_name) DO NOTHING;

INSERT INTO site_properties (property_order, property_label, property_name, property_value)
VALUES (40, 'Acknowledgments page URL', 'securitytxt.acknowledgments', '')
ON CONFLICT (property_name) DO NOTHING;

INSERT INTO site_properties (property_order, property_label, property_name, property_value)
VALUES (50, 'Encryption key URL', 'securitytxt.encryption', '')
ON CONFLICT (property_name) DO NOTHING;

INSERT INTO site_properties (property_order, property_label, property_name, property_value)
VALUES (60, 'Preferred languages (e.g. en, es)', 'securitytxt.preferredLanguages', '')
ON CONFLICT (property_name) DO NOTHING;
