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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.domain.model.User;

/**
 * Verifies the break-glass alert: who it tells, who it does not, and that nothing it does can fail
 * the sign-in that triggered it.
 *
 * @author SimIS Inc.
 */
class BreakGlassAlertCommandTest {

  private static User user(long id, String email, boolean breakGlass) {
    User user = new User();
    user.setId(id);
    user.setEmail(email);
    user.setFirstName("Test");
    user.setLastName("User");
    user.setBreakGlass(breakGlass);
    return user;
  }

  @Test
  void anOrdinaryAccountRaisesNoAlertAtAll() {
    try (MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class)) {

      BreakGlassAlertCommand.recordLogin(user(1L, "someone@example.com", false), "10.0.0.1", "s1", "form");

      audit.verify(() -> SaveAuditEventCommand.recordAuthentication(anyString(), anyString(), anyLong(),
          anyString(), anyString(), anyString(), anyString()), never());
      loadUser.verify(() -> LoadUserCommand.loadUsersHoldingCapability(anyString()), never());
    }
  }

  @Test
  void aNullUserIsIgnored() {
    // The failed-login path looks the account up by an attempted address, which frequently
    // resolves to nothing at all
    try (MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      assertDoesNotThrow(
          () -> BreakGlassAlertCommand.recordFailedLogin((User) null, "10.0.0.1", "s1", "bad password"));
      audit.verify(() -> SaveAuditEventCommand.recordAuthentication(anyString(), anyString(), anyLong(),
          anyString(), anyString(), anyString(), anyString()), never());
    }
  }

  @Test
  void aBlankAttemptedAddressIsIgnored() {
    // The address overload is what LoginWidget calls; a submitted-empty form must not become a
    // database lookup, let alone an alert
    try (MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      assertDoesNotThrow(() -> BreakGlassAlertCommand.recordFailedLogin("", "10.0.0.1", "s1", "blank"));
      assertDoesNotThrow(() -> BreakGlassAlertCommand.recordFailedLogin((String) null, "10.0.0.1", "s1", "blank"));
      audit.verify(() -> SaveAuditEventCommand.recordAuthentication(anyString(), anyString(), anyLong(),
          anyString(), anyString(), anyString(), anyString()), never());
    }
  }

  @Test
  void aBreakGlassLoginIsAudited() {
    try (MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class)) {
      loadUser.when(() -> LoadUserCommand.loadUsersHoldingCapability(anyString()))
          .thenReturn(Collections.emptyList());

      BreakGlassAlertCommand.recordLogin(user(1L, "admin@example.com", true), "10.0.0.1", "s1", "form");

      audit.verify(() -> SaveAuditEventCommand.recordAuthentication(
          org.mockito.ArgumentMatchers.eq(BreakGlassAlertCommand.EVENT_LOGIN_SUCCESS),
          org.mockito.ArgumentMatchers.eq("success"), anyLong(), anyString(), anyString(), anyString(), anyString()));
    }
  }

  @Test
  void aFailedBreakGlassAttemptIsAuditedAsAFailure() {
    try (MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class)) {
      loadUser.when(() -> LoadUserCommand.loadUsersHoldingCapability(anyString()))
          .thenReturn(Collections.emptyList());

      BreakGlassAlertCommand.recordFailedLogin(user(1L, "admin@example.com", true), "10.0.0.1", "s1", "bad password");

      audit.verify(() -> SaveAuditEventCommand.recordAuthentication(
          org.mockito.ArgumentMatchers.eq(BreakGlassAlertCommand.EVENT_LOGIN_FAILURE),
          org.mockito.ArgumentMatchers.eq("failure"), anyLong(), anyString(), anyString(), anyString(), anyString()));
    }
  }

  @Test
  void aFailingAuditWriteDoesNotPropagate() {
    // The whole point of the account is that it works when other things are broken -- a failure to
    // notify must never become a failure to authenticate
    try (MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class)) {
      audit.when(() -> SaveAuditEventCommand.recordAuthentication(anyString(), anyString(), anyLong(),
          any(), any(), any(), any())).thenThrow(new RuntimeException("audit database is down"));
      loadUser.when(() -> LoadUserCommand.loadUsersHoldingCapability(anyString()))
          .thenReturn(Collections.emptyList());

      assertDoesNotThrow(
          () -> BreakGlassAlertCommand.recordLogin(user(1L, "admin@example.com", true), "10.0.0.1", "s1", "form"));
    }
  }

  @Test
  void aFailingRecipientLookupDoesNotPropagate() {
    try (MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class)) {
      loadUser.when(() -> LoadUserCommand.loadUsersHoldingCapability(anyString()))
          .thenThrow(new RuntimeException("capability lookup failed"));

      assertDoesNotThrow(
          () -> BreakGlassAlertCommand.recordLogin(user(1L, "admin@example.com", true), "10.0.0.1", "s1", "form"));
    }
  }

  @Test
  void theBreakGlassAccountIsNotToldAboutItsOwnUse() {
    // It is the only holder here, so after excluding itself there is nobody left and no mail is
    // attempted -- exercised through EmailCommand never being reached
    try (MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<com.simisinc.platform.application.email.EmailCommand> emailCommand =
            mockStatic(com.simisinc.platform.application.email.EmailCommand.class)) {
      User breakGlass = user(1L, "admin@example.com", true);
      loadUser.when(() -> LoadUserCommand.loadUsersHoldingCapability(anyString()))
          .thenReturn(Arrays.asList(breakGlass));

      BreakGlassAlertCommand.recordLogin(breakGlass, "10.0.0.1", "s1", "form");

      emailCommand.verify(com.simisinc.platform.application.email.EmailCommand::prepareNewEmail, never());
    }
  }
}
