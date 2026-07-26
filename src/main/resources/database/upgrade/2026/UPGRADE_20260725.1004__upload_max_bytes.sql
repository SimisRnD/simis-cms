-- Maximum permitted upload size in bytes; applied before writing to disk.
-- Operators can tune this via Admin > Site Properties without a code change.
-- Default is 10 MB; the @MultipartConfig hard limit on PageServlet is 100 MB.
INSERT INTO site_properties (property_order, property_label, property_name, property_value)
VALUES (10, 'Maximum upload size (bytes)', 'system.upload.maxBytes', '10485760')
ON CONFLICT (property_name) DO NOTHING;
