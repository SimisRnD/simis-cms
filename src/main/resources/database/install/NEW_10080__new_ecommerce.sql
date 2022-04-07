-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- E-Commerce

-- Be able to find:
-- Abandoned carts, First purchases, Specific product follow-ups, Any product follow-ups, Category follow-ups, Best customers
-- products, skus, coupons, discounts, customers, orders, subscriptions, taxes, charges, refunds, returns;
-- order_items, subscription_items

CREATE TABLE lookup_fulfillment_options (
  fulfillment_id BIGSERIAL PRIMARY KEY,
  code VARCHAR(20) NOT NULL,
  title VARCHAR(100) NOT NULL,
  enabled BOOLEAN DEFAULT true,
  overrides_others BOOLEAN DEFAULT false
);

INSERT INTO lookup_fulfillment_options (code, title) VALUES ('IN-HOUSE', 'In-House');
INSERT INTO lookup_fulfillment_options (code, title) VALUES ('BOXZOOKA', 'Boxzooka');

CREATE TABLE us_sales_tax_rates (
  state VARCHAR(2),
  zip_code VARCHAR(5),
  region_name VARCHAR(100),
  combined_rate NUMERIC(8,6),
  state_rate NUMERIC(8,6),
  estimated_county_rate NUMERIC(8,6),
  estimated_city_rate NUMERIC(8,6),
  estimated_special_rate NUMERIC(8,6),
  risk_level INTEGER
);
CREATE INDEX us_stax_rates_zip_idx ON us_sales_tax_rates(zip_code);

CREATE TABLE sales_tax_nexus_addresses (
  address_id BIGSERIAL PRIMARY KEY,
  street_address VARCHAR(100),
  address_line_2 VARCHAR(100),
  city VARCHAR(100),
  state VARCHAR(100),
  country VARCHAR(100),
  postal_code VARCHAR(100),
  latitude FLOAT DEFAULT 0,
  longitude FLOAT DEFAULT 0,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT REFERENCES users(user_id),
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id)
);

CREATE TABLE lookup_product_categories (
  category_id BIGSERIAL PRIMARY KEY,
  category_unique_id VARCHAR(255) UNIQUE NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  enabled BOOLEAN DEFAULT true,
  display_order INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX prod_cat_unique_idx ON lookup_product_categories(category_unique_id);

CREATE TABLE products (
  product_id BIGSERIAL PRIMARY KEY,
  product_order INTEGER DEFAULT 100,
  name VARCHAR(255) NOT NULL,
  product_unique_id VARCHAR(255) UNIQUE NOT NULL,
  description TEXT,
  caption VARCHAR(512),
  is_good BOOLEAN DEFAULT false,
  is_service BOOLEAN DEFAULT false,
  is_virtual BOOLEAN DEFAULT false,
  is_download BOOLEAN DEFAULT false,
  active_date TIMESTAMP(3),
  deactivate_on TIMESTAMP(3),
  available_date TIMESTAMP(3),
  shippable BOOLEAN DEFAULT false,
  package_height NUMERIC(5,2) DEFAULT 0,
  package_length NUMERIC(5,2) DEFAULT 0,
  package_width NUMERIC(5,2) DEFAULT 0,
  package_weight_lbs INTEGER DEFAULT 0,
  package_weight_ozs INTEGER DEFAULT 0,
  sku_attributes JSONB,
  image_url VARCHAR(255),
  product_url VARCHAR(255),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT REFERENCES users(user_id),
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id),
  tsv TSVECTOR,
  enabled BOOLEAN DEFAULT true,
  order_count BIGINT NOT NULL DEFAULT 0,
  taxable BOOLEAN DEFAULT false,
  tax_code VARCHAR(20),
  square_catalog_id VARCHAR(56),
  fulfillment_id BIGINT REFERENCES lookup_fulfillment_options(fulfillment_id),
  exclude_us_states VARCHAR(255)
);
CREATE UNIQUE INDEX products_unique_idx ON products(product_unique_id);
CREATE INDEX products_ord_idx ON products(product_order);
CREATE INDEX products_nm_idx ON products(name);

