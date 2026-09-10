-- Moves the password-age warning threshold under the "security" prefix so an administrator can
-- actually change it.
--
-- UPGRADE_20260729.2002 (#492) seeded this as 'password.maxAgeDays'. The row is correct and both
-- read sites (UserDetailsWidget, UsersListWidget) resolve it, but "password" is registered as a
-- sitePropertiesEditor <prefix> on no admin page -- SitePropertyRepository.findAllByPrefix matches
-- property_name LIKE prefix || '.%', so /admin/security-properties ("security") never returned it
-- and SitePropertiesEditorWidget renders and saves only the rows that query returns. The setting
-- therefore existed in the database with no field anywhere in the UI, and changing it required a
-- direct database update.
--
-- Renaming rather than registering a one-property "password" page keeps it with the
-- security.password.minLength / security.password.requireComplexity rows it belongs with, and
-- picks up that page's step-up re-authentication gate (SitePropertiesEditorWidget's
-- SECURITY_SENSITIVE_PREFIXES). property_order moves from 2 to 20 to sort beside them.
--
-- Renamed in place so a deployment that already tuned the threshold keeps its configured value,
-- along with the modified/modified_by audit columns.
UPDATE site_properties
SET property_name = 'security.password.maxAgeDays',
    property_label = 'Password age warning threshold (days)',
    property_order = 20,
    property_type = 'text'
WHERE property_name = 'password.maxAgeDays'
  AND NOT EXISTS (SELECT 1 FROM site_properties WHERE property_name = 'security.password.maxAgeDays');

-- No-op after a successful rename above. Only does anything if both rows somehow existed, in which
-- case the UPDATE was skipped (property_name is UNIQUE) and the old orphan has to go.
DELETE FROM site_properties WHERE property_name = 'password.maxAgeDays';

-- Belt and braces: if neither row was present, seed the default so the field still appears.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (20, 'Password age warning threshold (days)', 'security.password.maxAgeDays', '90', 'text')
ON CONFLICT (property_name) DO NOTHING;
