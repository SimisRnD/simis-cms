---
id: installation
title: Production Installation
# prettier-ignore
description: SimIS CMS is delivered as a Java Web Application Archive (.war) and requires PostgreSQL with extensions.
---

SimIS CMS is delivered as a Java Web Application Archive (.war) and runs with Apache Tomcat. PostgreSQL with PostGIS extensions is required.

An optimized web application archive (.war), with production settings, is released to this project's GitHub releases, ready for installation and which automatically upgrades previously installed versions. Always have a backup of your database and file library path.

## System Requirements

- [OpenJDK 17+](https://learn.microsoft.com/en-us/java/openjdk/download)
- [Apache Tomcat 9.0.x](https://tomcat.apache.org)
- [PostgreSQL 14+](https://www.postgresql.org) with [PostGIS 3.2](https://postgis.net)
- The web application and optional services have only been tested on Linux, MacOS, and Windows with WSL

## Typical Steps

1. Download the latest release from <https://github.com/SimisRnD/simis-cms/releases>.
2. Review the release notes for any unusual upgrade notices.
3. It's recommended to copy the .war into a container image for deployment and to set several environment variables for database connectivity and for the initial Administrator user account.
4. To log into a new site, add "/login" to the URL. Later, turn on the login setting to reveal a login button for your website.

## Production Steps

- Install the .war into a base container image like "tomcat:9.0-jdk17" (call it ROOT.war for a root web context)
- Setup PostgreSQL and PostGIS using a managed service like [Amazon RDS for PostgreSQL](https://aws.amazon.com/rds/postgresql/) with [PostGIS extension](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.PostGIS.html); optionally use a containerized PostgreSQL like "postgres:14" and install the needed extensions
- Create the PostgreSQL database and user credentials (the default name is "simis-cms" but it can be configured)
- Decide on a directory location for uploaded and generated assests
- Set the application's environment variables specific to your deployment strategy
- Start up the application
- The database will be installed and Tomcat's log will show the application startup status
- Login, navigate to Admin, and follow the Getting Started to-do list
- If an Administrator was not defined in the environment variables, then the Tomcat log will contain two random checksums for the Admin's user/pass (you must review the logs for this one-time login information)

## Environment Variables

```dotenv
CMS_ADMIN_USERNAME=
CMS_ADMIN_PASSWORD=
CMS_FORCE_SSL=true|false
CMS_NODE_TYPE=<empty>|standalone|web
CMS_PATH=<empty>|${USER_HOME}/Web/simis-cms|/opt/simis
DB_SERVER_NAME=
DB_NAME=
DB_USER=
DB_PASSWORD=
```

## Upgrading

Upgrading is as simple as replacing the application ROOT.war with a newer version. Check the release notes for any unusual upgrade steps. Always have a database backup for rollbacks. An application rollaback without a database rollback *may* work depending on any database changes.
