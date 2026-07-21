-- Adds the site-wide color scheme setting used by platform-tokens.css via the data-theme attribute.
--   light  forced light, no toggle (the default, so an existing site looks exactly as it did)
--   dark   forced dark, no toggle
--   auto   follows the visitor's operating system setting
--   user   follows the operating system, and the colorSchemeToggle widget lets the visitor override
-- Defaults to 'light' deliberately: dark mode is opt-in, never applied to a running site by an upgrade.
-- The value is mapped through a whitelist in main.jsp before it reaches the markup.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (6, 'Color Scheme', 'theme.ui.mode', 'light', 'text')
ON CONFLICT (property_name) DO NOTHING;
