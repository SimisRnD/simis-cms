# Forms

**Audience:** Site administrators, content managers, DevOps
**Goal:** Explain what the Forms feature does, how to configure a form correctly, and what to check before/after going live on Azure.

## Overview

```
Visitor loads a page with a form widget
  → FormWidget.execute() renders fields from FormDefinition (or legacy XML <fields>)
    → client-side JS checks only "required" fields (not authoritative)

Visitor submits
  → FormWidget.post() checks the hidden honeypot field -- filled in, the submission is dropped
    silently: not saved, and the sender sees the same redirect as a success
    → re-validates server-side (required + email format) -- authoritative
      → captcha check (if enabled for this form)
        → rate limit check (anonymous users only, per-IP)
          → spam heuristics (if checkForSpam is on) -- flags, does NOT reject
            → FormData row saved (JSONB field values + IP/session/URL/query-string)
              → "form-submitted" workflow fires an email notification, UNLESS spam-flagged
                → recipient: form's emailTo, or every community-manager if blank

Every rejection (honeypot, missing field, bad email, captcha failure, rate limit) is recorded to
FormSubmissionFailureRepository -- visible as a dashboard chart, not raw field values.
```

## Form Builder

Build/manage forms at `/admin/forms` (list) and `/admin/forms-editor` (settings + fields):

