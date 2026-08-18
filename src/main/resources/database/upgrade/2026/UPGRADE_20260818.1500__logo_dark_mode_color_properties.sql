-- Make the logo's dark-mode rendering admin-overridable, independently per location, the same
-- way theme.logo.color/theme.footer.logo.color already made light-mode rendering configurable
-- (see UPGRADE_20260818.1200). Until now cms/logo.jsp's dark-mode branch was 100% hardcoded to
-- always show the all-white logo whenever theme.ui.mode puts a visitor in dark mode -- reachable
-- for 3 of its 4 real options (Dark only, Match visitor's device, Match device/let visitor
-- choose), not just a hypothetical -- with no site property controlling it at all.
--
-- Both new properties seed at 'all-white', the exact value the old hardcoding always produced, so
-- this changes nothing about how any existing site actually renders -- it only makes the
-- previously-fixed behavior changeable. Plain idempotent inserts, not conditional updates, since
-- these properties never existed before this migration (same pattern as
-- UPGRADE_20260806.1300__theme_link_color.sql).
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (8, 'Logo color (dark mode)', 'theme.logo.color.dark', 'all-white', 'text')
ON CONFLICT (property_name) DO NOTHING;

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (121, 'Footer logo color (dark mode)', 'theme.footer.logo.color.dark', 'all-white', 'text')
ON CONFLICT (property_name) DO NOTHING;

-- The property inserts above are necessary but not sufficient for the footer axis. footer.jsp's
-- widget preferences are not read from footer-layout.xml on an already-installed site --
-- NEW_20000__insert_containers.sql copied a snapshot of that XML into the web_containers table at
-- install time, and XMLFooterLoader always prefers the database row over the file (same trap
-- UPGRADE_20260818.1200 already hit and documented). Without this, footer.default/footer.4column
-- would keep rendering from the stale database copy indefinitely, and the new
-- theme.footer.logo.color.dark property would silently have no effect on the footer.
--
-- Scoped to an exact substring match of the markup UPGRADE_20260818.1200 itself left behind (same
-- before/after text as this migration's NEW_20000 seed edit), so this is a no-op -- not a
-- corruption risk -- on any install where an admin has already customized footer.default/
-- footer.4column via the Page Layout designer and the stock markup is no longer present verbatim.
-- Header needs no equivalent change: none of the 6 header layouts pass a colorProperty today, so
-- logo.jsp's new default (theme.logo.color.dark) covers header.default automatically, same as it
-- already does for light mode.
UPDATE web_containers
SET container_xml = REPLACE(
  container_xml,
  '<widget name="logo">
        <colorProperty>theme.footer.logo.color</colorProperty>
        <maxHeight>50px</maxHeight>
      </widget>',
  '<widget name="logo">
        <colorProperty>theme.footer.logo.color</colorProperty>
        <colorPropertyDark>theme.footer.logo.color.dark</colorPropertyDark>
        <maxHeight>50px</maxHeight>
      </widget>'
)
WHERE container_name = 'footer.default'
  AND container_xml LIKE '%<widget name="logo">
        <colorProperty>theme.footer.logo.color</colorProperty>
        <maxHeight>50px</maxHeight>
      </widget>%';

UPDATE web_containers
SET container_xml = REPLACE(
  container_xml,
  '<widget name="logo">
          <colorProperty>theme.footer.logo.color</colorProperty>
          <maxHeight>50px</maxHeight>
        </widget>',
  '<widget name="logo">
          <colorProperty>theme.footer.logo.color</colorProperty>
          <colorPropertyDark>theme.footer.logo.color.dark</colorPropertyDark>
          <maxHeight>50px</maxHeight>
        </widget>'
)
WHERE container_name = 'footer.4column'
  AND container_xml LIKE '%<widget name="logo">
          <colorProperty>theme.footer.logo.color</colorProperty>
          <maxHeight>50px</maxHeight>
        </widget>%';
