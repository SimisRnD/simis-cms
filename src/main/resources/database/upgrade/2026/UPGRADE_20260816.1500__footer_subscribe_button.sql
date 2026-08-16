-- Replaces the footer's inline newsletter signup (heading + email field + captcha) with a plain
-- button linking to /subscribe (added in this same upgrade batch). Existing installs' stored
-- header/footer content is a DB snapshot (web_containers.container_xml), seeded once at install
-- time by NEW_20000__insert_containers.sql -- editing that file alone never reaches an existing
-- site, the same gap fixed for the header search placeholder in
-- UPGRADE_20260816.1200__header_search_placeholder.sql. A no-op (not an error) if an admin has
-- since edited this footer's newsletter widget by hand and the text no longer matches exactly.
UPDATE web_containers
SET container_xml = REPLACE(
  container_xml,
  '      <widget name="content">
        <html><![CDATA[<p class="margin-top-30 text-bold">Subscribe to Our News</p>]]></html>
      </widget>
      <widget name="emailSubscribe" class="margin-bottom-30">
        <view>inline</view>
        <buttonName>Subscribe</buttonName>
      </widget>',
  '      <widget name="button" class="margin-top-30">
        <title>Subscribe to Our News</title>
        <link>/subscribe</link>
      </widget>'
)
WHERE container_name = 'footer.default';
