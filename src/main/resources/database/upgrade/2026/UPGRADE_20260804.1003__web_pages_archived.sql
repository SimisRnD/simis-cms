-- Adds an archived state to web_pages (issue #427) so the bulk-actions toolbar on /admin/web-pages
-- can archive pages, mirroring calendar_events' archived column (NEW_10010__new_cms.sql) exactly.
ALTER TABLE web_pages ADD COLUMN IF NOT EXISTS archived TIMESTAMP(3) DEFAULT NULL;
