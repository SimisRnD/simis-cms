-- Repair the web_pages full-text search index (issue #1745).
--
-- Symptom: the webPageTitleSearchResults widget returns nothing for every query, site-wide. It
-- carries showWhenEmpty=false, so it renders no heading at all and the failure is invisible --
-- what gets noticed is "one page cannot be found", which is how #1745 was reported.
--
-- Measured on the pilot before writing this:
--   * a page whose title is "SimIS Appoints New CFO and CTO" is not returned for "cfo", "cto" or
--     "appoints", while the content search returns that same page -- and the content search's own
--     query already requires searchable = true and draft = false, so the row satisfies every other
--     term in WebPageRepository.search()'s WHERE clause;
--   * that leaves only "tsv @@ PLAINTO_TSQUERY('title_stem', ?)";
--   * re-saving a page through the admin form did not make it findable, so the BEFORE INSERT OR
--     UPDATE trigger that is supposed to maintain tsv is not running;
--   * the wiki and item search widgets on the same page DO return results, and both query their own
--     tsvector columns through title_stem -- so the text search configuration itself is healthy and
--     the fault is specific to web_pages.
--
-- How a database ends up in that state -- confirmed from the repository history, not inferred.
-- The web_pages tsvector is created on the install track by NEW_10195__new_web_pages_tsv.sql and on
-- the upgrade track by UPGRADE_20260726.1003__web_pages_tsvector.sql:
--
--   2026-07-26  UPGRADE_20260726.1003 added
--   2026-08-13  the affected database is installed
--   2026-08-26  NEW_10195 added
--
-- A database installed in that window gets the feature from neither track:
--
--   * DatabaseCommand.installDatabase() baselines the upgrade track at a version at least as high as
--     every UPGRADE_* that exists at install time ("baseline to a high version to prevent old
--     migrations from running"). The 26 July upgrade is below that baseline, so Flyway records it as
--     already applied and never executes it. outOfOrder(true) on the upgrade track does not help --
--     it permits a lower version to run later, but not one the baseline has already accounted for.
--   * The install track only runs when the database is not yet installed, so NEW_10195 -- added two
--     weeks after -- is never applied either, and never even resolved.
--
-- Nothing reports this. The column is simply absent, and the only symptom is a search widget that
-- renders nothing because showWhenEmpty=false.
--
-- This is not specific to web_pages: any install-track migration added after a deployment exists
-- will never reach that deployment, and where its upgrade twin predates the deployment, neither
-- will. Worth addressing separately -- this migration repairs the one case that was found.
--
-- NEW_10195 also notes, correctly for its own track, that no backfill is needed because web_pages is
-- still empty when it runs. That assumption is what makes it unable to repair a database that
-- already has pages.
--
-- This migration is deliberately a superset of both files and is safe to run in any state: every
-- object is created only if missing, and the backfill is unconditional rather than WHERE tsv IS
-- NULL, so it also repairs rows whose vector is present but stale. On a healthy database it
-- recomputes a small table and changes nothing observable.

-- The column, if the install never created it
ALTER TABLE web_pages ADD COLUMN IF NOT EXISTS tsv tsvector;

-- The maintenance function. Reuses title_stem (NEW_10024__new_items.sql), the same English-stemming
-- configuration items/blog_posts/wikis/ecommerce already share.
-- Weights: A=title (most important), B=keywords, C=description.
CREATE OR REPLACE FUNCTION web_pages_tsv_trigger() RETURNS trigger AS $$
begin
  new.tsv :=
    setweight(to_tsvector('title_stem', COALESCE(new.page_title, '')), 'A') ||
    setweight(to_tsvector('title_stem', COALESCE(new.page_keywords, '')), 'B') ||
    setweight(to_tsvector('title_stem', COALESCE(new.page_description, '')), 'C');
  return new;
end $$ LANGUAGE plpgsql;

-- Dropped first so this is a repair rather than a duplicate-trigger error where one already exists
DROP TRIGGER IF EXISTS web_pages_tsv_trigger ON web_pages;
CREATE TRIGGER web_pages_tsv_trigger BEFORE INSERT OR UPDATE
ON web_pages FOR EACH ROW EXECUTE PROCEDURE web_pages_tsv_trigger();

-- Unconditional backfill. UPGRADE_20260726.1003 guards this with WHERE tsv IS NULL, which is right
-- for adding the feature but cannot repair a vector that exists and is wrong -- for example one
-- computed before a page's keywords or description were filled in.
UPDATE web_pages
SET tsv = setweight(to_tsvector('title_stem', COALESCE(page_title, '')), 'A') ||
          setweight(to_tsvector('title_stem', COALESCE(page_keywords, '')), 'B') ||
          setweight(to_tsvector('title_stem', COALESCE(page_description, '')), 'C');

-- GIN is optimal for tsvector searches
CREATE INDEX IF NOT EXISTS web_pages_tsv_idx ON web_pages USING gin(tsv);

-- Supports WebPageRepository.search()'s searchable = true filter
CREATE INDEX IF NOT EXISTS web_pages_searchable_idx ON web_pages(searchable) WHERE enabled = true;
