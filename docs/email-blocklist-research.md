# Email Blocklist/Spam Filtering Research

**Date:** July 26, 2026  
**Research Scope:** Industry best practices for mailing list spam blocking  
**Target:** simis-cms mailing list implementation  

---

## Executive Summary

Comprehensive research across 8+ major open-source CMSs (WordPress, Drupal, Statamic, Grav, Craft, Laravel, GNU Mailman, Zendesk) reveals a **multi-layer approach** is most effective:

1. **Disposable domain blocklist** (100K+ domains)
2. **Custom admin blocklist** (database with audit trail)
3. **Pattern matching** (role-based: test@, admin@, noreply@)
4. **Optional:** External API validation (ZeroBounce, Kickbox, NeverBounce)

**Effort Estimate:** 10-15 hours total (4 phases)  
**ROI:** High - blocks 40-60% of spam signups per industry reports

---

## CMS-by-CMS Findings

### WordPress (Most Mature Implementation)

**Plugins:**
- Email Blocklist
- CM E-Mail Blacklist
- Advanced Form Blocker

**Features:**
- Domain and email-based blocking
- Global blocklist from GitHub (auto-updates daily)
- User scanning to identify existing accounts with suspicious domains
- JSON file format: `{"domains": [...], "emails": [...]}`

**Storage:**
- Local WordPress `wp_options` table
- GitHub JSON caching for global lists

**External Services:**
- Akismet (comment/form spam detection)
- Zero Spam (real-time email & IP validation)

**Effectiveness:** High (40-60% spam reduction reported)

---

### Drupal (Most Comprehensive)

**Modules:**
- Email Blacklist Module
- Stop Spam Registrations Module
- Domain Blacklist Module
- Disposable Email Address (DEA) Blocker

**Features:**
- Domain-based blocking (primary)
- DNS Blacklist (DNSBL/RBL) queries for reputation checking
- Regex pattern matching
- Web-based admin UI under `admin/config/`

**Database Schema:**
- Dedicated tables for blocked addresses
- Extensible via `hook_schema()`

**External Services:**
- Spam Master (RBL technology)
- Mollom legacy integration

**Effectiveness:** Very High (comprehensive approach)

---

### Laravel (Simplest Framework Approach)

**Packages:**
1. **laravel-email-domain-blacklist** (Alariva)
   ```php
   $this->validate($request, ['email' => 'required|email|blacklist']);
   ```

2. **Blacklister** (niclas-timm)
   - JSON file storage: `storage/framework/blacklist.json`

