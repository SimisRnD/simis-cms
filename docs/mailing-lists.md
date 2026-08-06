# Mailing Lists

**Audience:** Site administrators, community managers, DevOps
**Goal:** Explain what the Mailing Lists feature does, how signups become sends, what to configure before going live on Azure, and what to monitor.

## Overview

```
Visitor submits an email (footer form / ajax widget / checkout opt-in)
  → SaveEmailCommand validates + GeoIP-enriches + upserts an `emails` row
    → MailingListMemberRepository.addEmailToList() creates/reactivates a
      `mailing_list_members` row, PENDING confirmation (double opt-in)
        → MailingListConfirmationCommand fires a domain event
          → jeasy-flows "mailing-list-member-confirmation-requested" playbook
            → confirm-subscription.html emailed via SMTP (EmailTask)
              → visitor clicks the link → MailingListConfirmSubscriptionWidget
                → member.confirmed set, is_valid = true, member is now ACTIVE

Admin CSV import / admin manual "Add Email" on /admin/mailing-list-members
  → same SaveEmailCommand path, but BYPASSES confirmation (admin attests consent)
    → member is ACTIVE immediately

Active members
  → ZeroBounce classifies deliverability (emails.validation_status)
    → confirmed-bad addresses auto-quarantined (archived, is_valid = false)
  → NewsletterSendCommand enqueues a send (mailing_list_sent, per recipient)
    → NewsletterQueueJob sends via SMTP, OR MailChimpCommand hands the whole
      send off to MailChimp as a Campaign instead
  → recipient can click a one-click unsubscribe link (unsubscribe_token) at
    any time, no login required
```

## Data Model

