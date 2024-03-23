# Changelog

All notable changes to this project will be documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the [Common Changelog](https://common-changelog.org/).

## Unreleased

- A static site generator allows an online and offline page renderer to output complete standalone SEO-compliant websites powered by Nginx

## 2024-03-07

- Refactored FileSystem classes for OS compatibility

## 2024-02-21

- In the layout editor, widget examples and their preferences are shown in an accordion
- Widgets can specify a Thymeleaf HTML template in addition to a JSP (for static site generator SSG feature)

## 2024-02-18

- Added OAuth OpenID Connect endpoint discovery
- Added OAUTH environment variables: `OAUTH_ENABLED`, `OAUTH_SERVER_URL`, `OAUTH_CLIENT_ID`, `OAUTH_CLIENT_SECRET`, `OAUTH_REDIRECT_URI`
- Added HTML widget templates
- Changed Menu tabs to gain highlighting when a submenu item matches the current page
- Refactored instances of business logic from presentation layer to application layer

## 2024-01-06

- Minimum of Java 17 is required

## 2023-12-13

- Added MKDocs style TechDocs
- Datasets can load TSV files

## 2023-06-10

- Updated item editor to allow linking an image to the item

## 2023-04-08

- Added `CMS_PATH` environment variable to specify the file library location

## 2022-08-11

- Added option so that dataset downloads can be scheduled
- Datasets can be sync'd to items

## 2022-07-23

- Added collection custom fields editor
- Added collection custom fields
- Added editor for items with custom fields

## 2022-07-18

- Added Moodle calendar events

## 2022-07-07

- Added MermaidJS to the wiki module

## 2022-07-04

- Added Apache Superset dashboard widget

## 2022-06-19

- Changed Square dependency and removed deprecated/unsupported Square Connect

## 2022-06-16

- Added integration with Moodle
- Added integration with PERLS
- Added integration with BigBlueButton
- Added RemoteCourseList widget to display courses for student, teacher, or any enrolled status

## 2022-06-09

- Added dataset transforms with realtime previews
- Added progressCard and statisticCard widgets
- Updated to Full Calendar 5
- Added calendar view preferences

## 2022-05-10

- Added card view preference for item lists

## 2022-05-04

- User groups added to menu navigation
- User groups added to search results
- `CMS_URL` environment variable added for container deployments

## 2022-04-27

- Added OAuth integration, includes Keycloak example

## 2022-04-17

- Added option so web components can require a user group for access by using the group="example-group" attribute

## 2022-04-06

_Public Project_

## 2022-03-25

- Added configuration directory for reloadable system files
- Added Font Awesome 6

## 2021-11-30

- Collections now have icons, themes, and directory listing and search preferences
- Added Collection and Item editing links

## 2021-04-26

- The user is emailed after registering with the site

## 2021-04-19

- New /show/itemUniqueId/* pages, collection-based tabs

## 2021-04-07

- Added fields for marketing and sales funnels
- Added BlogPostPublishedEvent, WebPagePublishedEvent, WebPageUpdatedEvent
- Integrated asynchronous workflow engine and playbooks
- Added xAPI Statements

## 2021-02-03

- Added product catalog Strike Price which is shown with a strike through the price
- Added ETL setting to skip duplicate data when previewing a dataset

## 2021-01-14

- Checks for spam in forms using `spam-list.csv`

## 2020-05-28

- Added product catalog BOGO-like pricing rules

## 2020-05-04

- Order Management supports multiple fulfillment options and multiple tracking numbers
- Order shipment tracking numbers can be manually entered

## 2020-04-10

- Fulfillment Shipping rates can exclude specific SKUs
- Updated cart pricing rules based on eligible amounts, limits, valid SKUs, min subtotal, min order qty
- Product catalog supports multiple fulfillment options
- In the content editor, the add link command shows a list of web pages to choose from
- In the content editor, the user can choose to turn a link into a button

## 2020-03-30

- Added "itemsMapApp", an items list widget with multiple markers shown on a map with interactive filters

## 2020-03-26

- Added Security module to the admin menu

## 2020-03-25

- Added blocked IP list manager
- Added auto-refreshing stat tables

## 2020-03-08

- Creates Square orders with line-items, discounts, taxes, and shipping
- Square Location Id property can be set for orders
- Improved dataset module with tabbed ui, grid preview, text preview, remote download, conversion formats
- Added Bot detection with customizable `bot-list.csv` and integrated with site statistics

## 2020-02-19

- Added blog cards to new page templates
- Added nested accordions by using H1 tags in the content editor
- Added Upload/Remote Download of JSON datasets with admin customizable data mapping

## 2020-02-11

- E-Commerce analytics module shows map, orders, and shipping info
- When user validates their email, associated orders are now linked
- Tracking numbers have links to the respective delivery carrier website
- Added admin feature to download a CSV list of orders
- Community analytics module updated to show number of users online now, and for the current day
- Content analytics module updated to show top paths and top web hits

## 2020-01-13

- Editing the product catalog creates and updates the product catalog in Square

## 2020-01-03

- Fixed Instagram issue so cached Instagram media will update on the website when the admin access token has changed

## 2019-12-23

- Added Admin UI to create and update product catalog pricing rules

## 2019-12-15

- Orders are checked on a schedule for customer processing updates and for tracking information updates

## 2019-11-21

- Added a product catalog promo codes database, rules engine, and promo code entry form

## 2019-11-20

- Added order management capabilities to cancel orders, refund payments, get tracking numbers, search orders and customers with partial information
- Added site keyword property for title SEO
- Added globalMessage widget for shared page UI messages for success, errors, etc.

## 2019-11-15

- Users can create an account during e-commerce checkout to see orders

## 2019-11-13

- Sends order confirmation email to customer with order details

## 2019-11-11

- Boxzooka product fulfillment and order integration

## 2019-11-07

- Square order payment integration charges credit card

## 2019-10-29

- Added searching customers and orders

## 2019-10-22

- Added product catalog restricted shipping locations

## 2019-10-17

- Formatting for product catalog item attributes
- Feature added to allow admin seeing checkout process without a processor
- Fixed compile errors on e-commerce pages

## 2019-10-02

- Changed CMS edit page layout to use CodeMirror XML editor
- When HTML page renders, styles and javascripts are combined for browsers

## 2019-09-12

- Added Instagram photos widget and background scheduler to retrieve new images
- Email subscribes are added to Mail Chimp Lists/Audiences
- Added Mailing List settings for MailChimp API integration
- Added Social Media settings for Instagram API integration
- Added page template for Photo Album and Gallery

## 2019-09-09

- 'Add to cart' allows a user to choose from any configured product options

## 2019-09-08

- Page XML layouts have new attributes for section style, column style, and widget style

## 2019-09-03

- Added Blog page keywords and description
- Blog content is indexed as text for the search engine
- Added Folder categories, which allows files to be assigned to a category
- Added File URLs, so files can be added by specifying a URL, without uploading a file

## 2019-08-30

- Added Sub-Folders under Folders admin module
- Added Album Gallery (albumGallery) widget and Photo Gallery (photoGallery) widget
- Added /admin/sticky-footer-links
- Added search feature to search web pages, sitemap titles, table of contents, and upcoming calendar events
- Search terms are highlighted in the results
- Added new widgets: searchInfo, webPageSearchResults, webPageTitleSearchResults
- Added calendar event landing pages
- Added Blog post list preference showSort=true to show sort drop-down
- Added blogPostName widget to use and style the blog post name
- BlogPost preference showTitle=false to hide title
- Added content widget preference view=carousel and display=text|images
- When choosing a web page template, the web page title can be entered

## 2019-08-20

- Added editing footer useful links for some footer layouts

## 2019-08-18

- Added a site preference to show a Site Confirmation prompt before accessing the site
- Added content widget preference to embed content to reveal on a link click "addReveal"
- Added blog list preferences for view=cards, view=masonry
- Added upcoming event list preferences for view=cards, includeLastEvent=true/false
- Added data widgets: addItemForm, editItemForm, approveItemButton, hideItemButton, deleteItemButton
- Added itemsList widget preference view=table
- Added feature so mailing lists can be downloaded as CSV files

## 2019-08-10

- Added productName widget to show product name and price from database
- Streamlined the add to cart based on SKUs and product attributes
- Shipping rates can display text to the user during checkout
- Email is sent to data managers when a form is submitted, configurable per form

## 2019-08-07

- Added Data Manager administrative role

## 2019-08-02

- Added HTML content editor 'anchors' for section targeting on a page
- Added UpcomingCalendarWidget preference 'showMonthName'
- Added BlogPostListWidget preferences 'showDate', 'addDateToTitle'
- Added Content 'accordion' view and preferences for 'smallCardCount', 'mediumCardCount', 'largeCardCount'
- Added AddItemButtonWidget preferences 'requiresPermission', 'buttonName', 'addUrl'
- Added CategoriesListWidget preference 'directoryUrl'
- Added CreateAnItemWidget preferences 'requiresPermission', 'form'='business'
- Added ItemsListWidget preference 'view'='table'
- Added PropertyMapAppWidget custom CSS
- Added PropertyMapAppWidget preferences 'titleHtml', 'city' for POIs
- Added Google Analytics and configuration setting
- Added Footer settings for Address, Phone Number, and Hours to be used in Footer design
- Added CSS animate
- Directory item widget can allow any item to be added, for approval

## 2019-07-24

- Property Map allows for CSS customization
- Added CSS animations
- Added footer values for address, phone, hours
- Added content accordion

## 2019-07-15

- Added order confirmation page
- Added order history details to the user page
- Admin can look at customers and orders

## 2019-07-12

- Added saving and restoring themes
- Footer and Header configuration improvements
- Admin can upload site logos

## 2019-07-09

- Stripe integration creates customer, order, and charges card
- Added system-level order number formatting

## 2019-06-30

- The cart calculates the US sales tax amount based on nexus addresses

## 2019-06-28

- Admin can add/update shipping rates
- The cart calculates the shipping and handling amount
- If USPS makes an address suggestion, the user can choose which address to use

## 2019-06-25

- Added title, subtitle to form widget

## 2019-06-24

- USPS formats the shipping address when configured

## 2019-06-19

- Added 'reveal' view to content widget

## 2019-06-18

- Added a Utility Bar header choice
- Added default footers including e-mail subscription form
- Added image 'gallery' view to content widget
- Users can subscribe and unsubscribe to mailing lists
- Users can view their order history
- Added Sales Tax nexus addresses in Admin
- Added E-Commerce Manager administrative role

## 2019-04-25

- Added saving the cart contact and shipping information
- Added US Sales Tax Rates 2019-04

## 2019-04-18

- Added 'card' view to content widget

## 2019-04-15

- Added cart module features: price change, item no longer available, remove item

## 2019-04-08

- Added visitor tracking
- Added Cart tracking, database, and module
- User roles for adding and updating users

## 2019-03-26

- Content can be saved as a draft, then published or reverted

## 2019-03-24

- Added MailingList, MailingListForm, and EmailSubscribe widgets
- Added Mailing List settings in Admin

## 2019-03-22

- Added ProductList and ProductForm widgets
- Added E-Commerce settings in Admin

## 2019-03-15

- Added interactive property map PropertyMapApp widget

## 2019-02-26

- Added Documentation section in Admin

## 2019-02-21

- Fixed UTF-8 character encoding issues

## 2019-02-18

- Form fields set to required="true" will generate a form submit warning

## 2019-02-12

- Added UpcomingCalendarEvents widget

## 2019-02-11

- Blog posts now have an HTML page title for SEO
- Added calendarUniqueId preference to Calendar widget

## 2019-02-10

- Added Wiki module

## 2019-02-08

- Added Logo Color site property
- Added preview data grid
- Added GeoJSON datasets loading

## 2019-01-30

- Admin UI changed to use an off-canvas menu

## 2019-01-24

- Added FileDropZone widget
- Added FileList widget preferences: showLinks, folderUniqueId, rules, orderBy, withinLastDays, showWhenEmpty
- Implemented Dropbox (write-only) folders
- Fixed new versions of uploaded documents did not update the modified date field

## 2019-01-16

- Added emailTo preference for Form widget, otherwise defaults to community manager

## 2019-01-11

- A `user.csv` file can be uploaded to batch add users with First Name, Last Name, Email, Date, Groups
- Added email and telephone footer options

## 2019-01-10

- Logins go to "my-page" and "my-page" is now customizable

## 2019-01-02

- Added Files widget

## 2018-12-21

- Added Map preferences showMarker, mapZoomLevel

## 2018-12-18

- Files can be moved to other folders, file versions can be added, summary can be edited

## 2018-12-17

- Videos and PDF Files can be selected in the HTML editor
- Videos form now includes poster image and alternative video type

## 2018-12-14

- File uploads and downloads have been added
- Videos can be played back using HTML5 tags

## 2018-12-13

- A Folder and File management system has been added to Admin with user groups

## 2018-12-12

- In HTML Editor, `<hr>` tag is now allowed
- In HTML Editor, Added image-left/image-right layout content class tags

## 2018-12-10

- New page template for Content With Table of Contents
- New Table of Contents widget with editor

## 2018-12-07

- New page templates for Calendar and Content With Sub-Tabs
- Boolean settings now have a checkbox UI slider

## 2018-12-06

- Added a default timezone setting in Admin
- Blog and Calendar use system timezone
- Updated GeoIP data

## 2018-11-30

- Analytics show values when there are 0 records for the date

## 2018-11-28

- Content managers can duplicate calendar events

## 2018-11-27

- Content managers can update and delete calendar events
- Admins can delete whole blogs and calendars
- Change money types to numeric(15,6) for exact precision

## 2018-11-19

- Added `check-site.sh` script for monitoring the site's availability
- Added check for header `X-Monitor` to skip statistics, like with health checks

## 2018-11-13

- Calendar uses dynamic queries to show events
- Added calendar event tooltips

## 2018-11-08

- Users can add Calendar Events
- Form data shows url data
- Added form-data record paging

## 2018-11-05

- Medicine tracker `/med/medicineLogList`

## 2018-10-29

- API session tracking added for guests

## 2018-10-26

- Removed autofocus from forms due to Safari page scrolling

## 2018-10-12

- CMS: Added 'pro' theme

## 2018-10-11

- Updated blog editor to upload banner images
- Blog list can show "titles" only with links for recent posts

## 2018-10-03

- Added API medicine tracker features

## 2018-10-01

- Access to item relationships now uses the user's group and member settings
- Captcha preference added to user registration form

## 2018-09-26

- Access to collections (add/edit/delete) now uses the user's group settings
- Improved analytics reporting for top web paths

## 2018-09-24

- Added default Timezone, User GeoIP Timezone fallback

## 2018-09-06

- Added resource filter for LetsEncrypt renewals

## 2018-09-05

- Added 'center', 'left', 'right', 'none' top bar menu options

## 2018-08-27

- Users can be added as Members to Items
- List of items now uses user member permissions

## 2018-08-23

- Added Google ReCaptcha site setting and useCaptcha form preference

## 2018-08-21

- Added Activity Stream
- Added Profile Messages UI for Activity Stream

## 2018-08-20

- Added "Blog Posts" and "Blog Post Article" templates
- Added chooser for blog post banner image

## 2018-08-16

- Dataset split() function added
- Added Search Form name auto-complete
- Added Item custom fields
- Collection access implemented

## 2018-08-09

- Added Icon Chooser to Content Editor
- Support for custom fields added to Items

## 2018-08-08

- Added Blogs for Blogging, News, Press, Videos, Webinars
- Added dynamic web page URLs

## 2018-08-01

- Added "Bio Page" template

## 2018-07-30

- Content Tab widget can use links to other web pages

## 2018-07-27

- Video background can be placed behind any section
- Item relationships to other items can be made

## 2018-07-26

- Collection relationships can be made to other collections

## 2018-07-25

- Expanded the collection user group access to include add, edit, delete permissions

## 2018-07-20

- Added features for user management to Update, Suspend, Restore, Delete, Reset Password, Assign Roles and Groups

## 2018-07-19

- Admins can assign user groups to collections

## 2018-07-16

- Email for user registration event and form data events

## 2018-07-01

_First release._

- Platform: Java-based Widgets (based on micro-frontends), Connection Pool, Cache, Scheduler, Upgrade Scripts, Record Paging
- Settings: Theme, Site SEO Settings, Social Media, Mail Server, Maps
- CMS: Site Map, Web Pages (Templates, UI Designer, SEO), Content, Images, HTML Editor, Form Data
- Analytics: Tracking for Sessions, Hits, Geolocation, Content; Charts
- Data: Datasets (CSV and RSS sources), Collections (Geolocation, Multiple Categories, Indexed, Searchable)
- Collaboration: Users (Register, Validation, Login), User Groups
- Web Browser and Mobile API accessible
