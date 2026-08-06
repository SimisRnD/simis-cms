# Forms

**Audience:** Site administrators, content managers, DevOps
**Goal:** Explain what the Forms feature does, how to configure a form correctly, and what to check before/after going live on Azure.

> **Note on scope:** two capabilities described here (a spam-flag filter on `/admin/form-data`, and a retention/purge job for `form_data` itself) are built and shipped as separate pull requests that are not yet merged to `main` as of this writing — PR #1025 (spam filter UI) and a form-data retention PR. Both are called out explicitly below wherever they apply so this doc stays accurate whether or not they've landed yet.

## Overview

```
Visitor loads a page with a form widget
  → FormWidget.execute() renders fields from FormDefinition (or legacy XML <fields>)
    → client-side JS checks only "required" fields (not authoritative)

Visitor submits
  → FormWidget.post() re-validates server-side (required + email format) -- authoritative
    → rate limit check (anonymous users only, per-IP)
      → captcha check (if enabled for this form)
        → spam heuristics (if checkForSpam is on) -- flags, does NOT reject
          → FormData row saved (JSONB field values + IP/session/URL/query-string)
            → "form-submitted" workflow fires an email notification, UNLESS spam-flagged
              → recipient: form's emailTo, or every community-manager if blank

Every rejection (missing field, bad email, captcha failure, rate limit) is recorded to
FormSubmissionFailureRepository -- visible as a dashboard chart, not raw field values.
```

## Form Builder

Build/manage forms at `/admin/forms` (list) and `/admin/forms-editor` (settings + fields):