CREATE INDEX products_tsv_idx ON products USING gin(tsv);
CREATE OR REPLACE FUNCTION products_tsv_trigger() RETURNS trigger AS $$
begin
  new.tsv :=
    setweight(to_tsvector('title_stem', new.name), 'A') ||
    setweight(to_tsvector('title_stem', coalesce(new.caption,'')), 'B') ||
    setweight(to_tsvector('title_stem', coalesce(new.description,'')), 'D');
  return new;
end
$$ LANGUAGE plpgsql;

CREATE TABLE product_categories (
  category_id BIGSERIAL PRIMARY KEY,
  product_id BIGINT REFERENCES products(product_id) NOT NULL,
  product_category_id BIGINT REFERENCES lookup_product_categories(category_id) NOT NULL,
  display_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE product_skus (
  sku_id BIGSERIAL PRIMARY KEY,
  product_id BIGINT REFERENCES products(product_id) NOT NULL,
  sku_order INTEGER DEFAULT 100,
  sku VARCHAR(20) NOT NULL UNIQUE,
  currency VARCHAR(3),
  price NUMERIC(15,6) DEFAULT 0,
  cost_of_good NUMERIC(15,6) DEFAULT 0,
  barcode VARCHAR(1024),
  attributes JSONB,
  active_date TIMESTAMP(3),
  deactivate_on TIMESTAMP(3),
  available_date TIMESTAMP(3),
  inventory_qty INTEGER DEFAULT 0,
  inventory_qty_low INTEGER DEFAULT 0,
  inventory_qty_incoming INTEGER DEFAULT 0,
  minimum_purchase_qty INTEGER DEFAULT 0,
  maximum_purchase_qty INTEGER DEFAULT 0,
  allow_backorders BOOLEAN DEFAULT false,
  package_height NUMERIC(5,2) DEFAULT 0,
  package_length NUMERIC(5,2) DEFAULT 0,
  package_width NUMERIC(5,2) DEFAULT 0,
  package_weight_lbs INTEGER DEFAULT 0,
  package_weight_ozs INTEGER DEFAULT 0,
  image_url VARCHAR(255),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT REFERENCES users(user_id),
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id),
  enabled BOOLEAN DEFAULT true,
  square_variation_id VARCHAR(56),
  strike_price NUMERIC(15,6) DEFAULT 0
);
CREATE INDEX prod_sku_prod_id_idx ON product_skus(product_id);
CREATE INDEX prod_sku_ord_idx ON product_skus(sku_order);

-- Coupons
-- Features:
--   apply to max number of qualifying items
--   flat discount amount
--   percent of subtotal
--   free shipping
--   max_number_of_usages
-- usage_count

-- CREATE TABLE coupons (
--   coupon_id SERIAL PRIMARY KEY,
--   coupon_code VARCHAR(255) NOT NULL,
--   valid_start TIMESTAMP(3),
--   valid_end TIMESTAMP(3),
--   coupon_type VARCHAR(10),
--   max_amount NUMERIC(5,2) DEFAULT 0
-- );


