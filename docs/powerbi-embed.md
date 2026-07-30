---
id: powerbi-embed
title: PowerBI Embed Widget
# prettier-ignore
description: How to embed a Power BI "Publish to web" report on a page using the powerBi widget.
---

`PowerBiWidget` embeds a Power BI report or dashboard on a page by rendering it in an iframe. This describes what the widget actually does today, as implemented in `PowerBiWidget` and `PowerBiEmbedCommand`, and its known limitations.

## What it does

Unlike Superset's guest-token flow, Power BI's **"Publish to web"** feature produces a complete, self-contained embed URL that Power BI itself signs and hosts — there's no server-side secret to configure and no token exchange at request time. The widget's only job is rendering that URL in an iframe, after confirming it's actually a Power BI embed URL and not an arbitrary attacker-controlled address.

This means a "Publish to web" report is **public to anyone who has the link** — Power BI does not apply row-level security, Azure AD authentication, or any other access control to a publish-to-web report, and neither does this widget. Don't publish anything confidential or CUI this way.

## Getting a "Publish to web" URL

In the Power BI Service (`app.powerbi.com`), open the report, then **File → Embed report → Publish to web (public)** and confirm the publish. Power BI generates an embed URL of the form `https://app.powerbi.com/view?r=...` — copy that URL (not the `<iframe>` snippet) into the widget's `embedUrl` preference.

## Adding the widget to a page

There is no admin-UI form for this widget today — it's configured directly in page-layout XML, the same as other dashboard widgets (`superset`, `statisticCard`):

```xml
<widget name="powerBi">
  <embedUrl>https://app.powerbi.com/view?r=eyJrIjoiYWJjMTIzIn0%3D</embedUrl>
  <height>500px</height>
</widget>
```

| Preference | Type | Default | Effect |
|---|---|---|---|
| `embedUrl` | string | *(required)* | The Power BI "Publish to web" URL. Must be an `https://app.powerbi.com/...` address — see Validation below. |
| `height` | CSS length | `300px` | Minimum height of the rendered iframe, sanitized with the same CSS-length filter other widgets use. |

## Validation

`PowerBiEmbedCommand.validateEmbedUrl` rejects and refuses to render:

- A missing or blank `embedUrl`.
- A malformed URL.
- Any URL not using `https`.
- Any URL whose host isn't exactly `app.powerbi.com` — including lookalikes like `app.powerbi.com.evil.com` or `notapp.powerbi.com`.

There's no error message shown when validation fails — the widget silently renders nothing. If a report you configured isn't appearing, check that `embedUrl` is the raw URL Power BI generated (not the `<iframe>` HTML snippet it also offers) and that it starts with exactly `https://app.powerbi.com/`.

## Known limitations

- No admin-UI form — page-layout XML only, the same as the item-search facet preferences.
- Only supports the "Publish to web" flow. Power BI's other embedding option, **"Embed for your organization"** (Azure AD auth, row-level security, non-public reports), needs an Azure app registration and server-side token generation this widget doesn't implement.
- Because a publish-to-web report is public, there's no way to scope who can view it short of not publishing it this way.

## Related

- `superset` widget — the equivalent embedding widget for Superset dashboards, using a guest-token flow instead of a pre-signed public URL.
