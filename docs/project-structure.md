---
id: project-structure
title: Project Structure
# prettier-ignore
description: SimIS CMS frontend and backend details
---

SimIS CMS is driven by a custom web framework which facilitates dynamic pages, layouts, components called widgets, preferences, user roles and groups.

The key concepts are:

- Application Modules
- Domain Model and Events
- Infrastructure Components
  - Database
  - Cache
  - Permissions
  - Scheduled Tasks
  - Workflows
- Web Presentation Controller and Widgets
- Rest Controller and Services

## Source Code Repo

```
├── build.xml (the Ant scripts for build, test, package)
├── package.json (the Javascript dependencies)
├── pom.xml (the Java dependencies)
├── config (templates for externally managing lists)
│   ├── cms
│   └── e-commerce
├── docker (example container images)
│   ├── app
│   └── db
├── lib
│   ├── build (snapshots of run-time open source libraries)
│   │   └── <libraries>
│   ├── compile (snapshots of compile-time open source libraries)
│   │   ├── jee
│   │   └── lombok
│   ├── style (snapshots of open source libraries for linting)
│   ├── tests (snapshots of open source libraries for unit testing)
│   └── war (snapshots of open source libraries for building the war)
└── src
    ├── main
    │   ├── java
    │   │   └── com
    │   │       └── simisinc
    │   │           └── platform
    │   │               ├── application
    │   │               │   ├── admin
    │   │               │   ├── calendar
    │   │               │   ├── cms
    │   │               │   ├── dashboards
    │   │               │   ├── datasets
    │   │               │   ├── ecommerce
    │   │               │   ├── elearning
    │   │               │   ├── email
    │   │               │   ├── filesystem
    │   │               │   ├── gis
    │   │               │   ├── http
    │   │               │   ├── items
    │   │               │   ├── json
    │   │               │   ├── login
    │   │               │   ├── mailinglists
    │   │               │   ├── maps
    │   │               │   ├── medicine
    │   │               │   ├── oauth
    │   │               │   ├── register
    │   │               │   ├── socialmedia
    │   │               │   ├── userProfile
    │   │               │   ├── workflow
    │   │               │   └── xapi
    │   │               ├── domain
    │   │               │   ├── events
    │   │               │   └── model
    │   │               ├── infrastructure
    │   │               │   ├── cache
    │   │               │   ├── database
    │   │               │   ├── distributedlock
    │   │               │   ├── instance
    │   │               │   ├── persistence
    │   │               │   ├── scheduler
    │   │               │   └── workflow
    │   │               ├── presentation
    │   │               │   ├── controller
    │   │               │   └── widgets
    │   │               └── rest
    │   │                   ├── controller
    │   │                   └── services
    │   ├── resources
    │   │   └── database
    │   │       ├── install (migrations for installing the database)
    │   │       └── upgrade (migrations for upgrading the database)
    │   └── webapp
    │       ├── META-INF
    │       ├── WEB-INF (run-time files used by the web application)
    │       │   ├── email-templates
    │       │   │   ├── cms
    │       │   │   └── ecommerce
    │       │   ├── geo-ip
    │       │   ├── json-services (registered endpoints for dynamic Javascript)
    │       │   ├── jsp (all the dynamic views for the web application)
    │       │   │   ├── admin
    │       │   │   ├── calendar
    │       │   │   ├── cms
    │       │   │   ├── dashboard
    │       │   │   ├── datasets
    │       │   │   ├── ecommerce
    │       │   │   ├── elearning
    │       │   │   ├── items
    │       │   │   ├── login
    │       │   │   ├── mailinglists
    │       │   │   ├── maps
    │       │   │   ├── portal
    │       │   │   ├── register
    │       │   │   ├── todoList
    │       │   │   └── userProfile
    │       │   ├── permissions
    │       │   ├── rest-services (the registered endpoints)
    │       │   ├── tlds
    │       │   ├── web-layouts (pre-configured web pages and their layouts)
    │       │   │   ├── collection
    │       │   │   ├── footer
    │       │   │   ├── header
    │       │   │   └── page
    │       │   ├── web-templates (Sample XML layout files used when adding new web pages)
    │       │   │   └── page
    │       │   │       ├── cms
    │       │   │       ├── e-commerce
    │       │   │       ├── portal
    │       │   │       └── remote
    │       │   ├── widgets (the registered widget entry declarations)
    │       │   └── workflows (the registered application workflows)
    │       ├── css
    │       │   └── <style sheets>
    │       ├── fonts (some open source fonts)
    │       ├── images (static images used by the portal)
    │       └── javascript (snapshot of open source javascript libraries)
    │           ├── <libraries>
    │           └── tinymce-plugins
    └── test
        ├── java
        │   └── com
        │       └── simisinc
        │           └── platform
        │               ├── application
        │               │   ├── admin
        │               │   ├── calendar
        │               │   ├── cms
        │               │   ├── datasets
        │               │   ├── ecommerce
        │               │   └── email
        │               ├── domain
        │               │   ├── events
        │               │   └── model
        │               ├── infrastructure
        │               │   ├── persistence
        │               │   └── workflow
        │               └── presentation
        │                   ├── controller
        │                   └── widgets
        └── resources (related resources for unit testing)
```

## Deployed Application Generated Files (CMS_PATH)

```
├── config (optional configurations filled out from source code, polled for updates)
│   ├── cms
│   │   ├── bot-list.csv
│   │   ├── country-ignore-list.csv
│   │   ├── email-ignore-list.csv
│   │   ├── ip-allow-list.csv
│   │   ├── ip-deny-list.csv
│   │   ├── redirects.csv
│   │   ├── spam-list.csv
│   │   └── url-block-list.csv
│   └── e-commerce
│       └── shipping-rates.csv
├── customization (custom files used by the application)
└── files (generated files and user contributed uploads)
    ├── backups
    ├── datasets
    ├── exports
    ├── images
    └── uploads
```