| Table | Key columns | Purpose |
|---|---|---|
| `emails` | `email`, `first_name`/`last_name`/`organization`, `ip_address` + GeoIP fields, `subscribed`/`unsubscribed`, `validation_status`/`validation_sub_status`/`validated_at` (ZeroBounce), `last_order`/`number_of_orders`/`total_spent` | One row per address, shared across mailing lists **and** ecommerce customers |
| `mailing_lists` | `name`, `title`, `member_count`, `show_online`, `enabled` | A list a visitor can subscribe to; `member_count` is a display-only counter (never decremented on unsubscribe, see `countDistinctSubscribers()`'s own comment) |
| `mailing_list_members` | `list_id`, `email_id`, `is_valid`, `unsubscribed`/`unsubscribed_by`/`unsubscribe_reason`, `quarantined`/`quarantine_reason`, `unsubscribe_token`, `confirmed`/`confirm_token`/`confirm_token_expires` | One row per (list, email) membership. `is_valid = false` means either genuinely unsubscribed, quarantined, or **pending double opt-in confirmation** — the admin member table's status filter distinguishes these (Active / Pending Confirmation / Unsubscribed / Quarantined) |
| `mailing_list_history` / `mailing_list_sent` | `service`, `subject`, `mailchimp_campaign_id`; per-recipient `status` (queued/sent/failed) | One send "batch" (history) and its per-recipient delivery rows (sent) |

## Signup Paths

| Path | Widget/Command | Requires confirmation? |
|---|---|---|
| Footer/newsletter signup form | `EmailSubscribeWidget` | **Yes** |
| Ajax inline signup (same form, JS variant) | `EmailSubscribeAjax` | **Yes** |
| Checkout "subscribe to newsletter" checkbox | `PlaceOrderWidget` | **Yes** |
| CSV bulk import | `ProcessEmailCSVFileCommand` → `/admin/mailing-list-members` upload | No — admin is attesting consent already exists |
| Admin manual "Add Email" | `MailingListMembersWidget`'s add-email action | No — same reasoning as CSV import |

All five converge on `SaveEmailCommand`. The three public self-service paths call `saveEmailRequiringConfirmation(...)`; CSV import and the admin manual-add form call the original `saveEmail(...)` overloads unchanged. A pending (unconfirmed) row is `is_valid = false` with a live `confirm_token`, expiring after a configurable number of days (`mailing-list.confirmation.expiryDays`, default 7) — an expired or already-used token behaves exactly like an unknown one on `/confirm-subscription`, matching `UserRepository`'s account-token precedent.

**Reactivation is also gated.** If someone previously unsubscribed and signs up again through a public path, they get a *fresh* confirmation email rather than being silently reactivated — this closes the loophole an earlier issue on this same page found (unbounded reactivation without re-consent). A **quarantined** address is never reactivated by any signup path, public or admin — only deliberate admin review clears a quarantine.

## Deliverability: ZeroBounce + Quarantine

If `mailing-list.zerobounce.apiKey` is configured, a background job periodically classifies subscriber emails via ZeroBounce's verification API into `emails.validation_status`. Any membership linked to a confirmed-bad status (`invalid`, `spamtrap`, `abuse`, `do_not_mail`) is automatically **quarantined** — archived (`is_valid = false`, `quarantined` timestamp set) but never deleted, and never touched by `unsubscribed` logic since quarantine is a distinct reason a membership stopped being active. `catch_all`/`unknown` statuses deliberately do **not** trigger quarantine, since ZeroBounce itself isn't calling those bad, just unresolved.

A dashboard tile tracks the quarantine rate against a configurable alert threshold (`mailing-list.quarantine.alertThresholdPercent`, default 10%) — if this spikes, it usually means either a compromised signup form (bot traffic feeding garbage addresses) or a genuine list-hygiene problem worth investigating before it drags down sender reputation.

## Sending: Direct SMTP vs. MailChimp

The app has **two independent send mechanisms**, chosen per list/send at `/admin/newsletter-send`:

1. **Direct SMTP** (`NewsletterSendCommand` → `NewsletterQueueJob`) — enqueues one row per active, valid member into `mailing_list_sent`, then sends each individually via `EmailCommand.prepareNewEmail()` (Apache Commons `ImageHtmlEmail`) using the site's configured `mail.host_name`/`mail.port`/`mail.username`/`mail.password`/`mail.ssl` properties — i.e., a raw SMTP relay you provide.
2. **MailChimp Campaign** (`MailChimpCommand`) — hands the whole send off to MailChimp's Campaigns API instead, using `mailing-list.mailchimp.apiKey`/`mailing-list.mailchimp.listId`. MailChimp also has its own separate sync path that pushes members into a MailChimp audience independent of sending a campaign through it.

The confirm-subscription and unsubscribe emails (transactional, one-off) always go through the direct-SMTP path via the same `jeasy-flows` workflow-YAML mechanism the rest of the app's transactional email uses (`WEB-INF/workflows/*.yml` + `EmailTask`) — they are not affected by which mechanism a list's *newsletter* sends use.

## Azure Deployment Guidance

**Outbound SMTP port 25 is blocked** on Azure App Service and (for most subscription types) VMs — this affects the direct-SMTP send path above regardless of list size. Configure `mail.port` to **587** (authenticated submission) against your SMTP provider; port 25 will silently fail or hang depending on the provider. This is confirmed current Microsoft Learn guidance (last updated July 2026) — see [Troubleshoot Outbound SMTP Connectivity in Azure](https://learn.microsoft.com/en-us/troubleshoot/azure/virtual-network/troubleshoot-outbound-smtp-connectivity).

**Raw SMTP relay is fine for transactional volume (unsubscribe/confirm emails, order confirmations) but risky for bulk newsletter sends.** Sending hundreds/thousands of marketing emails through a generic SMTP relay with no reputation management tends toward the spam folder over time, independent of Azure specifically. For real newsletter volume, prefer either:

- **MailChimp** (already integrated, `MailChimpCommand`) — reputation management, deliverability tooling, and campaign analytics are MailChimp's job, not yours.
- **A dedicated ESP as the SMTP relay target** instead of a generic mailbox provider — swap `mail.host_name`/`mail.username`/`mail.password` to point at the ESP's SMTP endpoint; no code change needed since the send path is already relay-agnostic. Reasonable options with Java-integrable REST APIs if you outgrow raw SMTP entirely: **Amazon SES** (cheapest at volume, but starts in a sandbox limited to 200 msgs/24h/verified-recipients-only until you request production access), **SendGrid**, **Postmark** (explicitly separates transactional vs. broadcast traffic into different IP pools — don't run newsletter blasts through a "transactional" stream if you adopt it), **Brevo/Sendinblue** (free tier includes both transactional + marketing, closest like-for-like MailChimp swap), **Mailgun**.
- **Azure Communication Services (ACS) Email** — a native Azure option explicitly positioned for bulk/marketing (not transactional-only), with its own SPF/DKIM/ARC, bounce/open/click analytics, and suppression-list handling, and its own 587 relay as one of the documented port-25 workarounds. Worth evaluating if you want to stay entirely within Azure's ecosystem rather than adding a third-party vendor.

**Captcha egress**: `EmailSubscribeWidget`/`EmailSubscribeAjax` validate reCAPTCHA/Turnstile server-side (`CaptchaCommand`) on every public signup. If the app runs behind VNet integration with restricted egress (NAT Gateway, firewall), explicitly allow-list the captcha provider's verification endpoint (`www.google.com`/`recaptcha.net` for reCAPTCHA, `challenges.cloudflare.com` for Turnstile) — a blocked verification call fails closed (signup rejected), which is safe but will look like "the signup form is broken" if nobody remembers to open the egress rule.

**IP/domain reputation**: a dedicated sending IP only pays off above roughly 100k emails/month of consistent volume; below that, a reputable shared pool (the default for SES/SendGrid/Mailgun/Brevo/ACS) is easier to maintain. Warm up any new sending domain or IP gradually — start with your most-engaged segment and ramp volume over days/weeks — regardless of which provider you choose.

## Monitoring & What To Watch

| Signal | Where | What it means |
|---|---|---|
| Quarantine rate spike | Mailing Lists dashboard tile, `mailing-list.quarantine.alertThresholdPercent` | Bot signups or a genuine hygiene problem |
| Rising "Pending Confirmation" count that never converts | `/admin/mailing-list-members?status=pending` | Confirmation emails aren't being delivered/opened — check SMTP relay health first |
| `mailing_list_sent.status = 'failed'` rows | `NewsletterQueueJob` per-recipient tracking | Individual send failures during a newsletter blast — check `error_message` |
| Bounce/complaint feedback | Provider-side (MailChimp dashboard, or SES/SendGrid/etc. webhooks if you adopt one) | Not currently surfaced inside SimIS CMS for the direct-SMTP path — this is an external tool you'd check on the provider's own dashboard |
| Rate-limit rejections on signup | Not currently persisted for mailing-list signups specifically (unlike Form Data's `FormSubmissionFailureRepository`) | A burst of legitimate-looking but rejected signups wouldn't show up anywhere today — a gap worth knowing about if signup volume seems lower than expected |

## Best Practices

- **Never bypass confirmation on a public-facing path.** The bypass exists only for CSV import and admin manual-add, both of which assume the admin already has a legal basis for the address. Anything reachable by an anonymous visitor should always go through `saveEmailRequiringConfirmation(...)`.
- **Keep the confirm-link expiry short enough to matter, long enough to be usable.** 7 days (the default) balances "don't let a stale token linger forever" against "don't punish someone who signs up on a Friday and checks email Monday."
- **Don't manually flip `is_valid = true` in the database** to "fix" a stuck pending signup — that skips consent entirely. Either wait for them to click the link, or re-trigger a fresh signup.
- **Route confirmation/unsubscribe email through a reliable relay before you route newsletter blasts through it.** If the transactional path is unreliable, double opt-in itself becomes the deliverability bottleneck (nobody can confirm if the email never arrives).
- **Check the quarantine dashboard before every large send**, not just periodically — a spike right before a scheduled campaign is a strong signal something (a scraper, a compromised form) fed the list garbage addresses recently.

## Newsletter / Blog Post Notifications

Publishing a blog post can optionally trigger a "Notify subscribers" email to a chosen mailing list (configured per-post in the blog editor) — this reuses the same `NewsletterSendCommand`/`NewsletterQueueJob` mechanism above, templated via `newsletter-blog-post.html`. A blog can also be permanently associated with a specific mailing list, which both defaults that picker and lets a blog-scoped signup CTA (`EmailSubscribeWidget`'s `blogUniqueId` preference) subscribe visitors directly to the right list from the blog's own page.

---

**See Also:**
- `SaveEmailCommand.java` / `MailingListMemberRepository.java` — signup + confirmation logic
- `MailChimpCommand.java` / `NewsletterSendCommand.java` / `NewsletterQueueJob.java` — send mechanisms
- `MailingListConfirmSubscriptionWidget.java` / `NewsletterUnsubscribeWidget.java` — public token-based landing pages
- `WEB-INF/workflows/mailinglists-workflows.yml` — confirmation email playbook
- [Troubleshoot Outbound SMTP Connectivity in Azure](https://learn.microsoft.com/en-us/troubleshoot/azure/virtual-network/troubleshoot-outbound-smtp-connectivity)
- [Azure Communication Services Email overview](https://learn.microsoft.com/en-us/azure/communication-services/concepts/email/email-overview)
