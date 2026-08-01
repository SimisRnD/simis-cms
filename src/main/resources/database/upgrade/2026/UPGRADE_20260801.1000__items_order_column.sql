-- Issue #815: items has no order column, so drag-to-reorder in the collection item management UI
-- (items-list.jsp) has nowhere to persist a custom order -- PageServlet's reorderCollectionItem
-- mutation has honestly returned HTTP 501 since PR #698 rather than lying about success.
--
-- Mirrors the menu_items.item_order / mailing_lists.list_order convention already used elsewhere
-- in this schema (a plain nullable-by-default INTEGER, no FK, no NOT NULL). Also mirrored into
-- database/install/NEW_10024__new_items.sql so a fresh install and this upgrade path produce the
-- same column -- this program already hit the bug shape of a migration only living in one of the
-- two paths (media_assets, issue #431), so both are done together here.
ALTER TABLE items ADD COLUMN IF NOT EXISTS item_order INTEGER DEFAULT 100;

-- Backfill existing rows to match today's default listing order (LOWER(name) within a collection)
-- rather than an arbitrary/meaningless value, so that once ItemRepository.findAll() switches its
-- default sort to item_order, a collection that has never been manually reordered renders
-- identically to before the migration -- only an actual drag-to-reorder action should ever change
-- what a user sees.
WITH ranked AS (
  SELECT item_id,
         ROW_NUMBER() OVER (PARTITION BY collection_id ORDER BY LOWER(name), item_id) AS rn
  FROM items
)
UPDATE items
SET item_order = ranked.rn
FROM ranked
WHERE items.item_id = ranked.item_id;

CREATE INDEX IF NOT EXISTS items_ord_idx ON items(item_order);
