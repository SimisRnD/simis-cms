-- Third navigation level (issue #1728): a menu item can now be the child of another menu item,
-- not just of a tab. The site had outgrown two levels -- nine contract-vehicle pages and the
-- autonomous product pages had no route in from the nav at all, because everything that is not
-- a top-nine "solution" had nowhere to live.
--
-- Self-referencing rather than a third table, so a nested item keeps the same columns as any
-- other item (link, page_title, draft, enabled, role_id_list) and the access rules do not fork.
--
-- NULL is the existing behaviour: every row already in this table sits directly under its tab and
-- keeps doing so, so this is additive and needs no backfill.
--
-- Mirrors the CREATE TABLE in install/NEW_10010__new_cms.sql -- both are required, since a fresh
-- install never runs this file and an existing database never runs that one.
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS parent_menu_item_id BIGINT
  REFERENCES menu_items(menu_item_id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS menu_items_parent_idx ON menu_items(parent_menu_item_id);
