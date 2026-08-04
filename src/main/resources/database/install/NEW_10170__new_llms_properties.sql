-- Issue #417: the llms.* site property namespace backing LlmsTxtServlet (/llms.txt), the
-- llmstxt.org-formatted markdown summary consumed by LLM/agentic-browsing clients rather than
-- traditional crawlers -- Chrome's Lighthouse 13.3.0 "Agentic Browsing" audit (shipped to the
-- default configuration 2026-05-05) checks for this file's presence. Edited from the admin UI at
-- /admin/llms-properties via the same generic sitePropertiesEditor widget every other
-- /admin/*-properties settings page uses, mirroring the robots.* properties precedent (see
-- NEW_10110__new_robots_properties.sql).
--
-- llms.enabled defaults to 'true', matching the servlet's own default-allow behavior (an explicit
-- "false" is the only value that disables generation; a static config/cms/llms.txt override file,
-- when present, is always served regardless of this toggle -- see LlmsTxtServlet.loadLlmsTxt()).
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Enable /llms.txt', 'llms.enabled', 'true', 'boolean');

-- The free-text supplement a site owner can add without editing files directly (this issue's
-- acceptance criteria) -- rendered as plain prose after the site.description blockquote. No
-- property_type is set, matching site.description's own precedent (NEW_10000__new_database.sql):
-- a NULL/unrecognized type falls through to the editor's plain text-input default.
INSERT INTO site_properties (property_order, property_label, property_name, property_value) VALUES (20, 'Custom llms.txt description', 'llms.description', '');
