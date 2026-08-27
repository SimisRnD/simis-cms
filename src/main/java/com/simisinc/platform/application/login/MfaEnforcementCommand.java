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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.presentation.controller.UserSession;

/**
 * Enforces the org-level MFA policy expressed in the {@code mfa.required.roles} site property.
 *
 * <p>When the property names one or more role codes (comma-separated), any logged-in user who
 * holds one of those roles but has not yet enrolled MFA is redirected to the enrollment page
 * on every request until they complete enrolment. The enrollment page itself is always reachable
 * without MFA so that no administrator can be permanently locked out.
 *
 * <p>Enabling or changing the required-roles list is audited by the existing site-properties
 * audit trail ({@code setting.update} events) — that event log is the governance evidence for
 * SSP IA-2(1).
 *
 * @author SimIS Inc.
 */
public class MfaEnforcementCommand {

  private static Log LOG = LogFactory.getLog(MfaEnforcementCommand.class);

  public static final String PROPERTY_REQUIRED_ROLES = "mfa.required.roles";
  public static final String PROPERTY_ENROLLMENT_URL = "mfa.enrollment.url";
  public static final String DEFAULT_ENROLLMENT_URL = "/my-profile";

  private MfaEnforcementCommand() {
  }

  /**
   * Returns {@code true} if the user's session includes at least one role listed in
   * {@code mfa.required.roles}.
   */
  public static boolean isEnforcedForUser(UserSession session) {
    if (session == null || !session.isLoggedIn()) {
      return false;
    }
    String required = LoadSitePropertyCommand.loadByName(PROPERTY_REQUIRED_ROLES);
    if (StringUtils.isBlank(required)) {
      return false;
    }
    for (String role : required.split(",")) {
      String trimmed = role.trim();
      if (!trimmed.isEmpty() && session.hasRole(trimmed)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns {@code true} if the user is in a required-MFA role but has not yet enrolled, and is
   * not a break-glass account. The caller should redirect to {@link #getEnrollmentUrl()} when this
   * returns {@code true}.
   */
  public static boolean requiresEnrollment(UserSession session, User user) {
    if (user == null) {
      return false;
    }
    // A break-glass account is never redirected to the enrollment page. Enforcement exempts only
    // that page, so a policy naming a role this account holds would strand it exactly like any
    // other admin -- and an account that is stranded by the same misconfiguration everyone else is
    // stranded by is not a recovery path. Its sign-ins alert every other administrator instead
    // (BreakGlassAlertCommand), so the exemption is loud rather than silent.
    //
    // This is not an exemption from MFA itself: if the account has MFA enrolled, LoginWidget still
    // demands a code before establishing the session.
    if (user.getBreakGlass()) {
      return false;
    }
    return isEnforcedForUser(session) && !user.getMfaEnabled();
  }

  /**
   * The configured MFA enrollment page URL (where non-enrolled users are redirected), or the
   * default {@value #DEFAULT_ENROLLMENT_URL} when the property is not set.
   */
  public static String getEnrollmentUrl() {
    return LoadSitePropertyCommand.loadByName(PROPERTY_ENROLLMENT_URL, DEFAULT_ENROLLMENT_URL);
  }

  /**
   * Returns {@code true} for URLs that must never be blocked by MFA enforcement: the enrollment
   * page itself, the login page, and the logout endpoint.
   */
  public static boolean isExemptUrl(String resource, String enrollmentUrl) {
    return resource.equals(enrollmentUrl)
        || resource.startsWith("/login")
        || resource.equals("/logout");
  }
}
