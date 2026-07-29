---
id: ip-blocking
title: IP Blocking Strategy
# prettier-ignore
description: How SimIS CMS's IP firewall decides which requests to block, where each list lives, and its current limitations.
---

SimIS CMS blocks requests by IP address before they reach any page, form, or API. This describes what the firewall actually does today (as implemented in `WebRequestFilter` and `BlockedIPListCommand`), where each list lives, and its known gaps.

## What happens on a match

Every request's IP is checked in `WebRequestFilter`, before authentication, routing, or any page rendering. A blocked IP receives an HTTP **404 (Not Found)** response — not a 403 — so a blocked client sees the same response as a nonexistent page, with no indication it was blocked deliberately.

## Check order

Four lists are consulted, in this order, and the first match decides the outcome:

1. **Allow list** — `config/cms/ip-allow-list.csv`. A match here always passes, short-circuiting the remaining checks.
2. **Deny list** — `config/cms/ip-deny-list.csv`. A match here always blocks.
3. **Blocked IP list** — the database-backed list managed at `/admin/blocked-ip-list` (this is the only one of the four with an admin UI). A match blocks.
4. **URL probe list** — `config/cms/url-block-list.csv`, a list of request paths associated with known scanning/attack tools (e.g. common admin-panel or vulnerability-scanner probe paths). If the *requested path* — not the IP — matches an entry here, the requesting IP is added to the database-backed blocked IP list immediately and blocks going forward.

The two file-based lists (1 and 2) and the probe-path list (4) are **not editable from any admin screen** — they're deployment-time files on the server, polled for changes every 5 minutes by a background job (and loaded once at startup), so edits take effect without a restart, just not instantly.

There is no automatic blocking tied to failed logins, rate limiting, or form submissions today — the only automatic path onto the blocked IP list is #4 above (hitting a known probe path).

## Matching and validation

All four lists match by **exact IP address string** — there is no CIDR/subnet range support anywhere in this path. An entry added through `/admin/blocked-ip-list` (manually or via CSV) must be a valid IPv4 or IPv6 address; invalid or blank input is rejected.

## Managing the blocked IP list (`/admin/blocked-ip-list`)

- **Add** — the sidebar form adds a single IP with a free-text reason.
- **Delete** — the delete icon next to a row removes that IP, unblocking it immediately.
- **CSV upload** — bulk add/update/remove. Columns:
  - `IP Address` (required)
  - `Reason` (optional)
  - `Date` (optional, `yyyy-MM-dd hh:mm:ss`)
  - `Remove` (optional; set to `true` on a row to unblock that IP instead of adding/updating it)
  - Rows matching an existing IP with the same reason are skipped as duplicates.
- **CSV download** — exports `IP Address`, `Date`, `Reason` for every currently blocked IP.
- **Self-lockout guard** — both the manual add form and the CSV upload path reject any attempt to block the IP address the request is currently coming from. This is enforced in code, not just a warning.

## Audit trail

Every add, delete, CSV import, and CSV export on `/admin/blocked-ip-list` is recorded through the platform's audit logging (`blocked_ip.remove`, `blocked_ip.import`, `data.export` events). These do not appear inline on the page — view them from the **Audit Log** admin screen.

## Known limitations

- No admin UI for the allow list or deny list — they're server-file-only today, so exempting an IP (e.g. your own office network) requires file access to `config/cms/ip-allow-list.csv` on the deployment, not just admin-panel access.
- No CIDR/subnet blocking — blocking a range means adding every address individually.
- No expiration — a manually or automatically blocked IP stays blocked until someone deletes it; there's no time-limited or graduated blocking.
- No search/filter on the blocked IP list UI beyond pagination.

## Related

- If SimIS CMS runs behind a reverse proxy, load balancer, or CDN, set `CMS_TRUSTED_PROXIES` (see [Production Installation](installation.md)) so this firewall — along with rate limiting and geo filtering — evaluates the real client IP instead of the proxy's address.
