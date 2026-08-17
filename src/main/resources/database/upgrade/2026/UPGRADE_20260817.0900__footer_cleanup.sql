-- Footer cleanup, for existing installs: existing installs' stored footer content is a DB
-- snapshot (web_containers.container_xml), seeded once at install time and patched here to match
-- -- editing footer-layout.xml/NEW_20000 alone never reaches an existing site, the same gap the
-- three UPGRADE_20260816 footer migrations already fixed for their own changes.
--
-- 1. Remove the "Subscribe to Our News" button (a plain "Subscribe" link now lives under
--    About Us instead -- see the table_of_contents update below), the Privacy Policy link, and
--    the Terms of Use link -- both of the latter are now redundant with Site Links. Also
--    center-justifies "Follow Us" and the social media icons, and relocates the copyright line
--    into this column, right-aligned on medium+ screens (bottom-right of the footer).
-- A no-op (not an error) if an admin has since edited this footer's Follow Us column by hand and
-- the text no longer matches exactly.
UPDATE web_containers
SET container_xml = REPLACE(container_xml,
'      <widget name="socialMediaLinks" class="margin-bottom-10 small-margin-bottom-20" />
      <widget name="button" class="margin-top-30">
        <title>Subscribe to Our News</title>
        <link>/subscribe</link>
      </widget>
      <widget name="link">
        <name>Privacy Policy</name>
        <link>/legal/privacy</link>
        <class>margin-left-10 margin-right-10 text-underline</class>
      </widget>
      <widget name="link">
        <name>Terms of Use</name>
        <link>/legal/terms</link>
        <class>text-underline</class>
      </widget>',
'      <widget name="socialMediaLinks" class="margin-bottom-10 small-margin-bottom-20 text-center" />
      <widget name="copyright" class="width-full margin-bottom-40 text-center medium-text-right" />')
WHERE container_name = 'footer.default';

-- 2. Center the "Follow Us" heading itself.
UPDATE web_containers
SET container_xml = REPLACE(container_xml,
'      <widget name="content">
        <html><![CDATA[<p class="margin-bottom-5 text-bold">Follow Us</p>]]></html>',
'      <widget name="content" class="text-center">
        <html><![CDATA[<p class="margin-bottom-5 text-bold">Follow Us</p>]]></html>')
WHERE container_name = 'footer.default';

-- 3. Remove the copyright widget from its old spot in the logo column, now that it has moved to
--    the Follow Us column above.
UPDATE web_containers
SET container_xml = REPLACE(container_xml,
'      <widget name="content" class="margin-top-15">
        <uniqueId>site-footer</uniqueId>
      </widget>
      <widget name="copyright" class="width-full margin-bottom-40" />
    </column>',
'      <widget name="content" class="margin-top-15">
        <uniqueId>site-footer</uniqueId>
      </widget>
    </column>')
WHERE container_name = 'footer.default';

-- 4. Add the "Subscribe" link under About Us, matching NEW_80000's fresh-install seed -- only
--    when the list is still exactly the original single-entry default, so an admin's own
--    customized About Us links are left alone. jsonb equality (not text matching) so this isn't
--    thrown off by insignificant whitespace/key-order differences in how the value is stored.
UPDATE table_of_contents
SET entries = entries || '[{"id":2,"name":"Subscribe","link":"/subscribe"}]'::jsonb
WHERE toc_unique_id = 'footer-about-us'
  AND entries = '[{"id":1,"name":"About Us","link":"/about-us"}]'::jsonb;
