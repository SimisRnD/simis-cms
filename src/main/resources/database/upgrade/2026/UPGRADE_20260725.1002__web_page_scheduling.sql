-- Adds optional scheduling fields to web_pages so editors can set a future go-live
-- date (publish_at) and an automatic expiry date (expires_at). Both are nullable;
-- existing pages with null values behave identically to today.
ALTER TABLE web_pages ADD COLUMN IF NOT EXISTS publish_at TIMESTAMP;
ALTER TABLE web_pages ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
