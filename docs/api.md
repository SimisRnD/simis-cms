---
id: api
title: API
# prettier-ignore
description: SimIS CMS has an API
---

SimIS CMS includes an extendable api for user-based access and server-to-server capabilities.

## User-Based JSON API

1. Create an app client in Admin, note the app id and secret key.
2. Make sure the API server is enabled in Site Settings
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