CREATE TABLE customers (
  customer_id BIGSERIAL PRIMARY KEY,
  customer_unique_id VARCHAR(255),
  email VARCHAR(255),
  first_name VARCHAR(100),
  last_name VARCHAR(100),
  organization VARCHAR(100),
  barcode VARCHAR(1024),
  street_address VARCHAR(100),
  address_line_2 VARCHAR(100),
  address_line_3 VARCHAR(100),
  city VARCHAR(100),
  state VARCHAR(100),
  country VARCHAR(100),
  postal_code VARCHAR(100),
  county VARCHAR(100),
  phone_number VARCHAR(30),
  billing_first_name VARCHAR(100),
  billing_last_name VARCHAR(100),
  billing_organization VARCHAR(100),
  billing_street_address VARCHAR(100),
  billing_address_line_2 VARCHAR(100),
  billing_address_line_3 VARCHAR(100),
  billing_city VARCHAR(100),
  billing_state VARCHAR(100),
  billing_country VARCHAR(100),
  billing_postal_code VARCHAR(100),
  billing_county VARCHAR(100),
  billing_phone_number VARCHAR(30),
  shipping_first_name VARCHAR(100),
  shipping_last_name VARCHAR(100),
  shipping_organization VARCHAR(100),
  shipping_street_address VARCHAR(100),
  shipping_address_line_2 VARCHAR(100),
  shipping_address_line_3 VARCHAR(100),
  shipping_city VARCHAR(100),
  shipping_state VARCHAR(100),
  shipping_country VARCHAR(100),
  shipping_postal_code VARCHAR(100),
  shipping_county VARCHAR(100),
  shipping_phone_number VARCHAR(30),
  tax_id VARCHAR(100),
  remote_customer_id VARCHAR(255),
  currency VARCHAR(3) NOT NULL DEFAULT 'usd',
  account_balance NUMERIC(15,6) DEFAULT 0,
  total_spend NUMERIC(15,6) DEFAULT 0,
  order_count INTEGER DEFAULT 0,
  delinquent BOOLEAN DEFAULT false,
  discount VARCHAR(255),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT REFERENCES users(user_id),
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id),
  latitude FLOAT DEFAULT 0,
  longitude FLOAT DEFAULT 0,
  billing_latitude FLOAT DEFAULT 0,
  billing_longitude FLOAT DEFAULT 0,
  shipping_latitude FLOAT DEFAULT 0,
  shipping_longitude FLOAT DEFAULT 0
);

-- customer_subscriptions

-- "subscriptions": {
-- "object": "list",
-- "data": [],
-- "has_more": false,
-- "total_count": 0,
-- "url": "/v1/customers/cus_EbHejiUmeB6RlQ/subscriptions"
-- },
-- "tax_info": null,
-- "tax_info_verification": null
-- }

-- For Goods:
--   A created order can become paid or canceled.
--   A paid order can become fulfilled or canceled. If the order becomes canceled, Stripe will automatically refund the payment.
--   A fulfilled order is normally the final state, but can be returned in which case the payment is refunded.

CREATE TABLE lookup_order_status (
  status_id SERIAL PRIMARY KEY,
  level INTEGER NOT NULL,
  code VARCHAR(20),
  title VARCHAR(100)
);

INSERT INTO lookup_order_status (level, code, title) VALUES (10, 'Created', 'Created');
INSERT INTO lookup_order_status (level, code, title) VALUES (20, 'Paid', 'Paid');
INSERT INTO lookup_order_status (level, code, title) VALUES (26, 'Preparing', 'Preparing');
INSERT INTO lookup_order_status (level, code, title) VALUES (27, 'Partially Prepared', 'Partially Prepared');
INSERT INTO lookup_order_status (level, code, title) VALUES (30, 'Fulfilled', 'Fulfilled');
INSERT INTO lookup_order_status (level, code, title) VALUES (40, 'Shipped', 'Shipped');
INSERT INTO lookup_order_status (level, code, title) VALUES (42, 'Partially Shipped', 'Partially Shipped');
INSERT INTO lookup_order_status (level, code, title) VALUES (50, 'Completed', 'Completed');
INSERT INTO lookup_order_status (level, code, title) VALUES (60, 'On Hold', 'On Hold');
INSERT INTO lookup_order_status (level, code, title) VALUES (70, 'Canceled', 'Canceled');
INSERT INTO lookup_order_status (level, code, title) VALUES (80, 'Refunded', 'Refunded');
INSERT INTO lookup_order_status (level, code, title) VALUES (90, 'Returned', 'Returned');

CREATE TABLE lookup_shipping_carrier (
  carrier_id SERIAL PRIMARY KEY,
  level INTEGER NOT NULL,
  code VARCHAR(20),
  title VARCHAR(100),
  enabled BOOLEAN DEFAULT false
);

INSERT INTO lookup_shipping_carrier (level, code, title, enabled) VALUES (10, 'USPS', 'U.S. Postal Service', true);
INSERT INTO lookup_shipping_carrier (level, code, title, enabled) VALUES (20, 'UPS', 'United Parcel Service', true);
INSERT INTO lookup_shipping_carrier (level, code, title, enabled) VALUES (30, 'FEDEX', 'Federal Express', true);
INSERT INTO lookup_shipping_carrier (level, code, title, enabled) VALUES (40, 'DHL', 'DHL Express', true);

