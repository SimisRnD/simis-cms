-- Issue #400: per-AI-crawler opt-out controls for /robots.txt (see RobotsServlet). Default value
-- 'true' matches the servlet's own default-allow behavior and keeps the admin toggle showing
-- "Yes" (allowed) out of the box, rather than looking blocked when nothing has been configured yet.
--
-- Each vendor below runs separate, independently-controllable crawlers for training versus
-- real-time citation/retrieval (confirmed against each vendor's own crawler documentation).
-- Disallowing the training bot has no effect on the citation bot or vice versa, so both need
-- their own toggle rather than one row per vendor. Google has no separate citation-specific
-- crawler to add here: Google's own docs state AI Overviews/AI Mode ride on standard Search
-- indexing (Googlebot), with Google-Extended governing AI training use only.

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Allow GPTBot (OpenAI training)', 'robots.ai.gptbot', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (11, 'Allow OAI-SearchBot (OpenAI citation)', 'robots.ai.oai-searchbot', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (12, 'Allow ChatGPT-User (OpenAI user fetch)', 'robots.ai.chatgpt-user', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'Allow ClaudeBot (Anthropic training)', 'robots.ai.claudebot', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (21, 'Allow Claude-SearchBot (search index)', 'robots.ai.claude-searchbot', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (22, 'Allow Claude-User (Anthropic fetch)', 'robots.ai.claude-user', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (30, 'Allow Google-Extended', 'robots.ai.google-extended', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (40, 'Allow PerplexityBot (crawler)', 'robots.ai.perplexitybot', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (41, 'Allow Perplexity-User (user fetch)', 'robots.ai.perplexity-user', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (50, 'Allow CCBot (Common Crawl)', 'robots.ai.ccbot', 'true', 'boolean');
