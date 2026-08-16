-- Removes the "Learn more about us" button from footer.default's first column -- it was
-- competing with the logo/site-description content and copyright line for limited vertical
-- space in that column. Matches the install-seed edit in NEW_20000.
UPDATE web_containers
SET container_xml = REPLACE(container_xml,
'      <widget name="content" class="margin-top-15">
        <uniqueId>site-footer</uniqueId>
      </widget>
      <widget name="button">
        <title>Learn more about us</title>
        <link>/about-us</link>
        <class>primary round</class>
      </widget>
      <widget name="copyright" class="width-full margin-bottom-40" />',
'      <widget name="content" class="margin-top-15">
        <uniqueId>site-footer</uniqueId>
      </widget>
      <widget name="copyright" class="width-full margin-bottom-40" />')
WHERE container_name = 'footer.default';
