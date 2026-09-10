-- The securitytxt.* site property namespace backing SecurityTxtServlet
-- (/.well-known/security.txt), the RFC 9116 document a security researcher looks for before
-- reporting a vulnerability. Edited from the admin UI at /admin/security-txt-properties via the
-- same generic sitePropertiesEditor widget every other /admin/*-properties page uses, mirroring
-- the llms.* and robots.* precedents (see NEW_10170__new_llms_properties.sql).
--
-- securitytxt.enabled defaults to 'true' to match llms.enabled, but that does NOT mean a fresh
-- install starts publishing one: the servlet returns 404 whenever securitytxt.contact is blank,
-- because RFC 9116 makes Contact mandatory and a document naming no way to reach anyone is worse
-- than no document -- a reporter reads it as a channel that exists and stops looking. So the
-- shipped state is inert for every existing site, and publishing begins only when an administrator
-- supplies a contact.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Enable /.well-known/security.txt', 'securitytxt.enabled', 'true', 'boolean');

-- The one mandatory field. Accepts several, separated by commas or newlines, in decreasing order
-- of preference. A bare address is normalized to a mailto: URI by the servlet, since that is what
-- an administrator will actually type and RFC 9116 requires a URI.
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (20, 'Security contact (email or URL)', 'securitytxt.contact', '');

-- Optional RFC 9116 fields. No property_type is set on any of them, matching site.description's
-- precedent (NEW_10000__new_database.sql): a NULL type falls through to the editor's plain
-- text-input default.
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (30, 'Vulnerability disclosure policy URL', 'securitytxt.policy', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (40, 'Acknowledgments page URL', 'securitytxt.acknowledgments', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (50, 'Encryption key URL', 'securitytxt.encryption', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (60, 'Preferred languages (e.g. en, es)', 'securitytxt.preferredLanguages', '');
