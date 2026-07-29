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

package com.simisinc.platform.presentation.widgets.admin;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;

import java.net.ConnectException;

import javax.mail.AuthenticationFailedException;

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.ImageHtmlEmail;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.email.EmailCommand;
import com.simisinc.platform.domain.model.User;

/**
 * @author SimIS Inc.
 */
class SendMailWidgetTest extends WidgetBase {

  private static User adminUser() {
    User user = new User();
    user.setId(1L);
    user.setEmail("admin@example.com");
    user.setFirstName("Admin");
    user.setLastName("User");
    return user;
  }

  /**
   * A real ImageHtmlEmail (not a Mockito mock) so the production addTo/setSubject/setMsg calls run their
   * normal validation; only send() is overridden to succeed or throw on command.
   */
  private static class StubEmail extends ImageHtmlEmail {
    private final EmailException toThrow;

    StubEmail(EmailException toThrow) {
      this.toThrow = toThrow;
    }

    @Override
    public String send() throws EmailException {
      if (toThrow != null) {
        throw toThrow;
      }
      return "stub-message-id";
    }
  }

  @Test
  void postSendsATestEmailAndReportsSuccess() throws Exception {
    try (MockedStatic<LoadUserCommand> loadUserCommand = mockStatic(LoadUserCommand.class);
        MockedStatic<EmailCommand> emailCommand = mockStatic(EmailCommand.class)) {
      loadUserCommand.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(adminUser());
      emailCommand.when(EmailCommand::prepareNewEmail).thenReturn(new StubEmail(null));

      new SendMailWidget().post(widgetContext);
    }

    Assertions.assertNotNull(widgetContext.getSuccessMessage());
    Assertions.assertNull(widgetContext.getErrorMessage());
  }

  @Test
  void postCategorizesAnAuthenticationFailureWithoutLeakingTheRawMessage() throws Exception {
    EmailException toThrow = new EmailException(new AuthenticationFailedException(
        "535 5.7.8 Username and Password not accepted, secret-app-password-xyz"));

    try (MockedStatic<LoadUserCommand> loadUserCommand = mockStatic(LoadUserCommand.class);
        MockedStatic<EmailCommand> emailCommand = mockStatic(EmailCommand.class)) {
      loadUserCommand.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(adminUser());
      emailCommand.when(EmailCommand::prepareNewEmail).thenReturn(new StubEmail(toThrow));

      new SendMailWidget().post(widgetContext);
    }

    Assertions.assertNull(widgetContext.getSuccessMessage());
    String errorMessage = widgetContext.getErrorMessage();
    Assertions.assertNotNull(errorMessage);
    Assertions.assertTrue(errorMessage.contains("auth"));
    Assertions.assertFalse(errorMessage.contains("secret-app-password-xyz"));
  }

  @Test
  void postCategorizesAConnectionFailureWithoutLeakingTheRawMessage() throws Exception {
    EmailException toThrow = new EmailException(new ConnectException("Connection refused to internal-relay.simis.local:25"));

    try (MockedStatic<LoadUserCommand> loadUserCommand = mockStatic(LoadUserCommand.class);
        MockedStatic<EmailCommand> emailCommand = mockStatic(EmailCommand.class)) {
      loadUserCommand.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(adminUser());
      emailCommand.when(EmailCommand::prepareNewEmail).thenReturn(new StubEmail(toThrow));

      new SendMailWidget().post(widgetContext);
    }

    String errorMessage = widgetContext.getErrorMessage();
    Assertions.assertNotNull(errorMessage);
    Assertions.assertTrue(errorMessage.contains("connect"));
    Assertions.assertFalse(errorMessage.contains("internal-relay.simis.local"));
  }

  @Test
  void postFallsBackToAnUnknownCategoryWithoutLeakingTheRawMessage() throws Exception {
    EmailException toThrow = new EmailException(
        "Something unexpected involving admin@simisinc.com and other internal details");

    try (MockedStatic<LoadUserCommand> loadUserCommand = mockStatic(LoadUserCommand.class);
        MockedStatic<EmailCommand> emailCommand = mockStatic(EmailCommand.class)) {
      loadUserCommand.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(adminUser());
      emailCommand.when(EmailCommand::prepareNewEmail).thenReturn(new StubEmail(toThrow));

      new SendMailWidget().post(widgetContext);
    }

    String errorMessage = widgetContext.getErrorMessage();
    Assertions.assertNotNull(errorMessage);
    Assertions.assertTrue(errorMessage.contains("unknown"));
    Assertions.assertFalse(errorMessage.contains("admin@simisinc.com"));
  }
}