3. **Laravel-Disposable-Email** (Propaganistas)
   - Uses disposable domains from [disposable/disposable](https://github.com/disposable/disposable)
   - Weekly updates via scheduled command: `disposable:update`
   - Two validation approaches:
     - Domain list matching
     - MX record inspection (resolves mail server records)

**Disposable Domains Source:**
- 100K+ domains tracked
- GitHub Actions builds daily
- Multiple aggregated sources
- Formats: `.txt`, `.json`, `manual_domains.txt`

**Effectiveness:** High (simple + effective)

---

### Statamic & Grav

**Statamic:**
- Form Guard addon: up to 90% spam reduction
- Honeypot technique + third-party service integration
- Akismet integration

**Grav:**
- Comments Pro plugin: honeypot, time-based checks, keyword blacklisting
- Antispam plugin: email obfuscation (JavaScript encoding)

**Effectiveness:** Medium (basic patterns)

---

### Craft CMS

**Features:**
- Email Validator plugin (pre-signup validation)
- Campaign plugin (mailing list management)
- CleanTalk integration (spam protection)

**Database:**
- Campaigns, contacts, mailing lists, segments as element types

**Effectiveness:** Medium-High

---

### GNU Mailman 3 (Dedicated Mailing List Platform)

**Regex-Based Filtering:**
- Three parameters per list:
  - `discard_these_nonmembers`: ['^name_*bperson*@example.com']
  - `reject_these_nonmembers`: ['^b[hello]*@example.com']
  - `hold_these_nonmembers`: ['^re[gG]ex@example.com']

**API Endpoints:**
- `PUT/PATCH /3.0/lists/{list}/config`

**Features:**
- Regex pattern support
- Literal email addresses
- Array format in REST requests

**Effectiveness:** Medium (lightweight, pattern-based)

---

### Zendesk (Enterprise SaaS)

**Blocklist UI:**
- Admin Center → People → Configuration → End users

**Features:**
- Up to 10,000 characters per field
- Domain entries (without @): `megaspam.com`
- Email entries (with @): `reject:randomspammer@gmail.com`
- Keywords: `suspend:`, `reject:`
- Wildcards: `*` for all except allowlist

**Effectiveness:** Medium (simple configuration)

---

## Common Spam Patterns Across All CMSs

| Pattern | Detection Method | False Positive Risk |
|---------|------------------|-------------------|
| **Disposable/Temporary Emails** | Domain list (10minutemail, tempmail, mailinator) | Low |
| **Role-Based Addresses** | Regex/prefix matching (test@, admin@, noreply@) | Medium |
| **Catch-All Domains** | MX record inspection | Low-Medium |
| **Honeypot** | Hidden form fields | Very Low |
| **Time-Based** | Submission speed detection | Medium |

---

## Storage & Database Approaches

| Approach | Examples | Pros | Cons |
|----------|----------|------|------|
| **JSON files** | WordPress, Laravel, Disposable-Email | Simple, portable, no DB overhead | File I/O, scalability limits |
| **Database tables** | Drupal, Craft CMS | Queryable, scalable, audit trails | More complex setup |
| **Git repositories** | Email Blocklist plugin, Disposable-Email | Auto-updated, version controlled | Network dependency |
| **wp_options table** | WordPress plugins | Native integration, easy access | Single key limitation |
| **Custom config** | Laravel packages | Flexible, framework-integrated | Requires config publishing |

---

## External Service Integrations

### Spam Detection APIs

**1. ZeroBounce**
- Free tier: 100 validations/day
- Capabilities: Syntax check, MX record validation, SMTP verification, catch-all detection
- Pricing: $0.0025-0.006 per email (bulk rates)

**2. NeverBounce**
- SMTP server verification
- Role-based email detection
- Batch processing available

**3. Kickbox**
- Disposable email detection built-in
- Free tier: 100/month
- Disposable email database (50K+ tracked)

**4. CleanTalk**
- Cross-platform spam protection
- Email blocklist capability
- API-based (no setup needed)

### Other Services

**Akismet**
- Comment/form spam detection
- Wide CMS integration (WordPress, Statamic)
- Reputation-based (not disposable-specific)

---

## Recommended Architecture for simis-cms

### Phase 1: Database Table + Admin UI (4-6 hours)

**Schema:**
```sql
CREATE TABLE email_blocklist (
  id BIGSERIAL PRIMARY KEY,
  entry_type VARCHAR(20), -- 'email', 'domain', 'pattern'
  entry_value VARCHAR(255) UNIQUE,
  reason TEXT,
  date_added TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  added_by BIGINT REFERENCES users(user_id),
  is_active BOOLEAN DEFAULT TRUE,
  created TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ON email_blocklist(entry_type, entry_value, is_active);
CREATE INDEX ON email_blocklist(date_added);
```

**Admin UI Features:**
- Search/filter blocklist entries
- Add single entry with reason
- Bulk import (CSV/JSON)
- Bulk export
- Disable/enable entries
- Delete with confirmation
- Audit log (who added, when, why)

**Statistics Dashboard:**
- Blocked signups per day/week/month
- Top blocked domains
- Blocked emails by reason
- Effectiveness metrics

### Phase 2: Validation Logic (2-3 hours)

**Integration Point:** `SaveEmailCommand.java` line 66-71

```java
// After JMail validation, add:
if (isEmailBlocklisted(emailBean.getEmail())) {
  throw new DataException("This email address cannot be used");
}

private static boolean isEmailBlocklisted(String email) {
  // 1. Check exact email in blocklist table (is_active = true)
  if (EmailBlocklistRepository.findByEmail(email) != null) {
    return true;
  }
  
  // 2. Extract domain, check domain blocklist
  String domain = extractDomain(email);
  if (EmailBlocklistRepository.findByDomain(domain) != null) {
    return true;
  }
  
  // 3. Check disposable domains cache (in-memory map)
  if (DisposableDomainCache.contains(domain)) {
    return true;
  }
  
  // 4. Check pattern matches (regex)
  if (matchesBlockedPattern(email)) {
    return true;
  }
  
  // 5. Allow admin override (whitelist)
  if (EmailBlocklistRepository.isWhitelisted(email)) {
    return false;
  }
  
  return false;
}
```

**Default Patterns to Include:**
- `^(test|admin|noreply|support|sales)@.*`
- `^.*@(example\.com|localhost|127\.0\.0\.1)$`
- Custom patterns per admin

### Phase 3: Testing & Documentation (2-3 hours)

- Unit tests for blocklist validation
- Integration tests with mailing list signup
- Performance testing (blocklist lookup overhead)
- Admin UI testing
- Documentation for admins
- Release notes

### Phase 4: External API Integration (2-4 hours, Optional)

```java
// Optional: Add to SaveEmailCommand
private static boolean isEmailValidViaAPI(String email) {
  if (!USE_EXTERNAL_VALIDATION) return true; // Skip if disabled
  
  ZeroBounceResult result = ZeroBounceAPI.validate(email);
  
  // Block if:
  // - Invalid syntax
  // - Catch-all domain (likely spam)
  // - Disposable email (via API)
  // - Role-based email
  
  return result.isValid() && !result.isDisposable();
}
```

**Configuration:**
```
email.blocklist.enabled=true
email.blocklist.useExternalValidation=false
email.blocklist.externalService=zerobounce
email.blocklist.apiKey=xxx
email.blocklist.checkMxRecords=true
```

---

## Implementation Order

1. **Phase 1** (Week of Aug 2):
   - Create database migration
   - Build admin UI (simple CRUD)
   - Add basic statistics

2. **Phase 2** (Week of Aug 2-9):
   - Implement validation logic
   - Load disposable domains JSON
   - Add pattern matching

3. **Phase 3** (Week of Aug 9):
   - Write tests
   - Document for admins
   - Measure spam reduction

4. **Phase 4** (Optional, Week of Aug 16+):
   - Evaluate ZeroBounce/Kickbox
   - Integrate API if ROI justifies cost
   - Monitor hit rate vs false positives

---

## Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Spam signup reduction | 40-60% | Blocked/total signups |
| False positive rate | <2% | Blocked valid emails |
| Lookup latency | <5ms per check | Database query time |
| Admin ease-of-use | >90% satisfaction | Survey/usage analytics |

---

## Files to Create/Modify

1. **New migration:** `UPGRADE_20260726.XXXX__create_email_blocklist_table.sql`
2. **Model:** `src/main/java/.../domain/model/mailinglists/EmailBlocklist.java`
3. **Repository:** `src/main/java/.../persistence/mailinglists/EmailBlocklistRepository.java`
4. **Command:** Modify `SaveEmailCommand.java` (add validation logic)
5. **Widget:** Create `EmailBlocklistAdminWidget.java` for UI
6. **Config:** Add `email-blocklist.properties`
7. **Tests:** Unit + integration tests
8. **Cache:** Disposable domains loader (scheduled job)

---

## Decision Points

**Q1: Should we start with Phase 1-2 or wait for Phase 4?**
- **Recommendation:** Start with Phase 1-2 (no external dependencies)
- Phase 4 can be added later if needed

**Q2: Disposable email source - auto-update or static?**
- **Recommendation:** Git-based auto-updates (weekly)
- Keeps data fresh without API calls

**Q3: Whitelist support (admin override)?**
- **Recommendation:** Yes - for false positives
- Add to Phase 1

**Q4: Case sensitivity for patterns?**
- **Recommendation:** Case-insensitive regex
- Reduces maintenance

---

## References

### WordPress
- [Email Blocklist Plugin](https://wordpress.org/plugins/email-blocklist/)
- [Zero Spam Plugin](https://www.zerospam.org/)

### Drupal
- [Stop Spam Registrations](https://www.drupal.org/project/stop_spam_regs)
- [Disposable Email Address Blocker](https://www.drupal.org/project/dea_blocker)

### Laravel
- [GitHub: laravel-email-domain-blacklist](https://github.com/alariva/laravel-email-domain-blacklist)
- [GitHub: Laravel-Disposable-Email](https://github.com/Propaganistas/Laravel-Disposable-Email)
- [GitHub: disposable/disposable](https://github.com/disposable/disposable)

### Mailing Lists
- [GNU Mailman 3 Documentation](https://docs.mailman3.org/)
- [Zendesk Blocklist Guide](https://support.zendesk.com/hc/en-us/articles/4408886840986)

### External APIs
- [ZeroBounce Email Validation](https://www.zerobounce.net/)
- [NeverBounce Email Verification](https://neverbounce.com/)
- [Kickbox Email Validation](https://kickbox.io/)

---

**Document Status:** Ready for implementation planning  
**Next Step:** Milestone approval → Phase 1 implementation kickoff
