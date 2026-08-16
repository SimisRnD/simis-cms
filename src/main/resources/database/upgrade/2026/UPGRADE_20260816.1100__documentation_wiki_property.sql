-- Adds the site property that lets an admin choose which of their own wikis appears on the
-- built-in /admin/documentation page. That page was previously hardcoded to a wiki uniqueId
-- ("simis-documentation") that no install has ever had, so the link showed "Wiki Has Not Been
-- Setup" no matter what wiki an admin created. Existing installs get the same blank default as a
-- fresh install -- an admin picks a wiki by pasting its Unique Id (shown on /admin/wikis) into
-- this new field.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (243, 'Documentation wiki (Unique Id)', 'documentation.wiki.uniqueId', '', 'text');
