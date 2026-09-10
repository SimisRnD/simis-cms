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

package com.simisinc.platform.application.login;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.mail.ImageHtmlEmail;

import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.application.email.EmailCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;

/**
 * Announces use of a break-glass account to every other administrator.
 *
 * <p>A break-glass account exists to be usable when the normal path is broken -- which is exactly
 * when nobody is watching. The account is therefore deliberately privileged in one way (org-level
 * MFA enforcement never redirects it, see {@link MfaEnforcementCommand#requiresEnrollment}), and
 * that privilege is paid for by making every use of it visible. Detection, not prevention.
 *
 * <p>Two independent records are written, because they fail independently:
 * <ul>
 * <li>an audit event, which is durable and local, and survives a broken mail configuration</li>
 * <li>an email to everyone holding {@code admin:manage}, which is the part a person actually
 * notices</li>
 * </ul>
 *
 * <p>Nothing here throws. An alert that could break a sign-in would make the break-glass account
 * less reliable than an ordinary one, defeating its purpose -- a failure to notify must never
 * become a failure to authenticate. Every path is best-effort and logged.
 *
 * @author SimIS Inc.
 */
public class BreakGlassAlertCommand {

  private static Log LOG = LogFactory.getLog(BreakGlassAlertCommand.class);

  /** Capability whose holders are notified -- the people who can act on a misuse */
  public static final String NOTIFY_CAPABILITY = "admin:manage";

  private static final String DEFAULT_SITE_NAME = "SimIS CMS";

  public static final String EVENT_LOGIN_SUCCESS = "authentication.break-glass.login";
  public static final String EVENT_LOGIN_FAILURE = "authentication.break-glass.login.failure";

  private BreakGlassAlertCommand() {
  }

  /**
   * Records and announces a successful break-glass sign-in.
   *
   * @param user the break-glass account that signed in
   * @param ipAddress the client address, when known
   * @param sessionId the session established, for correlation with the rest of the audit trail
   * @param source how the session was established (e.g. "form", "token", "oauth", "api")
   */
  public static void recordLogin(User user, String ipAddress, String sessionId, String source) {
    // Outermost guarantee that nothing here reaches the caller. Composing the message reads a site
    // property, which is a database call like any other -- on the day this account is needed, the
    // database may be exactly what is unwell.
    try {
      if (user == null || !user.getBreakGlass()) {
        return;
      }
      safelyAudit(EVENT_LOGIN_SUCCESS, "success", user.getId(), user.getEmail(), ipAddress, sessionId, source);
      safelyNotify(user,
          siteName() + " - break-glass account signed in",
          "The break-glass account " + describe(user) + " signed in"
              + (StringUtils.isNotBlank(source) ? " (" + source + ")" : "")
              + (StringUtils.isNotBlank(ipAddress) ? " from " + ipAddress : "")
              + ". If this was not one of your administrators, treat it as a credential compromise: "
              + "rotate the account's password and review the Security Audit Log.");
    } catch (Exception e) {
      LOG.error("Could not record break-glass login", e);
    }
  }

  /**
   * Records and announces a failed sign-in attempt against a break-glass account. Someone probing
   * these credentials is at least as interesting as a legitimate use, and the audit log alone is
   * only read after somebody already suspects something.
   *
   * <p>The caller is responsible for rate limiting -- see
   * {@link com.simisinc.platform.application.login.RateLimitCommand}, which already throttles
   * repeated attempts on an account, so a burst of guesses does not become a burst of mail.
   */
  /**
   * Records and announces a failed sign-in attempt, resolving the account from the address that was
   * attempted. Authentication failures never yield a {@link User}, so the caller has only what was
   * typed; an address matching no account, or matching an ordinary one, alerts nobody.
   *
   * <p>The lookup lives inside this method's guarantee rather than at the call site: it is a
   * database read on an unauthenticated path, and a failure to look up an account must not turn a
   * rejected password into a server error.
   */
  public static void recordFailedLogin(String attemptedEmail, String ipAddress, String sessionId, String reason) {
    try {
      if (StringUtils.isBlank(attemptedEmail)) {
        return;
      }
      recordFailedLogin(UserRepository.findByEmailAddress(attemptedEmail), ipAddress, sessionId, reason);
    } catch (Exception e) {
      LOG.error("Could not resolve an account for a failed break-glass login", e);
    }
  }

  public static void recordFailedLogin(User user, String ipAddress, String sessionId, String reason) {
    try {
      if (user == null || !user.getBreakGlass()) {
        return;
      }
      safelyAudit(EVENT_LOGIN_FAILURE, "failure", user.getId(), user.getEmail(), ipAddress, sessionId, reason);
      safelyNotify(user,
          siteName() + " - failed sign-in on the break-glass account",
          "A sign-in attempt on the break-glass account " + describe(user) + " failed"
              + (StringUtils.isNotBlank(ipAddress) ? " from " + ipAddress : "")
              + ". Nobody signed in. If none of your administrators was trying to use it, someone is "
              + "guessing at these credentials -- review the Security Audit Log.");
    } catch (Exception e) {
      LOG.error("Could not record failed break-glass login", e);
    }
  }

  /** Never throws: an audit write must not be able to fail a sign-in */
  private static void safelyAudit(String eventType, String outcome, long userId, String email,
      String ipAddress, String sessionId, String detail) {
    try {
      SaveAuditEventCommand.recordAuthentication(eventType, outcome, userId, email, ipAddress, sessionId, detail);
    } catch (Exception e) {
      LOG.error("Could not audit break-glass event " + eventType, e);
    }
  }

  /**
   * Mails every {@code admin:manage} holder except the break-glass account itself -- telling the
   * account about its own use is noise, and if it is the only administrator there is nobody to
   * tell. Never throws.
   */
  private static void safelyNotify(User breakGlassUser, String subject, String message) {
    try {
      // Work out the audience before building anything: if the break-glass account is the only
      // admin:manage holder there is nobody to tell, and no mail should be attempted at all
      List<User> addressable = new ArrayList<>();
      for (User recipient : LoadUserCommand.loadUsersHoldingCapability(NOTIFY_CAPABILITY)) {
        if (recipient.getId() == breakGlassUser.getId()) {
          continue;
        }
        if (recipient.getEmail() != null && recipient.getEmail().contains("@")) {
          addressable.add(recipient);
        }
      }
      if (addressable.isEmpty()) {
        // Worth a log line rather than silence: it means the alert has no audience, which an
        // administrator would want to know before relying on it
        LOG.warn("Break-glass alert has no recipients -- no other user holds " + NOTIFY_CAPABILITY);
        return;
      }
      ImageHtmlEmail email = EmailCommand.prepareNewEmail();
      for (User recipient : addressable) {
        email.addTo(recipient.getEmail(), recipient.getFullName());
      }
      email.setSubject(subject);
      email.setTextMsg(message);
      email.setHtmlMsg("<p>" + escape(message) + "</p>");
      email.send();
    } catch (Exception e) {
      LOG.error("Could not send break-glass alert", e);
    }
  }

  private static String describe(User user) {
    String name = StringUtils.trimToNull(user.getFullName());
    return name != null ? name + " (" + user.getEmail() + ")" : String.valueOf(user.getEmail());
  }

  private static String siteName() {
    try {
      String name = LoadSitePropertyCommand.loadByName("site.name");
      return StringUtils.isNotBlank(name) ? name : DEFAULT_SITE_NAME;
    } catch (Exception e) {
      LOG.warn("Could not read site.name for the break-glass alert subject", e);
      return DEFAULT_SITE_NAME;
    }
  }

  /** The message is composed from an account's own name and email, so escape before embedding */
  private static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
