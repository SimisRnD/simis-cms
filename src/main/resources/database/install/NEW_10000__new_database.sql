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
  property_value TEXT NOT NULL,
  property_type VARCHAR(100),
  modified TIMESTAMP,
  modified_by BIGINT,
  expires_at TIMESTAMP
);

-- System

INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('SSL required', 'system.ssl', 'true');
INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('WWW context', 'system.www.context', '/web-content');
INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('Customizations path', 'system.customizations.filepath', '/opt/simis/customization');
INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('File server path', 'system.filepath', '/opt/simis/files');
INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('Configuration path', 'system.configpath', '/opt/simis/config');
-- The single server-enforced upload ceiling for every upload path (folder drop zone, image
-- upload, dataset upload, media API). Introduced at 10 MB by UPGRADE_20260725.1004 and raised to
-- 50 MB by UPGRADE_20260815.1000 for issue #1198; seeded here at the raised value so a fresh
-- install enforces the same ceiling as an upgraded deployment rather than the older 10 MB.
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (10, 'Maximum upload size (bytes)', 'system.upload.maxBytes', '52428800');
-- Mirrors UPGRADE_20260725.1003__dataset_max_rows.sql for the same reason (issue #1211): the row
-- existed only in the upgrade path, so fresh installs never had it. Read by
-- DatasetDownloadRemoteFileCommand.resolveMaxRows() to bound accumulated rows during a paged
-- remote dataset download.
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (10, 'Dataset max rows (paged download)', 'dataset.maxRows', '100000');

-- Site

INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (5, 'Name of the site', 'site.name', 'New Site');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (8, 'Site URL', 'site.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (11, 'Additional title keyword or brand name', 'site.name.keyword', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (12, 'Search engine description', 'site.description', 'A site for sharing information with others');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (14, 'Search engine keywords', 'site.keywords', 'community, groups, calendar');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (16, 'Site Open Graph image', 'site.image', '', 'image');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'Is online?', 'site.online', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (21, 'Is API enabled?', 'site.api', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (22, 'Is Sitemap.xml enabled?', 'site.sitemap.xml', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (25, 'Is the blog feed enabled?', 'site.feed.xml', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (23, 'Show cart?', 'site.cart', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (24, 'Allow registrations?', 'site.registrations', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (26, 'Show login?', 'site.login', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (27, 'Require review approval before publishing content', 'content.review.required', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (241, 'Require review approval to publish web pages', 'webPage.review.required', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (242, 'Require review approval to publish blog posts', 'blogPost.review.required', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (28, 'Roles that must enroll in MFA (comma-separated)', 'mfa.required.roles', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (29, 'MFA enrollment URL', 'mfa.enrollment.url', '/my-page', 'web-page');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (243, 'Documentation wiki (Unique Id)', 'documentation.wiki.uniqueId', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (30, 'Header line 1', 'site.header.line1', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (31, 'Header link name', 'site.header.link', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (32, 'Header details page', 'site.header.page', '', 'web-page');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (50, 'Footer line 1', 'site.footer.line1', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (51, 'Footer line 2', 'site.footer.line2', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (60, 'Show privacy policy link?', 'site.privacy.policy', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (61, 'Show terms and conditions link?', 'site.terms.conditions', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (100, 'Default timezone', 'site.timezone', 'America/New_York', 'timezone');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (150, 'Show site confirmation?', 'site.confirmation', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (152, 'Confirmation line 1', 'site.confirmation.line1', 'To visit this site, you must be 21.');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (153, 'Confirmation line 2', 'site.confirmation.line2', 'Please confirm that you are 21 years of age or older.');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (155, 'Message when declined', 'site.confirmation.declined.text', 'Sorry, you must be 21 years of age or older to visit this site');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (160, 'Show subscribe to newsletter overlay?', 'site.newsletter.overlay', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (161, 'Newsletter headline', 'site.newsletter.headline', 'Be the first to know');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (162, 'Newsletter message', 'site.newsletter.message', 'Enter your email for the latest trends, product info, and deals.');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (165, 'Newsletter text color', 'site.newsletter.color', '#000000', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (166, 'Newsletter background color', 'site.newsletter.backgroundColor', '#FFFFFF', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (200, 'Full color logo', 'site.logo', '', 'image');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (210, 'All white logo', 'site.logo.white', '', 'image');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (220, 'Mixed color logo', 'site.logo.mixed', '', 'image');

-- Theme

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (5, 'Menu theme', 'theme.menu.location', 'custom', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (6, 'Color scheme', 'theme.ui.mode', 'light', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (7, 'Logo color', 'theme.logo.color', 'color-and-white', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (8, 'Logo color (dark mode)', 'theme.logo.color.dark', 'all-white', 'text');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Headlines font', 'theme.fonts.headlines', '', 'font');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (11, 'Body font', 'theme.fonts.body', '', 'font');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (14, 'Web page background', 'theme.body.backgroundColor', '#ffffff', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (15, 'Web page text color', 'theme.body.text.color', '#000000', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (16, 'Link color', 'theme.link.color', '#0067ff', 'color');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (17, 'System alert bar', 'theme.utilitybar.backgroundColor', '#000000', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (18, 'System alert text color', 'theme.utilitybar.text.color', '#ffffff', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (19, 'System alert link color', 'theme.utilitybar.link.color', '#ffffff', 'color');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'Top bar', 'theme.topbar.backgroundColor', '#353535', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (21, 'Top bar text color', 'theme.topbar.text.color', '#ffffff', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (22, 'Menu tab text', 'theme.topbar.menu.text.color', '#FFFFFF', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (24, 'Menu tab arrow', 'theme.topbar.menu.arrow.color', '#FFFFFF', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (26, 'Menu tab hover text', 'theme.topbar.menu.hoverTextColor', '#FFFFFF', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (28, 'Menu tab hover bg', 'theme.topbar.menu.text.hoverBackgroundColor', '#4d4d4d', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (29, 'Menu tab active text', 'theme.topbar.menu.activeTextColor', '#FFFFFF', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (30, 'Menu tab active bg', 'theme.topbar.menu.activeBackgroundColor', '#4d4d4d', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (31, 'Drop down menu', 'theme.topbar.menu.dropdown.backgroundColor', '#2e2e2e', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (32, 'Drop down menu text', 'theme.topbar.menu.dropdown.text.color', '#FFFFFF', 'color');
-- INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (34, 'Drop down menu hover text', 'theme.topbar.menu.dropdown.text.color', '#FFFFFF', 'color');
-- INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (35, 'Drop down menu hover bg', 'theme.topbar.menu.dropdown.text.color', '#FFFFFF', 'color');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (50, 'Button text', 'theme.button.text.color', '#FFFFFF', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (52, 'Default button', 'theme.button.default.backgroundColor', '#53575c', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (54, 'Default button hover', 'theme.button.default.hoverBackgroundColor', '#3e4045', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (56, 'Primary button', 'theme.button.primary.backgroundColor', '#53575c', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (58, 'Primary button hover', 'theme.button.primary.hoverBackgroundColor', '#3e4045', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (60, 'Secondary button', 'theme.button.secondary.backgroundColor', '#767676', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (62, 'Secondary button hover', 'theme.button.secondary.hoverBackgroundColor', '#5e5e5e', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (64, 'Success button', 'theme.button.success.backgroundColor', '#43AC6A', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (66, 'Success button hover', 'theme.button.success.hoverBackgroundColor', '#3a9158', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (68, 'Warning button', 'theme.button.warning.backgroundColor', '#ffae00', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (70, 'Warning button hover', 'theme.button.warning.hoverBackgroundColor', '#cc8b00', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (72, 'Alert button', 'theme.button.alert.backgroundColor', '#cc4b37', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (74, 'Alert button hover', 'theme.button.alert.hoverBackgroundColor', '#a53b2a', 'color');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (80, 'Callout background', 'theme.callout.backgroundColor', '#ffffea', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (81, 'Callout text color', 'theme.callout.text.color', '#0a0a0a', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (83, 'Primary callout background', 'theme.callout.primary.backgroundColor', '#d7ecfa', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (84, 'Primary callout text color', 'theme.callout.primary.text.color', '#0a0a0a', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (86, 'Secondary callout background', 'theme.callout.secondary.backgroundColor', '#eaeaea', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (87, 'Secondary callout text color', 'theme.callout.secondary.text.color', '#0a0a0a', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (89, 'Success callout background', 'theme.callout.success.backgroundColor', '#e1faea', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (90, 'Success callout text color', 'theme.callout.success.text.color', '#0a0a0a', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (92, 'Warning callout background', 'theme.callout.warning.backgroundColor', '#fff3d9', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (93, 'Warning callout text color', 'theme.callout.warning.text.color', '#0a0a0a', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (95, 'Alert callout background', 'theme.callout.alert.backgroundColor', '#f7e4e1', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (96, 'Alert callout text color', 'theme.callout.alert.text.color', '#0a0a0a', 'color');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (110, 'Footer theme', 'theme.footer.style', 'custom', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (111, 'Footer layout', 'theme.footer.layout', 'footer.default', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (112, 'Footer background', 'theme.footer.backgroundColor', '#353535', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (114, 'Footer text color', 'theme.footer.text.color', '#acacac', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (116, 'Footer links color', 'theme.footer.links.color', '#cdcdcd', 'color');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (120, 'Footer logo color', 'theme.footer.logo.color', 'all-white', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (121, 'Footer logo color (dark mode)', 'theme.footer.logo.color.dark', 'all-white', 'text');

-- Mail

INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('Default from address', 'mail.from_address', 'auto-sender@site.local');
INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('Default from name', 'mail.from_name', 'New Site');
INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('Host name', 'mail.host_name', '127.0.0.1');
INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('SMTP port', 'mail.port', '25');
INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('SMTP username', 'mail.username', '');
INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('SMTP password', 'mail.password', '');
INSERT INTO site_properties (property_label, property_name, property_value, property_type) VALUES ('SMTP SSL', 'mail.ssl', 'false', 'boolean');
INSERT INTO site_properties (property_label, property_name, property_value, property_type) VALUES ('SMTP STARTTLS', 'mail.starttls', 'false', 'boolean');

-- Mailing List

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Mailing list service', 'mailing-list.service', 'None', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'MailChimp API key', 'mailing-list.mailchimp.apiKey', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (22, 'MailChimp list ID', 'mailing-list.mailchimp.listId', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (24, 'ZeroBounce API key', 'mailing-list.zerobounce.apiKey', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (26, 'Mailing list quarantine alert threshold (%)', 'mailing-list.quarantine.alertThresholdPercent', '10', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (27, 'Mailing list confirmation link expiry (days)', 'mailing-list.confirmation.expiryDays', '7', 'text');

-- Search

INSERT INTO site_properties (property_label, property_name, property_value, property_type) VALUES ('Zero-result search alert threshold (count/24h)', 'search.zeroResultAlertThreshold', '20', 'text');
INSERT INTO site_properties (property_label, property_name, property_value, property_type) VALUES ('Search log retention (days)', 'search.retentionDays', '180', 'text');
INSERT INTO site_properties (property_label, property_name, property_value, property_type) VALUES ('High-value search terms (comma-separated)', 'search.highValueTerms', '', 'text');

-- Maps

INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (10, 'Map tiles service', 'maps.service.tiles', 'openstreetmap');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (20, 'Map geocoder service', 'maps.service.geocoder', 'nominatim');
-- UPDATE site_properties SET property_value = 'custom' WHERE property_name = 'maps.service.tiles';
-- No property_type: the url validator rejects the required {z}/{x}/{y} placeholders; FindMapTilesCredentialsCommand validates instead
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (40, 'Custom map tiles URL ({z}/{x}/{y} template)', 'maps.custom.tileserver.url', '');

-- Analytics

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (5, 'Cookieless analytics (no visitor cookie)?', 'analytics.cookieless', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (6, 'Anonymize analytics IP addresses?', 'analytics.anonymizeIp', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (8, 'Analytics data retention (days)', 'analytics.retentionDays', '365');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (1, 'Audit log retention (days)', 'audit.retentionDays', '2555');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (2, 'Password age warning threshold (days)', 'password.maxAgeDays', '90', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (11, 'Form submission failure retention (days)', 'formData.failureRetentionDays', '90');
-- Only applies to form_data rows that have reached a terminal state (processed or dismissed by an
-- admin) -- rows still awaiting review are never deleted by this, regardless of age. See
-- FormDataRepository.deleteOlderThan and FormDataRetentionJob.
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (14, 'Form data retention (days, terminal-state only)', 'formData.retentionDays', '90');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (12, 'Web page version history limit (per page)', 'webPage.versionHistoryLimit', '20', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (13, 'Content block version history limit (per block)', 'content.versionHistoryLimit', '20', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (10, 'Analytics service', 'analytics.service', 'google');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (7, 'Honor Do-Not-Track / Global Privacy Control?', 'analytics.honorDnt', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (9, 'Require visitor consent before loading analytics?', 'analytics.consentRequired', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (20, 'Google Analytics GA key', 'analytics.google.key', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (22, 'Google Tag Manager GTM key', 'analytics.google.tagmanager', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (25, 'SimpliFi tag value', 'analytics.simplifi.value', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (26, 'Brand CDN path value', 'analytics.brandcdn.value', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (27, 'Brand CDN path value 2', 'analytics.brandcdn.value2', '');

-- Conversion funnel (issue #565, phase 1): pairs a page path and a formUniqueId to instrument as the
-- contact-form funnel (view -> submitted -> processed). Blank by default -- every site names its
-- contact page/form differently, so recording must stay off until an admin opts in with real values,
-- the same way the pre-existing #563 conversion-rate tile ships commented out until configured.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (1, 'Contact form funnel: page path', 'funnel.contactForm.pagePath', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (2, 'Contact form funnel: form unique ID', 'funnel.contactForm.formUniqueId', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (3, 'Funnel event retention (days)', 'funnel.retentionDays', '90', 'text');

-- Captcha

INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (10, 'Captcha service', 'captcha.service', 'google');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (20, 'Google reCAPTCHA site key', 'captcha.google.sitekey', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (30, 'Google reCAPTCHA secret key', 'captcha.google.secretkey', '');
-- Issue #1615: reCAPTCHA Enterprise. A key issued by Google's current console cannot be verified by
-- the legacy siteverify endpoint the secret key above is for, so it takes the assessment API and its
-- own credentials. Enterprise is inferred from these two being set rather than from a fourth
-- captcha.service value -- that property is free text, and issue #1614 is what a typo in it costs.
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (31, 'Google reCAPTCHA Enterprise project id', 'captcha.google.projectid', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (32, 'Google reCAPTCHA Enterprise API key', 'captcha.google.apikey', '');
-- Optional. When blank the score is logged rather than enforced, so an operator can see what real
-- traffic scores before choosing a number to reject people on.
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (33, 'Google reCAPTCHA minimum score (0.0-1.0)', 'captcha.google.scorethreshold', '');
-- Issue #519: Cloudflare Turnstile, a second captcha.service option alongside Google reCAPTCHA above.
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (40, 'Cloudflare Turnstile site key', 'captcha.turnstile.sitekey', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (50, 'Cloudflare Turnstile secret key', 'captcha.turnstile.secretkey', '');

-- Social Media
-- issue #516: platform link fields (Facebook/Instagram/LinkedIn/Twitter/Flickr/YouTube) moved to the
-- dynamic social_media_links table below -- an admin-editable list of (platform, url) pairs instead of
-- a fixed set of hardcoded properties. Contact info and the Instagram feed-embed integration stay here,
-- since they aren't platform links.

INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (5, 'Email address', 'social.email', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (10, 'Telephone', 'social.phone', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (27, 'Instagram access token', 'social.instagram.accessToken', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (28, 'Instagram Facebook page value', 'social.instagram.facebookPageValue', '', 'text');

-- BI

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (1, 'Enable Superset?', 'bi.enabled', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Superset URL', 'bi.superset.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (12, 'Superset ID', 'bi.superset.id', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (14, 'Superset secret', 'bi.superset.secret', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (16, 'Enable Metabase?', 'bi.metabase.enabled', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (18, 'Metabase URL', 'bi.metabase.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'Metabase secret', 'bi.metabase.secret', '', 'text');

-- E-Commerce

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (1, 'Enable e-commerce?', 'ecommerce.enabled', 'true', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (3, 'Enable real orders?', 'ecommerce.production', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (7, 'Last order date', 'ecommerce.lastOrderDate', 'None', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Payment processor API', 'ecommerce.paymentProcessor', 'None', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (15, 'Sales tax API', 'ecommerce.salesTaxService', 'None', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'Order fulfillment API', 'ecommerce.orderFulfillment', 'None', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (25, 'Order number format', 'ecommerce.orderNumberFormat', 'yymmdd-####-****', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (26, 'Customer number format', 'ecommerce.customerNumberFormat', 'C-#######-*****-??', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (27, 'Vendor number format', 'ecommerce.vendorNumberFormat', 'V-#######-*****-??', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (40, 'Order from name', 'ecommerce.from.name', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (41, 'Order from phone number', 'ecommerce.from.phone', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (42, 'Order from email address', 'ecommerce.from.email', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (43, 'Order from address line 1', 'ecommerce.from.address1', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (44, 'Order from address line 2', 'ecommerce.from.address2', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (45, 'Order from address city', 'ecommerce.from.city', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (46, 'Order from address state code', 'ecommerce.from.stateCode', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (47, 'Order from address country code', 'ecommerce.from.countryCode', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (48, 'Order from address postal code', 'ecommerce.from.postalCode', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (50, 'Default currency', 'ecommerce.defaultCurrency', 'USD', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (200, 'Stripe test key', 'ecommerce.stripe.test.key', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (205, 'Stripe test secret', 'ecommerce.stripe.test.secret', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (210, 'Stripe production key', 'ecommerce.stripe.production.key', '', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (215, 'Stripe production secret', 'ecommerce.stripe.production.secret', '', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (220, 'Square test app ID', 'ecommerce.square.test.key', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (221, 'Square test secret', 'ecommerce.square.test.secret', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (222, 'Square test location ID', 'ecommerce.square.test.location', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (223, 'Square production app ID', 'ecommerce.square.production.key', '', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (224, 'Square production secret', 'ecommerce.square.production.secret', '', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (225, 'Square production location ID', 'ecommerce.square.production.location', '', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (230, 'Boxzooka customer ID', 'ecommerce.boxzooka.production.id', '', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (231, 'Boxzooka secret', 'ecommerce.boxzooka.production.secret', '', 'disabled');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (240, 'TaxJar API key', 'ecommerce.taxjar.apiKey', '', 'text');

-- E-Learning

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (1, 'Enable e-learning?', 'elearning.enabled', 'true', 'boolean');

-- Moodle sorts first (issue #521) -- it's the only one of the three with a real, working
-- integration (RemoteCourseListWidget, CalendarAjaxMoodleEvents); LRS/PERLS are kept for
-- historical/future purposes but sort after it.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Enable Moodle?', 'elearning.moodle.enabled', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (12, 'Moodle URL', 'elearning.moodle.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (14, 'Moodle token', 'elearning.moodle.token', '', 'text');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'Enable LRS xAPI?', 'elearning.xapi.enabled', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (22, 'LRS URL', 'elearning.lrs.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (23, 'LRS key', 'elearning.lrs.key', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (24, 'LRS secret', 'elearning.lrs.secret', '', 'text');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (30, 'Enable PERLS?', 'elearning.perls.enabled', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (32, 'PERLS URL', 'elearning.perls.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (34, 'PERLS client ID', 'elearning.perls.clientId', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (36, 'PERLS secret', 'elearning.perls.secret', '', 'text');

-- Authentication

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'OpenAuth provider', 'oauth.provider', 'None', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (12, 'OpenAuth client ID', 'oauth.clientId', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (14, 'OpenAuth client secret', 'oauth.clientSecret', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (16, 'OpenAuth service URL', 'oauth.serviceUrl', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (18, 'OpenAuth redirect guests', 'oauth.redirectGuests', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'OpenAuth enabled', 'oauth.enabled', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (22, 'OpenAuth role attribute', 'oauth.role.attribute', 'roles', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (24, 'OpenAuth group attribute', 'oauth.group.attribute', 'groups', 'text');

-- Security

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Minimum password length', 'security.password.minLength', '15', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'Require password complexity?', 'security.password.requireComplexity', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (30, 'Additional iframe embed hosts', 'security.iframe.allowedHosts', '', 'text');

CREATE TABLE lookup_role (
  role_id SERIAL PRIMARY KEY,
  level INTEGER NOT NULL,
  code VARCHAR(20),
  title VARCHAR(100),
  oauth_path VARCHAR(255)
);

INSERT INTO lookup_role (level, code, title) VALUES (70, 'content-editor', 'Content Editor');
INSERT INTO lookup_role (level, code, title) VALUES (80, 'content-manager', 'Content Manager');
INSERT INTO lookup_role (level, code, title) VALUES (90, 'community-manager', 'Community Manager');
INSERT INTO lookup_role (level, code, title) VALUES (93, 'data-manager', 'Data Manager');
INSERT INTO lookup_role (level, code, title) VALUES (95, 'ecommerce-manager', 'E-commerce Manager');
INSERT INTO lookup_role (level, code, title) VALUES (100, 'admin', 'System Administrator');

CREATE TABLE capabilities (
  capability_id BIGSERIAL PRIMARY KEY,
  code VARCHAR(100) UNIQUE NOT NULL,
  category VARCHAR(50),
  description VARCHAR(500),
  created TIMESTAMP DEFAULT NOW()
);

CREATE TABLE role_capabilities (
  role_id INTEGER NOT NULL REFERENCES lookup_role (role_id),
  capability_id BIGINT NOT NULL REFERENCES capabilities (capability_id),
  PRIMARY KEY (role_id, capability_id)
);

CREATE INDEX idx_role_capabilities_capability_id ON role_capabilities (capability_id);

-- Seeded mechanically from the existing hasRole()/hasRole()-OR-chain survey (issue #701) --
-- one capability per (module, verb) actually observed, mapped onto the role(s) that already --
-- pass that check today. This is a read model over the status quo, not a policy change: no --
-- role gains or loses access to anything as a result of this migration.
--
-- Note: 'content-editor' is not referenced by any hasRole() call site yet, so there is no
-- observed behavior to derive a capability set from. Seeding it here would invent access it
-- doesn't currently grant.

INSERT INTO capabilities (code, category, description) VALUES
  ('content:manage', 'content', 'Create, edit, and publish web pages, blog posts, and wiki content'),
  ('community:manage', 'community', 'Manage mailing lists, users, and community/forum content'),
  ('data:manage', 'data', 'Manage structured data items and collections'),
  ('ecommerce:manage', 'ecommerce', 'Manage products and orders'),
  ('admin:manage', 'admin', 'Full administrative access to all site settings and configuration'),
  -- Added by issue #733's follow-up: a dedicated capability for /admin/users and its sibling
  -- Users/Groups pages, deliberately separate from admin:manage/community:manage so it can be
  -- granted without also handing out unrelated admin/community access.
  ('users:manage', 'users', 'Manage user accounts, user groups, and unsuspend requests');

-- admin: the existing "admin OR X" pattern found at every one of the 191 hasRole() call sites
-- means admin implicitly has every capability - expressed here as explicit rows so the table
-- stays a complete, queryable matrix (nothing is implied at query time).
INSERT INTO role_capabilities (role_id, capability_id)
SELECT lr.role_id, c.capability_id
FROM lookup_role lr, capabilities c
WHERE lr.code = 'admin';

INSERT INTO role_capabilities (role_id, capability_id)
SELECT lr.role_id, c.capability_id
FROM lookup_role lr, capabilities c
WHERE lr.code = 'content-manager' AND c.code = 'content:manage';

INSERT INTO role_capabilities (role_id, capability_id)
SELECT lr.role_id, c.capability_id
FROM lookup_role lr, capabilities c
WHERE lr.code = 'community-manager' AND c.code = 'community:manage';

-- No community-manager row for users:manage: that role's existing role="admin,community-manager"
-- attribute already covers /admin/users, /admin/user-details, /admin/unsuspend-requests, and
-- /admin/modify-user, but NOT /admin/groups or /admin/group (role="admin" only). Mapping
-- community-manager to this single capability would silently widen its access to the Groups
-- pages once capability="users:manage" is added there - out of scope here, since this table is
-- only meant to describe access that already exists, not grant new access.

INSERT INTO role_capabilities (role_id, capability_id)
SELECT lr.role_id, c.capability_id
FROM lookup_role lr, capabilities c
WHERE lr.code = 'data-manager' AND c.code = 'data:manage';

INSERT INTO role_capabilities (role_id, capability_id)
SELECT lr.role_id, c.capability_id
FROM lookup_role lr, capabilities c
WHERE lr.code = 'ecommerce-manager' AND c.code = 'ecommerce:manage';

-- The wiki widget's 3-way admin/content-manager/community-manager OR-check (issue #701 survey)
-- means content-manager and community-manager both also need content:manage's wiki slice.
-- Kept coarse (whole content:manage) rather than splitting out a wiki-only capability in this
-- walking-skeleton PR - narrowing that is future work once a widget actually needs it.
INSERT INTO role_capabilities (role_id, capability_id)
SELECT lr.role_id, c.capability_id
FROM lookup_role lr, capabilities c
WHERE lr.code = 'community-manager' AND c.code = 'content:manage';

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
  account_token_expires TIMESTAMP(3),
  validated TIMESTAMP(3),
  title VARCHAR(100),
  department VARCHAR(100),
  timezone VARCHAR(100),
  city VARCHAR(100),
  state VARCHAR(100),
  country VARCHAR(100),
  postal_code VARCHAR(100),
  latitude FLOAT DEFAULT 0,
  longitude FLOAT DEFAULT 0,
  geom geometry(Point,4326),
  description TEXT,
  description_text TEXT,
  image_url VARCHAR(255),
  video_url VARCHAR(255),
  field_values JSONB,
  mfa_secret VARCHAR(64),
  mfa_enabled BOOLEAN DEFAULT false,
  failed_attempt_count INTEGER DEFAULT 0,
  locked_until TIMESTAMP(3),
  last_password_changed_at TIMESTAMP(3),
  suspension_reason VARCHAR(255),
  -- A break-glass account: its sign-ins alert every other administrator, and org-level MFA
  -- enforcement never redirects it to the enrollment page (see MfaEnforcementCommand). The seeded
  -- system-administrator is marked in V71120__create_admin.
  break_glass BOOLEAN DEFAULT false
);
CREATE UNIQUE INDEX users_lc_email ON users (LOWER(email));
CREATE UNIQUE INDEX users_lc_username ON users (LOWER(username));
CREATE INDEX users_act_token_idx ON users(account_token);
CREATE INDEX users_created_idx ON users(created);
CREATE INDEX users_unique_id ON users(unique_id);
CREATE INDEX users_geom_gix ON users USING GIST (geom);

CREATE TABLE user_roles (
  user_role_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(user_id) NOT NULL,
  role_id BIGINT REFERENCES lookup_role(role_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX user_roles_rol_idx ON user_roles(role_id);
CREATE INDEX user_roles_usr_idx ON user_roles(user_id);

-- Direct, individually-trackable capability grants (issue #702) - independent of role_capabilities.
-- A user can hold a capability two ways: through a role (role_capabilities, #701) or through a
-- direct grant here (e.g. a temporary contractor who shouldn't get a whole role). expires_at is
-- nullable - null means permanent, matching a direct grant with no time limit.
CREATE TABLE capability_grants (
  capability_grant_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users (user_id),
  capability_id BIGINT NOT NULL REFERENCES capabilities (capability_id),
  granted_by BIGINT REFERENCES users (user_id),
  granted TIMESTAMP DEFAULT NOW(),
  reason VARCHAR(500),
  expires_at TIMESTAMP,
  revoked_at TIMESTAMP,
  expiration_notified_at TIMESTAMP
);

CREATE INDEX idx_capability_grants_user_id ON capability_grants (user_id);
CREATE INDEX idx_capability_grants_capability_id ON capability_grants (capability_id);

-- Only one *active* grant of a given capability per user at a time - prevents silently stacking
-- duplicate grants; revoke (or let expire) the existing one before granting again.
CREATE UNIQUE INDEX idx_capability_grants_active_unique ON capability_grants (user_id, capability_id)
  WHERE revoked_at IS NULL;

-- Sweep queries (CapabilityGrantExpirationJob) filter on both columns together.
CREATE INDEX idx_capability_grants_expires_at ON capability_grants (expires_at) WHERE revoked_at IS NULL;


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
  -- Nullable: the daily PII-scrub job (SessionsPiiScrubJob, GH-365) nulls this out for rows past
  -- the analytics retention window. See UPGRADE_20260727.1000 for the existing-install side of this.
  ip_address VARCHAR(200),
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
  is_bot BOOLEAN DEFAULT false,
  is_anonymous BOOLEAN NOT NULL DEFAULT false
);

COMMENT ON COLUMN sessions.is_anonymous IS 'True if session is from anonymous visitor (no user login)';

CREATE INDEX sessions_created_idx ON sessions(created);
CREATE INDEX sessions_sess_id_idx ON sessions(session_id);
CREATE INDEX sessions_is_bot_idx ON sessions(is_bot);
CREATE INDEX sessions_referer_idx ON sessions(referer);
CREATE INDEX idx_sessions_is_anonymous_created ON sessions(is_anonymous, created DESC);

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

-- Security audit log (Milestone #3 / Phase 1; mirrored by UPGRADE_20260719.1006 for existing installs).
-- Append-only; no foreign key on actor_user_id so a record survives the deletion of the user it
-- references; the full source IP is retained for forensics. previous_hash/record_hash form a tamper-evident
-- SHA-256 hash chain (Phase 4): record_hash = SHA-256(previous_hash || canonical(row)), previous_hash is the
-- record_hash of the row inserted just before. Any edit, delete, reorder, or mid-chain insert breaks the
-- chain (see AuditLogIntegrityCommand). They are populated by the application, not the database.
CREATE TABLE audit_log (
  audit_id BIGSERIAL PRIMARY KEY,
  occurred TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
  event_category VARCHAR(50) NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  outcome VARCHAR(20) NOT NULL,
  actor_user_id BIGINT,
  actor_username VARCHAR(255),
  source_ip VARCHAR(200),
  target_type VARCHAR(50),
  target_id VARCHAR(255),
  target_label VARCHAR(255),
  details TEXT,
  session_id VARCHAR(255),
  schema_version INTEGER DEFAULT 1 NOT NULL,
  previous_hash VARCHAR(64),
  record_hash VARCHAR(64)
);
CREATE INDEX audit_log_occurred_idx ON audit_log(occurred);
CREATE INDEX audit_log_category_type_idx ON audit_log(event_category, event_type);
CREATE INDEX audit_log_actor_idx ON audit_log(actor_user_id);
CREATE INDEX audit_log_target_idx ON audit_log(target_type, target_label);

-- Issue #558: cold storage for audit_log rows purged by AuditLogRepository.deleteOlderThan(). Rows are
-- copied verbatim (including previous_hash/record_hash, never re-hashed or re-anchored) in the same
-- transaction as the delete, so a failed archive copy cannot lose rows. Strictly cold storage: never read
-- by AuditLogIntegrityCommand or the audit log viewer. audit_id is not a SERIAL here -- values are always
-- copied from the live table, never generated.
CREATE TABLE audit_log_archive (
  audit_id BIGINT PRIMARY KEY,
  occurred TIMESTAMP(3) NOT NULL,
  event_category VARCHAR(50) NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  outcome VARCHAR(20) NOT NULL,
  actor_user_id BIGINT,
  actor_username VARCHAR(255),
  source_ip VARCHAR(200),
  target_type VARCHAR(50),
  target_id VARCHAR(255),
  target_label VARCHAR(255),
  details TEXT,
  session_id VARCHAR(255),
  schema_version INTEGER NOT NULL,
  previous_hash VARCHAR(64),
  record_hash VARCHAR(64),
  archived TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Audit log prefix-deletion watermark (#296, AU-9; mirrored by UPGRADE_20260725.1002 for existing
-- installs). Left empty on a fresh install -- there is no audit history yet to backfill from, and
-- the application sets row id=1 atomically on the very first hashed insert (see
-- AuditLogRepository.add()). Pre-seeding a placeholder row here would permanently block that
-- INSERT ... ON CONFLICT DO NOTHING from ever recording the real value. See
-- AuditLogIntegrityCommand for how the watermark is used to detect oldest-prefix deletion.
CREATE TABLE audit_log_watermark (
  id                     INTEGER PRIMARY KEY DEFAULT 1,
  lowest_hashed_audit_id BIGINT  NOT NULL DEFAULT 0
);

-- Issue #492 Phase 3: maker-checker approval for unsuspending elevated-role accounts (mirrored by
-- UPGRADE_20260730.1001 for existing installs). No foreign key on target_user_id/requested_by/
-- decided_by, matching audit_log's own precedent above -- a request row is a governance/audit
-- record and must survive the deletion of any user it references, not block it. The partial
-- unique index enforces "at most one pending request per target" at the database level.
CREATE TABLE unsuspend_requests (
  request_id BIGSERIAL PRIMARY KEY,
  target_user_id BIGINT NOT NULL,
  target_email VARCHAR(255),
  target_role_snapshot VARCHAR(255),
  requested_by BIGINT NOT NULL,
  requested_by_email VARCHAR(255),
  reason VARCHAR(255) NOT NULL,
  requested_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
  status VARCHAR(20) DEFAULT 'pending' NOT NULL,
  decided_by BIGINT,
  decided_by_email VARCHAR(255),
  decided_at TIMESTAMP(3),
  decision_reason VARCHAR(255)
);
CREATE INDEX unsuspend_requests_target_idx ON unsuspend_requests(target_user_id);
CREATE INDEX unsuspend_requests_status_idx ON unsuspend_requests(status, requested_at);
CREATE UNIQUE INDEX ux_unsuspend_requests_one_pending_per_target
  ON unsuspend_requests(target_user_id) WHERE status = 'pending';

-- Multi-factor authentication recovery codes: one-time backup codes, stored as SHA-256 hashes
CREATE TABLE user_mfa_recovery_codes (
  recovery_code_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(user_id) NOT NULL,
  code_hash VARCHAR(64) NOT NULL,
  used BOOLEAN DEFAULT false,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX user_mfa_recovery_codes_user_idx ON user_mfa_recovery_codes(user_id);

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

-- issue #516: an admin-editable list of (platform, url) pairs -- any platform name, not a fixed set
CREATE TABLE social_media_links (
  social_media_link_id BIGSERIAL PRIMARY KEY,
  platform_name VARCHAR(100) NOT NULL,
  url VARCHAR(512) NOT NULL,
  link_order INTEGER DEFAULT 100,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX social_media_links_order_idx ON social_media_links(link_order);

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

CREATE TABLE distributed_lock (
  name VARCHAR(64) PRIMARY KEY NOT NULL,
  locked_at TIMESTAMP(3) NOT NULL,
  lock_until TIMESTAMP(3) NOT NULL,
  uuid VARCHAR(255) NOT NULL
);

-- Records a workflow side effect that must happen at most once, so a replayed playbook cannot
-- repeat it. A workflow step claims a key here before acting; the claim is the INSERT, so two
-- attempts race on the primary key and exactly one wins. See EmailTask's once-key handling and
-- issue 1643, where a retried playbook re-sent a notification that had already gone out.
CREATE TABLE workflow_notification_sent (
  notification_key VARCHAR(255) PRIMARY KEY NOT NULL,
  sent_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Aggregated Content-Security-Policy violation reports (see UPGRADE_20260827.1000). One row per
-- (directive, host), not per event: /csp-report is necessarily unauthenticated, so aggregating
-- means a flood inflates a counter instead of growing the table. Only the blocked URL's host is
-- stored, never its path or query string.
CREATE TABLE csp_violation (
  violation_id BIGSERIAL PRIMARY KEY,
  effective_directive VARCHAR(64) NOT NULL,
  blocked_host VARCHAR(255) NOT NULL,
  occurrences BIGINT NOT NULL DEFAULT 1,
  sample_document_path VARCHAR(512),
  first_seen TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
  last_seen TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT uq_csp_violation_directive_host UNIQUE (effective_directive, blocked_host)
);
CREATE INDEX idx_csp_violation_last_seen ON csp_violation(last_seen DESC);
