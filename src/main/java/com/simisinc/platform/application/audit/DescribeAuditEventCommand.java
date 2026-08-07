/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.simisinc.platform.application.audit;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * Turns a raw {@code audit_log.event_type} code (e.g. {@code content.publish}) into a short plain-language
 * phrase (e.g. {@code "published a page"}) for the admin activity feed (issue #1006). The phrase is written
 * to read naturally after an actor's name -- "{actor} {phrase}", e.g. "jane@example.com published a page" --
 * and is deliberately outcome-agnostic (the caller appends a failure indicator separately; see
 * {@code ActivityFeedWidget}) so a single entry covers both the success and failure record of the same
 * action rather than doubling the table.
 *
 * <p>The mapping was built from every real {@code eventType} literal passed to
 * {@code AuditEventCommand.record}/{@code SaveAuditEventCommand.recordAdminEvent}/{@code recordAuthentication}
 * in this codebase as of issue #1006 (grepped, not guessed) -- see that issue for the full audit. Anything
 * not in the table (a future eventType this mapping hasn't caught up with yet, or one this sweep missed)
 * falls back to {@link #prettify}, so the feed never shows a raw unmapped code with no fallback.
 *
 * @author SimIS Inc.
 */
public class DescribeAuditEventCommand {

  private static final String FALLBACK = "performed an action";

  private static final Map<String, String> DESCRIPTIONS = buildDescriptions();

  private DescribeAuditEventCommand() {
    // Static utility
  }

  /** Never returns null or an empty string -- blank input and unmapped codes both get a fallback phrase. */
  public static String describe(String eventType) {
    if (StringUtils.isBlank(eventType)) {
      return FALLBACK;
    }
    String mapped = DESCRIPTIONS.get(eventType.trim());
    if (mapped != null) {
      return mapped;
    }
    return prettify(eventType.trim());
  }

  /**
   * Generic fallback for an eventType with no explicit mapping: split camelCase and the {@code .}/{@code _}
   * separators into words, then title-case them -- {@code "content.saveDraft"} -> {@code "Content Save
   * Draft"}. Never returns a raw dotted/underscored code as-is, and never blank (falls back to the original
   * string if, somehow, nothing splittable was found).
   */
  static String prettify(String eventType) {
    String spaced = eventType
        .replace('.', ' ')
        .replace('_', ' ')
        .replace('-', ' ')
        // Split "saveDraft" -> "save Draft" before word-casing below
        .replaceAll("([a-z0-9])([A-Z])", "$1 $2");
    String[] words = spaced.trim().split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (String word : words) {
      if (word.isEmpty()) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(Character.toUpperCase(word.charAt(0)));
      if (word.length() > 1) {
        sb.append(word.substring(1).toLowerCase());
      }
    }
    return sb.length() > 0 ? sb.toString() : eventType;
  }

  private static Map<String, String> buildDescriptions() {
    Map<String, String> m = new LinkedHashMap<>();

    // authentication
    m.put("authentication.login.success", "logged in");
    m.put("authentication.login.failure", "failed to log in");
    m.put("authentication.mfa.verify.success", "verified a login with two-factor authentication");
    m.put("authentication.mfa.verify.failure", "failed a two-factor authentication check");
    m.put("authentication.logout", "logged out");
    m.put("account.lockout", "was locked out after repeated failed logins");
    m.put("step-up.verify", "re-verified their identity for a sensitive action");

    // user_management
    m.put("user.create", "created a user account");
    m.put("user.update", "updated a user account");
    m.put("user.disable", "suspended a user account");
    m.put("user.bulk_disable", "suspended multiple user accounts");
    m.put("user.enable", "re-enabled a user account");
    m.put("user.bulk_enable", "re-enabled multiple user accounts");
    m.put("user.unsuspend.requested", "requested account reinstatement");
    m.put("user.unsuspend.approved", "approved a reinstatement request");
    m.put("user.unsuspend.denied", "denied a reinstatement request");
    m.put("user.unsuspend.reverified", "re-verified a reinstatement request");
    m.put("user.password.reset", "reset a user's password");
    m.put("user.bulk_password_reset", "reset multiple users' passwords");
    m.put("user.password.reset.requested", "requested a password reset");
    m.put("user.password.reset.completed", "completed a password reset");
    m.put("user.password.invalidated", "had their password invalidated");
    m.put("user.mfa.reset", "cleared a user's two-factor authentication");
    m.put("user.delete", "deleted a user account");
    m.put("user.unlock", "cleared a login lockout");
    m.put("user.bulk_role_assign", "assigned roles to multiple users");
    m.put("user.registered", "registered a new account");

    // authorization
    m.put("capability_grant.grant", "granted a capability to a user");
    m.put("capability_grant.revoke", "revoked a capability from a user");
    m.put("capability_grant.expire", "let an expired capability grant lapse");
    m.put("role_capability.grant", "added a capability to a role");
    m.put("role_capability.revoke", "removed a capability from a role");
    m.put("folder.access.update", "changed a folder's access permissions");
    m.put("collection.access.update", "changed a collection's access permissions");
    m.put("collection.member.add", "added a member to a collection");
    m.put("collection.member.remove", "removed a member from a collection");
    m.put("group.create", "created a user group");
    m.put("group.update", "updated a user group");
    m.put("group.delete", "deleted a user group");

    // configuration
    m.put("setting.update", "updated a site setting");
    m.put("secret.rotate", "rotated a secret setting");
    m.put("integration.install", "installed an integration");
    m.put("integration.uninstall", "uninstalled an integration");
    m.put("sitemap.bulk_update", "updated SEO sitemap settings for multiple pages");
    m.put("mailing_list.create", "created a mailing list");
    m.put("mailing_list.update", "updated a mailing list");
    m.put("mailing_list.delete", "deleted a mailing list");
    m.put("mailing_list.quarantine", "quarantined mailing list members with bad deliverability");
    m.put("mailing_list.reactivation_blocked", "blocked a quarantined address from resubscribing");
    m.put("social_media_link.save", "saved a social media link");
    m.put("social_media_link.remove", "removed a social media link");
    m.put("blocked_ip.add", "blocked an IP address");
    m.put("blocked_ip.remove", "unblocked an IP address");
    m.put("blocked_ip.import", "imported a list of blocked IP addresses");
    m.put("allowed_ip.add", "allowed an IP address");
    m.put("allowed_ip.remove", "removed an allowed IP address");
    m.put("allowed_ip.import", "imported a list of allowed IP addresses");
    m.put("bot_user_agent.add", "added a bot user agent entry");
    m.put("bot_user_agent.remove", "removed a bot user agent entry");
    m.put("bot_user_agent.import", "imported a list of bot user agents");
    m.put("cache.clear_all", "cleared all caches");
    m.put("cache.clear", "cleared a cache");
    m.put("web_redirect.add", "created a web redirect");
    m.put("web_redirect.update", "updated a web redirect");
    m.put("web_redirect.remove", "deleted a web redirect");
    m.put("web_redirect.enable", "enabled a web redirect");
    m.put("web_redirect.disable", "disabled a web redirect");
    m.put("webhook_subscription.add", "created a webhook subscription");
    m.put("webhook_subscription.update", "updated a webhook subscription");
    m.put("webhook_subscription.remove", "deleted a webhook subscription");
    m.put("webhook_subscription.enable", "enabled a webhook subscription");
    m.put("webhook_subscription.disable", "disabled a webhook subscription");
    m.put("webhook_subscription.rotate_secret", "rotated a webhook's signing secret");
    m.put("theme.create", "created a theme");
    m.put("theme.restore", "restored a previous theme");
    m.put("theme.delete", "deleted a theme");
    m.put("audit.retention.purge", "purged aged audit log records");
    m.put("audit.integrity.check", "detected an audit log integrity check failure");

    // content
    m.put("content.publish", "published a page");
    m.put("content.unpublish", "unpublished a page");
    m.put("content.saveDraft", "saved a draft");
    m.put("content.submit", "submitted content for review");
    m.put("content.approve", "approved submitted content");
    m.put("content.reject", "sent content back for changes");
    m.put("content.delete", "deleted content");
    m.put("content.archive", "archived content");
    m.put("content.move", "moved content");
    m.put("newsletter.enqueue", "queued a newsletter send");
    m.put("page_layout.reorder", "reordered a page's layout");
    m.put("page_layout.addSection", "added a page section");
    m.put("page_layout.removeSection", "removed a page section");
    m.put("page_layout.setSectionClass", "changed a page section's style");
    m.put("page_layout.addColumn", "added a page column");
    m.put("page_layout.removeColumn", "removed a page column");
    m.put("page_layout.setColumnClass", "changed a page column's style");
    m.put("page_layout.addWidget", "added a widget to a page");
    m.put("page_layout.removeWidget", "removed a widget from a page");
    m.put("page_layout.setWidgetPreferences", "changed a widget's settings");
    m.put("folder_file.create", "uploaded a file");
    m.put("folder_file.update", "updated a file's details");
    m.put("folder_file.version", "uploaded a new file version");
    m.put("folder_file.delete", "deleted a file");
    m.put("image.setFocalPoint", "set an image's focal point");
    m.put("image.delete", "deleted an image");
    m.put("calendarEvent.archive", "archived a calendar event");
    m.put("calendarEvent.move", "moved a calendar event");
    m.put("calendarEvent.delete", "deleted a calendar event");

    // data_access
    m.put("data.export", "exported data");
    m.put("data.import", "imported data");
    m.put("audit_log.export", "exported the security audit log");
    m.put("audit_log.api_query", "queried the audit log via the API");
    m.put("user.export", "exported the user list");
    m.put("folder_file.view", "viewed a file");
    m.put("folder_file.download", "downloaded a file");

    return Map.copyOf(m);
  }
}
