# CMS Platform -- SimIS CMS

[![Java CI](https://github.com/rajkowski/cms-platform/actions/workflows/ant.yml/badge.svg)](https://github.com/rajkowski/cms-platform/actions/workflows/ant.yml)

What is SimIS CMS? [Agile, Enterprise, Open Source Content Management System (CMS) and Portal](https://www.simiscms.com).

SimIS CMS comes out-of-the-box with modules, advanced security, easy setup, and powerful developer features. Use and configure what's there, and customize what's not. The flexible Open Source license lets you move beyond the technology to focus on delivering a quality website.

## Documentation

The latest CMS Platform documentation is available at <https://github.com/rajkowski/cms-platform/blob/main/docs/index.md> including technical documentation and diagrams.

The CHANGELOG is at <https://github.com/rajkowski/cms-platform/blob/main/docs/CHANGELOG.md>.

Documentation is in MKDocs format intended for use in platforms which use MKDocs like [Spotify Backstage](https://backstage.io).

## License

```text
Copyright 2024

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Overview

Need a website or web portal? CMS Platform can be used from Day 1:

* Once installed the administrator signs in and can quickly create a sitemap. Working on their own, or with others, the pages of the site are added.
* Each web page can have shared elements and styles, as well as their own elements and styles.
* A designer can work on the site's global style and layout, then target page-by-page improvements, while content authors fill out the web page content.
* Content authors work with the page elements which include searchable text, images, and videos. There are many dynamic elements which can be selected, including slideshows, news feeds, calendar events, blog posts, and more.
* For more complex components, a developer can work both online and off to enable the functionality or create it.

## Features

* **CMS**: Site Map, Web Pages (Templates, UI Designer, SEO, Searchable) with Content and Images, HTML Editor, CSS Editor, Blogs, Form Data, Calendars, Folders and Files, Mailing Lists, Videos, Wikis, Search, Site Alerts, Form Pop-Ups, Sticky Header and Buttons, Responsive, Bot Detection, Static Site Generator (SSG)
* **Analytics**: Tracking for Sessions, Hits, Geolocation, Content, Searches, Referrals; Charts; xAPI; Pixels
* **Data Integration**: Datasets (CSV, TSV, JSON, GeoJSON, and RSS sources), Collections (Profiles, Geolocation, Multiple Categories, Relationships, Custom Fields, Indexed, Searchable), Data Sources
* **Collaboration**: Users (Register, Validation, Login, Invite), User Groups, Collection Membership and Permissions, Chat
* **E-commerce**: Products, SKUs, Categories, Customers, Orders, Account Management, Shipping Methods, Carriers, Tracking Numbers, Pricing Rules (Constraints, Discounts, and Promos)
* **CRM**: Forms, Leads & Customers, Orders
* **Settings**: Theme, Site SEO, Social Media, Mail Server, Maps, Captcha, Analytics, E-commerce, Mailing Lists
* **Integration**: Google Analytics, Map Box, Open Street Map, Square, Stripe, Taxjar, USPS, Boxzooka
* **Security**: OAuth, Firewall (Integration and Blocked IP lists), Spam Filter, Geo Filter, Rate Limiting, Snyk scanning
* **API**: Rest API
* **Platform**: Micro Widgets, Connection Pool, Cache, Scheduler, Workflow, Expression Engine, Upgrades, Migrations, Record Paging

## Release Process

In general:

1. Deploy
2. Login
3. Configure
4. Create
5. Maintain

An optimized web application archive (.war), with production settings, is released to this project's GitHub releases, ready for installation and which automatically upgrades previously installed versions. Always have a backup of your database and file library path.

The latest CMS Platform release is at <https://github.com/rajkowski/cms-platform/releases>.

Release notes include a list of changes for review.

Download the .war and follow your choice of deployment options.

To log into a new site, add "/login" to the URL. Later, turn on the login setting to reveal a login button for your website.

## Attribution

Thank you to all those who have helped make SimIS CMS!

This project uses and licenses several technologies:

```text
 Server:

   Apache Commons              Apache   Utilities                                         https://commons.apache.org
   Argon2                      LGPL     Password hashing                                  https://github.com/phxql/argon2-jvm
   Bucket4J                    Apache   Rate limiting                                     https://github.com/bucket4j/bucket4j
   Caffeine                    Apache   High performance cache                            https://github.com/ben-manes/caffeine
   ClassGraph                  MIT      Module scanner                                    https://github.com/classgraph/classgraph
   Easy Flows Playbooks EditionMIT      Workflow engine                                   https://github.com/rajkowski/easy-flows
   FlexMark                    BSD2     Markdown parsing                                  https://github.com/vsch/flexmark-java
   Flyway                      Apache   Database scripts install and upgrade tracking     https://github.com/flyway/flyway
   GeoIP2                      Apache   IP geolocation client                             https://github.com/maxmind/GeoIP2-java
   GeoJson                     Apache   GeoJson Parser                                    https://github.com/opendatalab-de/geojson-jackson
   Google GSON                 Apache   Convert Java Objects to/from JSON representation  https://github.com/google/gson
   Granule                     Apache   CSS and Javascript combine/minify                 https://github.com/rajkowski/Granule
   HikariCP                    Apache   High performance database connection pooling      https://github.com/brettwooldridge/HikariCP
   im4java                     LGPL     Interface to ImageMagick and GraphicsMagick       http://im4java.sourceforge.net
   Jackson                     Apache   Json Parser                                       https://github.com/FasterXML/jackson
   Jackson Core Utils          LGPL     Json Parser Utility                               https://github.com/fge/jackson-coreutils
   JavaMail                    CDDL     Mail library                                      https://javaee.github.io/javamail/
   JMail                       MIT      Email validator                                   https://github.com/RohanNagar/jmail
   Jobrunr                     LGPL     Job scheduler                                     https://github.com/jobrunr/jobrunr
   Johnzon                     Apache   Json processing                                   https://johnzon.apache.org
   JSON Schema Validator       Apache   JSON schema validator                             https://github.com/networknt/json-schema-validator
   JSoup                       MIT      HTML cleansing                                    https://github.com/jhy/jsoup/
   JSTL                        Eclipse  Servlet tag library                               https://github.com/eclipse-ee4j/jstl-api
   libphonenumber              Apache   Phone number validation                           https://github.com/google/libphonenumber
   PostgreSQL                  BSD      Database driver                                   https://jdbc.postgresql.org
   Pushy                       MIT      Push notifications                                https://github.com/relayrides/pushy
   RabbitMQ                    Apache   RabbitMQ java client library                      https://www.rabbitmq.com/java-client.html
   RestFB                      MIT      Facebook Graph API client                         https://github.com/restfb/restfb
   ROME                        Apache   RSS                                               https://github.com/rometools/rome
   SLF4j                       MIT      Logger                                            https://github.com/qos-ch/slf4j
   Square                      Apache   Java bindings for SquareUp.com                    https://github.com/square/square-java-sdk
   Stripe                      MIT      Java bindings for Stripe.com                      https://github.com/stripe/stripe-java
   Thymeleaf                   Apache   Template engine                                   https://github.com/thymeleaf/thymeleaf
   Timeago                     Apache   Relative time                                     https://github.com/marlonlom/timeago
   uniVocity                   Apache   CSV processing                                    https://github.com/uniVocity/univocity-parsers

 Frontend:

   Foundation                  MIT      Responsive CSS framework                          https://github.com/foundation/foundation-sites
   animate.css                 MIT      CSS animations                                    https://github.com/animate-css/animate.css
   autoComplete                MIT      Form auto-complete                                https://github.com/Pixabay/JavaScript-autoComplete
   ChartJS                     MIT      Charts                                            https://github.com/chartjs/Chart.js
   Clipboard.JS                MIT      Clipboard utility                                 https://github.com/zenorocha/clipboard.js
   DatePicker                  Apache   Date and time picker                              https://github.com/najlepsiwebdesigner/foundation-datepicker
   Dragula                     MIT      Drag and drop utilities                           https://github.com/bevacqua/dragula
   DropZoneJS                  MIT      Drag and drop utilities                           https://gitlab.com/meno/dropzone
   FontAwesome                 CC       Icons                                             https://github.com/FortAwesome/Font-Awesome
   FullCalendar                MIT      Calendar                                          https://github.com/fullcalendar/fullcalendar
   Google Fonts                Apache   Fonts                                             https://fonts.google.com
   GridManager                 MIT      Column editor                                     https://github.com/neokoenig/jQuery-gridmanager
   ImagesLoaded                MIT      UI event handler                                  https://github.com/desandro/imagesloaded
   Jspreadsheet                MIT      Data grid                                         https://github.com/jspreadsheet/ce
   JS Cookie                   MIT      Cookie functions                                  https://github.com/js-cookie/js-cookie
   Leaflet                     License  Maps                                              https://github.com/Leaflet/Leaflet
   MarkerCluster               Apache   Clusters for Leaflet                              https://github.com/Leaflet/Leaflet.markercluster
   Masonry                     MIT      UI Layout                                         https://github.com/desandro/masonry
   Mermaid                     MIT      Diagramming and charting                          https://github.com/mermaid-js/mermaid
   Add to Calendar Buttons     MIT      Add to calendar                                   https://github.com/WebuddhaInc/add-to-calendar-buttons
   Payform                     License  Credit card form                                  https://github.com/jondavidjohn/payform
   Prism                       MIT      Highlighter                                       https://github.com/PrismJS/prism/
   Spectrum                    MIT      Color picker                                      https://github.com/bgrins/spectrum
   Superset-UI Embedded SDK    Apache   Visualizations and dashboards                     https://github.com/apache/superset
   Swiper                      MIT      Modern mobile touch slider                        https://github.com/nolimits4web/swiper
   TinyMCE                     LGPL     HTML editor                                       https://github.com/tinymce/tinymce
   TinyMCE-FontAwesome-Plugin  MIT      Icon chooser                                      https://github.com/josh18/TinyMCE-FontAwesome-Plugin

 Testing:
 
   JUnit5                      Apache   Unit testing framework                            https://github.com/junit-team/junit5
   Jacoco                      Eclipse  Code coverage                                     https://github.com/jacoco/jacoco
   MeanBean                    Apache   JavaBean testing                                  https://github.com/meanbeanlib/meanbean
   Mockito                     MIT      Mocking framework                                 https://github.com/mockito/mockito
   Testcontainers              MIT      Containers library for tests                      https://github.com/testcontainers/testcontainers-java

 Data:

   Avalara                     CC       US sales tax rate tables                          https://www.avalara.com/taxrates/en/download-tax-tables.html
   GeoLite2                    CC       Data created by MaxMind                           https://dev.maxmind.com/geoip/geolite2-free-geolocation-data
   GeoNames                    CC       Geographic data                                   http://download.geonames.org/export/dump/
   Simple Maps                 CC       City, country, Lat, Long                          https://simplemaps.com/data/world-cities
   Zip Codes                   Attrib.  Zip codes                                         http://federalgovernmentzipcodes.us/

 Optional Services:

   Boxzooka                    Shipping/Fulfillment                                       https://boxzooka.com
   Google Analytics            Analytics                                                  https://marketingplatform.google.com/about/analytics/
   Map Box                     Geocoding, Map tiles                                       https://www.mapbox.com
   Open Street Map             Geocoding, Map tiles                                       https://www.openstreetmap.org
   Square                      Payment processing                                         https://squareup.com/us/en
   Stripe                      Payment processing                                         https://stripe.com
   Taxjar                      Taxes                                                      https://www.taxjar.com
   USPS Address Validation     Address verification                                       https://www.usps.com/business/web-tools-apis/
```
