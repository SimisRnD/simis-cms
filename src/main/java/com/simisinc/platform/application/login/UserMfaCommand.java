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
import com.simisinc.platform.infrastructure.persistence.UserRepository;

/**
 * Enrolls a user in TOTP multi-factor authentication and manages its lifecycle. Uses TotpCommand for the
 * cryptography and stores the secret on the user record. Prompting for a code at login is handled separately.
 *
 * @author SimIS Inc.
 * @created 2026-07-17
 */
public class UserMfaCommand {

  private static Log LOG = LogFactory.getLog(UserMfaCommand.class);

  private UserMfaCommand() {
    // Static utility, not instantiated
  }

  /**
   * Begins enrollment: generates a fresh secret, stores it as pending (not yet enabled), and returns the otpauth://
   * URI to present as a QR code. MFA is not active until confirmEnrollment succeeds with a valid code.
   *
   * @param user the user enrolling
   * @return the otpauth:// enrollment URI
   */
  public static String startEnrollment(User user) {
    String secret = TotpCommand.generateSecret();
    UserRepository.saveMfaSecret(user, secret);
    user.setMfaSecret(secret);
    return buildEnrollmentUri(user);
  }

  /**
   * Builds the otpauth:// enrollment URI from the secret already stored on the user. This lets a pending enrollment be
   * re-displayed as a QR code without generating a new secret.
   *
   * @param user the user with a pending (stored, not yet enabled) secret
   * @return the otpauth:// enrollment URI
   */
  public static String buildEnrollmentUri(User user) {
    String issuer = StringUtils.defaultIfBlank(LoadSitePropertyCommand.loadByName("site.name"), "SimIS CMS");
    String account = StringUtils.isNotBlank(user.getEmail()) ? user.getEmail() : user.getUsername();
    return TotpCommand.generateUri(issuer, account, user.getMfaSecret());
  }

  /**
   * Confirms enrollment by verifying a code against the pending secret. On success the user's MFA becomes enabled.
   *
   * @param user the user enrolling
   * @param code the code from the user's authenticator app
   * @return true when the code verified and MFA was enabled
   */
  public static boolean confirmEnrollment(User user, String code) {
    if (user == null || StringUtils.isBlank(user.getMfaSecret())) {
      return false;
    }
    if (!TotpCommand.verifyCode(user.getMfaSecret(), code)) {
      LOG.debug("MFA enrollment code did not verify for user: " + user.getId());
      return false;
    }
    UserRepository.enableMfa(user);
    return true;
  }

  /**
   * Turns MFA off for a user and clears the stored secret.
   *
   * @param user the user
   */
  public static void disable(User user) {
    UserRepository.disableMfa(user);
  }
}
