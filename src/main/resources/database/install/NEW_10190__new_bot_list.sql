-- Admin-manageable, database-backed bot user-agent list (previously bot detection was a
-- server-side file only, config/cms/bot-list.csv, with no admin UI, and shipped with zero
-- signatures -- the file also never reached a Docker/Azure container's file store, since
-- nothing copies it onto the CMS_PATH volume). Mirrors the existing allow_list/block_list
-- tables (issue #641) so the bot list gets the same CRUD/CSV/paging admin UI.

CREATE TABLE bot_list (
  bot_list_id BIGSERIAL PRIMARY KEY,
  user_agent VARCHAR(255) UNIQUE NOT NULL,
  label VARCHAR(100),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO bot_list (user_agent, label) VALUES
  ('Googlebot', 'Google'),
  ('Bingbot', 'Bing'),
  ('Slurp', 'Yahoo'),
  ('DuckDuckBot', 'DuckDuckGo'),
  ('Baiduspider', 'Baidu'),
  ('YandexBot', 'Yandex'),
  ('Applebot', 'Apple'),
  ('facebookexternalhit', 'Facebook/Meta link preview'),
  ('Twitterbot', 'Twitter/X link preview'),
  ('LinkedInBot', 'LinkedIn link preview'),
  ('WhatsApp', 'WhatsApp link preview'),
  ('TelegramBot', 'Telegram link preview'),
  ('AhrefsBot', 'Ahrefs SEO crawler'),
  ('SemrushBot', 'Semrush SEO crawler'),
  ('MJ12bot', 'Majestic SEO crawler'),
  ('DotBot', 'Moz SEO crawler'),
  ('PetalBot', 'Huawei Petal Search crawler'),
  ('ia_archiver', 'Internet Archive / Alexa'),
  ('GPTBot', 'OpenAI training crawler'),
  ('OAI-SearchBot', 'OpenAI search crawler'),
  ('ChatGPT-User', 'ChatGPT user-triggered fetch'),
  ('ClaudeBot', 'Anthropic training crawler'),
  ('Claude-User', 'Claude user-triggered fetch'),
  ('Claude-SearchBot', 'Anthropic search crawler'),
  ('PerplexityBot', 'Perplexity crawler'),
  ('Perplexity-User', 'Perplexity user-triggered fetch'),
  ('Bytespider', 'ByteDance/TikTok crawler')
ON CONFLICT (user_agent) DO NOTHING;