-- These are the values shown on the order
CREATE TABLE lookup_shipping_method (
  method_id SERIAL PRIMARY KEY,
  level INTEGER NOT NULL,
  code VARCHAR(20),
  title VARCHAR(100),
  enabled BOOLEAN DEFAULT false,
  boxzooka_code VARCHAR(30)
);

INSERT INTO lookup_shipping_method (level, code, title, enabled, boxzooka_code) VALUES (10, 'Standard', 'Standard Shipping', true, 'USPS.PARCEL');
INSERT INTO lookup_shipping_method (level, code, title, enabled, boxzooka_code) VALUES (15, 'Priority', 'USPS Priority', true, 'USPS.PRIORITY');
INSERT INTO lookup_shipping_method (level, code, title, enabled, boxzooka_code) VALUES (20, 'Expedited', 'Expedited Delivery', true, 'USPS.PRIORITY');
INSERT INTO lookup_shipping_method (level, code, title, enabled) VALUES (30, '2-Day', '2 Business Day', true);
INSERT INTO lookup_shipping_method (level, code, title, enabled) VALUES (40, 'Intl.', 'International', true);
INSERT INTO lookup_shipping_method (level, code, title, enabled) VALUES (50, 'Pickup', 'Local Pickup', false);
INSERT INTO lookup_shipping_method (level, code, title, enabled) VALUES (100, 'Restricted', 'Unable to ship to location', true);

CREATE TABLE lookup_shipping_countries (
  country_id SERIAL PRIMARY KEY,
  level INTEGER NOT NULL,
  code VARCHAR(20) UNIQUE NOT NULL,
  title VARCHAR(100),
  enabled BOOLEAN DEFAULT false
);

