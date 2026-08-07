---
id: feature-flags
title: Feature Flags
# prettier-ignore
description: How SimIS CMS's features.* site-property flags work, how to add one, and how to gate a code path behind it.
---

SimIS CMS has a lightweight per-feature toggle mechanism: `features.*` site properties, read through `FeatureFlagCommand`, editable from `/admin/feature-flags` without a deployment. This describes how it works today and how to add a new flag.

## How it works

A feature flag is just a boolean site property under the `features.` prefix (e.g. `features.layout-editor`). It uses the same site-property machinery every other admin setting does:

- Properties are grouped and cached by their prefix (`LoadSitePropertyCommand`, backed by `CacheManager.SYSTEM_PROPERTY_PREFIX_CACHE`).
- That cache expires after 5 minutes and refreshes in the background after 1 minute. A saved change is visible immediately on the instance that handled the save, because `SitePropertyRepository.saveAll()` calls `CacheManager.invalidateKey()` for the prefix on every save -- the TTL/refresh exists as a backstop for any *other* instance in a multi-instance deployment, which picks up the change within about a minute rather than instantly.
- `/admin/feature-flags` is a normal `sitePropertiesEditor` widget page (`admin-layout.xml`) with `<prefix>features</prefix>`, the same generic editor used by `/admin/security-properties`, `/admin/captcha-properties`, and every other `/admin/*-properties` page.
- Active flags are logged once at startup (`ContextListener`), so the log reflects the feature posture the application booted with.

## Reading a flag

```java
if (FeatureFlagCommand.isEnabled("layout-editor")) {
  // ...
}
```

`isEnabled(name)` takes the flag's bare name (no `features.` prefix) and returns `false` for a blank name or a flag that was never seeded -- it never fails open.

## Adding a new flag

1. Add one `INSERT INTO site_properties (...) VALUES (..., 'features.<name>', '<default>', 'boolean') ON CONFLICT (property_name) DO NOTHING;` row to a new file under `src/main/resources/database/upgrade/2026/`, **and** the equivalent plain `INSERT` (no `ON CONFLICT`, matching every other install-side seed) under `src/main/resources/database/install/` -- a fresh install never runs the `upgrade/` tree, so a flag seeded only there is missing on day one. See `NEW_10150__new_feature_flag_properties.sql` / `UPGRADE_20260802.1009__feature_flag_properties.sql` for the pattern this issue (#410) established.
2. Pick the default value deliberately:
   - Gating **new** behavior: default `false` (dark-launched, opt-in rollout).
   - Gating **existing, already-shipped** behavior you want an off-switch for: default `true`, so upgrading installs see no behavior change until someone flips it off.
3. Read it with `FeatureFlagCommand.isEnabled("<name>")` at the point(s) that decide the behavior. Prefer gating as few, as central call sites as possible -- see `WebPageDesignerWidget`'s `features.layout-editor` gate for a real example of finding every entry point into a feature (a query-param branch, a POST branch, a save-time redirect, and the page-creation template that offers it in the first place) rather than gating only the most obvious one.
4. If turning the flag off should stop *offering* a feature without retroactively touching data that already reflects it being on (e.g. previously-created records, previously-saved content), gate the offering, not the data -- don't write a migration that converts or deletes existing records when a flag flips.

## Known limitations

- No percentage/gradual rollout, no per-user or per-role targeting -- a flag is a single global boolean.
- No audit trail specific to flag changes beyond the same audit logging every `sitePropertiesEditor` save gets (`setting.update`, prefix `features`).
- No expiration or "flag debt" tracking -- a flag stays in `site_properties` until someone removes the migration-seeded row and its call site(s) by hand.
