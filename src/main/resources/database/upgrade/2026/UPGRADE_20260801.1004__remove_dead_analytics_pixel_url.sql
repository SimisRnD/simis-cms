-- Issue #517: removes the "Image Pixel URL" analytics setting (analytics.pixel.url). It has never
-- been read anywhere outside its own site_properties row -- no Java, JSP, or JS ever consumes it.
DELETE FROM site_properties WHERE property_name = 'analytics.pixel.url';
