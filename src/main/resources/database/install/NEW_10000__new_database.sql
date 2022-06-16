-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Core Database

CREATE EXTENSION postgis;

CREATE TABLE database_version (
  version_id BIGSERIAL PRIMARY KEY,
  file VARCHAR(100) UNIQUE NOT NULL,
  version VARCHAR(100) NOT NULL,
  installed TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE site_properties (
  property_id SERIAL PRIMARY KEY,
  property_order INTEGER DEFAULT 100,
  property_label VARCHAR(50),
  property_name VARCHAR(50) UNIQUE NOT NULL,
  property_value VARCHAR(255) NOT NULL,
  property_type VARCHAR(100)
);

-- System

INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('SSL Required', 'system.ssl', 'true');
INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('WWW Context', 'system.www.context', '/web-content');
INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('Customizations path', 'system.customizations.filepath', '/opt/simis/customization');
INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('File server path', 'system.filepath', '/opt/simis/files');
INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('Configuration path', 'system.configpath', '/opt/simis/config');

-- Site

INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (5, 'Name of the site', 'site.name', 'New Site');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (8, 'Site Url', 'site.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (11, 'Additional title keyword or brand name', 'site.name.keyword', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (12, 'Search engine description', 'site.description', 'A site for sharing information with others');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (14, 'Search engine keywords', 'site.keywords', 'community, groups, calendar');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (16, 'Site Open Graph Image', 'site.image', '', 'image');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'Is online?', 'site.online', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (22, 'Show cart?', 'site.cart', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (24, 'Allow registrations?', 'site.registrations', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (26, 'Show login?', 'site.login', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (30, 'Header line 1', 'site.header.line1', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (31, 'Header link name', 'site.header.link', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (32, 'Header details page', 'site.header.page', '', 'web-page');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (50, 'Footer line 1', 'site.footer.line1', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (51, 'Footer line 2', 'site.footer.line2', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (60, 'Show privacy policy link?', 'site.privacy.policy', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (61, 'Show terms and conditions link?', 'site.terms.conditions', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (100, 'Default Timezone', 'site.timezone', 'America/New_York', 'timezone');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (150, 'Show site confirmation?', 'site.confirmation', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (152, 'Confirmation line 1', 'site.confirmation.line1', 'To visit this site, you must be 21.');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (153, 'Confirmation line 2', 'site.confirmation.line2', 'Please confirm that you are 21 years of age or older.');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (155, 'Message when declined', 'site.confirmation.declined.text', 'Sorry, you must be 21 years of age or older to visit this site');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (160, 'Show subscribe to newsletter overlay?', 'site.newsletter.overlay', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (161, 'Newsletter Headline', 'site.newsletter.headline', 'Be the first to know');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (162, 'Newsletter Message', 'site.newsletter.message', 'Enter your email for the latest trends, product info, and deals.');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (165, 'Newsletter Text Color', 'site.newsletter.color', '#000000', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (166, 'Newsletter Background Color', 'site.newsletter.backgroundColor', '#FFFFFF', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (200, 'Full Color Logo', 'site.logo', '', 'image');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (210, 'All White Logo', 'site.logo.white', '', 'image');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (220, 'Mixed Color Logo', 'site.logo.mixed', '', 'image');

-- Theme

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (5, 'Menu Theme', 'theme.menu.location', 'custom', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (7, 'Logo Color', 'theme.logo.color', 'text-only', 'text');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Headlines Font', 'theme.fonts.headlines', '', 'font');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (11, 'Body Font', 'theme.fonts.body', '', 'font');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (14, 'Web Page Background', 'theme.body.backgroundColor', '#ffffff', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (15, 'Web Page Text Color', 'theme.body.text.color', '#000000', 'color');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (17, 'System Alert Bar', 'theme.utilitybar.backgroundColor', '#0067ff', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (18, 'System Alert Text Color', 'theme.utilitybar.text.color', '#ffffff', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (19, 'System Alert Link Color', 'theme.utilitybar.link.color', '#ffffff', 'color');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'Top Bar', 'theme.topbar.backgroundColor', '#353535', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (21, 'Top Bar Text Color', 'theme.topbar.text.color', '#ffffff', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (22, 'Menu Tab Text', 'theme.topbar.menu.text.color', '#FFFFFF', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (24, 'Menu Tab Arrow', 'theme.topbar.menu.arrow.color', '#FFFFFF', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (26, 'Menu Tab Hover Text', 'theme.topbar.menu.hoverTextColor', '#FFFFFF', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (28, 'Menu Tab Hover Bg', 'theme.topbar.menu.text.hoverBackgroundColor', '#4d4d4d', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (29, 'Menu Tab Active Text', 'theme.topbar.menu.activeTextColor', '#FFFFFF', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (30, 'Menu Tab Active Bg', 'theme.topbar.menu.activeBackgroundColor', '#4d4d4d', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (31, 'Drop Down Menu', 'theme.topbar.menu.dropdown.backgroundColor', '#2e2e2e', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (32, 'Drop Down Menu Text', 'theme.topbar.menu.dropdown.text.color', '#FFFFFF', 'color');
-- INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (34, 'Drop Down Menu Hover Text', 'theme.topbar.menu.dropdown.text.color', '#FFFFFF', 'color');
-- INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (35, 'Drop Down Menu Hover Bg', 'theme.topbar.menu.dropdown.text.color', '#FFFFFF', 'color');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (50, 'Button Text', 'theme.button.text.color', '#FFFFFF', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (52, 'Default Button', 'theme.button.default.backgroundColor', '#1779ba', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (54, 'Default Button Hover', 'theme.button.default.hoverBackgroundColor', '#126195', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (56, 'Primary Button', 'theme.button.primary.backgroundColor', '#1779ba', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (58, 'Primary Button Hover', 'theme.button.primary.hoverBackgroundColor', '#126195', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (60, 'Secondary Button', 'theme.button.secondary.backgroundColor', '#767676', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (62, 'Secondary Button Hover', 'theme.button.secondary.hoverBackgroundColor', '#5e5e5e', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (64, 'Success Button', 'theme.button.success.backgroundColor', '#43AC6A', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (66, 'Success Button Hover', 'theme.button.success.hoverBackgroundColor', '#3a9158', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (68, 'Warning Button', 'theme.button.warning.backgroundColor', '#ffae00', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (70, 'Warning Button Hover', 'theme.button.warning.hoverBackgroundColor', '#cc8b00', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (72, 'Alert Button', 'theme.button.alert.backgroundColor', '#cc4b37', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (74, 'Alert Button Hover', 'theme.button.alert.hoverBackgroundColor', '#a53b2a', 'color');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (80, 'Callout Background', 'theme.callout.backgroundColor', '#ffffea', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (81, 'Callout Text Color', 'theme.callout.text.color', '#0a0a0a', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (83, 'Primary Callout Background', 'theme.callout.primary.backgroundColor', '#d7ecfa', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (84, 'Primary Callout Text Color', 'theme.callout.primary.text.color', '#0a0a0a', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (86, 'Secondary Callout Background', 'theme.callout.secondary.backgroundColor', '#eaeaea', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (87, 'Secondary Callout Text Color', 'theme.callout.secondary.text.color', '#0a0a0a', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (89, 'Success Callout Background', 'theme.callout.success.backgroundColor', '#e1faea', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (90, 'Success Callout Text Color', 'theme.callout.success.text.color', '#0a0a0a', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (92, 'Warning Callout Background', 'theme.callout.warning.backgroundColor', '#fff3d9', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (93, 'Warning Callout Text Color', 'theme.callout.warning.text.color', '#0a0a0a', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (95, 'Alert Callout Background', 'theme.callout.alert.backgroundColor', '#f7e4e1', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (96, 'Alert Callout Text Color', 'theme.callout.alert.text.color', '#0a0a0a', 'color');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (110, 'Footer Theme', 'theme.footer.style', 'custom', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (112, 'Footer Background', 'theme.footer.backgroundColor', '#353535', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (114, 'Footer Text Color', 'theme.footer.text.color', '#acacac', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (116, 'Footer Links Color', 'theme.footer.links.color', '#cdcdcd', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (120, 'Footer Logo Color', 'theme.footer.logo.color', 'text-only', 'text');

-- Mail

INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('Default From Address', 'mail.from_address', 'auto-sender@site.local');
INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('Default From Name', 'mail.from_name', 'New Site');
INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('Host Name', 'mail.host_name', '127.0.0.1');
INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('SMTP Port', 'mail.port', '25');
INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('SMTP Username', 'mail.username', '');
INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('SMTP Password', 'mail.password', '');
INSERT INTO site_properties (property_label, property_name, property_value, property_type) VALUES ('SMTP SSL', 'mail.ssl', 'false', 'boolean');

-- Mailing List

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Mailing List Service', 'mailing-list.service', 'None', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'MailChimp API Key', 'mailing-list.mailchimp.apiKey', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (22, 'MailChimp List Id', 'mailing-list.mailchimp.listId', '', 'text');

-- Maps

INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (10, 'Map Tiles Service', 'maps.service.tiles', 'openstreetmap');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (20, 'Map Geocoder Service', 'maps.service.geocoder', 'nominatim');
-- UPDATE site_properties SET property_value = 'mapbox' WHERE property_name = 'maps.service.tiles';
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (30, 'Map Box Access Token', 'maps.mapbox.accesstoken', '');
-- UPDATE site_properties SET property_value = 'google' WHERE property_name = 'maps.service.tiles';
-- INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('Google Maps Access Token', 'maps.google.accesstoken', '');

-- Analytics

INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (10, 'Analytics Service', 'analytics.service', 'google');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (20, 'Google Analytics GA Key', 'analytics.google.key', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (22, 'Google Tag Manager GTM Key', 'analytics.google.tagmanager', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (25, 'SimpliFi Tag Value', 'analytics.simplifi.value', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (26, 'Brand CDN Path Value', 'analytics.brandcdn.value', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (27, 'Brand CDN Path Value 2', 'analytics.brandcdn.value2', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (50, 'Image Pixel URL', 'analytics.pixel.url', '', 'url');

-- Captcha

INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (10, 'Captcha Service', 'captcha.service', 'google');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (20, 'Google reCAPTCHA Site Key', 'captcha.google.sitekey', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (30, 'Google reCAPTCHA Secret Key', 'captcha.google.secretkey', '');

-- Social Media

INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (5, 'Email Address', 'social.email', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (10, 'Telephone', 'social.phone', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (15, 'Email Subscribe Link', 'social.subscribe.url', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'Facebook', 'social.facebook.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (25, 'Instagram', 'social.instagram.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (27, 'Instagram Access Token', 'social.instagram.accessToken', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (28, 'Instagram Facebook Page Value', 'social.instagram.facebookPageValue', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (30, 'LinkedIn', 'social.linkedin.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (35, 'Twitter', 'social.twitter.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (40, 'Flickr', 'social.flickr.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (45, 'Youtube', 'social.youtube.url', '', 'url');

-- E-Commerce

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (1, 'Enable e-commerce?', 'ecommerce.enabled', 'true', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (3, 'Enable real orders?', 'ecommerce.production', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (7, 'Last Order Date', 'ecommerce.lastOrderDate', 'None', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Payment Processor API', 'ecommerce.paymentProcessor', 'None', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (12, 'Address Validation API', 'ecommerce.addressValidation', 'None', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (15, 'Sales Tax API', 'ecommerce.salesTaxService', 'None', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'Order Fulfillment API', 'ecommerce.orderFulfillment', 'None', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (25, 'Order Number Format', 'ecommerce.orderNumberFormat', 'yymmdd-####-****', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (26, 'Customer Number Format', 'ecommerce.customerNumberFormat', 'C-#######-*****-??', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (27, 'Vendor Number Format', 'ecommerce.vendorNumberFormat', 'V-#######-*****-??', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (40, 'Order From Name', 'ecommerce.from.name', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (41, 'Order From Phone Number', 'ecommerce.from.phone', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (42, 'Order From Email Address', 'ecommerce.from.email', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (43, 'Order From Address Line 1', 'ecommerce.from.address1', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (44, 'Order From Address Line 2', 'ecommerce.from.address2', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (45, 'Order From Address City', 'ecommerce.from.city', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (46, 'Order From Address State Code', 'ecommerce.from.stateCode', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (47, 'Order From Address Country Code', 'ecommerce.from.countryCode', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (48, 'Order From Address Postal Code', 'ecommerce.from.postalCode', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (50, 'Default Currency', 'ecommerce.defaultCurrency', 'USD', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (100, 'USPS Web Tools User Id', 'ecommerce.usps.webtools.userid', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (200, 'Stripe Test Key', 'ecommerce.stripe.test.key', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (205, 'Stripe Test Secret', 'ecommerce.stripe.test.secret', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (210, 'Stripe Production Key', 'ecommerce.stripe.production.key', '', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (215, 'Stripe Production Secret', 'ecommerce.stripe.production.secret', '', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (220, 'Square Test App Id', 'ecommerce.square.test.key', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (221, 'Square Test Secret', 'ecommerce.square.test.secret', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (222, 'Square Test Location Id', 'ecommerce.square.test.location', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (223, 'Square Production App Id', 'ecommerce.square.production.key', '', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (224, 'Square Production Secret', 'ecommerce.square.production.secret', '', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (225, 'Square Production LocationId', 'ecommerce.square.production.location', '', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (230, 'Boxzooka Customer Id', 'ecommerce.boxzooka.production.id', '', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (231, 'Boxzooka Secret', 'ecommerce.boxzooka.production.secret', '', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (240, 'TaxJar API Key', 'ecommerce.taxjar.apiKey', '', 'text');

-- E-Learning

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (1, 'Enable e-learning?', 'elearning.enabled', 'true', 'boolean');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Enable LRS xAPI?', 'elearning.xapi.enabled', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (12, 'LRS URL', 'elearning.lrs.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (13, 'LRS Key', 'elearning.lrs.key', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (14, 'LRS Secret', 'elearning.lrs.secret', '', 'text');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'Enable Moodle?', 'elearning.moodle.enabled', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (22, 'Moodle URL', 'elearning.moodle.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (24, 'Moodle Token', 'elearning.moodle.token', '', 'text');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (30, 'Enable PERLS?', 'elearning.perls.enabled', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (32, 'PERLS URL', 'elearning.perls.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (34, 'PERLS Client Id', 'elearning.perls.clientId', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (36, 'PERLS Secret', 'elearning.perls.secret', '', 'text');

-- Web Conferencing

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (1, 'Enable web conferencing?', 'conferencing.enabled', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'BBB URL', 'conferencing.bbb.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (12, 'BBB Secret', 'conferencing.bbb.secret', '', 'text');

-- Authentication

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'OpenAuth Provider', 'oauth.provider', 'None', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (12, 'OpenAuth Client Id', 'oauth.clientId', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (14, 'OpenAuth Client Secret', 'oauth.clientSecret', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (16, 'OpenAuth Service URL', 'oauth.serviceUrl', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (18, 'OpenAuth Redirect Guests', 'oauth.redirectGuests', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'OpenAuth Enabled', 'oauth.enabled', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (22, 'OpenAuth Role Attribute', 'oauth.role.attribute', 'roles', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (24, 'OpenAuth Group Attribute', 'oauth.group.attribute', 'groups', 'text');

CREATE TABLE lookup_role (
  role_id SERIAL PRIMARY KEY,
  level INTEGER NOT NULL,
  code VARCHAR(20),
  title VARCHAR(100),
  oauth_path VARCHAR(255)
);

INSERT INTO lookup_role (level, code, title) VALUES (80, 'content-manager', 'Content Manager');
INSERT INTO lookup_role (level, code, title) VALUES (90, 'community-manager', 'Community Manager');
INSERT INTO lookup_role (level, code, title) VALUES (93, 'data-manager', 'Data Manager');
INSERT INTO lookup_role (level, code, title) VALUES (95, 'ecommerce-manager', 'E-commerce Manager');
INSERT INTO lookup_role (level, code, title) VALUES (100, 'admin', 'System Administrator');

CREATE TABLE users (
  user_id BIGSERIAL PRIMARY KEY,
  unique_id VARCHAR(255) UNIQUE NOT NULL,
  first_name VARCHAR(100),
  last_name VARCHAR(100),
  organization VARCHAR(100),
  nickname VARCHAR(100),
  email VARCHAR(255) UNIQUE,
  username VARCHAR(255) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL,
  enabled BOOLEAN DEFAULT true,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT REFERENCES users(user_id),
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id),
  account_token VARCHAR(255),
  validated TIMESTAMP(3),
  title VARCHAR(100),
  department VARCHAR(100),
  timezone VARCHAR(100),
  city VARCHAR(100),
  state VARCHAR(100),
  country VARCHAR(100),
  postal_code VARCHAR(100)
);
CREATE UNIQUE INDEX users_lc_email ON users (LOWER(email));
CREATE UNIQUE INDEX users_lc_username ON users (LOWER(username));
CREATE INDEX users_act_token_idx ON users(account_token);
CREATE INDEX users_created_idx ON users(created);
CREATE INDEX users_unique_id ON users(unique_id);

CREATE TABLE user_roles (
  user_role_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(user_id) NOT NULL,
  role_id BIGINT REFERENCES lookup_role(role_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX user_roles_rol_idx ON user_roles(role_id);
CREATE INDEX user_roles_usr_idx ON user_roles(user_id);


CREATE TABLE groups (
  group_id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) UNIQUE NOT NULL,
  description TEXT,
  user_count BIGINT NOT NULL DEFAULT 0,
  unique_id VARCHAR(255) UNIQUE NOT NULL,
  oauth_path VARCHAR(255)
);

INSERT INTO groups (name, unique_id) VALUES ('All Users', 'users');

CREATE TABLE user_groups (
  user_group_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(user_id) NOT NULL,
  group_id BIGINT REFERENCES groups(group_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  is_manager BOOLEAN DEFAULT FALSE NOT NULL
);
CREATE INDEX user_group_grp_idx ON user_groups(group_id);
CREATE INDEX user_group_usr_idx ON user_groups(user_id);

CREATE TABLE apps (
  app_id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  summary TEXT,
  created_by BIGINT REFERENCES users(user_id),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  public_key VARCHAR(255) UNIQUE,
  private_key VARCHAR(255),
  enabled BOOLEAN DEFAULT true
);
-- INSERT INTO apps(name, public_key, enabled) VALUES ('Default Test', '253C36E3-67C5-47A8-A5D2-6555A8AED071', false);

CREATE TABLE visitors (
  visitor_id BIGSERIAL PRIMARY KEY,
  token VARCHAR(255),
  session_id VARCHAR(255),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sessions (
  id BIGSERIAL PRIMARY KEY,
  session_id VARCHAR(255),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  ip_address VARCHAR(200) NOT NULL,
  user_agent VARCHAR(255),
  referer VARCHAR(255),
  continent VARCHAR(20),
  country_iso VARCHAR(2),
  country VARCHAR(100),
  city VARCHAR(100),
  state_iso VARCHAR(3),
  state VARCHAR(100),
  postal_code VARCHAR(50),
  timezone VARCHAR(50),
  latitude float,
  longitude float,
  metro_code INTEGER,
  source VARCHAR(50),
  app_id BIGINT REFERENCES apps(app_id),
  visitor_id BIGINT REFERENCES visitors(visitor_id),
  is_bot BOOLEAN DEFAULT false
);

CREATE INDEX sessions_created_idx ON sessions(created);
CREATE INDEX sessions_sess_id_idx ON sessions(session_id);
CREATE INDEX sessions_is_bot_idx ON sessions(is_bot);
CREATE INDEX sessions_referer_idx ON sessions(referer);

-- CREATE TABLE session_country_snapshots (
--   snapshot_id BIGSERIAL PRIMARY KEY,
--   snapshot_date TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
--   date_value VARCHAR(10) UNIQUE NOT NULL,
--   unique_sessions BIGINT DEFAULT 0
-- );
--
-- CREATE INDEX sess_ctry_snp_dt_idx ON session_country_snapshots(snapshot_date);


CREATE TABLE user_logins (
  login_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(user_id) NOT NULL,
  ip_address VARCHAR(200) NOT NULL,
  user_agent VARCHAR(255) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  session_id VARCHAR(255),
  source VARCHAR(50)
);
CREATE INDEX user_logins_date_idx ON user_logins(created);

CREATE TABLE user_tokens (
  token_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(user_id) NOT NULL,
  login_id BIGINT REFERENCES user_logins(login_id) NOT NULL,
  token VARCHAR(255) UNIQUE,
  expires TIMESTAMP(3) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX user_tokens_token_idx ON user_tokens(token);

CREATE TABLE oauth_tokens (
  token_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(user_id) NOT NULL,
  user_token_id BIGINT REFERENCES user_tokens(token_id) NOT NULL,
  provider VARCHAR(50) NOT NULL,
  access_token TEXT NOT NULL,
  token_type VARCHAR(100) NOT NULL,
  expires_in INTEGER DEFAULT NULL,
  refresh_token TEXT,
  refresh_expires_in INTEGER DEFAULT NULL,
  scope VARCHAR(100),
  expires TIMESTAMP(3),
  refresh_expires TIMESTAMP(3),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  enabled BOOLEAN DEFAULT true
);
CREATE INDEX oauth_user_id_idx ON oauth_tokens(user_id);
CREATE INDEX oauth_provider_idx ON oauth_tokens(provider);

CREATE TABLE block_list (
  block_list_id BIGSERIAL PRIMARY KEY,
  ip_address VARCHAR(200) UNIQUE NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  reason VARCHAR(255)
);

CREATE TABLE world_cities (
  country VARCHAR(2),
  city VARCHAR(100),
  accent_city VARCHAR(100),
  region VARCHAR(2),
  latitude float,
  longitude float,
  population int,
  geom geometry(Point,4326)
);
-- CREATE INDEX world_cit_country_idx ON world_cities(country);
CREATE INDEX world_cit_city_idx ON world_cities(city);
CREATE INDEX world_cit_reg_idx ON world_cities(region);
CREATE INDEX world_cit_pop_idx ON world_cities(population);
-- CREATE INDEX world_cit_geom_gix ON world_cities USING GIST (geom);

-- COPY world_cities(country,city,accent_city,region,latitude,longitude,population,geom)
-- FROM '/opt/simis/data/world_cities.csv' DELIMITER ',' CSV HEADER;
--
-- UPDATE world_cities SET geom = ST_SetSRID(ST_MakePoint(latitude, longitude), 4326) WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND geom IS NULL;
-- UPDATE world_cities SET population = 0 WHERE population IS NULL;

-- Find most likely based on name
-- SELECT * FROM world_cities WHERE population = (SELECT MAX(population) FROM world_cities WHERE city = 'london') OR city = 'london' ORDER BY population DESC;

-- SELECT round(ST_DistanceSphere(
--  (SELECT coordinates FROM houses WHERE id = 1),
--  (SELECT coordinates FROM points ORDER BY coordinates <->
--     (select coordinates from houses where id = 1) LIMIT 1 )
--  ))
-- as Distance;

-- Things closest to the specified point
-- SELECT item_id, name FROM items ORDER BY geom <-> st_setsrid(st_makepoint(-90,40),4326) LIMIT 10;

-- Things closest to the specified city
-- SELECT item_id, name
-- FROM items
-- WHERE
-- collection_id = 31
-- AND geom IS NOT NULL
-- AND ST_DWithin(geom::geography, (SELECT geom::geography FROM world_cities WHERE population = (SELECT MAX(population) FROM world_cities WHERE city = 'cary') OR city = 'cary' ORDER BY population DESC LIMIT 1), 48280)
-- ORDER BY geom <->
-- (SELECT geom FROM world_cities WHERE population = (SELECT MAX(population) FROM world_cities WHERE city = 'cary') OR city = 'cary' ORDER BY population DESC LIMIT 1)
-- LIMIT 20;


-- Things searched on and closest to the specified city
-- SELECT item_id, name, TS_RANK_CD(tsv, PLAINTO_TSQUERY('school')) AS rank, city
-- FROM items
-- WHERE
-- collection_id = 31
-- AND tsv @@ PLAINTO_TSQUERY('school')
-- AND geom IS NOT NULL
-- AND ST_DWithin(geom::geography, (SELECT geom::geography FROM world_cities WHERE population = (SELECT MAX(population) FROM world_cities WHERE city = 'cary') ORDER BY population DESC LIMIT 1), 48280)
-- ORDER BY geom <-> (SELECT geom FROM world_cities WHERE population = (SELECT MAX(population) FROM world_cities WHERE city = 'cary') ORDER BY population DESC LIMIT 1),
-- rank DESC
-- ;

-- SELECT item_id, name
-- FROM items
-- WHERE geom IS NOT NULL AND
-- ST_DWithin(geom::geography, (SELECT geom::geography FROM world_cities WHERE population = (SELECT MAX(population) FROM world_cities WHERE city = 'london') OR city = 'london' ORDER BY population DESC LIMIT 1), 5000)
-- ORDER BY geom <->
-- (SELECT geom FROM world_cities WHERE population = (SELECT MAX(population) FROM world_cities WHERE city = 'london') OR city = 'london' ORDER BY population DESC LIMIT 1) LIMIT 20;

CREATE TABLE zip_codes (
  code VARCHAR(5),
  code_type VARCHAR(15),
  city VARCHAR(27),
  state VARCHAR(2),
  location_type VARCHAR(7),
  latitude float,
  longitude float,
  location VARCHAR(52),
  decommissioned boolean,
  tax_returns int,
  population int,
  total_wages int,
  geom geometry(Point,4326)
);

CREATE INDEX zip_codes_code_idx ON zip_codes(code);

-- COPY zip_codes
-- FROM '/opt/simis/data/zipcodes.csv' DELIMITER ',' CSV HEADER;
--
-- UPDATE zip_codes SET geom = ST_SetSRID(ST_MakePoint(latitude, longitude), 4326) WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND geom IS NULL;

