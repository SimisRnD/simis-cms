-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- A real destination for the footer's "Subscribe" link/button (footer.default, NEW_20000) so it
-- isn't a dead link on a fresh install -- matching the /about-us precedent in NEW_50020. Uses the
-- emailSubscribe widget's showName=true form (name, email, organization, title, phone, country,
-- and any public mailing lists an admin has marked show_online), keeping the newsletter signup
-- off the footer itself instead of embedding it inline there.
--
-- Runs after NEW_50030, which makes web_pages.translation_group NOT NULL -- unlike that
-- migration's own backfill (an UPDATE against rows that already existed from before the
-- constraint), this INSERT has to satisfy NOT NULL immediately, before the new row's own id is
-- known to build a 'wp-' || web_page_id value from. Uses a random placeholder instead, the same
-- reasoning WebPageRepository.add() applies for pages created through the admin UI (issue #1237).
--
-- mailingListUniqueId names the list seeded by NEW_10071 by its stable id rather than its name, so
-- an admin renaming that list in Admin/Mailing Lists cannot break the page the product ships with
-- (issue #1724). UPGRADE_20260831.1900 makes the same substitution on existing sites.
INSERT INTO web_pages (link, page_title, page_description, searchable, page_xml, translation_group) VALUES
('/subscribe', 'Subscribe', 'Subscribe to receive our latest news and updates.', true,
'<page>
  <section>
    <column class="small-12 medium-8 medium-offset-2 cell">
      <widget name="content">
        <uniqueId>subscribe-content</uniqueId>
        <html><![CDATA[<h2>Subscribe</h2><p>Sign up to receive our latest news and updates.</p>]]></html>
      </widget>
      <widget name="emailSubscribe">
        <mailingListUniqueId>newsletter</mailingListUniqueId>
        <showName>true</showName>
        <buttonName>Subscribe</buttonName>
        <successMessage>You are now subscribed!</successMessage>
      </widget>
    </column>
  </section>
</page>',
'wp-' || gen_random_uuid());
