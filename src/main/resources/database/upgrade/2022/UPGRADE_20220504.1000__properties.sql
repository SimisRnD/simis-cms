
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (1, 'Enable e-commerce?', 'ecommerce.enabled', 'true', 'disabled');

CREATE UNIQUE INDEX users_lc_email ON users (LOWER(email));
CREATE UNIQUE INDEX users_lc_username ON users (LOWER(username));
