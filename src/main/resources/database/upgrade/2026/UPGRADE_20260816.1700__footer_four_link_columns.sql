-- Splits footer.default's "Company"/"Support" 2-column link layout into 4 columns -- About Us,
-- Featured Solutions, Information For, Site Links -- each independently admin-configurable via
-- its own table_of_contents entry, matching the Booz Allen reference the footer redesign is
-- modeled on. The 4 link columns become their own top row (each medium-3, 25% width, so all four
-- titles -- including the longest, "Featured Solutions" -- fit on one line); the logo and Follow
-- Us move to a second row below, sharing it evenly. Matches the install-seed edit in NEW_20000.
--
-- Applied as several independent REPLACEs, each touching only a column's own opening tag, deleting
-- a whole sibling column, or inserting a whole new section -- none of them depend on what's inside
-- the Follow Us column, so this applies cleanly whether or not the /subscribe footer-button
-- migration has already run.

-- 1. Remove the old Company/Support columns entirely.
UPDATE web_containers
SET container_xml = REPLACE(container_xml,
'    <column class="small-6 medium-offset-1 medium-2 cell small-margin-bottom-20 medium-order-2 small-order-3">
      <widget name="menu">
        <title>Company</title>
        <class>vertical</class>
        <tocUniqueId>footer-useful-links-1</tocUniqueId>
      </widget>
    </column>
    <column class="small-6 medium-2 cell small-margin-bottom-20 medium-order-3 small-order-4">
      <widget name="menu">
        <title>Support</title>
        <class>vertical</class>
        <tocUniqueId>footer-useful-links-2</tocUniqueId>
      </widget>
    </column>
', '')
WHERE container_name = 'footer.default';

-- 2. The logo column now only shares its row with Follow Us.
UPDATE web_containers
SET container_xml = REPLACE(container_xml,
'<column class="small-12 medium-4 cell medium-order-1 small-order-1">',
'<column class="small-12 medium-6 cell">')
WHERE container_name = 'footer.default';

-- 3. Follow Us takes the other half of that row (its internal widgets are untouched).
UPDATE web_containers
SET container_xml = REPLACE(container_xml,
'<column class="small-12 medium-auto cell medium-order-4 small-order-2 padding-bottom-30">',
'<column class="small-12 medium-6 cell padding-bottom-30">')
WHERE container_name = 'footer.default';

-- 4. Insert the 4 link columns as a new first section, ahead of the (now logo+Follow-Us-only)
-- original section, which becomes the second row.
UPDATE web_containers
SET container_xml = REPLACE(container_xml,
'<footer name="footer.default" title="Default Footer">
  <section class="padding-top-20">',
'<footer name="footer.default" title="Default Footer">
  <section class="padding-top-20">
    <column class="small-6 medium-3 cell small-margin-bottom-20">
      <widget name="menu">
        <title>About Us</title>
        <class>vertical</class>
        <tocUniqueId>footer-about-us</tocUniqueId>
      </widget>
    </column>
    <column class="small-6 medium-3 cell small-margin-bottom-20">
      <widget name="menu">
        <title>Featured Solutions</title>
        <class>vertical</class>
        <tocUniqueId>footer-featured-solutions</tocUniqueId>
      </widget>
    </column>
    <column class="small-6 medium-3 cell small-margin-bottom-20">
      <widget name="menu">
        <title>Information For</title>
        <class>vertical</class>
        <tocUniqueId>footer-information-for</tocUniqueId>
      </widget>
    </column>
    <column class="small-6 medium-3 cell small-margin-bottom-20">
      <widget name="menu">
        <title>Site Links</title>
        <class>vertical</class>
        <tocUniqueId>footer-site-links</tocUniqueId>
      </widget>
    </column>
  </section>
  <section class="padding-bottom-20">')
WHERE container_name = 'footer.default';

-- Carry over any links an admin already configured under the old 2-column names, rather than
-- silently orphaning them -- position 1 (Company) becomes About Us, position 2 (Support) becomes
-- Featured Solutions. The two new columns (Information For, Site Links) start empty, same as a
-- fresh install.
UPDATE table_of_contents SET toc_unique_id = 'footer-about-us' WHERE toc_unique_id = 'footer-useful-links-1';
UPDATE table_of_contents SET toc_unique_id = 'footer-featured-solutions' WHERE toc_unique_id = 'footer-useful-links-2';
