-- Issue #1615: reCAPTCHA Enterprise credentials.
--
-- A key issued by Google's current console cannot be verified by the legacy siteverify endpoint at
-- all -- not with its secret key, and not with the "legacy secret key" the console offers for
-- third-party integrations, which covers checkbox keys only. Verified against the live service on a
-- policy-based key: invalid-input-response every time. So a site that creates a key today has no
-- working route without these.
--
-- Enterprise is inferred from projectid and apikey both being set, rather than from a fourth
-- captcha.service value: that property is free text, and issue #1614 is what a typo in it costs.
--
-- Matching inserts live in the install script. A property added to only one of the two is invisible
-- on whichever install path missed it (issue #1478).
INSERT INTO site_properties (property_order, property_label, property_name, property_value)
  SELECT 31, 'Google reCAPTCHA Enterprise project id', 'captcha.google.projectid', ''
  WHERE NOT EXISTS (SELECT 1 FROM site_properties WHERE property_name = 'captcha.google.projectid');

INSERT INTO site_properties (property_order, property_label, property_name, property_value)
  SELECT 32, 'Google reCAPTCHA Enterprise API key', 'captcha.google.apikey', ''
  WHERE NOT EXISTS (SELECT 1 FROM site_properties WHERE property_name = 'captcha.google.apikey');

-- Optional. Blank means the score is logged rather than enforced.
INSERT INTO site_properties (property_order, property_label, property_name, property_value)
  SELECT 33, 'Google reCAPTCHA minimum score (0.0-1.0)', 'captcha.google.scorethreshold', ''
  WHERE NOT EXISTS (SELECT 1 FROM site_properties WHERE property_name = 'captcha.google.scorethreshold');
