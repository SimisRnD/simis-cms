-- Issue #2130, step 4: where the ClamAV clamd that scans form uploads is. Same rows as
-- NEW_10140__new_security_properties.sql, for databases that already exist. They appear on
-- /admin/security-properties beside the other security settings.
--
-- The host is blank on purpose: MalwareScanCommand fails closed, so with no scanner configured every
-- file attached to a form is rejected. Forms without a file field are not affected, and file fields
-- stay off live forms until the upload pipeline is wired in, so seeding these changes nothing yet.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (60, 'Malware scanner host (ClamAV clamd)', 'security.clamav.host', '', 'text')
ON CONFLICT (property_name) DO NOTHING;

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (61, 'Malware scanner port', 'security.clamav.port', '3310', 'text')
ON CONFLICT (property_name) DO NOTHING;
