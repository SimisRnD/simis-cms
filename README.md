# SimIS CMS

What is SimIS CMS? [Agile, Enterprise, Open Source, Content Management System (CMS)](https://www.simiscms.com).

SimIS CMS comes out-of-the-box with modules, advanced security, easy setup, and powerful developer features. Use and configure what's there, and customize what's not. The flexible Open Source license lets you move beyond the technology to focus on delivering a quality website.

## License

```
Copyright 2022 SimIS Inc. (https://www.simiscms.com)

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

Need a website? SimIS CMS can be used from Day 1. Once installed the Admin signs in and can quickly create a sitemap. Working on their own, or with others, the pages of the site are added. Each web page can have shared elements and styles, as well as their own elements and styles. A designer can work on the site's global style, then target page-by-page improvements, while content authors fill out the web page content. The page elements include searchable text, images, and videos. There are many dynamic elements which can be added, including slideshows, news feeds, calendar events, blog posts, and more. For more complex components, a developer can work both online and off to enable the functionality or create it.

## Features

- CMS: Site Map, Web Pages (Templates, UI Designer, SEO, Searchable) with Content and Images, HTML Editor, CSS Editor, Blogs, Form Data, Calendars, Folders and Files, Mailing Lists, Videos, Wikis, Search, Site Alerts, Form Pop-Ups, Sticky Header and Buttons, Responsive, Bot Detection
- Analytics: Tracking for Sessions, Hits, Geolocation, Content, Searches, Referrals; Charts; xAPI; Pixels
- Data Integration: Datasets (CSV, JSON, GeoJSON, and RSS sources), Collections (Profiles, Geolocation, Multiple Categories, Relationships, Custom Fields, Indexed, Searchable), Data Sources
- Collaboration: Users (Register, Validation, Login, Invite), User Groups, Collection Membership and Permissions, Chat
- E-commerce: Products, SKUs, Categories, Customers, Orders, Account Management, Shipping Methods, Carriers, Tracking Numbers, Pricing Rules (Constraints, Discounts, and Promos)
- CRM: Forms, Leads & Customers, Orders
- Settings: Theme, Site SEO, Social Media, Mail Server, Maps, Captcha, Analytics, E-commerce, Mailing Lists
- Integration: Google Analytics, Map Box, Open Street Map, Square, Stripe, Taxjar, USPS, Boxzooka
- Security: Firewall (Integration and Blocked IP lists), Spam Filter, Geo Filter, Rate Limiting, Snyk scanning
- API: Rest API
- Platform: Micro Widgets, Connection Pool, Cache, Scheduler, Workflow, Expression Engine, Upgrades, Migrations, Record Paging

## Requirements

 - [Java SDK 17+](https://www.oracle.com/java/technologies/downloads/)
 - [Apache Tomcat 9.0.x](https://tomcat.apache.org)
 - [PostgreSQL 14](https://www.postgresql.org) with [PostGIS 3.2](https://postgis.net)

## Build Requirements

 - [Apache Ant 1.10+](https://ant.apache.org)
 - [Apache Maven 3.6+](https://maven.apache.org)

## Build Process

Make sure Java is installed:

```
java -version
```

Download Apache Tomcat, and set CATALINA_HOME to Tomcat's directory:

```
export CATALINA_HOME=$HOME/Tools/apache-tomcat-9.0.62
```

Download Apache Ant, and set ANT_HOME and path to Ant's directory:

```
export ANT_HOME=$HOME/Tools/apache-ant-1.10.11
export PATH=$ANT_HOME/bin:$MAVEN_HOME/bin:$PATH
```

To build and package the .war, run "ant"

```bash
ant
```

The optimized web application archive is found in target/simis-cms.war

## Deploying with Apache Tomcat

* Create a "simis-cms" database
* Create a "simis-cms" file directory for uploaded and generated assets (see below)
* Deploy the web-app .war in Tomcat's webapps folder (call it ROOT.war for a root web context) and start the service
* The database will be installed and the Tomcat log will contain two random checksums for the Admin's user/pass
* Login, navigate to Admin, and follow the Getting Started to-do list

The path for file assets and external configuration on linux is /opt/simis; otherwise $USER_HOME/Web/simis-cms

```bash
mkdir -p /opt/simis
```
or
```bash
mkdir -p ~/Web/simis-cms
```

With Tomcat installed, and CATALINA_HOME configured, copy the .war into place:

```bash
cp target/simis-cms.war $CATALINA_HOME/webapps/ROOT.war
```

## Deploying with Docker

```bash
$ ant
$ cp env-simis-cms .env
$ vi .env
$ docker-compose up --build
```

Review the installation logs for the administrator's username and password.

Run docker-compose with `-d` to skip the logs and run detached.

Example .env config:

```
CMS_URL_SCHEME=https
CMS_HOSTNAME=www.example.com
```

## Security Scan

```bash
npm install -g snyk
snyk auth
snyk test --file=mvn-pom.xml --package-manager=maven
```

## Developer Resources

* [SimIS CMS](https://www.simiscms.com)
* [Java 17 SDK Documentation](https://docs.oracle.com/en/java/javase/17/)
* [MVC Example with Servlets and JSP](https://www.baeldung.com/mvc-servlet-jsp)
* [Servlet 4.0 API](https://tomcat.apache.org/tomcat-9.0-doc/servletapi/index.html)
* [JSP 2.3 API](https://tomcat.apache.org/tomcat-9.0-doc/jspapi/index.html)
* [JSTL 1.2.5 API](https://github.com/javaee/jstl-api)
* [PostgreSQL Documentation](https://www.postgresql.org/docs/)
* [Domain Driven Design Intro](https://airbrake.io/blog/software-design/domain-driven-design)
* [Foundation for Sites Documentation](https://foundation.zurb.com/sites/docs/)
* [Font Awesome Icons](https://fontawesome.com/icons?d=gallery)
* [Apache Commons JEXL](https://commons.apache.org/proper/commons-jexl/reference/syntax.html)

## Projects used

This project uses and licenses several technologies:

```
 Server:

   Apache Commons              Apache   Utilities                                         https://commons.apache.org
   Argon2                      LGPL     Password hashing                                  https://github.com/phxql/argon2-jvm
   Caffeine                    Apache   High performance cache                            https://github.com/ben-manes/caffeine
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
   Jobrunr                     LGPL     Job scheduler                                     https://github.com/jobrunr/jobrunr
   Johnzon                     Apache   Json Processing                                   https://johnzon.apache.org
   JSoup                       MIT      HTML cleansing                                    https://github.com/jhy/jsoup/
   JUnit5                      Apache   Unit testing framework                            https://junit.org/junit5/
   libphonenumber              Apache   Phone number validation                           https://github.com/google/libphonenumber
   PostgreSQL                  BSD      Database driver                                   https://jdbc.postgresql.org
   Pushy                       MIT      Push notifications                                https://github.com/relayrides/pushy
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
   Add to Calendar Buttons     MIT      Add to calendar                                   https://github.com/WebuddhaInc/add-to-calendar-buttons
   Payform                     License  Credit card form                                  https://github.com/jondavidjohn/payform
   Prism                       MIT      Highlighter                                       https://github.com/PrismJS/prism/
   Spectrum                    MIT      Color picker                                      https://github.com/bgrins/spectrum
   Swiper                      MIT      Modern mobile touch slider                        https://github.com/nolimits4web/swiper
   TinyMCE                     LGPL     HTML editor                                       https://github.com/tinymce/tinymce
   TinyMCE-FontAwesome-Plugin  MIT      Icon chooser                                      https://github.com/josh18/TinyMCE-FontAwesome-Plugin

 Data:

   Avalara                     CC       US sales tax rate tables                          https://www.avalara.com/taxrates/en/download-tax-tables.html
   GeoLite2                    CC       Data created by MaxMind                           https://dev.maxmind.com/geoip/geolite2-free-geolocation-data
   GeoNames                    CC       Geographic data                                   http://download.geonames.org/export/dump/
   Simple Maps                 CC       City, country, Lat, Long                          https://simplemaps.com/data/world-cities
   Zip Codes                   Attrib.  Zip codes                                         http://federalgovernmentzipcodes.us/

 Services:

   Boxzooka                    Shipping/Fulfillment                                       https://boxzooka.com
   Google Analytics            Analytics                                                  https://marketingplatform.google.com/about/analytics/
   Map Box                     Geocoding, Map tiles                                       https://www.mapbox.com
   Open Street Map             Geocoding, Map tiles                                       https://www.openstreetmap.org
   Square                      Payment processing                                         https://squareup.com/us/en
   Stripe                      Payment processing                                         https://stripe.com
   Taxjar                      Taxes                                                      https://www.taxjar.com
   USPS Address Validation     Address verification                                       https://www.usps.com/business/web-tools-apis/
```
