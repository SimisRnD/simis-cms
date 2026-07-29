-- Add full-text search support to web_pages table
-- Implements PostgreSQL tsvector/GIN for efficient full-text search on page titles, keywords, and descriptions
-- Issue #404: Move web page full-text search to PostgreSQL tsvector/GIN

-- Add tsvector column to store the searchable text vector
ALTER TABLE web_pages ADD COLUMN IF NOT EXISTS tsv tsvector;

-- Reuses title_stem (NEW_10024__new_items.sql), the same English-stemming text search
-- configuration items/blog_posts/wikis/ecommerce already share, rather than creating a redundant,
-- functionally identical one -- there is no page-specific stemming need here.

-- Create or replace trigger function to maintain tsvector on insert/update
-- Weights: A=title (most important), B=keywords, C=description
CREATE OR REPLACE FUNCTION web_pages_tsv_trigger() RETURNS trigger AS $$
begin
  new.tsv :=
    setweight(to_tsvector('title_stem', COALESCE(new.page_title, '')), 'A') ||
    setweight(to_tsvector('title_stem', COALESCE(new.page_keywords, '')), 'B') ||
    setweight(to_tsvector('title_stem', COALESCE(new.page_description, '')), 'C');
  return new;
end $$ LANGUAGE plpgsql;

-- Drop existing trigger if it exists (to allow re-creation)
DROP TRIGGER IF EXISTS web_pages_tsv_trigger ON web_pages;

-- Create trigger to automatically update tsvector on insert or update
CREATE TRIGGER web_pages_tsv_trigger BEFORE INSERT OR UPDATE
ON web_pages FOR EACH ROW EXECUTE PROCEDURE web_pages_tsv_trigger();

-- Backfill tsvector for all existing web pages
UPDATE web_pages
SET tsv = setweight(to_tsvector('title_stem', COALESCE(page_title, '')), 'A') ||
          setweight(to_tsvector('title_stem', COALESCE(page_keywords, '')), 'B') ||
          setweight(to_tsvector('title_stem', COALESCE(page_description, '')), 'C')
WHERE tsv IS NULL;

-- Create GIN index for fast full-text search queries
-- GIN (Generalized Inverted Index) is optimal for tsvector searches
CREATE INDEX IF NOT EXISTS web_pages_tsv_idx ON web_pages USING gin(tsv);

-- Create index on searchable flag to support filtering (searchable=true)
CREATE INDEX IF NOT EXISTS web_pages_searchable_idx ON web_pages(searchable) WHERE enabled = true;
