-- Adds the organization details the structured data needs (issue #1795): a postal address and a
-- founding year for the Organization node, which until now could emit only name, url, logo and
-- sameAs.
--
-- These are new rows, not a rename: there is no earlier property to carry a value over from. The
-- old site.footer.address.line1/.line2/.phone/.hours were removed outright by
-- UPGRADE_20220404.1000__properties.sql and nothing replaced them, so an existing site has had no
-- address anywhere in the platform since then.
--
-- Seeded empty on purpose. PageServlet omits any part of the address that is blank and omits the
-- address entirely when every part is, so an existing site emits exactly what it emits today until
-- an administrator fills these in.
--
-- Paired with the same rows in install/NEW_10000__new_database.sql. Both tracks, per issue #1478:
-- SitePropertiesEditorWidget renders and saves only the rows findAllByPrefix returns, so a property
-- with no row has no field on any settings page and saving that page cannot create one -- an
-- install-only or upgrade-only row is invisible on exactly the sites that missed it.

INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (33, 'Street address', 'site.address.street', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (34, 'City', 'site.address.city', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (35, 'State or region', 'site.address.state', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (36, 'Postal code', 'site.address.postalCode', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (37, 'Country', 'site.address.country', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (38, 'Year founded', 'site.founded', '');
