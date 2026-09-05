-- An optional button on the calendar event page, and the label it carries.
--
-- Replaces the Add-to-Calendar control, which never worked: the vendored library builds its button
-- with innerHTML and puts an inline onclick on it, and PageServlet sends script-src 'self'
-- 'nonce-...' with no 'unsafe-inline', so the browser refused to run the attribute. The button was
-- inert on every deployment (issue #1188's class of dead control).
--
-- Site properties rather than widget preferences because /calendar-event{/event-unique-id} is a
-- platform layout, and WebPageXmlLayoutCommand checks the XML pages before any database page, so
-- that layout always wins for this path and a site cannot override it to pass a preference. These
-- are editable under Site Settings, which renders every site.* row.
--
-- Seeded blank on purpose: the JSP renders nothing when the URL is empty, so no existing
-- deployment gains a button it did not ask for.

INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (27, 'Event page button label', 'site.calendar.actionLabel', '');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (28, 'Event page button link', 'site.calendar.actionUrl', '', 'url');
