-- Issue #400 follow-up: each AI vendor runs separate, independently-controllable crawlers for
-- training versus real-time citation/retrieval (confirmed against each vendor's own crawler
-- documentation) -- disallowing GPTBot has no effect on OAI-SearchBot or ChatGPT-User, and the
-- same split applies to Anthropic and Perplexity. The existing robots.ai.* properties only ever
-- covered the training bot per vendor; this adds the missing citation/retrieval bots as their own
-- toggles. Google has no separate citation crawler to add: Google's own docs state AI Overviews/
-- AI Mode ride on standard Search indexing (Googlebot), with Google-Extended governing AI
-- training use only. property_label is VARCHAR(50), so these stay short.

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (11, 'Allow OAI-SearchBot (OpenAI citation)', 'robots.ai.oai-searchbot', 'true', 'boolean')
ON CONFLICT (property_name) DO NOTHING;

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (12, 'Allow ChatGPT-User (OpenAI user fetch)', 'robots.ai.chatgpt-user', 'true', 'boolean')
ON CONFLICT (property_name) DO NOTHING;

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (21, 'Allow Claude-SearchBot (search index)', 'robots.ai.claude-searchbot', 'true', 'boolean')
ON CONFLICT (property_name) DO NOTHING;

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (22, 'Allow Claude-User (Anthropic fetch)', 'robots.ai.claude-user', 'true', 'boolean')
ON CONFLICT (property_name) DO NOTHING;

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (41, 'Allow Perplexity-User (user fetch)', 'robots.ai.perplexity-user', 'true', 'boolean')
ON CONFLICT (property_name) DO NOTHING;

-- Clarify that the two existing training-crawler labels now have a citation-crawler sibling above
UPDATE site_properties SET property_label = 'Allow GPTBot (OpenAI training)' WHERE property_name = 'robots.ai.gptbot';
UPDATE site_properties SET property_label = 'Allow ClaudeBot (Anthropic training)' WHERE property_name = 'robots.ai.claudebot';
UPDATE site_properties SET property_label = 'Allow PerplexityBot (crawler)' WHERE property_name = 'robots.ai.perplexitybot';
