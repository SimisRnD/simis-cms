-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- The site-wide footer (footer-layout.xml / the footer.default and footer.4column containers seeded
-- in NEW_20000) has always had a "Learn more about us" button linking to /about-us, but nothing
-- previously created that page -- on a fresh install it was a dead link shown on every page of the
-- site by default. Seed it here, matching the /legal/privacy and /legal/terms pages above.

INSERT INTO web_pages (link, page_title, page_description, searchable, page_xml) VALUES
('/about-us', 'About Us', 'Learn more about us.', true,
'<page>
  <section>
    <column class="small-12 cell">
      <widget name="content">
        <uniqueId>about-us</uniqueId>
        <html><![CDATA[<h2>About Us</h2><p>Coming soon.</p>]]></html>
      </widget>
    </column>
  </section>
</page>');
