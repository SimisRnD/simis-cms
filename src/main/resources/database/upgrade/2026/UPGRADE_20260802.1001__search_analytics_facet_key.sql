-- Adds a facet dimension column so search analytics can distinguish a plain search from one
-- narrowed by a facet/filter (issue #638). Nullable: most searches have no facet applied, and
-- several search-results widgets have no facet concept at all yet.
ALTER TABLE search_analytics ADD COLUMN IF NOT EXISTS facet_key VARCHAR(100);
