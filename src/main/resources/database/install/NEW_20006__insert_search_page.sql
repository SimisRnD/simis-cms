-- Copyright 2026 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Search Results page
--
-- The default site header (header.default, see NEW_20000__insert_containers.sql) ships a search
-- box (searchForm widget) whose form is hard-coded to submit to /search on every site -- but
-- unlike every other bundled widget, nothing ever created a page there. Without this, the
-- always-present header search box leads to a 404 until an admin manually builds /search from the
-- "Search Results" page template (WEB-INF/web-templates/page/cms/Search Results.xml) -- the same
-- page_xml that template produces, with ${webPageName} substituted to "search" the same way
-- WebPageDesignerWidget.post() does it.

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
</page>');
