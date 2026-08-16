-- Adds a placeholder to the header.default search overlay so it reads "What can we help you
-- with?" instead of the bare default "Search" once expanded. header-layout.xml only supplies the
-- template catalog the Web Container Designer offers -- the actually-installed header content is
-- this separate DB snapshot, seeded once at install time by NEW_20000__insert_containers.sql, so
-- editing that XML file alone never reaches an existing site's already-stored container_xml.
-- A no-op (not an error) if an admin has since edited this header's search widget by hand and the
-- text no longer matches exactly.
UPDATE web_containers
SET container_xml = REPLACE(
  container_xml,
  '<widget name="searchForm" class="float-right header-search">
        <expand>true</expand>
      </widget>',
  '<widget name="searchForm" class="float-right header-search">
        <expand>true</expand>
        <placeholder>What can we help you with?</placeholder>
      </widget>'
)
WHERE container_name = 'header.default';
