-- Adds the /subscribe page for existing installs, matching NEW_50040 on fresh ones -- a real
-- destination for the footer's "Subscribe" link so it isn't a dead link, and to move the
-- newsletter signup form (and its captcha) off the footer onto its own page.
--
-- translation_group is NOT NULL on this column already (UPGRADE_20260813.1000), so this INSERT
-- has to satisfy it immediately -- unlike that migration's own backfill (an UPDATE against rows
-- that already existed from before the constraint), this new row's own id isn't known yet to
-- build a 'wp-' || web_page_id value from. Uses a random placeholder instead, the same reasoning
-- WebPageRepository.add() applies for pages created through the admin UI (issue #1237).
INSERT INTO web_pages (link, page_title, page_description, searchable, page_xml, translation_group)
SELECT '/subscribe', 'Subscribe', 'Subscribe to receive our latest news and updates.', true,
'<page>
  <section>
    <column class="small-12 medium-8 medium-offset-2 cell">
      <widget name="content">
        <uniqueId>subscribe-content</uniqueId>
        <html><![CDATA[<h2>Subscribe</h2><p>Sign up to receive our latest news and updates.</p>]]></html>
      </widget>
      <widget name="emailSubscribe">
        <mailingList>Newsletter</mailingList>
        <showName>true</showName>
        <buttonName>Subscribe</buttonName>
        <successMessage>You are now subscribed!</successMessage>
      </widget>
    </column>
  </section>
</page>',
'wp-' || gen_random_uuid()
WHERE NOT EXISTS (SELECT 1 FROM web_pages WHERE link = '/subscribe');
