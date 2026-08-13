-- Multi-language content: locale + translation grouping (issue #414).
--
-- Model: content-as-variants. A page has one logical identity and N locale
-- variants linked by a shared translation_group. See the runbooks decision
-- record "decision-multilanguage-content-model.md".
--
-- Why no constraint is dropped here: locale lives in the URL path (/es/about),
-- so web_pages.link stays globally unique on its own and its UNIQUE constraint
-- is untouched. That keeps this migration additive, which matters because it
-- now runs against a live database.
--
-- Why web_page_versions is absent although #414 lists it: versions reference
-- web_pages(web_page_id) ON DELETE CASCADE, so a Spanish page's versions are
-- already scoped by their parent row. A locale column there would duplicate the
-- parent's value with nothing keeping the two in step.

-- locale: BCP 47, sized for language-script-region (e.g. 'pt-BR', 'zh-Hant-TW').
-- Existing rows are English -- the master template has hardcoded lang="en" since
-- the beginning, so this backfill states what is already true rather than
-- assuming a default.
ALTER TABLE web_pages  ADD COLUMN IF NOT EXISTS locale VARCHAR(35) NOT NULL DEFAULT 'en';
ALTER TABLE content    ADD COLUMN IF NOT EXISTS locale VARCHAR(35) NOT NULL DEFAULT 'en';
ALTER TABLE blog_posts ADD COLUMN IF NOT EXISTS locale VARCHAR(35) NOT NULL DEFAULT 'en';

-- translation_group: shared by every variant of the same logical content.
-- VARCHAR rather than a self-referencing id, matching the existing
-- content_unique_id / post_unique_id convention in this schema, and so a
-- variant can be created before its siblings exist.
ALTER TABLE web_pages  ADD COLUMN IF NOT EXISTS translation_group VARCHAR(255);
ALTER TABLE content    ADD COLUMN IF NOT EXISTS translation_group VARCHAR(255);
ALTER TABLE blog_posts ADD COLUMN IF NOT EXISTS translation_group VARCHAR(255);

-- Backfill: every existing row is the sole member of its own group. Seeded from
-- the row's own stable identifier so the value is reproducible and readable,
-- not a random id nobody can trace back.
UPDATE web_pages  SET translation_group = 'wp-'   || web_page_id WHERE translation_group IS NULL;
UPDATE content    SET translation_group = 'cnt-'  || content_id  WHERE translation_group IS NULL;
UPDATE blog_posts SET translation_group = 'post-' || post_id     WHERE translation_group IS NULL;

-- Enforced only after the backfill, so the migration cannot half-apply and
-- leave rows that violate it.
ALTER TABLE web_pages  ALTER COLUMN translation_group SET NOT NULL;
ALTER TABLE content    ALTER COLUMN translation_group SET NOT NULL;
ALTER TABLE blog_posts ALTER COLUMN translation_group SET NOT NULL;

-- One variant per locale per group. This is the constraint that makes
-- "fetch the group" safe: without it a group could hold two Spanish rows and
-- hreflang would emit duplicate entries for the same locale, which Google
-- treats as an error rather than ignoring.
CREATE UNIQUE INDEX IF NOT EXISTS uq_web_pages_group_locale  ON web_pages  (translation_group, locale);
CREATE UNIQUE INDEX IF NOT EXISTS uq_content_group_locale    ON content    (translation_group, locale);
CREATE UNIQUE INDEX IF NOT EXISTS uq_blog_posts_group_locale ON blog_posts (translation_group, locale);

-- Supports the language switcher and hreflang emission, both of which fetch
-- every sibling of the current page on each render.
CREATE INDEX IF NOT EXISTS idx_web_pages_translation_group  ON web_pages  (translation_group);
CREATE INDEX IF NOT EXISTS idx_content_translation_group    ON content    (translation_group);
CREATE INDEX IF NOT EXISTS idx_blog_posts_translation_group ON blog_posts (translation_group);

-- The site's default locale. Content in any other locale is served from a
-- prefixed path; the default stays unprefixed so existing URLs keep working.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (2500, 'Default language', 'site.language.default', 'en', 'text')
ON CONFLICT (property_name) DO NOTHING;