- **Field types**: `text`, `email`, `textarea`, `select`, `checkbox`, `date`, and, when the `features.form-file-uploads` flag is on, `file` — that's the full list; there's no radio type in the admin builder (`SaveFormFieldCommand`'s allow-list). See [File upload fields](#file-upload-fields).
- **Per-field options**: label, internal name, placeholder, default value, required (yes/no), and an options list for `select`/`checkbox` (rendered as a dropdown or a checkbox group depending on type).
- **Per-form settings**: title/subtitle/button text, success title/message, `emailTo` (who gets notified), `useCaptcha`, `checkForSpam`, `enabled`.

Pages can also still define a form the old way via a `<fields>` XML preference on the `form` widget — that path skips the field-type allow-list entirely, so a typo'd type just silently renders as a plain text input rather than erroring. The one exception is `type="file"`, which is left out like a builder `file` field.

### File upload fields

Issue #2130 adds a `file` field type so a visitor can attach a screenshot to an inquiry. **Turn it on only once a malware scanner is set up** (see below): with no scanner, every attached file is rejected.

**Defining one**

- The type is offered in the field editor only while `features.form-file-uploads` is on (`/admin/feature-flags`, off by default). An existing `file` field can still be edited with the flag off.
- Each `file` field stores the kinds it accepts (`accepted_kinds`; today only `image`: JPG, PNG, WebP) and a maximum size (`max_bytes`; default 10 MB, never above `system.upload.maxBytes`). Options and a default value are cleared, since neither applies to a file. The Placeholder, if set, is shown as help text under the file input.
- The fields list warns when a form has a `file` field and **Use Captcha?** is off.
- With the flag off, `file` fields are left off the live form entirely, so a required one can't block submissions. An XML `fields` preference with `type="file"` is always left out.

**What happens when a visitor attaches a file**

Only a form with a `file` field is sent as `multipart/form-data`; every other form is unchanged. In the browser, `accept` filters the file picker and a file over the limit is caught before upload — conveniences only. On the server, `FormWidget.post()` runs the existing checks first (honeypot, required fields, email, captcha, rate limit), and **nothing is written until they pass**. Then each file goes through `SaveFormUploadCommand`:

1. **Size and type** (`ValidateFormUploadCommand`), before any write: the size against the field's limit, then both the filename extension and the content type in the file's first bytes against the field's list. PNG data named `.jpg` passes as a PNG; SVG, HTML, PDF, GIF and executables are rejected under any name.
2. **Quarantine:** a copy under `forms/quarantine/` in the file-server root, named by the server, stopped at the size limit rather than trusting the declared size. Deleted whatever happens.
3. **Re-encode** (`SanitizeFormUploadCommand`): decoded and encoded again from its pixels, so EXIF (including GPS), comments, text chunks and anything appended after the image data are dropped. JPEG and PNG in-process (JPEG turned the way its EXIF orientation says; PNG keeps transparency); WebP through the hardened ImageMagick `convert`. An image that won't decode, or claims more than 50 megapixels, is rejected. A thumbnail up to 320 pixels wide is made for review. Animated images keep their first frame.
4. **Malware scan** (`MalwareScanCommand`) of the file as it arrived, then of the re-encoded image that will be stored. It **fails closed**: only a clean scan passes; no scanner, a scanner that is down or slow, or an unexpected reply rejects the file.
5. **Store:** the re-encoded image and its thumbnail go under `forms/<form>/` with server-chosen names, and a `form_uploads` row is written with `scan_status = 'clean'`. The submission's stored value for the field is that row's id.

If any file is rejected, nothing is kept for the submission: files already stored for it are removed, the form is shown again with a plain sentence naming the rule (never what the file turned out to be), and the rejection is recorded as `file_<reason>` — `file_too_large`, `file_type_not_allowed`, `file_undecodable`, `file_infected`, `file_scan_unavailable` and so on — in the Rejected Submissions chart. A file input can't be refilled, so the visitor is asked to attach the file again.

**Where the files are, and who can get them**

Form uploads are kept apart from the file library: the `form_uploads` table and the `forms/` directory, not `files` and folders, so they never appear in the file library, folder pages or site search. They are served only by `/assets/form-upload?id=N` to admins and community managers (the roles that review form data), only when `scan_status` is `clean` — anything else answers 403 — and always as a download (`attachment`, `nosniff`); `&thumbnail=true` serves the thumbnail inline for review. Every request is recorded in the audit log as `form_upload.download`.

**Reviewing them**

- **`/admin/form-data`** shows each attached file with the submission: its thumbnail (the re-encoded preview, never the file itself), the visitor's name for it, its type, size and dimensions, whether it scanned clean, and a **Download** button. A file is looked up within its own submission only. If its record is gone, the row says the file is no longer available.
- **The notification email** names each file with its type and size (for example "screenshot.png (PNG image, 1.2 MB)") and links to the submission in Form Data. It never attaches the file or links it directly. The submitter's confirmation email lists no answers, files included.
- **Download CSV** writes a file field as the file's name and its download link, e.g. `Screenshot: screenshot.png (https://example.com/assets/form-upload?id=17)`. The link is absolute when `site.url` is set, and works only for a signed-in admin or community manager.

**When they're deleted**

- With their submission: when the retention job (see [Retention](#retention)) deletes a submission, the same run deletes its files and their records. Deleting a submission unlinks its uploads (`form_data_id` becomes null), and the job then removes uploads with no submission.
- A nightly sweep at 06:00 (`FormUploadSweepJob`) removes uploads whose submission was never saved, then any file under `forms/` that no `form_uploads` row names: left by a crash mid-submission, a delete that failed, or a quarantine copy. It skips anything less than a day old, so it can't catch a submission in progress. It doesn't follow symbolic links. If it can't read the list of stored files, it deletes nothing.
- A rejected file is never kept, so there is nothing to clean up for it. Dismissing or archiving a submission keeps its files, the same as its answers.

#### Setting up the malware scanner

- **Docker Compose:** `docker-compose.yaml` runs a `clamav` service (ClamAV 1.4 LTS, pinned by digest) on the internal network. Set **Malware scanner host** to `clamav` and leave the port at `3310` on `/admin/security-properties`. On first start the container downloads its signature database, which takes a minute or two; uploads made before `clamd` answers are rejected.
- **Stream size:** `clamd` refuses a stream larger than its `StreamMaxLength` setting, and that refusal rejects the upload. Make sure it is at least the largest file field limit (at most `system.upload.maxBytes`). In the Compose image it is 100 MB, above the 50 MB upload limit.
- **Azure:** App Service does not run the Compose file. Production needs a `clamd` the app can reach on the virtual network (for example a sidecar container) before file uploads are turned on. Until `security.clamav.host` points at one, every form upload is rejected.
- **Checking it works:** on `/admin/security-properties`, save the host and port, then click **Test Malware Scanner**. As with saving there, it may ask you to re-authenticate first. The test sends the saved scanner an ordinary file and the [EICAR test file](https://www.eicar.org/download-anti-malware-testfile/), and passes only if the first is accepted and the second is flagged. A `clamd` that has just started answers before its signatures have loaded, and the test fails until they have. It tests the saved settings, not unsaved edits on the form. Each of its two scans waits at most 30 seconds for an unresponsive scanner. Each run is logged with the result. Run it before turning on `features.form-file-uploads`, and after any change to the scanner. Developers can run the same checks with `ClamAvScanProviderLiveTest`, with `CLAMAV_TEST_HOST` (and `CLAMAV_TEST_PORT`) pointing at the scanner. EICAR can't check it through an image field: ClamAV's EICAR signature matches only the whole test file, so on its own it fails the type check before reaching the scanner, and appended to an image it isn't flagged (that appended content is what the re-encode removes).

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
- An email contains/ends-with block-list, checked only against a field whose Name is `email` (not every `email`-type field)
- One org-specific heuristic (a field literally named `organization` valued `gsa`, combined with a non-US or specific-city GeoIP location)

These lists are flat CSV files on disk (`cms/country-ignore-list.csv`, `cms/spam-list.csv`, `cms/email-ignore-list.csv`) under the configured file-server config path — editable by an admin with file access, but **there's no admin UI for them**.

Separately from these lists, every form carries a **honeypot**: a visually hidden input (`_hpWebsite`, issue #1153) that a person never fills in. A submission that fills it in is dropped rather than flagged — not saved, no notification — and the sender gets the same redirect as a real success, so a bot has nothing to adapt to. Each one is counted under the `honeypot` reason in the rejection chart (see [Monitoring](#monitoring-rejected-submissions)).

A spam match **flags** the submission (`flagged_as_spam = true`) — it is still saved, and the admin can review it at `/admin/form-data`. It is **not** rejected outright: the sender sees the normal success page (though it isn't counted as a lead in Google Analytics), and (important) a spam-flagged submission does **not** trigger the email notification, so a false-positive spam flag is effectively silent unless an admin checks the list.

To review flagged submissions, use the **Spam** filter on `/admin/form-data`: *Spam-Flagged Only* lists just them (each marked "spam likely"), and *Exclude Spam-Flagged* hides them.

## Email Notifications

A `form-submitted` workflow (see `WEB-INF/workflows/cms-workflows.yml`) sends a notification email whenever a non-spam-flagged form is submitted:

- If the form has `emailTo` set, that address gets the notification.
- If `emailTo` is blank, **every user with the `community-manager` role** gets it instead.

The notification includes the submitter's IP, GeoIP-derived location string, and every field's label/value (an attached file is listed by name, type and size, with a link to review it, never attached) — sent through the same SMTP mechanism as the rest of the app's transactional email (see the [Mailing Lists doc](mailing-lists.md#azure-deployment-guidance) for the Azure port-25/587 guidance, which applies identically here).

## Rate Limiting

Anonymous submissions are rate-limited by IP: 10 attempts / 30-minute window by default, configurable via `security.rateLimit.ipMaxAttempts`/`security.rateLimit.ipWindowMinutes` at `/admin/security-properties` (bounded 1–1000 attempts, 1–1440 minutes). Logged-in users are **not** rate-limited on form submission.

**Important Azure/scaling caveat**: this rate limit is an in-process, per-JVM-instance cache (Caffeine, resets on restart). If the app runs as multiple Azure App Service instances or scaled-out replicas, **each instance tracks its own independent counter** — the effective limit multiplies by instance count, since there's no shared/distributed rate-limit store. If you scale out and abusive submission volume doesn't seem to be getting throttled as expected, this is why.

The bucket is also **shared site-wide by IP**, not scoped per form — a visitor's budget is spent across every rate-limited endpoint keyed by the same IP, not reset per form.

## Monitoring: Rejected Submissions

Every rejection on submit (honeypot, missing required field, invalid email, captcha failure, rate limit) is recorded — form ID, reason, IP, URL, timestamp, **deliberately no field values** — and surfaced as a "Rejected Submissions by Reason" chart on `/admin/community/analytics`. There's no dedicated list page, only the aggregate chart. A spike here is the fastest signal that something's actively attacking a form (or that a form itself broke, e.g. a required field silently stopped being submitted after a template change).

## Data Captured Per Submission

Stored in `form_data`: IP address, session ID, page URL, query string (if present), all field values as a single JSONB column (`{id, label, name, type, value}` per field — not separate relational columns), plus claim/process/dismiss/archive status for the admin review workflow. GeoIP location is **not stored** — it's computed on demand from the IP whenever displayed or emailed. **Referrer and user-agent are not captured anywhere** — if you need either for abuse investigation, that would be new work, not a config toggle.

## Retention

**Form submission *failures*** (the rejection-tracking table above) have a retention job: `formData.failureRetentionDays` (default 90, bounded 7–3650 days), deleted daily at 05:00.

**Submissions** (`form_data`, including IP addresses and free-text field values) have a retention job too: `formData.retentionDays` (default 90, bounded 7–3650 days), run daily at 05:45. It deletes only submissions an admin has **processed or dismissed**, counting from when that happened; a submission still awaiting review is never deleted, however old. So bounded retention depends on admins actually working the review queue.

Files visitors attached to a submission are deleted with it, in the same run (see [File upload fields](#file-upload-fields)).

Neither retention property has an admin editor yet; changing one means updating its `site_properties` row directly.

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
