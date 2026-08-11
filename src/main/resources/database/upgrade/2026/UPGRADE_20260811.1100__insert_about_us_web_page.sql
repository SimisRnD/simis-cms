-- The site-wide footer has always had a "Learn more about us" button linking to /about-us, but
-- nothing previously created that page -- on every existing install the button has had nowhere to
-- go. Seed it here, matching the fresh-install seed added alongside this migration (NEW_50020).
-- Guarded with ON CONFLICT since an admin may have already built an /about-us page by hand
-- (web_pages.link is UNIQUE) -- don't overwrite it.

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
</page>')
ON CONFLICT (link) DO NOTHING;
