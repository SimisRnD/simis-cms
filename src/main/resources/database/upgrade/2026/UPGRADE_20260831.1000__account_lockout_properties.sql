-- Adds security.lockout.threshold and security.lockout.durationMinutes, the two durable
-- account-lockout settings AuthenticateLoginCommand already enforces (#295, AC-7 / 800-171 3.1.8):
-- how many consecutive failed logins lock an account, and how long the lock lasts.
--
-- PR #318 shipped the enforcement reading both through LoadSitePropertyCommand.loadByName and
-- documented them in javadoc as "site property", but no migration ever inserted a row -- not
-- UPGRADE_20260725.1000__account_lockout.sql, which added only the users columns, and nothing in
-- install/. Lockout has therefore always worked, at the code-side defaults of 5 attempts and 15
-- minutes, with no way for an administrator to change either one. That is not just a missing
-- default: SitePropertiesEditorWidget renders and saves only the rows
-- SitePropertyRepository.findAllByPrefix(prefix) returns, so a property with no row has no field on
-- any settings page, and saving that page cannot create one.
--
-- Seeded under the "security" prefix instead of the "account" prefix the code originally read.
-- admin-layout.xml registers no "account" prefix on any page, so the original names had nowhere to
-- surface; "security" puts them on the existing /admin/security-properties page directly beneath
-- the security.rateLimit.* settings they interact with (the per-username rate limit throttles
-- attempts, this locks the account), and brings them under SitePropertiesEditorWidget's step-up
-- re-authentication gate, which is right for a setting whose whole effect is how easily a control
-- can be relaxed. The two loadByName call sites in AuthenticateLoginCommand move with them.
--
-- property_order 22/23 sorts them immediately after the per-username rate limit at 20/21
-- (findAllByPrefix orders by property_order, property_name). The seeded values are exactly the
-- constants the code already falls back to, so no existing site changes behaviour.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (22, 'Failed attempts before account lockout', 'security.lockout.threshold', '5', 'text')
ON CONFLICT (property_name) DO NOTHING;

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (23, 'Account lockout duration (minutes)', 'security.lockout.durationMinutes', '15', 'text')
ON CONFLICT (property_name) DO NOTHING;

-- No migration ever created an account.lockout.* row and the admin UI cannot create one, so a site
-- that has these set at all can only have had them inserted by hand against the database after
-- reading the javadoc. Carry such a value across before dropping the old row, so an operator who
-- deliberately tightened the threshold does not silently get loosened back to the default by this
-- rename. On every ordinary site both statements match nothing.
UPDATE site_properties SET property_value = o.property_value
  FROM site_properties o
 WHERE site_properties.property_name = 'security.lockout.threshold'
   AND o.property_name = 'account.lockout.threshold'
   AND o.property_value IS NOT NULL AND o.property_value <> '';

UPDATE site_properties SET property_value = o.property_value
  FROM site_properties o
 WHERE site_properties.property_name = 'security.lockout.durationMinutes'
   AND o.property_name = 'account.lockout.durationMinutes'
   AND o.property_value IS NOT NULL AND o.property_value <> '';

DELETE FROM site_properties
 WHERE property_name IN ('account.lockout.threshold', 'account.lockout.durationMinutes');
