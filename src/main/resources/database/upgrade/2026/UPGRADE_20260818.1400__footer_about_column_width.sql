-- Widen footer.default's logo/description column from 1/3 to 5/12 of the row, giving the
-- "site-footer" content block a bit more breathing room before it wraps.
--
-- Same web_containers staleness as UPGRADE_20260818.1200: header-layout.xml/footer-layout.xml
-- and NEW_20000__insert_containers.sql are not what an already-installed site actually renders
-- from -- XMLFooterLoader always prefers the container_xml snapshot NEW_20000 copied into the
-- database at install time. Editing the XML files alone would not change anything on an
-- existing install.
--
-- Scoped to an exact match of the column's full current markup (immediately followed by the
-- logo widget's colorProperty, which UPGRADE_20260818.1200 already landed), so this is a no-op
-- -- not a corruption risk -- on any install where an admin has customized this column's width
-- or content through the Page Layout designer.
UPDATE web_containers
SET container_xml = REPLACE(
  container_xml,
  '<column class="small-12 medium-4 cell small-margin-bottom-20">
      <widget name="logo">
        <colorProperty>theme.footer.logo.color</colorProperty>',
  '<column class="small-12 medium-5 cell small-margin-bottom-20">
      <widget name="logo">
        <colorProperty>theme.footer.logo.color</colorProperty>'
)
WHERE container_name = 'footer.default'
  AND container_xml LIKE '%<column class="small-12 medium-4 cell small-margin-bottom-20">
      <widget name="logo">
        <colorProperty>theme.footer.logo.color</colorProperty>%';
