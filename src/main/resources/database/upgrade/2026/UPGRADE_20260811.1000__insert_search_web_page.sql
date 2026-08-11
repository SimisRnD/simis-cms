-- The header search box (search-form.jsp) always submits to /search, but nothing previously
-- created that page -- on every existing install the search icon has had nowhere to go. Seed it
-- here, matching the fresh-install seed added alongside this migration (NEW_50010), using the same
-- layout as the "Search Results" filesystem template. Guarded with ON CONFLICT since an admin may
-- have already built a /search page by hand (web_pages.link is UNIQUE) -- don't overwrite it.

INSERT INTO web_pages (link, page_title, searchable, page_xml) VALUES
('/search', 'Search', false,
'<page>
  <section class="align-center">
    <column class="small-12 medium-9 cell">
      <widget name="content">
        <uniqueId>search-hello</uniqueId>
        <html><![CDATA[<h2>Search</h2>]]></html>
      </widget>
      <widget name="searchInfo" />
      <widget name="webPageTitleSearchResults" hr="true">
        <title>Pages found:</title>
        <showWhenEmpty>false</showWhenEmpty>
      </widget>
      <widget name="calendarSearchResults" hr="true">
        <title>Upcoming Event:</title>
        <limit>1</limit>
        <showWhenEmpty>false</showWhenEmpty>
      </widget>
      <widget name="webPageSearchResults" hr="true">
        <title>Web Pages Found:</title>
        <limit>5</limit>
        <showWhenEmpty>false</showWhenEmpty>
      </widget>
      <widget name="blogPostSearchResults" hr="true">
        <title>News Posts Found:</title>
        <limit>5</limit>
        <showWhenEmpty>false</showWhenEmpty>
      </widget>
      <widget name="wikiSearchResults" hr="true">
        <title>Documentation Found:</title>
        <limit>5</limit>
        <showWhenEmpty>false</showWhenEmpty>
      </widget>
      <widget name="itemsSearchResults" hr="true">
        <title>Resources Found:</title>
        <limit>10</limit>
        <showWhenEmpty>false</showWhenEmpty>
        <useItemLink>true</useItemLink>
      </widget>
      <widget name="content" class="margin-50">
        <uniqueId>search-footer</uniqueId>
      </widget>
    </column>
  </section>
</page>')
ON CONFLICT (link) DO NOTHING;
