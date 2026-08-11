-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- The header search box (search-form.jsp) always submits to /search, but nothing previously
-- created that page -- on a fresh install the search icon had nowhere to go. Seed it here using
-- the same layout as the "Search Results" filesystem template (web-templates/page/cms/Search
-- Results.xml), so search works out of the box without an admin needing to build this page first.
-- Not searchable itself, matching the /legal pages above -- a search results page searching
-- itself is not useful.

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
</page>');
