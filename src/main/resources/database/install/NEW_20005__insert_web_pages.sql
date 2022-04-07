-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Web Pages

INSERT INTO web_pages (link, page_title, searchable, page_xml) VALUES
('/legal/privacy', 'Privacy Policy', false,
'<page>
  <section>
    <column class="small-12 cell">
      <widget name="content">
        <uniqueId>legal-privacy</uniqueId>
        <html><![CDATA[<h2>Privacy Policy</h2><p>Coming soon.</p>]]></html>
      </widget>
    </column>
  </section>
</page>');

INSERT INTO web_pages (link, page_title, searchable, page_xml) VALUES
('/legal/terms', 'Terms of Service Agreement', false,
'<page>
  <section>
    <column class="small-12 cell">
      <widget name="content">
        <uniqueId>legal-terms</uniqueId>
        <html><![CDATA[<h2>Terms of Service Agreement</h2><p>Coming soon.</p>]]></html>
      </widget>
    </column>
  </section>
</page>');
