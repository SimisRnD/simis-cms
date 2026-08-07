-- Adds the new password-length/complexity policy properties (PasswordPolicyCommand), configurable
-- from the Security Settings admin page. Existing installs get the same defaults as a fresh install.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Minimum Password Length', 'security.password.minLength', '15', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'Require Password Complexity?', 'security.password.requireComplexity', 'true', 'boolean');
