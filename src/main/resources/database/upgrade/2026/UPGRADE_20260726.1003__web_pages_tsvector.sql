-- Add full-text search support to web_pages table
-- Implements PostgreSQL tsvector/GIN for efficient full-text search on page titles, keywords, and descriptions
-- Issue #404: Move web page full-text search to PostgreSQL tsvector/GIN

-- Add tsvector column to store the searchable text vector
ALTER TABLE web_pages ADD COLUMN IF NOT EXISTS tsv tsvector;

-- Create text search configuration for page-level content (if not exists)
-- Uses English stemmer via Snowball algorithm for intelligent matching
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'page_stem') THEN
    CREATE TEXT SEARCH DICTIONARY page_stem (
      TEMPLATE = snowball,
      Language = english
    );
    CREATE TEXT SEARCH CONFIGURATION page_stem (copy = english);
    ALTER TEXT SEARCH CONFIGURATION page_stem
      ALTER MAPPING FOR asciihword, asciiword, hword, hword_asciipart, hword_part, word
      WITH page_stem;
  END IF;
END $$;

-- Create or replace trigger function to maintain tsvector on insert/update
-- Weights: A=title (most important), B=keywords, C=description
CREATE OR REPLACE FUNCTION web_pages_tsv_trigger() RETURNS trigger AS $$
begin
  new.tsv :=
    setweight(to_tsvector('page_stem', COALESCE(new.page_title, '')), 'A') ||
    setweight(to_tsvector('page_stem', COALESCE(new.page_keywords, '')), 'B') ||
    setweight(to_tsvector('page_stem', COALESCE(new.page_description, '')), 'C');
  return new;
end $$ LANGUAGE plpgsql;

-- Drop existing trigger if it exists (to allow re-creation)
DROP TRIGGER IF EXISTS web_pages_tsv_trigger ON web_pages;

-- Create trigger to automatically update tsvector on insert or update
CREATE TRIGGER web_pages_tsv_trigger BEFORE INSERT OR UPDATE
ON web_pages FOR EACH ROW EXECUTE PROCEDURE web_pages_tsv_trigger();

-- Backfill tsvector for all existing web pages
UPDATE web_pages
SET tsv = setweight(to_tsvector('page_stem', COALESCE(page_title, '')), 'A') ||
          setweight(to_tsvector('page_stem', COALESCE(page_keywords, '')), 'B') ||
          setweight(to_tsvector('page_stem', COALESCE(page_description, '')), 'C')
WHERE tsv IS NULL;

-- Create GIN index for fast full-text search queries
-- GIN (Generalized Inverted Index) is optimal for tsvector searches
CREATE INDEX IF NOT EXISTS web_pages_tsv_idx ON web_pages USING gin(tsv);

-- Create index on searchable flag to support filtering (searchable=true)
CREATE INDEX IF NOT EXISTS web_pages_searchable_idx ON web_pages(searchable) WHERE enabled = true;