-- North America
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (100, 'US', 'United States', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (110, 'CA', 'Canada', true);
-- Oceania
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (200, 'AU', 'Australia', true);
-- European Union
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (300, 'AT', 'Austria', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (310, 'BE', 'Belgium', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (320, 'BG', 'Bulgaria', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (330, 'HR', 'Croatia', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (340, 'CY', 'Cyprus', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (350, 'CZ', 'Czech Republic', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (360, 'DK', 'Denmark', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (370, 'EE', 'Estonia', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (380, 'FI', 'Finland', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (390, 'FR', 'France', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (400, 'DE', 'Germany', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (410, 'GR', 'Greece', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (420, 'HU', 'Hungary', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (430, 'IE', 'Ireland', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (440, 'IT', 'Italy', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (450, 'LV', 'Latvia', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (460, 'LT', 'Lithuania', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (470, 'LU', 'Luxembourg', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (480, 'MT', 'Malta', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (490, 'NL', 'Netherlands', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (510, 'PL', 'Poland', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (520, 'PT', 'Portugal', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (530, 'RO', 'Romania', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (540, 'SK', 'Slovakia', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (550, 'SI', 'Slovenia', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (560, 'ES', 'Spain', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (570, 'SE', 'Sweden', true);
INSERT INTO lookup_shipping_countries (level, code, title, enabled) VALUES (580, 'GB', 'United Kingdom', true);


-- SHIPPING RATE TABLE FILE (to calculate the price of shipping) use * for wildcard
CREATE TABLE shipping_rates (
  rate_id SERIAL PRIMARY KEY,
  country_code VARCHAR(20) NOT NULL,
  region VARCHAR(200) NOT NULL,
  postal_code VARCHAR(50) NOT NULL,
  min_subtotal NUMERIC(15,6) DEFAULT 0,
  min_weight_oz INTEGER DEFAULT 0,
  shipping_fee NUMERIC(15,6) DEFAULT 0,
  handling_fee NUMERIC(15,6) DEFAULT 0,
  shipping_code VARCHAR(50),
  shipping_method INTEGER REFERENCES lookup_shipping_method(method_id),
  display_text VARCHAR(255),
  exclude_skus VARCHAR(255)
);

-- COUNTRY, REGION, POSTAL_CODE, Min Subtotal, Min Weight, Shipping Fee, Handling Fee, Description
-- US   AK   *      0   0   $10.00     $0     Standard
-- US   HI   *      0   0   $10.00     $0     Standard
-- US   *    27511  0   0   $5.00      $0     Standard
-- US   *    *      0   0   $15.00     $0     2-Day
-- US   *    *      0   0   $20.00     $0     Next Day
-- US   *    *      40  0   $0.00      $0     Standard
-- AUS  *    *      0   0   $9.95      $0     International
-- AUS  *    *      0   9   $19.95     $0     International
-- *    *    *      0   0   $29.95     $0     International


CREATE TABLE pricing_rules (
  rule_id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  error_message TEXT,
  from_date TIMESTAMP(3),
  to_date TIMESTAMP(3),
  promo_code VARCHAR(20),
  uses_per_code INTEGER,
  times_used INTEGER DEFAULT 0,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  enabled BOOLEAN DEFAULT true,
  minimum_subtotal NUMERIC(15,6) DEFAULT 0,
  minimum_order_qty INTEGER DEFAULT 0,
  maximum_order_qty INTEGER DEFAULT 0,
  valid_skus VARCHAR(255),
  invalid_skus VARCHAR(255),
  subtotal_percent INTEGER DEFAULT 0,
  subtract_amount NUMERIC(15,6) DEFAULT 0,
  free_shipping BOOLEAN DEFAULT false,
  free_product_sku VARCHAR(255),
  free_shipping_code VARCHAR(10),
  valid_country_code VARCHAR(10),
  uses_per_customer INTEGER DEFAULT 0,
  item_limit INTEGER DEFAULT 0,
  buy_x_items INTEGER DEFAULT 0,
  get_y_free INTEGER DEFAULT 0
);

CREATE INDEX pri_rule_from_d_idx ON pricing_rules(from_date);
CREATE INDEX pri_rule_to_dat_idx ON pricing_rules(to_date);
CREATE INDEX pri_rule_c_code_idx ON pricing_rules(promo_code);
CREATE INDEX pri_rule_enabled_idx ON pricing_rules(enabled);
CREATE INDEX pri_rule_cc_code_idx ON pricing_rules(valid_country_code);

CREATE TABLE promo_code_list (
  code_id BIGSERIAL PRIMARY KEY,
  pricing_rule_id BIGINT REFERENCES pricing_rules(rule_id),
  promo_code VARCHAR(100) NOT NULL,
  used_date TIMESTAMP(3)
);

CREATE INDEX pro_cod_list_code_idx ON promo_code_list(promo_code);
CREATE INDEX pro_cod_list_udat_idx ON promo_code_list(used_date);

CREATE SEQUENCE order_daily_seq START 1001;
-- ALTER SEQUENCE order_daily_seq RESTART WITH 1001;

CREATE TABLE orders (
  order_id BIGSERIAL PRIMARY KEY,
  order_unique_id VARCHAR(255) UNIQUE NOT NULL,
  customer_id BIGINT REFERENCES customers(customer_id),
  email VARCHAR(255),
  customer_note VARCHAR(500),
  billing_first_name VARCHAR(100),
  billing_last_name VARCHAR(100),
  billing_organization VARCHAR(100),
  billing_street_address VARCHAR(100),
  billing_address_line_2 VARCHAR(100),
  billing_address_line_3 VARCHAR(100),
  billing_city VARCHAR(100),
  billing_state VARCHAR(100),
  billing_country VARCHAR(100),
  billing_postal_code VARCHAR(100),
  billing_county VARCHAR(100),
  billing_phone_number VARCHAR(30),
  shipping_first_name VARCHAR(100),
  shipping_last_name VARCHAR(100),
  shipping_organization VARCHAR(100),
  shipping_street_address VARCHAR(100),
  shipping_address_line_2 VARCHAR(100),
  shipping_address_line_3 VARCHAR(100),
  shipping_city VARCHAR(100),
  shipping_state VARCHAR(100),
  shipping_country VARCHAR(100),
  shipping_postal_code VARCHAR(100),
  shipping_county VARCHAR(100),
  shipping_phone_number VARCHAR(30),
  shipping_method INTEGER REFERENCES lookup_shipping_method(method_id),
  total_items INTEGER DEFAULT 0,
  currency VARCHAR(3) NOT NULL DEFAULT 'usd',
  subtotal_amount NUMERIC(15,6) DEFAULT 0,
  discount_amount NUMERIC(15,6) DEFAULT 0,
  fee_amount NUMERIC(15,6) DEFAULT 0,
  fee_tax_amount NUMERIC(15,6) DEFAULT 0,
  shipping_amount NUMERIC(15,6) DEFAULT 0,
  shipping_tax_amount NUMERIC(15,6) DEFAULT 0,
  tax_amount NUMERIC(15,6) DEFAULT 0,
  total_amount NUMERIC(15,6) DEFAULT 0,
  total_paid NUMERIC(15,6) DEFAULT 0,
  total_pending NUMERIC(15,6) DEFAULT 0,
  total_refunded NUMERIC(15,6) DEFAULT 0,
  status INTEGER REFERENCES lookup_order_status(status_id) NOT NULL,
  has_preorder BOOLEAN DEFAULT false,
  has_backorder BOOLEAN DEFAULT false,
  paid BOOLEAN DEFAULT false,
  processed BOOLEAN DEFAULT false,
  shipped BOOLEAN DEFAULT false,
  canceled BOOLEAN DEFAULT false,
  refunded BOOLEAN DEFAULT false,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT REFERENCES users(user_id),
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id),
  barcode VARCHAR(1024),
  tax_id VARCHAR(100),
  billing_latitude FLOAT DEFAULT 0,
  billing_longitude FLOAT DEFAULT 0,
  shipping_latitude FLOAT DEFAULT 0,
  shipping_longitude FLOAT DEFAULT 0,
  remote_order_id VARCHAR(255),
  shipping_rate_id INTEGER REFERENCES shipping_rates(rate_id),
  payment_token VARCHAR(255),
  payment_type VARCHAR(50),
  payment_brand VARCHAR(255),
  payment_last4 VARCHAR(255),
  payment_fingerprint VARCHAR(255),
  payment_country VARCHAR(50),
  charge_token VARCHAR(255),
  live_mode BOOLEAN default false,
  ip_address VARCHAR(200),
  country_iso VARCHAR(2),
  country VARCHAR(100),
  city VARCHAR(100),
  state_iso VARCHAR(3),
  state VARCHAR(100),
  latitude float,
  longitude float,
  estimated_tax_amount NUMERIC(15,6) DEFAULT 0,
  customer_email_date TIMESTAMP,
  sales_tax_sync_date TIMESTAMP,
  shipping_sync_date TIMESTAMP,
  accounting_sync_date TIMESTAMP,
  returned BOOLEAN DEFAULT false,
  payment_date TIMESTAMP(3),
  processing_date TIMESTAMP(3),
  fulfillment_date TIMESTAMP(3),
  shipped_date TIMESTAMP(3),
  canceled_date TIMESTAMP(3),
  refunded_date TIMESTAMP(3),
  payment_processor VARCHAR(20),
  tracking_numbers VARCHAR(512),
  carrier VARCHAR(50),
  shipment_date VARCHAR(30),
  promo_code VARCHAR(100),
  pricing_rule_1 BIGINT REFERENCES pricing_rules(rule_id),
  tax_rate NUMERIC(15,6) DEFAULT 0,
  square_order_id VARCHAR(56),
  first_name VARCHAR(100),
  last_name VARCHAR(100),
  session_id VARCHAR(255)
);

CREATE INDEX order_cust_id_idx ON orders(customer_id);
CREATE INDEX order_uniq_id_idx ON orders(order_unique_id);
CREATE INDEX order_live_mode_idx ON orders(live_mode);
CREATE INDEX order_paid_idx ON orders(paid);
CREATE INDEX order_canceled_idx ON orders(canceled);
CREATE INDEX order_processed_idx ON orders(processed);
CREATE INDEX order_shipped_idx ON orders(shipped);
CREATE INDEX order_refunded_idx ON orders(refunded);
CREATE INDEX order_created_by_idx ON orders(created_by);
CREATE INDEX order_email_idx ON orders(email);
CREATE INDEX order_payment_date_idx ON orders(payment_date);
CREATE INDEX order_session_id_idx ON orders(session_id);

CREATE TABLE order_items (
  item_id BIGSERIAL PRIMARY KEY,
  order_id BIGINT REFERENCES orders(order_id) NOT NULL,
  customer_id BIGINT REFERENCES customers(customer_id),
  product_id BIGINT REFERENCES products(product_id),
  sku_id BIGINT REFERENCES product_skus(sku_id),
  sku_attributes JSONB,
  quantity NUMERIC(15,6) DEFAULT 0,
  currency VARCHAR(3) NOT NULL DEFAULT 'usd',
  each_amount NUMERIC(15,6) DEFAULT 0,
  total_amount NUMERIC(15,6) DEFAULT 0,
  product_name VARCHAR(255) NOT NULL,
  product_type VARCHAR(20),
  product_sku VARCHAR(20) NOT NULL,
  is_preorder BOOLEAN DEFAULT false,
  is_backordered BOOLEAN DEFAULT false,
  paid BOOLEAN DEFAULT false,
  processed BOOLEAN DEFAULT false,
  shipped BOOLEAN DEFAULT false,
  canceled BOOLEAN DEFAULT false,
  refunded BOOLEAN DEFAULT false,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT REFERENCES users(user_id),
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id),
  product_barcode VARCHAR(1024),
  payment_date TIMESTAMP(3),
  processing_date TIMESTAMP(3),
  fulfillment_date TIMESTAMP(3),
  shipped_date TIMESTAMP(3),
  canceled_date TIMESTAMP(3),
  refunded_date TIMESTAMP(3),
  status INTEGER REFERENCES lookup_order_status(status_id) NOT NULL
);

CREATE INDEX ord_item_ord_id_idx ON order_items(order_id);
CREATE INDEX ord_item_cust_id_idx ON order_items(customer_id);
CREATE INDEX ord_item_prod_id_idx ON order_items(product_id);


CREATE TABLE lookup_payment_type (
  type_id SERIAL PRIMARY KEY,
  level INTEGER NOT NULL,
  title VARCHAR(20)
);

INSERT INTO lookup_payment_type (level, title) VALUES (10, 'Cash');
INSERT INTO lookup_payment_type (level, title) VALUES (20, 'Check');
INSERT INTO lookup_payment_type (level, title) VALUES (30, 'Credit Card');


CREATE TABLE order_payments (
  payment_id BIGSERIAL PRIMARY KEY,
  order_id BIGINT REFERENCES orders(order_id) NOT NULL,
  customer_id BIGINT REFERENCES customers(customer_id),
  payment_type INTEGER REFERENCES lookup_payment_type(type_id) NOT NULL,
  processor VARCHAR(50),
  reference VARCHAR(255),
  receipt_number VARCHAR(255),
  receipt_url VARCHAR(255),
  amount NUMERIC(15,6) DEFAULT 0,
  amount_refunded NUMERIC(15,6) DEFAULT 0,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT REFERENCES users(user_id),
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id)
);

CREATE INDEX ord_pmt_ord_id_idx ON order_payments(order_id);
CREATE INDEX ord_pmt_cust_id_idx ON order_payments(customer_id);


-- Placed an order, Received a refund, Pre-Ordered, Fulfilled an order, etc....

CREATE TABLE order_history (
  history_id BIGSERIAL PRIMARY KEY,
  order_id BIGINT REFERENCES orders(order_id) NOT NULL,
  customer_id BIGINT REFERENCES customers(customer_id),
  payment_id BIGINT REFERENCES order_payments(payment_id),
  status_id INTEGER REFERENCES lookup_order_status(status_id),
  notes VARCHAR(255),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT REFERENCES users(user_id)
);

CREATE INDEX ord_hist_ord_id_idx ON order_history(order_id);
CREATE INDEX ord_hist_cust_id_idx ON order_history(customer_id);

CREATE TABLE carts (
  cart_id BIGSERIAL PRIMARY KEY,
  cart_unique_id VARCHAR(255) UNIQUE NOT NULL,
  visitor_id BIGINT REFERENCES visitors(visitor_id),
  session_id VARCHAR(255),
  customer_id BIGINT REFERENCES customers(customer_id),
  user_id BIGINT REFERENCES users(user_id),
  total_items INTEGER DEFAULT 0,
  total_qty NUMERIC(15,6) DEFAULT 0,
  currency VARCHAR(3) NOT NULL DEFAULT 'usd',
  subtotal_amount NUMERIC(15,6) DEFAULT 0,
  order_id BIGINT REFERENCES orders(order_id),
  order_date TIMESTAMP(3),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT REFERENCES users(user_id),
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id),
  expires TIMESTAMP(3),
  shipping_method INTEGER REFERENCES lookup_shipping_method(method_id),
  shipping_rate_id INTEGER REFERENCES shipping_rates(rate_id),
  handling_fee_amount NUMERIC(15,6) DEFAULT 0,
  handling_fee_tax_amount NUMERIC(15,6) DEFAULT 0,
  shipping_amount NUMERIC(15,6) DEFAULT 0,
  shipping_tax_amount NUMERIC(15,6) DEFAULT 0,
  tax_amount NUMERIC(15,6) DEFAULT 0,
  enabled BOOLEAN DEFAULT true,
  promo_code VARCHAR(100),
  pricing_rule_1 BIGINT REFERENCES pricing_rules(rule_id),
  discount_amount NUMERIC(15,6) DEFAULT 0,
  tax_rate NUMERIC(15,6) DEFAULT 0
);

CREATE INDEX cart_unique_id_idx ON carts(cart_unique_id);
CREATE INDEX cart_visitor_id_idx ON carts(visitor_id);
CREATE INDEX cart_session_id_idx ON carts(session_id);
CREATE INDEX cart_customer_id_idx ON carts(customer_id);
CREATE INDEX cart_user_id_idx ON carts(user_id);

-- Add backwards reference
ALTER TABLE orders ADD cart_id BIGINT REFERENCES carts(cart_id);

CREATE TABLE cart_items (
  item_id BIGSERIAL PRIMARY KEY,
  cart_id BIGINT REFERENCES carts(cart_id) NOT NULL,
  product_id BIGINT REFERENCES products(product_id),
  sku_id BIGINT REFERENCES product_skus(sku_id),
  sku_attributes JSONB,
  quantity NUMERIC(15,6) DEFAULT 0,
  currency VARCHAR(3) NOT NULL DEFAULT 'usd',
  each_amount NUMERIC(15,6) DEFAULT 0,
  total_amount NUMERIC(15,6) DEFAULT 0,
  product_name VARCHAR(255) NOT NULL,
  product_type VARCHAR(20),
  product_sku VARCHAR(20) NOT NULL,
  is_preorder BOOLEAN DEFAULT false,
  is_backordered BOOLEAN DEFAULT false,
  is_removed BOOLEAN DEFAULT false,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT REFERENCES users(user_id),
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id),
  product_barcode VARCHAR(1024),
  quantity_free NUMERIC(15,6) DEFAULT 0
);

CREATE INDEX cart_item_cart_id_idx ON cart_items(cart_id);
CREATE INDEX cart_item_prod_id_idx ON cart_items(product_id);


-- Tracking number
CREATE TABLE order_tracking_numbers (
  tracking_id BIGSERIAL PRIMARY KEY,
  order_id BIGINT REFERENCES orders(order_id) NOT NULL,
  tracking_number VARCHAR(50),
  shipping_carrier INTEGER REFERENCES lookup_shipping_carrier(carrier_id),
  ship_date TIMESTAMP(3),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT REFERENCES users(user_id),
  delivery_date TIMESTAMP(3),
  cart_item_id_list VARCHAR(255),
  order_item_id_list VARCHAR(255)
);
CREATE INDEX order_track_num_or_idx ON order_tracking_numbers(order_id);
CREATE INDEX order_track_num_cr_idx ON order_tracking_numbers(created);

-- CREATE TABLE order_tracking_number_items (
--   id BIGSERIAL PRIMARY KEY,
--   tracking_id BIGINT REFERENCES order_tracking_numbers(tracking_id) NOT NULL,
--   order_id BIGINT REFERENCES orders(order_id) NOT NULL,
--   order_item_id BIGINT REFERENCES order_items(item_id) NOT NULL
-- );
