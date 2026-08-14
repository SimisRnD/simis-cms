-- Multi-language content variants (issue #414) -- fresh-install finalization.
--
-- The locale and translation_group columns are declared inline in
-- NEW_10010__new_cms.sql. This file exists separately because the backfill and
-- the NOT NULL constraint must run *after* the seed data, and seeding is spread
-- across NEW_20005 (web pages), NEW_50010 (search page) and NEW_50020 (about
-- page). Declaring NOT NULL inline would reject every one of those inserts.
--
-- Keep this the highest-numbered install file that touches these tables. A new
-- seed added later with a number above 50030 would land without a
-- translation_group and fail the constraint.

-- Every seeded row is the sole member of its own group. Same derivation as
-- UPGRADE_20260813.1000, so an installed database and an upgraded one hold the
-- same values rather than diverging on a detail nobody would think to check.
UPDATE web_pages  SET translation_group = 'wp-'   || web_page_id WHERE translation_group IS NULL;
UPDATE content    SET translation_group = 'cnt-'  || content_id  WHERE translation_group IS NULL;
UPDATE blog_posts SET translation_group = 'post-' || post_id     WHERE translation_group IS NULL;

ALTER TABLE web_pages  ALTER COLUMN translation_group SET NOT NULL;
ALTER TABLE content    ALTER COLUMN translation_group SET NOT NULL;
ALTER TABLE blog_posts ALTER COLUMN translation_group SET NOT NULL;

-- One variant per locale per group -- without this a group could hold two
-- Spanish rows, and hreflang would emit duplicate entries for one locale, which
-- Google treats as an error rather than ignoring.
CREATE UNIQUE INDEX IF NOT EXISTS uq_web_pages_group_locale  ON web_pages  (translation_group, locale);
CREATE UNIQUE INDEX IF NOT EXISTS uq_content_group_locale    ON content    (translation_group, locale);
CREATE UNIQUE INDEX IF NOT EXISTS uq_blog_posts_group_locale ON blog_posts (translation_group, locale);

-- The language switcher and hreflang emission both fetch every sibling of the
-- current page on each render.
CREATE INDEX IF NOT EXISTS idx_web_pages_translation_group  ON web_pages  (translation_group);
CREATE INDEX IF NOT EXISTS idx_content_translation_group    ON content    (translation_group);
CREATE INDEX IF NOT EXISTS idx_blog_posts_translation_group ON blog_posts (translation_group);

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (2500, 'Default language', 'site.language.default', 'en', 'text')
ON CONFLICT (property_name) DO NOTHING;
