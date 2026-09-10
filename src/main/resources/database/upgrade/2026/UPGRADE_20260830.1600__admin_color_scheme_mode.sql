-- Lets the admin console run a different colour scheme from the public site.
--
-- theme.ui.mode is read once in main.jsp and stamped onto <html data-theme="..."> for EVERY
-- page, so setting it to 'dark' turns the public site dark as well. That is a reasonable
-- setting for a site that wants to be dark, and the wrong one for "I want a dark CMS" -- which
-- is the request this exists to serve.
--
-- Empty means "follow theme.ui.mode", so an existing site is completely unaffected by this
-- upgrade: the console keeps whatever the site already does. Set it to dark and only /admin
-- routes change; the public site keeps its own setting.
--
--   (empty)  follow theme.ui.mode -- the default
--   light    console forced light
--   dark     console forced dark
--   auto     console follows the operating system setting
--   user     console follows the OS, and the colorSchemeToggle widget can override it
--
-- Same whitelist as theme.ui.mode: the value is mapped in main.jsp before it reaches the
-- markup, so a malformed property cannot inject into the attribute.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
-- Order 9 rather than 7: 7 and 8 are the logo colour pair, and 9 is the free slot nearest
-- theme.ui.mode (6) without renumbering rows this migration has no business touching.
VALUES (9, 'Admin color scheme', 'theme.ui.mode.admin', '', 'text')
ON CONFLICT (property_name) DO NOTHING;
