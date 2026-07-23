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

package com.simisinc.platform.application.oauth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.presentation.controller.SessionConstants;

import jakarta.servlet.http.HttpSession;

/**
 * Verifies the OAuth login-CSRF guard: a callback's {@code state} is accepted only when it matches
 * the value bound to the session that started the flow, and the state is single-use. Without this,
 * an attacker could present a state+code for their own account and log a victim into it.
 *
 * @author Liz Houser
 * @created 7/23/2026
 */
class OAuthAuthorizationCommandTest {

  @Test
  void aMatchingSessionStateIsAcceptedAndConsumed() {
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.OAUTH_STATE)).thenReturn("ABC123");

    assertTrue(OAuthAuthorizationCommand.consumeValidState(session, "ABC123"));
    // Single use: the state is cleared so it cannot be replayed
    verify(session).removeAttribute(SessionConstants.OAUTH_STATE);
  }

  @Test
  void aStateNotBoundToThisSessionIsRejected() {
    // Login-CSRF case: the victim's session never generated a state, but the attacker supplies one
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.OAUTH_STATE)).thenReturn(null);

    assertFalse(OAuthAuthorizationCommand.consumeValidState(session, "attacker-supplied-state"));
  }

  @Test
  void aMismatchedStateIsRejectedAndStillCleared() {
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.OAUTH_STATE)).thenReturn("expected-state");

    assertFalse(OAuthAuthorizationCommand.consumeValidState(session, "some-other-state"));
    verify(session).removeAttribute(SessionConstants.OAUTH_STATE);
  }

  @Test
  void aNullSessionIsRejected() {
    assertFalse(OAuthAuthorizationCommand.consumeValidState(null, "anything"));
  }

  @Test
  void aBlankStateIsRejected() {
    HttpSession session = mock(HttpSession.class);
    assertFalse(OAuthAuthorizationCommand.consumeValidState(session, ""));
    assertFalse(OAuthAuthorizationCommand.consumeValidState(session, null));
  }
}
