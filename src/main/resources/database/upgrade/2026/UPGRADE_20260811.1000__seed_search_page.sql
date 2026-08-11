-- Mirrors NEW_20006__insert_search_page.sql for existing installs -- see that file for the full
-- rationale (the header search box's form is hard-coded to /search on every site, but nothing
-- ever created a page there). Idempotent via ON CONFLICT so this is a no-op on any database where
-- an admin already built /search by hand.
INSERT INTO web_pages (link, page_title, searchable, show_in_sitemap, page_xml) VALUES
('/search', 'Search Results', false, false,
'<page>
  <section class="align-center">
    <column class="small-12 medium-9 cell">
      <widget name="content">
        <uniqueId>search-hello</uniqueId>
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
