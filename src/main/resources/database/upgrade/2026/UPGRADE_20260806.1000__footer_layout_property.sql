-- Adds a site property so a site can choose which named footer container is loaded when
-- theme.footer.style is set to "custom" (rendered via WebContainerLayoutCommand.retrieveFooter()).
-- Previously that lookup hardcoded "footer.default", leaving the "footer.4column" layout already
-- defined in footer-layout.xml permanently unreachable.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (111, 'Footer Layout', 'theme.footer.layout', 'footer.default', 'text')
ON CONFLICT (property_name) DO NOTHING;

-- Seeds the footer.4column container into the DB (mirroring how footer.default is already seeded
-- by NEW_20000__insert_containers.sql on fresh installs) so the on-page "edit footer" pencil icon
-- works immediately once an admin selects it above, instead of only being reachable read-only via
-- the footer-layout.xml file fallback in XMLFooterLoader.
INSERT INTO web_containers (container_name, label, image_path, container_xml) VALUES
('footer.4column', '4-column footer with company description, links, and social icons', 'Standard Footer.png',
'<footer name="footer.4column" title="4-Column Footer">
    <style><![CDATA[[
.platform-footer .dropdown.menu>li>a { padding: .5rem 0; }
.platform-footer .menu-title { font-weight: bold; }
.platform-footer ul.dropdown.menu { margin-top: 6px; }
    ]]></style>
    <section class="padding-top-20">
      <column class="small-12 medium-4 cell">
        <widget name="logo">
          <view>white</view>
          <maxHeight>50px</maxHeight>
        </widget>
        <widget name="content" class="margin-top-15">
          <html><![CDATA[<p>Since 2007, SimIS has been a pioneer in the modeling and simulation community, creating a bridge between traditional Cyber Security services and responsive, simulated architecture design.</p>]]></html>
        </widget>
        <widget name="button">
          <title>Learn more about SimIS</title>
          <link>/about-us</link>
        </widget>
        <widget name="copyright" class="width-full margin-bottom-40" />
      </column>
      <column class="small-6 medium-offset-1 medium-2 cell small-margin-bottom-20">
        <widget name="menu">
          <title>Company</title>
          <class>vertical</class>
          <tocUniqueId>footer-useful-links-1</tocUniqueId>
        </widget>
      </column>
      <column class="small-6 medium-2 cell small-margin-bottom-20">
        <widget name="menu">
          <title>Support</title>
          <class>vertical</class>
          <tocUniqueId>footer-useful-links-2</tocUniqueId>
        </widget>
      </column>
      <column class="small-12 medium-offset-1 medium-2 cell">
        <widget name="content">
          <html><![CDATA[<p class="margin-bottom-5 text-bold">Follow Us</p>]]></html>
        </widget>
        <widget name="socialMediaLinks" class="margin-top-10 small-margin-bottom-20" />
      </column>
    </section>
</footer>')
ON CONFLICT (container_name) DO NOTHING;
