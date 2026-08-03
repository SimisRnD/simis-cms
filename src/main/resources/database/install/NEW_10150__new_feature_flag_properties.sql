-- Issue #410: the features.* site property namespace for lightweight per-feature toggles, read
-- through FeatureFlagCommand.isEnabled() (which is just
-- LoadSitePropertyCommand.loadByNameAsBoolean("features." + name) against the existing
-- cached/invalidated site-property machinery -- see LoadSitePropertyCommand and
-- SitePropertyRepository.saveAll()'s CacheManager.invalidateKey() call). Edited from the admin UI at
-- /admin/feature-flags via the same generic sitePropertiesEditor widget every other
-- /admin/*-properties settings page uses.
--
-- features.layout-editor gates the P4 composition-canvas designer in WebPageDesignerWidget and
-- defaults to true, matching the always-on behavior that shipped before this flag existed (PR #734
-- made that reachability path work) -- turning it off stops *offering* the designer (the
-- "Webpage Designer" page-creation template and the widget's editor="designer" branch points)
-- without touching any page's already-persisted XML.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (10, 'Enable visual page layout editor', 'features.layout-editor', 'true', 'boolean');

-- Reserved for the item-tags facet-query slice of issue #632 (Slice 1 shipped the tag model/CRUD in
-- PR #863; facet-query search itself was deferred). Not yet consulted by any code path -- toggling
-- this does nothing until that slice is built and wired to it.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (20, 'Enable item tag facet search', 'features.item-tags-facet-search', 'false', 'boolean');
