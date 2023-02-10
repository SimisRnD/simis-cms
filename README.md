# SimIS CMS

[![Java CI](https://github.com/SimisRnD/simis-cms/actions/workflows/ant.yml/badge.svg)](https://github.com/SimisRnD/simis-cms/actions/workflows/ant.yml)

Discussion and issues are hosted at github: <https://github.com/SimisRnD/simis-cms>.

What is SimIS CMS? [Agile, Enterprise, Open Source Content Management System (CMS) and Portal](https://www.simiscms.com).

SimIS CMS comes out-of-the-box with modules, advanced security, easy setup, and powerful developer features. Use and configure what's there, and customize what's not. The flexible Open Source license lets you move beyond the technology to focus on delivering a quality website.

## License

```
Copyright 2023 SimIS Inc. (https://www.simiscms.com)

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

Need a website or web portal? SimIS CMS can be used from Day 1:

* Once installed the administrator signs in and can quickly create a sitemap. Working on their own, or with others, the pages of the site are added.
* Each web page can have shared elements and styles, as well as their own elements and styles.
* A designer can work on the site's global style and layout, then target page-by-page improvements, while content authors fill out the web page content.
* Content authors work with the page elements which include searchable text, images, and videos. There are many dynamic elements which can be selected, including slideshows, news feeds, calendar events, blog posts, and more.
* For more complex components, a developer can work both online and off to enable the functionality or create it.

## Features

- CMS: Site Map, Web Pages (Templates, UI Designer, SEO, Searchable) with Content and Images, HTML Editor, CSS Editor, Blogs, Form Data, Calendars, Folders and Files, Mailing Lists, Videos, Wikis, Search, Site Alerts, Form Pop-Ups, Sticky Header and Buttons, Responsive, Bot Detection
- Analytics: Tracking for Sessions, Hits, Geolocation, Content, Searches, Referrals; Charts; xAPI; Pixels
- Data Integration: Datasets (CSV, JSON, GeoJSON, and RSS sources), Collections (Profiles, Geolocation, Multiple Categories, Relationships, Custom Fields, Indexed, Searchable), Data Sources
- Collaboration: Users (Register, Validation, Login, Invite), User Groups, Collection Membership and Permissions, Chat
- E-commerce: Products, SKUs, Categories, Customers, Orders, Account Management, Shipping Methods, Carriers, Tracking Numbers, Pricing Rules (Constraints, Discounts, and Promos)
- CRM: Forms, Leads & Customers, Orders
- Settings: Theme, Site SEO, Social Media, Mail Server, Maps, Captcha, Analytics, E-commerce, Mailing Lists
- Integration: Google Analytics, Map Box, Open Street Map, Square, Stripe, Taxjar, USPS, Boxzooka
- Security: OAuth, Firewall (Integration and Blocked IP lists), Spam Filter, Geo Filter, Rate Limiting, Snyk scanning
- API: Rest API
- Platform: Micro Widgets, Connection Pool, Cache, Scheduler, Workflow, Expression Engine, Upgrades, Migrations, Record Paging

## Release Process

In general:

1. Deploy
2. Login
3. Configure
4. Create
5. Maintain

An optimized web application archive (.war), with production settings, is released to this project's GitHub releases, ready for installation and which automatically upgrades previously installed versions. Always have a backup of your database and file library path.

The latest release is at <https://github.com/SimisRnD/simis-cms/releases>.

Release notes include a list of changes for review.

Download the .war and follow your choice of deployment options.

To log into a new site, add "/login" to the URL. Later, turn on the login setting to reveal a login button for your website.

## Deployment Options

### Deploying with Apache Tomcat

Production System Requirements:

- [Java SDK 17+](https://www.oracle.com/java/technologies/downloads/)
- [Apache Tomcat 9.0.x](https://tomcat.apache.org)
- [PostgreSQL 14](https://www.postgresql.org) with [PostGIS 3.2](https://postgis.net)
- The web application and optional services have only been tested on Linux and MacOS

Steps:

* Install Apache Tomcat
* Install PostgreSQL and PostGIS
* Create a "simis-cms" PostgreSQL database
* Create a "simis-cms" file directory for uploaded and generated assets (see below)
* Deploy the web-app .war in Tomcat's webapps folder (call it ROOT.war for a root web context) and start the Tomcat service
* The database will be installed and the Tomcat log will contain two random checksums for the Admin's user/pass (you must review the logs for the login information)
* Login, navigate to Admin, and follow the Getting Started to-do list

In detail:

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

Upgrading is as simple as replacing the ROOT.war with a newer version.

### Deploying with Docker

The build process places the .war in the target/ directory, or the release file can be downloaded and placed in target/ manually. You will need to create a .env file with additional settings.

.env contents:

```
CMS_ADMIN_USERNAME=
CMS_ADMIN_PASSWORD=
CMS_FORCE_SSL=true|false
DB_SERVER_NAME=
DB_NAME=
DB_USER=
DB_PASSWORD=
```

```bash
export DOCKER_BUILDKIT=1
docker-compose up --build -d
```

For new installs, if an installation property was not provided, review the runtime logs for the administrator's username and password.

```bash
docker logs --follow simis-cms-app-1
```

Testing scaling with multiple web servers:

```bash
docker-compose up -d --scale app=2
```

## Thoughts

1. [ISSUE] CSRF token is generated by each replica, resolve with db
2. [ISSUE] Scheduled job singleton using distributed lock
3. [ISSUE] Cache distributed evictions via RabbitMQ
4. [FEATURE] Plug-Ins when application starts (Plugin scanner; widget, database migrations, REST services, UI, etc.)
5. [FEATURE] Kafka broker for events and workflows, can extend from current asynchronous events
6. [FEATURE] Distributed components as needed
7. [FEATURE] WebHooks service for events (resilience similar to Dataset downloader); app, endpoint, headers, message
8. [FEATURE] Diacritic conversion
9. [FEATURE] Granular permissions; Open Policy Agent + Rego
10. [FEATURE] Bookmarks
11. [FEATURE] JWT
12. [TESTING] Archunit
13. [FEATURE] CDN setting

## Building from Source

### Developer Build Requirements

- [Apache Ant 1.10+](https://ant.apache.org)
- [Apache Maven 3.6+](https://maven.apache.org)

### Developer Build Process

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

To compile, package, and build the .war, run "ant"

```bash
ant
```

The optimized web application archive is found in target/simis-cms.war

For development, it is recommended to run and debug directly as a process in your IDE.

## CI/CD Pipeline

### Pipeline Build Dependencies Scan

```bash
npm install -g snyk
snyk auth
snyk test --all-projects
snyk monitor --all-projects
```

### Pipeline Build Stage

```
ant compile
```

### Pipeline Unit Tests

```bash
ant test
```

### Pipeline Generate Web Application

```bash
ant package
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
* [Snyk](https://snyk.io)

## OAuth Provider Login (Keycloak example)

In Keycloak:

1. Create a realm or use an existing one
2. Add a client: simis-cms
3. Add Client Roles to Keycloak: system-administrator, content-manager, community-manager, data-manager, ecommerce-manager
4. Add Realm Groups to Keycloak: employees, supervisors
5. Create Client Mappers and Tokens: User Client Role (roles), Group Membership (groups)
6. Create users and choose roles and groups for the user

In SimIS CMS:

```sql
UPDATE site_properties SET property_value = 'true' WHERE property_name = 'oauth.enabled';
UPDATE site_properties SET property_value = 'Keycloak' WHERE property_name = 'oauth.provider';
UPDATE site_properties SET property_value = 'simis-cms' WHERE property_name = 'oauth.clientId';
UPDATE site_properties SET property_value = 'client-secret' WHERE property_name = 'oauth.clientSecret';
UPDATE site_properties SET property_value = 'https://localhost/realms/example' WHERE property_name = 'oauth.serviceUrl';
UPDATE site_properties SET property_value = true WHERE property_name = 'oauth.redirectGuests';
UPDATE site_properties SET property_value = 'roles' WHERE property_name = 'oauth.role.attribute';
UPDATE site_properties SET property_value = 'groups' WHERE property_name = 'oauth.group.attribute';

UPDATE lookup_role SET oauth_path = 'system-administrator' where code = 'admin';
UPDATE lookup_role SET oauth_path = 'content-manager' where code = 'content-manager';
UPDATE lookup_role SET oauth_path = 'community-manager' where code = 'community-manager';
UPDATE lookup_role SET oauth_path = 'data-manager' where code = 'data-manager';
UPDATE lookup_role SET oauth_path = 'ecommerce-manager' where code = 'ecommerce-manager';

UPDATE groups SET oauth_path = '/learners' WHERE unique_id = 'learners';
UPDATE groups SET oauth_path = '/instructors' WHERE unique_id = 'instructors';
```

Reset the cache

## API

SimIS CMS includes an extendable api for user-based access and server-2-server capabilities.

### User-Based JSON API

1. Create an app client in Admin, note the app id and secret key.
2. Make sure the API is enabled in Site Settings
3. Optionally log the user in and obtain a 'token' for future calls:
   * POST
   * DIGEST AUTHENTICATION
   * REQUEST HEADER (X-API-Key)
4. If a user login is not required, then default access will be demoted to a Guest User
5. Make API calls:
   * GET/POST/PUT/DELETE
   * BEARER TOKEN (optional)
   * REQUEST HEADER (X-API-Key) or URL PARAMETER (key=) for api key

In your application, have the user supply their CMS username and password, then request authorization:

```bash
http -a username:password POST http://localhost:8080/api/oauth2/authorize X-API-Key:<secret_key>
```

When authorization is obtained, the response will include an access token to use.

```json
{
  "access_token": "you-receive-this",
  "expires_in": 2592000,
  "first_name": "First", "last_name": "Last", "name": "First Last",
  "scope": "create", "token_type": "bearer"
}
```

Now calls can be made with the access token:

```bash
http -A bearer -a <access_token> GET http://localhost:8080/api/me X-API-Key:<secret_key>
```

```bash
http -A bearer -a <access_token> GET http://localhost:8080/api/me?key=<secret_key>
```
Calls without a user:

```bash
http GET http://localhost:8080/api/me?key=<secret_key>
```

## Customization

* Content
* CSS
* Documents
* Images
* Layouts (header, footer)
* Setup
  * Blogs
  * Calendars
  * Collections
  * Datasets
  * Settings
  * Site Maps
  * User Groups
* Templates
* Videos
* Web Pages

## Development

* Application Widgets
* REST Services
* Domain Model
* Database
* Cache
* Permissions
* Scheduled Tasks
* Workflows

## Attribution

Thank you to all those who have helped make SimIS CMS!

This project uses and licenses several technologies:

```
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
