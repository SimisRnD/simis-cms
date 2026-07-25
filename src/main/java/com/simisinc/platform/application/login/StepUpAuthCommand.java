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

import com.simisinc.platform.application.UserPasswordCommand;
import com.simisinc.platform.domain.model.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Step-up re-authentication for sensitive actions (IA-2 / AC-6). Verifies the acting user's identity
 * via password or (when MFA is enrolled) a TOTP code before a high-privilege action is allowed.
 * The caller is responsible for recording the step-up in the session and auditing the outcome.
 */
public class StepUpAuthCommand {

  private static Log LOG = LogFactory.getLog(StepUpAuthCommand.class);

  private StepUpAuthCommand() {
  }

  /**
   * Returns true when the supplied credential matches the user's stored password, or when the user
   * has MFA enrolled and the supplied TOTP code is valid. At least one of password or totpCode must
   * be non-blank; if both are supplied, either match succeeds.
   */
  public static boolean verify(User user, String password, String totpCode) {
    if (user == null) {
      return false;
    }
    if (StringUtils.isNotBlank(password)) {
      try {
        if (UserPasswordCommand.verify(password, user.getPassword())) {
          return true;
        }
      } catch (Exception e) {
        LOG.warn("Step-up password verify error for user id " + user.getId() + ": " + e.getMessage());
      }
    }
    if (user.getMfaEnabled() && StringUtils.isNotBlank(totpCode)
        && StringUtils.isNotBlank(user.getMfaSecret())) {
      return TotpCommand.verifyCode(user.getMfaSecret(), totpCode);
    }
    return false;
  }
}
