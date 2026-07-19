# Remediation: exposed Mapbox access token

## Finding

A Mapbox secret access token was committed to this project's history in an early
public commit and later removed from the current code. Because it remains in git
history, GitHub secret scanning flagged it. Removing a secret from the current
code does not invalidate it — a committed secret stays live until it is revoked
at the provider.

## Impact on deployments

None operationally. The application reads the Mapbox token from a database site
property (`maps.mapbox.accesstoken`) that ships empty, and it supports free map
providers that require no token:

- Map tiles: `openstreetmap` (the built-in default) in place of `mapbox`
- Geocoding: `nominatim` in place of `mapbox`

## Remediation (settings only — no code, no account)

Configure deployments to use the no-credential providers:

- `maps.service.tiles = openstreetmap`
- `maps.service.geocoder = nominatim`
- Leave `maps.mapbox.accesstoken` empty

This removes any dependency on Mapbox and on the exposed token.

## Token revocation

The token is associated with a Mapbox account that this project does not control,
so it cannot be rotated from here. For public repositories, GitHub forwards
detected partner secrets — Mapbox among them — to the vendor, who validates and
may revoke them automatically. If direct revocation is needed, the leak can be
reported to Mapbox support/security, who revoke a leaked token regardless of who
owns the account.

Git history is intentionally not rewritten: once the token is revoked it is inert,
and rewriting shared public history is disruptive and unnecessary.

## Closure

The secret-scanning alert is dismissed once revocation is confirmed, noting that
deployments were migrated to OpenStreetMap and that the token is not on an account
this project controls.
