-- Full-text search support for web_pages (#404): the tsvector column, its maintenance trigger and
-- the GIN index backing WebPageRepository.search().
--
-- This exists as its own script, rather than living beside web_pages in NEW_10010, because the
-- trigger stems with the title_stem text search configuration -- and title_stem is created in
-- NEW_10024__new_items.sql, which Flyway runs AFTER NEW_10010. Numbered 10195 so it sorts after
-- 10024 (title_stem exists) and before NEW_20005__insert_web_pages.sql (so every seeded page gets
-- its tsv populated by the trigger on insert).
--
-- Must stay in step with UPGRADE_20260726.1003__web_pages_tsvector.sql, which does the same thing
-- for existing installs. No backfill statement is needed here: unlike the upgrade path, web_pages
-- is still empty at this point, and the trigger populates every row inserted from 20005 onward.

ALTER TABLE web_pages ADD COLUMN tsv TSVECTOR;

-- Reuses title_stem (NEW_10024), the same English-stemming configuration items/blog_posts/wikis/
-- ecommerce already share, rather than creating a redundant, functionally identical one -- there is
-- no page-specific stemming need here.
-- Weights: A=title (most important), B=keywords, C=description
CREATE OR REPLACE FUNCTION web_pages_tsv_trigger() RETURNS trigger AS $$
begin
  new.tsv :=
    setweight(to_tsvector('title_stem', COALESCE(new.page_title, '')), 'A') ||
    setweight(to_tsvector('title_stem', COALESCE(new.page_keywords, '')), 'B') ||
    setweight(to_tsvector('title_stem', COALESCE(new.page_description, '')), 'C');
  return new;
end $$ LANGUAGE plpgsql;

CREATE TRIGGER web_pages_tsv_trigger BEFORE INSERT OR UPDATE
ON web_pages FOR EACH ROW EXECUTE PROCEDURE web_pages_tsv_trigger();

-- GIN is optimal for tsvector searches
CREATE INDEX web_pages_tsv_idx ON web_pages USING gin(tsv);

-- Supports filtering on the searchable flag (searchable = true)
CREATE INDEX web_pages_searchable_idx ON web_pages(searchable) WHERE enabled = true;
