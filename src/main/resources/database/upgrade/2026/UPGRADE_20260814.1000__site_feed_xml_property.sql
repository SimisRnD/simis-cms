-- Issue #1182: adds the site property gating the Atom feed at /feed.xml and /feed/{blog}.xml.
-- Defaults to false, matching site.sitemap.xml -- syndicating content is an opt-in decision an
-- administrator makes, not something an upgrade turns on for an existing site without being asked.
--
-- property_order 25 rather than 23: the value belongs beside site.sitemap.xml (22), but 23 and 24
-- are already taken by site.cart and site.registrations, and renumbering live rows to close that
-- gap would conflict with any other migration in flight touching the same table.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (25, 'Is the blog feed enabled?', 'site.feed.xml', 'false', 'boolean')
ON CONFLICT (property_name) DO NOTHING;
