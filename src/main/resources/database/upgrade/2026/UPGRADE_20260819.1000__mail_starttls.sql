-- Adds the mail.starttls site property, so an existing site can use an SMTP provider that offers
-- STARTTLS (plain connection on port 587, upgraded to TLS afterward) instead of implicit SSL/TLS
-- (encrypted from connect, port 465). Until now EmailCommand only ever called setSSLOnConnect(),
-- so STARTTLS-only providers could not be used at all -- Microsoft 365 and Azure Communication
-- Services among them, since neither offers an implicit-SSL port as an alternative.
--
-- Seeds 'false', which is exactly the behavior every existing site has today (no STARTTLS), so
-- this changes nothing until an admin turns it on. Plain idempotent insert, not a conditional
-- update, since the property never existed before this migration (same pattern as
-- UPGRADE_20260818.1500__logo_dark_mode_color_properties.sql).
--
-- No property_order: the other mail.* properties don't set one either, and findAllByPrefix sorts
-- by "property_order, property_name", so this sorts alphabetically into place directly after
-- mail.ssl -- matching where NEW_10000__new_database.sql puts it on a fresh install.
INSERT INTO site_properties (property_label, property_name, property_value, property_type)
VALUES ('SMTP STARTTLS', 'mail.starttls', 'false', 'boolean')
ON CONFLICT (property_name) DO NOTHING;
