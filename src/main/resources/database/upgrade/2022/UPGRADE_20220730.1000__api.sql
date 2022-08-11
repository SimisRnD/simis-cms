INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (21, 'Is API enabled?', 'site.api', 'false', 'boolean');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (22, 'Is Sitemap.xml enabled?', 'site.sitemap.xml', 'false', 'boolean');
UPDATE site_properties SET property_order = 23 WHERE property_name = 'site.cart';

ALTER TABLE web_pages ADD sitemap_priority NUMERIC(2,1) DEFAULT 0.5;
ALTER TABLE web_pages ADD sitemap_changefreq VARCHAR(20);