- **Field types**: `text`, `email`, `textarea`, `select`, `checkbox`, `date` — that's the full list; there's no radio or file-upload type in the admin builder (`SaveFormFieldCommand`'s allow-list).
- **Per-field options**: label, internal name, placeholder, default value, required (yes/no), and an options list for `select`/`checkbox` (rendered as a dropdown or a checkbox group depending on type).
- **Per-form settings**: title/subtitle/button text, success title/message, `emailTo` (who gets notified), `useCaptcha`, `checkForSpam`, `enabled`.

Pages can also still define a form the old way via a `<fields>` XML preference on the `form` widget — that path skips the field-type allow-list entirely, so a typo'd type just silently renders as a plain text input rather than erroring.

## Validation

**Only two validation rules exist**: required, and email-format (only on `type="email"` fields). There is no regex pattern, no min/max length, and no numeric range option anywhere on a form field.

**Client-side JS only checks "required."** It does not check email format, and the email field is rendered as a plain `<input type="text">` (not `type="email"`), so there's no HTML5 email validation either. **All real validation is server-side** (`FormWidget.post()`), which re-checks required fields and validates email format against the raw submitted parameters independent of what the browser did — this is authoritative and cannot be bypassed by disabling JS, but also means a completely JS-free/scripted submission is validated exactly the same as a real browser submission.

## Captcha

Configure per-form via the "Use Captcha?" toggle (hard on/off, no partial/soft mode) at `/admin/forms-editor`. The active provider is a **site-wide** setting (`captcha.service` site property), not per-form:

- `turnstile` — Cloudflare Turnstile
- `google` (default) — reCAPTCHA v2 checkbox
- Anything else / misconfigured — falls back to a locally-rendered image captcha, or a session-stored text challenge

**Fail-open behavior**: if `captcha.service = "google"` (or `turnstile`) but the corresponding site/secret key is blank, captcha validation **silently passes everything** rather than blocking submissions or erroring loudly. If a form's captcha suddenly stops working after a config change, check the relevant site/secret key is actually populated before assuming something else broke.

Site properties: `captcha.service`, `captcha.google.sitekey`/`captcha.google.secretkey`, `captcha.turnstile.sitekey`/`captcha.turnstile.secretkey` — the two secret keys are encrypted at rest.

## Spam Handling

When `checkForSpam` is on (default), a submission is checked against:

- A country block-list (GeoIP-derived)
- A keyword contains-list run against `textarea` fields, plus a hardcoded disallowed-character check
- An email contains/wildcard block-list
- One org-specific heuristic (a field literally named `organization` valued `gsa`, combined with a non-US or specific-city GeoIP location)

These lists are flat CSV files on disk (`cms/country-ignore-list.csv`, `cms/spam-list.csv`, `cms/email-ignore-list.csv`) under the configured file-server path — editable by an admin with file access, but **there's no admin UI for them**, and **no honeypot field** exists anywhere in the form rendering.

A spam match **flags** the submission (`flagged_as_spam = true`) — it is still saved, and the admin can review it at `/admin/form-data`. It is **not** rejected outright, and (important) a spam-flagged submission does **not** trigger the email notification, so a false-positive spam flag is effectively silent unless an admin checks the list.

**Not yet merged**: a filter to isolate spam-flagged submissions on `/admin/form-data` (PR #1025) — until that lands, finding flagged submissions means scanning the full list for the "spam likely" label rather than filtering to just them.

## Email Notifications

A `form-submitted` workflow (see `WEB-INF/workflows/cms-workflows.yml`) sends a notification email whenever a non-spam-flagged form is submitted:

- If the form has `emailTo` set, that address gets the notification.
- If `emailTo` is blank, **every user with the `community-manager` role** gets it instead.

The notification includes the submitter's IP, GeoIP-derived location string, and every field's label/value — sent through the same SMTP mechanism as the rest of the app's transactional email (see the [Mailing Lists doc](mailing-lists.md#azure-deployment-guidance) for the Azure port-25/587 guidance, which applies identically here).

## Rate Limiting

Anonymous submissions are rate-limited by IP: 10 attempts / 30-minute window by default, configurable via `security.rateLimit.ipMaxAttempts`/`security.rateLimit.ipWindowMinutes` at `/admin/security-properties` (bounded 1–1000 attempts, 1–1440 minutes). Logged-in users are **not** rate-limited on form submission.

**Important Azure/scaling caveat**: this rate limit is an in-process, per-JVM-instance cache (Caffeine, resets on restart). If the app runs as multiple Azure App Service instances or scaled-out replicas, **each instance tracks its own independent counter** — the effective limit multiplies by instance count, since there's no shared/distributed rate-limit store. If you scale out and abusive submission volume doesn't seem to be getting throttled as expected, this is why.

The bucket is also **shared site-wide by IP**, not scoped per form — a visitor's budget is spent across every rate-limited endpoint keyed by the same IP, not reset per form.

## Monitoring: Rejected Submissions

Every rejection on submit (missing required field, invalid email, captcha failure, rate limit) is recorded — form ID, reason, IP, URL, timestamp, **deliberately no field values** — and surfaced as a "Rejected Submissions by Reason" chart on `/admin/community/analytics`. There's no dedicated list page, only the aggregate chart. A spike here is the fastest signal that something's actively attacking a form (or that a form itself broke, e.g. a required field silently stopped being submitted after a template change).

## Data Captured Per Submission

Stored in `form_data`: IP address, session ID, page URL, query string (if present), all field values as a single JSONB column (`{id, label, name, type, value}` per field — not separate relational columns), plus claim/process/dismiss/archive status for the admin review workflow. GeoIP location is **not stored** — it's computed on demand from the IP whenever displayed or emailed. **Referrer and user-agent are not captured anywhere** — if you need either for abuse investigation, that would be new work, not a config toggle.

## Retention

**Form submission *failures*** (the rejection-tracking table above) have a retention job: `formData.failureRetentionDays` (default 90, bounded 7–3650 days), deleted daily at 05:00.

**Not yet merged**: a retention/purge job for `form_data` itself — the actual submissions, including IP addresses and free-text field values, currently have **no expiry at all**. Every submission is retained indefinitely until an admin manually dismisses/archives it (which changes status, not deletion). If your compliance posture requires bounded retention of collected PII, treat this as an open gap until that job ships, not an already-configurable setting.

## Azure Deployment Guidance

- **Notification email** goes through the same raw-SMTP path as mailing list transactional email — port 25 is blocked on Azure, use port 587 authenticated submission. See the [Mailing Lists doc](mailing-lists.md#azure-deployment-guidance) for the full detail; it's identical here.
- **Captcha egress**: if running behind VNet integration with restricted egress, allow-list the captcha provider's verification endpoint (`www.google.com`/`recaptcha.net` for reCAPTCHA, `challenges.cloudflare.com` for Turnstile) — a blocked verification call fails open on this codebase's Google/Turnstile branches (see above), so a network-level block here doesn't loudly break the form, it silently disables the captcha check.
- **WAF false positives**: if Azure Front Door or Application Gateway WAF sits in front of the site, free-text fields (especially `textarea`) commonly trip generic SQLi/XSS managed rules (e.g. rule 942100) on ordinary user text. A WAF-blocked submission never reaches the app at all — it won't show up in the "Rejected Submissions" chart above, since that only records rejections the app itself decided on. If submissions seem to silently vanish for some visitors with nothing in the failure log, check the WAF logs before assuming it's an application bug. Fix with targeted per-field rule exclusions, not by disabling the rule site-wide.
- **Rate-limiting at the edge**: since the app's own rate limit is in-process and resets on restart/multiplies across instances (see above), consider Front Door's WAF rate-limiting as a complementary layer for anything facing real abuse, rather than relying on the app-level limit alone at scale.

---

**See Also:**
- `FormWidget.java` — rendering + submission handling (validation, captcha, spam, rate limiting)
- `FormCommand.java` — spam heuristics
- `SaveFormFieldCommand.java` — field-type allow-list
- `FormSubmissionFailureRepository.java` — rejection tracking + retention
- [Mailing Lists doc](mailing-lists.md) — shared SMTP/Azure email guidance
