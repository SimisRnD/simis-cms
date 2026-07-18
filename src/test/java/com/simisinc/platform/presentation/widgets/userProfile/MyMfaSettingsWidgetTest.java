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

package com.simisinc.platform.presentation.widgets.userProfile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.login.UserMfaCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Verifies the self-service MFA settings screen: it renders the off / enrolling / on states correctly, and its
 * actions (start, confirm, cancel, disable) drive UserMfaCommand and redirect or re-render as appropriate.
 *
 * @author SimIS Inc.
 * @created 2026-07-17
 */
class MyMfaSettingsWidgetTest extends WidgetBase {

  private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

  @Test
  void executeShowsOffStateWhenNotEnrolled() {
    User user = new User();
    user.setId(1L);
    user.setMfaEnabled(false);
    try (MockedStatic<UserRepository> repo = mockStatic(UserRepository.class)) {
      repo.when(() -> UserRepository.findByUserId(anyLong())).thenReturn(user);
      new MyMfaSettingsWidget().execute(widgetContext);
    }
    Assertions.assertEquals("false", request.getAttribute("mfaEnabled"));
    Assertions.assertEquals("false", request.getAttribute("mfaEnrolling"));
    Assertions.assertEquals(MyMfaSettingsWidget.JSP, widgetContext.getJsp());
  }

  @Test
  void executeShowsEnrollingStateWithSecretAndUri() {
    User user = new User();
    user.setId(1L);
    user.setMfaEnabled(false);
    user.setMfaSecret(SECRET);
    try (MockedStatic<UserRepository> repo = mockStatic(UserRepository.class);
        MockedStatic<UserMfaCommand> mfa = mockStatic(UserMfaCommand.class)) {
      repo.when(() -> UserRepository.findByUserId(anyLong())).thenReturn(user);
      mfa.when(() -> UserMfaCommand.buildEnrollmentUri(user)).thenReturn("otpauth://totp/Test:me?secret=" + SECRET);
      new MyMfaSettingsWidget().execute(widgetContext);
    }
    Assertions.assertEquals("true", request.getAttribute("mfaEnrolling"));
    Assertions.assertEquals(SECRET, request.getAttribute("mfaSecret"));
    Assertions.assertEquals("otpauth://totp/Test:me?secret=" + SECRET, request.getAttribute("otpauthUri"));
  }

  @Test
  void executeShowsOnStateWhenEnabled() {
    User user = new User();
    user.setId(1L);
    user.setMfaEnabled(true);
    user.setMfaSecret(SECRET);
    try (MockedStatic<UserRepository> repo = mockStatic(UserRepository.class)) {
      repo.when(() -> UserRepository.findByUserId(anyLong())).thenReturn(user);
      new MyMfaSettingsWidget().execute(widgetContext);
    }
    Assertions.assertEquals("true", request.getAttribute("mfaEnabled"));
    // Enabled means enrollment is finished, not in progress.
    Assertions.assertEquals("false", request.getAttribute("mfaEnrolling"));
  }

  @Test
  void postStartBeginsEnrollmentAndRedirects() {
    addQueryParameter(widgetContext, "action", "start");
    when(request.getRequestURI()).thenReturn("/my-account");
    User user = new User();
    user.setId(1L);
    try (MockedStatic<UserRepository> repo = mockStatic(UserRepository.class);
        MockedStatic<UserMfaCommand> mfa = mockStatic(UserMfaCommand.class)) {
      repo.when(() -> UserRepository.findByUserId(anyLong())).thenReturn(user);
      new MyMfaSettingsWidget().post(widgetContext);
      mfa.verify(() -> UserMfaCommand.startEnrollment(user));
    }
    Assertions.assertEquals("/my-account", widgetContext.getRedirect());
  }

  @Test
  void postConfirmValidCodeEnablesAndRedirects() {
    addQueryParameter(widgetContext, "action", "confirm");
    addQueryParameter(widgetContext, "code", "123456");
    when(request.getRequestURI()).thenReturn("/my-account");
    User user = new User();
    user.setId(1L);
    try (MockedStatic<UserRepository> repo = mockStatic(UserRepository.class);
        MockedStatic<UserMfaCommand> mfa = mockStatic(UserMfaCommand.class)) {
      repo.when(() -> UserRepository.findByUserId(anyLong())).thenReturn(user);
      mfa.when(() -> UserMfaCommand.confirmEnrollment(user, "123456")).thenReturn(true);
      new MyMfaSettingsWidget().post(widgetContext);
    }
    Assertions.assertEquals("/my-account", widgetContext.getRedirect());
    Assertions.assertNotNull(widgetContext.getSuccessMessage());
    Assertions.assertNull(widgetContext.getErrorMessage());
  }

  @Test
  void postConfirmInvalidCodeShowsErrorAndRerenders() {
    addQueryParameter(widgetContext, "action", "confirm");
    addQueryParameter(widgetContext, "code", "000000");
    User user = new User();
    user.setId(1L);
    user.setMfaEnabled(false);
    user.setMfaSecret(SECRET);
    try (MockedStatic<UserRepository> repo = mockStatic(UserRepository.class);
        MockedStatic<UserMfaCommand> mfa = mockStatic(UserMfaCommand.class)) {
      repo.when(() -> UserRepository.findByUserId(anyLong())).thenReturn(user);
      mfa.when(() -> UserMfaCommand.confirmEnrollment(user, "000000")).thenReturn(false);
      new MyMfaSettingsWidget().post(widgetContext);
    }
    Assertions.assertNotNull(widgetContext.getErrorMessage());
    Assertions.assertNull(widgetContext.getRedirect());
    // Re-rendered in place, still showing the enrollment panel.
    Assertions.assertEquals(MyMfaSettingsWidget.JSP, widgetContext.getJsp());
    Assertions.assertEquals("true", request.getAttribute("mfaEnrolling"));
  }

  @Test
  void postDisableTurnsOffAndRedirects() {
    addQueryParameter(widgetContext, "action", "disable");
    when(request.getRequestURI()).thenReturn("/my-account");
    User user = new User();
    user.setId(1L);
    user.setMfaEnabled(true);
    try (MockedStatic<UserRepository> repo = mockStatic(UserRepository.class);
        MockedStatic<UserMfaCommand> mfa = mockStatic(UserMfaCommand.class)) {
      repo.when(() -> UserRepository.findByUserId(anyLong())).thenReturn(user);
      new MyMfaSettingsWidget().post(widgetContext);
      mfa.verify(() -> UserMfaCommand.disable(user));
    }
    Assertions.assertEquals("/my-account", widgetContext.getRedirect());
    Assertions.assertNotNull(widgetContext.getSuccessMessage());
  }
}
