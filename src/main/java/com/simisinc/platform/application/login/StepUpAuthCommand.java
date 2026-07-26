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

import com.simisinc.platform.application.UserPasswordCommand;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.presentation.controller.UserSession;

/**
 * Manages step-up re-authentication for sensitive privileged actions (IA-2 / AC-6).
 *
 * <p>A step-up token is proof that the acting administrator re-verified their identity
 * within the last {@link #STEP_UP_VALIDITY_MS} milliseconds. Sensitive actions — changing
 * another user's roles or password, disabling MFA on an account, approving content for
 * publication — should call {@link #isValid(UserSession)} before proceeding and
 * redirect / reject the request when it returns {@code false}.
 *
 * <p>The step-up token is stored as a timestamp on the session object (not in the database)
 * and expires after 5 minutes. Every verification attempt, successful or not, is written to
 * the audit log.
 *
 * @author SimIS Inc.
 */
public class StepUpAuthCommand {

  private static Log LOG = LogFactory.getLog(StepUpAuthCommand.class);

  /** Step-up tokens are valid for 5 minutes. */
  public static final long STEP_UP_VALIDITY_MS = 5 * 60 * 1000L;

  private StepUpAuthCommand() {
  }

  /**
   * Returns {@code true} if the session holds a step-up token that has not yet expired.
   */
  public static boolean isValid(UserSession session) {
    return session != null && session.getStepUpExpiresAt() > System.currentTimeMillis();
  }

  /**
   * Verifies a step-up credential and, on success, stamps the session with a fresh expiry.
   *
   * <p>A 6-digit numeric string is tried as a TOTP code first (when MFA is enrolled on the
   * account); anything else — or a 6-digit code when MFA is not enrolled — is tried as a
   * password.  Every attempt, successful or not, is written to the audit log as a
   * {@code step-up.verify} authentication event.
   *
   * @param session    the current user session (expiry is written here on success)
   * @param user       the acting user's full record (password hash, MFA secret)
   * @param credential password or 6-digit TOTP code
   * @return {@code true} if the credential was accepted
   */
  public static boolean verify(UserSession session, User user, String credential) {
    if (session == null || user == null || StringUtils.isBlank(credential)) {
      return false;
    }
    boolean verified = false;
    // Try TOTP first when the credential looks like a 6-digit code and MFA is enrolled
    if (credential.matches("\\d{6}") && user.getMfaEnabled()) {
      verified = TotpCommand.verifyCode(user.getMfaSecret(), credential);
    }
    // Fall back to password
    if (!verified && StringUtils.isNotBlank(user.getPassword())) {
      verified = UserPasswordCommand.verify(credential, user.getPassword());
    }
    String outcome = verified ? "success" : "failure";
    SaveAuditEventCommand.recordAuthentication("step-up.verify", outcome,
        session.getUserId(), null, session.getIpAddress(), session.getSessionId(), null);
    if (verified) {
      session.setStepUpExpiresAt(System.currentTimeMillis() + STEP_UP_VALIDITY_MS);
      LOG.debug("Step-up verified for user " + session.getUserId());
    }
    return verified;
  }
}
