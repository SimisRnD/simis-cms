---
id: oauth
title: OAuth Setup
# prettier-ignore
description: SimIS CMS can be configured for OAuth users, roles, and groups
---

SimIS CMS has built-in user logins and management. OAuth can be configured for validating user logins instead, and the OAuth provider can also provide roles and groups to be used within SimIS CMS.

Roles map to the built-in portal roles, such as System Administrator, Content Manager, and Data Manager. Web pages, specific widgets, and data directories can be limited to specific roles.

Groups map to the customized portal groups which users can be assigned to. Web pages, specific widgets, and data directories can be limited to specific groups.

## OAuth Provider Login (Keycloak example)

In Keycloak:

1. Create a realm or use an existing one
2. Add a client: simis-cms
3. Add Client Roles to Keycloak: system-administrator, content-manager, community-manager, data-manager, ecommerce-manager
4. Add Realm Groups to Keycloak: employees, supervisors, global-data-manager, etc.
5. Create Client Mappers and Tokens: User Client Role (roles), Group Membership (groups)
6. Create users and choose roles and groups for the user

In the SimIS CMS Database, configure the OAuth provider:

```sql
UPDATE site_properties SET property_value = 'true' WHERE property_name = 'oauth.enabled';
UPDATE site_properties SET property_value = 'Keycloak' WHERE property_name = 'oauth.provider';
UPDATE site_properties SET property_value = 'simis-cms' WHERE property_name = 'oauth.clientId';
UPDATE site_properties SET property_value = 'client-secret' WHERE property_name = 'oauth.clientSecret';
UPDATE site_properties SET property_value = 'https://localhost/realms/example' WHERE property_name = 'oauth.serviceUrl';
UPDATE site_properties SET property_value = true WHERE property_name = 'oauth.redirectGuests';
```

In the SimIS CMS Database, configure the roles mappings:

```sql
UPDATE site_properties SET property_value = 'roles' WHERE property_name = 'oauth.role.attribute';
UPDATE lookup_role SET oauth_path = 'system-administrator' where code = 'admin';
UPDATE lookup_role SET oauth_path = 'content-manager' where code = 'content-manager';
UPDATE lookup_role SET oauth_path = 'community-manager' where code = 'community-manager';
UPDATE lookup_role SET oauth_path = 'data-manager' where code = 'data-manager';
UPDATE lookup_role SET oauth_path = 'ecommerce-manager' where code = 'ecommerce-manager';
```

In the SimIS CMS Database, configure the groups mappings:

```sql
UPDATE site_properties SET property_value = 'groups' WHERE property_name = 'oauth.group.attribute';
UPDATE groups SET oauth_path = '/learners' WHERE unique_id = 'learners';
UPDATE groups SET oauth_path = '/instructors' WHERE unique_id = 'instructors';
```

Reset the cache
