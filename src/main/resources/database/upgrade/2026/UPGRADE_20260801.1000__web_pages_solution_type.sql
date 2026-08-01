-- Adds an optional solution-page tag to web_pages (issue #570) so traffic and engagement can be
-- reported per solution type (e.g. government solution pages, contract/past-performance pages,
-- careers). Free-text like the existing template column -- not a foreign key to a new taxonomy
-- table -- and nullable, so existing pages are simply untagged until an editor sets one.
ALTER TABLE web_pages ADD COLUMN IF NOT EXISTS solution_type VARCHAR(255);
