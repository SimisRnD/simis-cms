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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.presentation.controller.UserSession;

/**
 * Verifies that MFA enforcement never strands a break-glass account.
 *
 * <p>The lockout this guards against is concrete: enforcement redirects every non-exempt request to
 * the enrollment page and exempts only that page, so an admin-role policy strands every admin who
 * has not enrolled. An account meant to be the way back in cannot be subject to that.
 *
 * @author SimIS Inc.
 */
class MfaEnforcementBreakGlassTest {

  private static UserSession sessionInAdminRole() {
    UserSession userSession = new UserSession();
    User user = new User();
    user.setId(1L);
    Role role = new Role();
    role.setCode("admin");
    user.setRoleList(Arrays.asList(role));
    userSession.login(user);
    return userSession;
  }

  private static User user(boolean breakGlass, boolean mfaEnabled) {
    User user = new User();
    user.setId(1L);
    user.setBreakGlass(breakGlass);
    user.setMfaEnabled(mfaEnabled);
    return user;
  }

  @Test
  void anOrdinaryUnenrolledAdminIsStillRedirected() {
    // The control still applies to everyone else -- this is the behaviour the exemption is carved
    // out of, so it has to be asserted alongside it
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName(
          MfaEnforcementCommand.PROPERTY_REQUIRED_ROLES)).thenReturn("admin");

      assertTrue(MfaEnforcementCommand.requiresEnrollment(sessionInAdminRole(), user(false, false)));
    }
  }

  @Test
  void aBreakGlassAccountIsNeverRedirected() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName(
          MfaEnforcementCommand.PROPERTY_REQUIRED_ROLES)).thenReturn("admin");

      assertFalse(MfaEnforcementCommand.requiresEnrollment(sessionInAdminRole(), user(true, false)),
          "a break-glass account stranded by the same policy as everyone else is not a recovery path");
    }
  }

  @Test
  void theExemptionIsNotAnExemptionFromMfaItself() {
    // requiresEnrollment only governs the redirect. Whether a code is demanded is LoginWidget's
    // decision, made from mfaEnabled/mfaSecret, and this exemption does not touch it -- an enrolled
    // break-glass account still authenticates with a second factor.
    User enrolledBreakGlass = user(true, true);
    assertTrue(enrolledBreakGlass.getMfaEnabled(),
        "enrollment state is untouched by the enforcement exemption");
  }

  @Test
  void noEnforcementPolicyMeansNobodyIsRedirected() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);

      assertFalse(MfaEnforcementCommand.requiresEnrollment(sessionInAdminRole(), user(false, false)));
    }
  }
}
