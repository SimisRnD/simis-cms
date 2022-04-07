-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Web headers and footers

-- Headers --

INSERT INTO web_containers (container_name, label, image_path, container_xml) VALUES
('header.default', 'Standard header with logo, buttons, links, search, and a menu', 'Standard Header.png',
'<header name="header.default" title="Logo, menu">
  <!-- Small menu -->
  <section id="platform-small-menu">
    <column class="small-12 medium-12 cell text-center">
      <widget name="systemAlert" class="utility-bar" />
      <widget name="toggleMenu">
        <view>white</view>
      </widget>
    </column>
  </section>
  <section id="platform-small-toggle-menu">
    <column>
      <widget name="mainMenu">
        <view>nested</view>
        <submenuIcon>fa-angle-right</submenuIcon>
      </widget>
      <widget name="menu">
        <class>vertical</class>
        <links>
          <link name="Contact Us" link="/contact-us" />
          <link name="Login" link="/login" role="guest" rule="site.login" />
          <link name="Register" link="/login" role="guest" rule="site.registrations" />
          <link name="My Account" link="/my-page" role="users" />
          <link name="Log Out" link="/logout" role="users" />
        </links>
      </widget>
    </column>
  </section>
  <!-- Main menu -->
  <section class="utility-bar grid-x align-middle">
    <column class="small-12 cell text-center">
      <widget name="systemAlert" />
    </column>
  </section>
  <section id="header-main-menu" class="padding-top-10 padding-bottom-10">
    <column class="small-12 cell">
      <widget name="logo" class="float-left margin-right-25">
        <view>color</view>
        <maxHeight>50px</maxHeight>
      </widget>
      <widget name="menu" style="font-size: 14px;" class="float-right header-item">
        <class>align-left text-no-wrap menu-button</class>
        <links>
          <link name="Contact Us" class="button primary round" link="/contact-us" />
          <link name="Login" link="/login" role="guest" />
          <link name="Register" link="/login" role="guest" rule="site.registrations" />
          <link name="My Account" link="/my-page" role="users" />
          <link name="Log Out" link="/logout" role="users" />
          <link name="Cart" icon="fa fa-bag-shopping" icon-only="true" type="cart" />
          <link name="Settings" icon="fa-cog" icon-only="true" type="admin" />
        </links>
      </widget>
      <widget name="searchForm" class="float-right header-search">
        <expand>true</expand>
      </widget>
      <widget name="mainMenu" class="float-left header-item">
        <submenuIcon>fa-angle-right</submenuIcon>
        <submenuIconClass>float-left margin-right-5</submenuIconClass>
      </widget>
    </column>
  </section>
</header>');

-- Footers --

INSERT INTO web_containers (container_name, label, image_path, container_xml) VALUES
('footer.default', 'Standard footer with logo, links, email subscribe, and copyright', 'Standard Footer.png',
'<footer name="footer.default" title="Default Footer">
  <section class="padding-top-20">
    <column class="small-12 medium-4 cell medium-order-1 small-order-1">
      <widget name="logo">
        <view>white</view>
        <maxHeight>50px</maxHeight>
      </widget>
      <widget name="content" class="margin-top-15">
        <uniqueId>site-footer</uniqueId>
      </widget>
      <widget name="button">
        <title>Learn more about us</title>
        <link>/about-us</link>
        <class>primary round</class>
      </widget>
      <widget name="copyright" class="width-full margin-bottom-40" />
    </column>
    <column class="small-6 medium-offset-1 medium-2 cell small-margin-bottom-20 medium-order-2 small-order-3">
      <widget name="menu">
        <title>Company</title>
        <class>vertical</class>
        <tocUniqueId>footer-useful-links-1</tocUniqueId>
      </widget>
    </column>
    <column class="small-6 medium-2 cell small-margin-bottom-20 medium-order-3 small-order-4">
      <widget name="menu">
        <title>Support</title>
        <class>vertical</class>
        <tocUniqueId>footer-useful-links-2</tocUniqueId>
      </widget>
    </column>
    <column class="small-12 medium-auto cell medium-order-4 small-order-2 padding-bottom-30">
      <widget name="content">
        <html><![CDATA[<p class="margin-bottom-5 text-bold">Follow Us</p>]]></html>
      </widget>
      <widget name="socialMediaLinks" class="margin-bottom-10 small-margin-bottom-20" />
      <widget name="content">
        <html><![CDATA[<p class="margin-top-30 text-bold">Subscribe to Our News</p>]]></html>
      </widget>
      <widget name="emailSubscribe" class="margin-bottom-30">
        <view>inline</view>
        <buttonName>Subscribe</buttonName>
      </widget>
      <widget name="link">
        <name>Privacy Policy</name>
        <link>/legal/privacy</link>
        <class>margin-left-10 margin-right-10 text-underline</class>
      </widget>
      <widget name="link">
        <name>Terms of Use</name>
        <link>/legal/terms</link>
        <class>text-underline</class>
      </widget>
    </column>
  </section>
</footer>');
