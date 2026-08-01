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

package com.simisinc.platform.presentation.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.domain.model.User;

/**
 * Tests the {@link AuditEventCommand#record(HttpServletRequest, UserSession, String, String, String,
 * String, String, String, String)} overload -- the bridge used by servlets that have a raw request and
 * user session but no {@link WidgetContext} (e.g. {@code PageServlet}, {@code MediaApiController}).
 *
 * @author elizabeth houser
 */
class AuditEventCommandServletOverloadTest {

  @Test
  void resolvesActorSessionAndUsernameFromUserSession() {
    UserSession userSession = mock(UserSession.class);
    when(userSession.getUserId()).thenReturn(42L);
    when(userSession.getSessionId()).thenReturn("sess-123");
    when(userSession.getIpAddress()).thenReturn("198.51.100.7");
    User actor = new User();
    actor.setEmail("editor@example.com");
    when(userSession.getUser()).thenReturn(actor);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn("198.51.100.7");

    try (MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      AuditEventCommand.record(request, userSession, AuditEventCommand.CONTENT, "page_layout.reorder",
          AuditEventCommand.SUCCESS, "web_page", "7", "/some-page", "s=0 c=0");

      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(AuditEventCommand.CONTENT, "page_layout.reorder",
          AuditEventCommand.SUCCESS, 42L, "editor@example.com", "198.51.100.7", "sess-123",
          "web_page", "7", "/some-page", "s=0 c=0"));
    }
  }

  @Test
  void prefersRequestRemoteAddrOverUserSessionIp() {
    // The two can differ behind a proxy/load balancer -- the live request address must win, matching
    // the WidgetContext overload's own behavior.
    UserSession userSession = mock(UserSession.class);
    when(userSession.getUserId()).thenReturn(-1L);
    when(userSession.getIpAddress()).thenReturn("203.0.113.9");
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn("198.51.100.7");

    try (MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      AuditEventCommand.record(request, userSession, AuditEventCommand.CONTENT, "page_layout.addSection",
          AuditEventCommand.SUCCESS, "web_page", "7", "/some-page", null);

      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(AuditEventCommand.CONTENT,
          "page_layout.addSection", AuditEventCommand.SUCCESS, -1L, null, "198.51.100.7",
          null, "web_page", "7", "/some-page", null));
    }
  }

  @Test
  void handlesNullUserSessionWithoutThrowing() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn("198.51.100.7");

    try (MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      AuditEventCommand.record(request, null, AuditEventCommand.CONTENT, "page_layout.reorder",
          AuditEventCommand.FAILURE, "web_page", "7", "/some-page", "no session");

      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(AuditEventCommand.CONTENT, "page_layout.reorder",
          AuditEventCommand.FAILURE, -1L, null, "198.51.100.7", null, "web_page", "7", "/some-page", "no session"));
    }
  }

  @Test
  void skipsUserLookupForAnUnauthenticatedActor() {
    // getUser() lazily loads from the database -- must not be called for a not-logged-in actor
    // (userId == -1), matching the WidgetContext overload's own guard.
    UserSession userSession = mock(UserSession.class);
    when(userSession.getUserId()).thenReturn(-1L);

    try (MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      AuditEventCommand.record(null, userSession, AuditEventCommand.CONTENT, "page_layout.reorder",
          AuditEventCommand.FAILURE, "web_page", "7", "/some-page", null);
    }
    verify(userSession, never()).getUser();
  }

  @Test
  void neverThrowsWhenActorResolutionFails() {
    UserSession userSession = mock(UserSession.class);
    when(userSession.getUserId()).thenThrow(new RuntimeException("boom"));
    HttpServletRequest request = mock(HttpServletRequest.class);

    try (MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      AuditEventCommand.record(request, userSession, AuditEventCommand.CONTENT, "page_layout.reorder",
          AuditEventCommand.FAILURE, "web_page", "7", "/some-page", "details");

      // Auditing is a side effect that must never break the caller -- the record is still written,
      // just with an unknown actor (-1), rather than propagating the exception.
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(AuditEventCommand.CONTENT, "page_layout.reorder",
          AuditEventCommand.FAILURE, -1L, null, null, null, "web_page", "7", "/some-page", "details"));
    }
  }
}
