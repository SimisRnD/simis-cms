-- Issue #519: Cloudflare Turnstile as a second captcha.service option alongside Google reCAPTCHA.
-- Follows the exact shape of the existing captcha.google.* rows in NEW_10000__new_database.sql,
-- which fresh installs already get identically. captcha.turnstile.secretkey is encrypted at rest --
-- see SecretSitePropertiesCommand.SECRET_PROPERTY_NAMES, which gets the matching entry in this
-- same change.
INSERT INTO site_properties (property_order, property_label, property_name, property_value)
VALUES (40, 'Cloudflare Turnstile Site Key', 'captcha.turnstile.sitekey', '')
ON CONFLICT (property_name) DO NOTHING;

INSERT INTO site_properties (property_order, property_label, property_name, property_value)
VALUES (50, 'Cloudflare Turnstile Secret Key', 'captcha.turnstile.secretkey', '')
ON CONFLICT (property_name) DO NOTHING;
