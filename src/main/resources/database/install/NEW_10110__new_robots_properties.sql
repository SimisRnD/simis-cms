-- Issue #400: per-AI-crawler opt-out controls for /robots.txt (see RobotsServlet). Default value
-- 'true' matches the servlet's own default-allow behavior and keeps the admin toggle showing
-- "Yes" (allowed) out of the box, rather than looking blocked when nothing has been configured yet.

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Allow GPTBot (OpenAI)', 'robots.ai.gptbot', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'Allow ClaudeBot (Anthropic)', 'robots.ai.claudebot', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (30, 'Allow Google-Extended', 'robots.ai.google-extended', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (40, 'Allow PerplexityBot', 'robots.ai.perplexitybot', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (50, 'Allow CCBot (Common Crawl)', 'robots.ai.ccbot', 'true', 'boolean');
