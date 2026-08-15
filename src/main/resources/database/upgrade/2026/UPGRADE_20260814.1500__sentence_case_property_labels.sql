-- Standardize admin settings labels on sentence case (issue #1200).
--
-- property_label values mixed sentence case and Title Case, sometimes in adjacent
-- fields on the same page, and "Site Url" was simply wrong. The agreed convention is
-- sentence case -- capitalize the first word only -- except genuine initialisms and
-- proper nouns (URL, API, ID, IP, SSL, SMTP, MFA, OAuth, Open Graph, MailChimp,
-- Google, Superset, Metabase, Moodle, Stripe, ... ), which keep their capitalization.
--
-- Display-only change: property_name, property_value and property_type are untouched,
-- and labels are not user-editable (the settings editor renders the label as text and
-- only submits the value), so these UPDATEs cannot clobber an operator's data. They
-- are unconditional on property_name rather than matching the old label, so a
-- deployment seeded by any earlier version converges on the same result.
--
-- Matches the same change to the install seed, so new and upgraded sites agree.

-- System
UPDATE site_properties SET property_label = 'SSL required' WHERE property_name = 'system.ssl';
UPDATE site_properties SET property_label = 'WWW context' WHERE property_name = 'system.www.context';

-- Site Settings
UPDATE site_properties SET property_label = 'Site URL' WHERE property_name = 'site.url';
UPDATE site_properties SET property_label = 'Site Open Graph image' WHERE property_name = 'site.image';
UPDATE site_properties SET property_label = 'Default timezone' WHERE property_name = 'site.timezone';
UPDATE site_properties SET property_label = 'Newsletter headline' WHERE property_name = 'site.newsletter.headline';
UPDATE site_properties SET property_label = 'Newsletter message' WHERE property_name = 'site.newsletter.message';
UPDATE site_properties SET property_label = 'Newsletter text color' WHERE property_name = 'site.newsletter.color';
UPDATE site_properties SET property_label = 'Newsletter background color' WHERE property_name = 'site.newsletter.backgroundColor';
UPDATE site_properties SET property_label = 'Full color logo' WHERE property_name = 'site.logo';
UPDATE site_properties SET property_label = 'All white logo' WHERE property_name = 'site.logo.white';
UPDATE site_properties SET property_label = 'Mixed color logo' WHERE property_name = 'site.logo.mixed';

-- Theme Settings
UPDATE site_properties SET property_label = 'Menu theme' WHERE property_name = 'theme.menu.location';
UPDATE site_properties SET property_label = 'Color scheme' WHERE property_name = 'theme.ui.mode';
UPDATE site_properties SET property_label = 'Logo color' WHERE property_name = 'theme.logo.color';
UPDATE site_properties SET property_label = 'Headlines font' WHERE property_name = 'theme.fonts.headlines';
UPDATE site_properties SET property_label = 'Body font' WHERE property_name = 'theme.fonts.body';
UPDATE site_properties SET property_label = 'Web page background' WHERE property_name = 'theme.body.backgroundColor';
UPDATE site_properties SET property_label = 'Web page text color' WHERE property_name = 'theme.body.text.color';
UPDATE site_properties SET property_label = 'Link color' WHERE property_name = 'theme.link.color';
UPDATE site_properties SET property_label = 'System alert bar' WHERE property_name = 'theme.utilitybar.backgroundColor';
UPDATE site_properties SET property_label = 'System alert text color' WHERE property_name = 'theme.utilitybar.text.color';
UPDATE site_properties SET property_label = 'System alert link color' WHERE property_name = 'theme.utilitybar.link.color';
UPDATE site_properties SET property_label = 'Top bar' WHERE property_name = 'theme.topbar.backgroundColor';
UPDATE site_properties SET property_label = 'Top bar text color' WHERE property_name = 'theme.topbar.text.color';
UPDATE site_properties SET property_label = 'Menu tab text' WHERE property_name = 'theme.topbar.menu.text.color';
UPDATE site_properties SET property_label = 'Menu tab arrow' WHERE property_name = 'theme.topbar.menu.arrow.color';
UPDATE site_properties SET property_label = 'Menu tab hover text' WHERE property_name = 'theme.topbar.menu.hoverTextColor';
UPDATE site_properties SET property_label = 'Menu tab hover bg' WHERE property_name = 'theme.topbar.menu.text.hoverBackgroundColor';
UPDATE site_properties SET property_label = 'Menu tab active text' WHERE property_name = 'theme.topbar.menu.activeTextColor';
UPDATE site_properties SET property_label = 'Menu tab active bg' WHERE property_name = 'theme.topbar.menu.activeBackgroundColor';
UPDATE site_properties SET property_label = 'Drop down menu' WHERE property_name = 'theme.topbar.menu.dropdown.backgroundColor';
UPDATE site_properties SET property_label = 'Drop down menu text' WHERE property_name = 'theme.topbar.menu.dropdown.text.color';
UPDATE site_properties SET property_label = 'Button text' WHERE property_name = 'theme.button.text.color';
UPDATE site_properties SET property_label = 'Default button' WHERE property_name = 'theme.button.default.backgroundColor';
UPDATE site_properties SET property_label = 'Default button hover' WHERE property_name = 'theme.button.default.hoverBackgroundColor';
UPDATE site_properties SET property_label = 'Primary button' WHERE property_name = 'theme.button.primary.backgroundColor';
UPDATE site_properties SET property_label = 'Primary button hover' WHERE property_name = 'theme.button.primary.hoverBackgroundColor';
UPDATE site_properties SET property_label = 'Secondary button' WHERE property_name = 'theme.button.secondary.backgroundColor';
UPDATE site_properties SET property_label = 'Secondary button hover' WHERE property_name = 'theme.button.secondary.hoverBackgroundColor';
UPDATE site_properties SET property_label = 'Success button' WHERE property_name = 'theme.button.success.backgroundColor';
UPDATE site_properties SET property_label = 'Success button hover' WHERE property_name = 'theme.button.success.hoverBackgroundColor';
UPDATE site_properties SET property_label = 'Warning button' WHERE property_name = 'theme.button.warning.backgroundColor';
UPDATE site_properties SET property_label = 'Warning button hover' WHERE property_name = 'theme.button.warning.hoverBackgroundColor';
UPDATE site_properties SET property_label = 'Alert button' WHERE property_name = 'theme.button.alert.backgroundColor';
UPDATE site_properties SET property_label = 'Alert button hover' WHERE property_name = 'theme.button.alert.hoverBackgroundColor';
UPDATE site_properties SET property_label = 'Callout background' WHERE property_name = 'theme.callout.backgroundColor';
UPDATE site_properties SET property_label = 'Callout text color' WHERE property_name = 'theme.callout.text.color';
UPDATE site_properties SET property_label = 'Primary callout background' WHERE property_name = 'theme.callout.primary.backgroundColor';
UPDATE site_properties SET property_label = 'Primary callout text color' WHERE property_name = 'theme.callout.primary.text.color';
UPDATE site_properties SET property_label = 'Secondary callout background' WHERE property_name = 'theme.callout.secondary.backgroundColor';
UPDATE site_properties SET property_label = 'Secondary callout text color' WHERE property_name = 'theme.callout.secondary.text.color';
UPDATE site_properties SET property_label = 'Success callout background' WHERE property_name = 'theme.callout.success.backgroundColor';
UPDATE site_properties SET property_label = 'Success callout text color' WHERE property_name = 'theme.callout.success.text.color';
UPDATE site_properties SET property_label = 'Warning callout background' WHERE property_name = 'theme.callout.warning.backgroundColor';
UPDATE site_properties SET property_label = 'Warning callout text color' WHERE property_name = 'theme.callout.warning.text.color';
UPDATE site_properties SET property_label = 'Alert callout background' WHERE property_name = 'theme.callout.alert.backgroundColor';
UPDATE site_properties SET property_label = 'Alert callout text color' WHERE property_name = 'theme.callout.alert.text.color';
UPDATE site_properties SET property_label = 'Footer theme' WHERE property_name = 'theme.footer.style';
UPDATE site_properties SET property_label = 'Footer layout' WHERE property_name = 'theme.footer.layout';
UPDATE site_properties SET property_label = 'Footer background' WHERE property_name = 'theme.footer.backgroundColor';
UPDATE site_properties SET property_label = 'Footer text color' WHERE property_name = 'theme.footer.text.color';
UPDATE site_properties SET property_label = 'Footer links color' WHERE property_name = 'theme.footer.links.color';
UPDATE site_properties SET property_label = 'Footer logo color' WHERE property_name = 'theme.footer.logo.color';

-- Social Media Settings
UPDATE site_properties SET property_label = 'Email address' WHERE property_name = 'social.email';
UPDATE site_properties SET property_label = 'Instagram access token' WHERE property_name = 'social.instagram.accessToken';
UPDATE site_properties SET property_label = 'Instagram Facebook page value' WHERE property_name = 'social.instagram.facebookPageValue';

-- Analytics Settings
UPDATE site_properties SET property_label = 'Analytics service' WHERE property_name = 'analytics.service';
UPDATE site_properties SET property_label = 'Google Analytics GA key' WHERE property_name = 'analytics.google.key';
UPDATE site_properties SET property_label = 'Google Tag Manager GTM key' WHERE property_name = 'analytics.google.tagmanager';
UPDATE site_properties SET property_label = 'SimpliFi tag value' WHERE property_name = 'analytics.simplifi.value';
UPDATE site_properties SET property_label = 'Brand CDN path value' WHERE property_name = 'analytics.brandcdn.value';
UPDATE site_properties SET property_label = 'Brand CDN path value 2' WHERE property_name = 'analytics.brandcdn.value2';

-- Captcha Settings
UPDATE site_properties SET property_label = 'Captcha service' WHERE property_name = 'captcha.service';
UPDATE site_properties SET property_label = 'Google reCAPTCHA site key' WHERE property_name = 'captcha.google.sitekey';
UPDATE site_properties SET property_label = 'Google reCAPTCHA secret key' WHERE property_name = 'captcha.google.secretkey';
UPDATE site_properties SET property_label = 'Cloudflare Turnstile site key' WHERE property_name = 'captcha.turnstile.sitekey';
UPDATE site_properties SET property_label = 'Cloudflare Turnstile secret key' WHERE property_name = 'captcha.turnstile.secretkey';

-- Search Settings
UPDATE site_properties SET property_label = 'Zero-result search alert threshold (count/24h)' WHERE property_name = 'search.zeroResultAlertThreshold';
UPDATE site_properties SET property_label = 'Search log retention (days)' WHERE property_name = 'search.retentionDays';
UPDATE site_properties SET property_label = 'High-value search terms (comma-separated)' WHERE property_name = 'search.highValueTerms';

-- Security Settings
UPDATE site_properties SET property_label = 'Minimum password length' WHERE property_name = 'security.password.minLength';
UPDATE site_properties SET property_label = 'Require password complexity?' WHERE property_name = 'security.password.requireComplexity';
UPDATE site_properties SET property_label = 'IP request rate alert threshold (hits/hour)' WHERE property_name = 'security.ipRequestRateAlertThreshold';
UPDATE site_properties SET property_label = 'Geo anomaly baseline window (days)' WHERE property_name = 'security.geoAnomalyBaselineDays';
UPDATE site_properties SET property_label = 'Geo anomaly recent window (hours)' WHERE property_name = 'security.geoAnomalyRecentHours';
UPDATE site_properties SET property_label = 'Draft preview link expiry (hours)' WHERE property_name = 'security.previewLinkTtlHours';

-- Password Settings
UPDATE site_properties SET property_label = 'Password age warning threshold (days)' WHERE property_name = 'password.maxAgeDays';

-- OpenAuth Settings
UPDATE site_properties SET property_label = 'OpenAuth provider' WHERE property_name = 'oauth.provider';
UPDATE site_properties SET property_label = 'OpenAuth client ID' WHERE property_name = 'oauth.clientId';
UPDATE site_properties SET property_label = 'OpenAuth client secret' WHERE property_name = 'oauth.clientSecret';
UPDATE site_properties SET property_label = 'OpenAuth service URL' WHERE property_name = 'oauth.serviceUrl';
UPDATE site_properties SET property_label = 'OpenAuth redirect guests' WHERE property_name = 'oauth.redirectGuests';
UPDATE site_properties SET property_label = 'OpenAuth enabled' WHERE property_name = 'oauth.enabled';
UPDATE site_properties SET property_label = 'OpenAuth role attribute' WHERE property_name = 'oauth.role.attribute';
UPDATE site_properties SET property_label = 'OpenAuth group attribute' WHERE property_name = 'oauth.group.attribute';

-- Mail Settings
UPDATE site_properties SET property_label = 'Default from address' WHERE property_name = 'mail.from_address';
UPDATE site_properties SET property_label = 'Default from name' WHERE property_name = 'mail.from_name';
UPDATE site_properties SET property_label = 'Host name' WHERE property_name = 'mail.host_name';
UPDATE site_properties SET property_label = 'SMTP port' WHERE property_name = 'mail.port';
UPDATE site_properties SET property_label = 'SMTP username' WHERE property_name = 'mail.username';
UPDATE site_properties SET property_label = 'SMTP password' WHERE property_name = 'mail.password';

-- Mailing List Settings
UPDATE site_properties SET property_label = 'Mailing list service' WHERE property_name = 'mailing-list.service';
UPDATE site_properties SET property_label = 'MailChimp API key' WHERE property_name = 'mailing-list.mailchimp.apiKey';
UPDATE site_properties SET property_label = 'MailChimp list ID' WHERE property_name = 'mailing-list.mailchimp.listId';
UPDATE site_properties SET property_label = 'ZeroBounce API key' WHERE property_name = 'mailing-list.zerobounce.apiKey';
UPDATE site_properties SET property_label = 'Mailing list quarantine alert threshold (%)' WHERE property_name = 'mailing-list.quarantine.alertThresholdPercent';
UPDATE site_properties SET property_label = 'Mailing list confirmation link expiry (days)' WHERE property_name = 'mailing-list.confirmation.expiryDays';

-- Funnel Settings
UPDATE site_properties SET property_label = 'Contact form funnel: page path' WHERE property_name = 'funnel.contactForm.pagePath';
UPDATE site_properties SET property_label = 'Contact form funnel: form unique ID' WHERE property_name = 'funnel.contactForm.formUniqueId';
UPDATE site_properties SET property_label = 'Funnel event retention (days)' WHERE property_name = 'funnel.retentionDays';

-- Map Settings
UPDATE site_properties SET property_label = 'Map tiles service' WHERE property_name = 'maps.service.tiles';
UPDATE site_properties SET property_label = 'Map geocoder service' WHERE property_name = 'maps.service.geocoder';
UPDATE site_properties SET property_label = 'Custom map tiles URL ({z}/{x}/{y} template)' WHERE property_name = 'maps.custom.tileserver.url';

-- BI Settings
UPDATE site_properties SET property_label = 'Superset secret' WHERE property_name = 'bi.superset.secret';
UPDATE site_properties SET property_label = 'Metabase secret' WHERE property_name = 'bi.metabase.secret';

-- E-commerce Settings
UPDATE site_properties SET property_label = 'Last order date' WHERE property_name = 'ecommerce.lastOrderDate';
UPDATE site_properties SET property_label = 'Payment processor API' WHERE property_name = 'ecommerce.paymentProcessor';
UPDATE site_properties SET property_label = 'Sales tax API' WHERE property_name = 'ecommerce.salesTaxService';
UPDATE site_properties SET property_label = 'Order fulfillment API' WHERE property_name = 'ecommerce.orderFulfillment';
UPDATE site_properties SET property_label = 'Order number format' WHERE property_name = 'ecommerce.orderNumberFormat';
UPDATE site_properties SET property_label = 'Customer number format' WHERE property_name = 'ecommerce.customerNumberFormat';
UPDATE site_properties SET property_label = 'Vendor number format' WHERE property_name = 'ecommerce.vendorNumberFormat';
UPDATE site_properties SET property_label = 'Order from name' WHERE property_name = 'ecommerce.from.name';
UPDATE site_properties SET property_label = 'Order from phone number' WHERE property_name = 'ecommerce.from.phone';
UPDATE site_properties SET property_label = 'Order from email address' WHERE property_name = 'ecommerce.from.email';
UPDATE site_properties SET property_label = 'Order from address line 1' WHERE property_name = 'ecommerce.from.address1';
UPDATE site_properties SET property_label = 'Order from address line 2' WHERE property_name = 'ecommerce.from.address2';
UPDATE site_properties SET property_label = 'Order from address city' WHERE property_name = 'ecommerce.from.city';
UPDATE site_properties SET property_label = 'Order from address state code' WHERE property_name = 'ecommerce.from.stateCode';
UPDATE site_properties SET property_label = 'Order from address country code' WHERE property_name = 'ecommerce.from.countryCode';
UPDATE site_properties SET property_label = 'Order from address postal code' WHERE property_name = 'ecommerce.from.postalCode';
UPDATE site_properties SET property_label = 'Default currency' WHERE property_name = 'ecommerce.defaultCurrency';
UPDATE site_properties SET property_label = 'Stripe test key' WHERE property_name = 'ecommerce.stripe.test.key';
UPDATE site_properties SET property_label = 'Stripe test secret' WHERE property_name = 'ecommerce.stripe.test.secret';
UPDATE site_properties SET property_label = 'Stripe production key' WHERE property_name = 'ecommerce.stripe.production.key';
UPDATE site_properties SET property_label = 'Stripe production secret' WHERE property_name = 'ecommerce.stripe.production.secret';
UPDATE site_properties SET property_label = 'Square test app ID' WHERE property_name = 'ecommerce.square.test.key';
UPDATE site_properties SET property_label = 'Square test secret' WHERE property_name = 'ecommerce.square.test.secret';
UPDATE site_properties SET property_label = 'Square test location ID' WHERE property_name = 'ecommerce.square.test.location';
UPDATE site_properties SET property_label = 'Square production app ID' WHERE property_name = 'ecommerce.square.production.key';
UPDATE site_properties SET property_label = 'Square production secret' WHERE property_name = 'ecommerce.square.production.secret';
UPDATE site_properties SET property_label = 'Square production location ID' WHERE property_name = 'ecommerce.square.production.location';
UPDATE site_properties SET property_label = 'Boxzooka customer ID' WHERE property_name = 'ecommerce.boxzooka.production.id';
UPDATE site_properties SET property_label = 'Boxzooka secret' WHERE property_name = 'ecommerce.boxzooka.production.secret';
UPDATE site_properties SET property_label = 'TaxJar API key' WHERE property_name = 'ecommerce.taxjar.apiKey';

-- E-learning Settings
UPDATE site_properties SET property_label = 'Moodle token' WHERE property_name = 'elearning.moodle.token';
UPDATE site_properties SET property_label = 'LRS key' WHERE property_name = 'elearning.lrs.key';
UPDATE site_properties SET property_label = 'LRS secret' WHERE property_name = 'elearning.lrs.secret';
UPDATE site_properties SET property_label = 'PERLS client ID' WHERE property_name = 'elearning.perls.clientId';
UPDATE site_properties SET property_label = 'PERLS secret' WHERE property_name = 'elearning.perls.secret';

-- Web conferencing (upgrade-only property)
UPDATE site_properties SET property_label = 'BBB secret' WHERE property_name = 'conferencing.bbb.secret';
