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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.LoadWebPageCommand;
import com.simisinc.platform.application.cms.MutateLayoutCommand;
import com.simisinc.platform.domain.model.MediaAsset;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.MediaAssetRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Covers the real {@code handleWidgetUpdate} logic added to fix the no-op "Widget update
 * initiated" lie: authentication, builder-tier permission, CSRF token, request validation, and
 * the delegation to {@link MutateLayoutCommand#setWidgetPreferences} that actually persists the
 * selected asset onto the target widget's preference.
 *
 * @author elizabeth houser
 */
class MediaApiControllerTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static UserSession loggedInSession(String... roleCodes) {
    User user = new User();
    user.setId(7L);
    List<Role> roles = new java.util.ArrayList<>();
    for (String code : roleCodes) {
      roles.add(new Role(code, code));
    }
    user.setRoleList(roles);
    UserSession userSession = new UserSession();
    userSession.login(user);
    return userSession;
  }

  private static HttpServletRequest requestWithSession(UserSession userSession, Map<String, String> params) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.USER)).thenReturn(userSession);
    when(request.getSession()).thenReturn(session);
    when(request.getPathInfo()).thenReturn("/widget-update");
    for (Map.Entry<String, String> e : params.entrySet()) {
      when(request.getParameter(e.getKey())).thenReturn(e.getValue());
    }
    return request;
  }

  private static Map<String, String> baseParams(String token) {
    Map<String, String> params = new HashMap<>();
    params.put("assetId", "asset-123");
    params.put("pagePath", "/about");
    params.put("prefKey", "url");
    params.put("sectionIdx", "0");
    params.put("columnIdx", "1");
    params.put("widgetIdx", "2");
    params.put("token", token);
    return params;
  }

  /** Captures the JSON body written to the response, along with whatever status was set (200 if none). */
  private static class Recorded {
    String body;
    int status = 200;
  }

  private static Recorded runWidgetUpdate(HttpServletRequest request) throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));
    Recorded recorded = new Recorded();
    doAnswer(inv -> {
      recorded.status = inv.getArgument(0);
      return null;
    }).when(response).setStatus(anyInt());

    new MediaApiController().doPost(request, response);
    recorded.body = body.toString();
    return recorded;
  }

  @Test
  void rejectsWhenNotAuthenticated() throws Exception {
    HttpServletRequest request = requestWithSession(null, baseParams("whatever"));

    Recorded result = runWidgetUpdate(request);

    assertEquals(401, result.status);
    assertTrue(result.body.contains("Not authenticated"));
  }

  @Test
  void rejectsWhenUserLacksBuilderPermission() throws Exception {
    // content-editor may edit content but is deliberately excluded from layout-builder capability
    UserSession userSession = loggedInSession("content-editor");
    HttpServletRequest request = requestWithSession(userSession, baseParams(userSession.getFormToken()));

    Recorded result = runWidgetUpdate(request);

    assertEquals(403, result.status);
    assertTrue(result.body.contains("Insufficient permission"));
  }

  @Test
  void rejectsCsrfTokenMismatch() throws Exception {
    UserSession userSession = loggedInSession("admin");
    HttpServletRequest request = requestWithSession(userSession, baseParams("not-the-real-token"));

    Recorded result = runWidgetUpdate(request);

    assertEquals(403, result.status);
    assertTrue(result.body.contains("Session expired"));
  }

  @Test
  void rejectsMissingRequiredParams() throws Exception {
    UserSession userSession = loggedInSession("admin");
    Map<String, String> params = baseParams(userSession.getFormToken());
    params.remove("prefKey");
    HttpServletRequest request = requestWithSession(userSession, params);

    Recorded result = runWidgetUpdate(request);

    assertEquals(400, result.status);
    assertTrue(result.body.contains("required"));
  }

  @Test
  void rejectsNonIntegerPosition() throws Exception {
    UserSession userSession = loggedInSession("admin");
    Map<String, String> params = baseParams(userSession.getFormToken());
    params.put("sectionIdx", "not-a-number");
    HttpServletRequest request = requestWithSession(userSession, params);

    Recorded result = runWidgetUpdate(request);

    assertEquals(400, result.status);
    assertTrue(result.body.contains("integers"));
  }

  @Test
  void returns404WhenAssetDoesNotExist() throws Exception {
    UserSession userSession = loggedInSession("admin");
    HttpServletRequest request = requestWithSession(userSession, baseParams(userSession.getFormToken()));

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class)) {
      assets.when(() -> MediaAssetRepository.findByAssetId("asset-123")).thenReturn(null);

      Recorded result = runWidgetUpdate(request);

      assertEquals(404, result.status);
      assertTrue(result.body.contains("Media asset not found"));
    }
  }

  @Test
  void returns400WhenPageDoesNotExist() throws Exception {
    UserSession userSession = loggedInSession("admin");
    HttpServletRequest request = requestWithSession(userSession, baseParams(userSession.getFormToken()));
    MediaAsset asset = new MediaAsset();
    asset.setAssetId("asset-123");
    asset.setStoragePath("/assets/photo.jpg");

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class);
         MockedStatic<LoadWebPageCommand> pages = mockStatic(LoadWebPageCommand.class)) {
      assets.when(() -> MediaAssetRepository.findByAssetId("asset-123")).thenReturn(asset);
      pages.when(() -> LoadWebPageCommand.loadByLink("/about")).thenReturn(null);

      Recorded result = runWidgetUpdate(request);

      assertEquals(400, result.status);
      assertTrue(result.body.contains("Page not found"));
    }
  }

  @Test
  void appliesTheAssetStoragePathToTheTargetWidgetPreference() throws Exception {
    UserSession userSession = loggedInSession("content-manager");
    HttpServletRequest request = requestWithSession(userSession, baseParams(userSession.getFormToken()));
    MediaAsset asset = new MediaAsset();
    asset.setAssetId("asset-123");
    asset.setStoragePath("/assets/photo.jpg");
    WebPage webPage = new WebPage();
    webPage.setId(55);
    webPage.setLink("/about");

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class);
         MockedStatic<LoadWebPageCommand> pages = mockStatic(LoadWebPageCommand.class);
         MockedStatic<MutateLayoutCommand> mutate = mockStatic(MutateLayoutCommand.class)) {
      assets.when(() -> MediaAssetRepository.findByAssetId("asset-123")).thenReturn(asset);
      pages.when(() -> LoadWebPageCommand.loadByLink("/about")).thenReturn(webPage);

      Recorded result = runWidgetUpdate(request);

      assertEquals(200, result.status);
      assertTrue(result.body.contains("\"success\":true"));

      ArgumentCaptor<String> prefsJsonCaptor = ArgumentCaptor.forClass(String.class);
      mutate.verify(() -> MutateLayoutCommand.setWidgetPreferences(
          eq(webPage), eq(0), eq(1), eq(2), prefsJsonCaptor.capture()));
      JsonNode prefs = MAPPER.readTree(prefsJsonCaptor.getValue());
      assertEquals("/assets/photo.jpg", prefs.get("url").asText());
    }
  }

  @Test
  void surfacesADataExceptionFromTheMutationAsABadRequest() throws Exception {
    UserSession userSession = loggedInSession("admin");
    HttpServletRequest request = requestWithSession(userSession, baseParams(userSession.getFormToken()));
    MediaAsset asset = new MediaAsset();
    asset.setAssetId("asset-123");
    asset.setStoragePath("/assets/photo.jpg");
    WebPage webPage = new WebPage();
    webPage.setId(55);
    webPage.setLink("/about");

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class);
         MockedStatic<LoadWebPageCommand> pages = mockStatic(LoadWebPageCommand.class);
         MockedStatic<MutateLayoutCommand> mutate = mockStatic(MutateLayoutCommand.class)) {
      assets.when(() -> MediaAssetRepository.findByAssetId("asset-123")).thenReturn(asset);
      pages.when(() -> LoadWebPageCommand.loadByLink("/about")).thenReturn(webPage);
      mutate.when(() -> MutateLayoutCommand.setWidgetPreferences(any(), anyInt(), anyInt(), anyInt(), anyString()))
          .thenThrow(new DataException("Widget index 2 at 0:1 out of range (1 widget(s))"));

      Recorded result = runWidgetUpdate(request);

      assertEquals(400, result.status);
      assertTrue(result.body.contains("out of range"));
    }
  }

  @Test
  void neverCallsSetStatusOnTheHappyPathSoTheServletDefaultOf200Applies() throws Exception {
    UserSession userSession = loggedInSession("admin");
    HttpServletRequest request = requestWithSession(userSession, baseParams(userSession.getFormToken()));
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
    MediaAsset asset = new MediaAsset();
    asset.setAssetId("asset-123");
    asset.setStoragePath("/assets/photo.jpg");
    WebPage webPage = new WebPage();
    webPage.setId(55);

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class);
         MockedStatic<LoadWebPageCommand> pages = mockStatic(LoadWebPageCommand.class);
         MockedStatic<MutateLayoutCommand> mutate = mockStatic(MutateLayoutCommand.class)) {
      assets.when(() -> MediaAssetRepository.findByAssetId("asset-123")).thenReturn(asset);
      pages.when(() -> LoadWebPageCommand.loadByLink("/about")).thenReturn(webPage);

      new MediaApiController().doPost(request, response);

      verify(response, never()).setStatus(anyInt());
    }
  }

  @Test
  void doesNotAuthenticateAgainstALiteralUserSessionAttribute() throws Exception {
    // Regression guard for the fixed auth bug: the session attribute holds a UserSession under
    // SessionConstants.USER ("userSession"), never a User under the literal string "user".
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpSession session = mock(HttpSession.class);
    when(request.getSession()).thenReturn(session);
    when(session.getAttribute("user")).thenReturn(new User());
    when(session.getAttribute(SessionConstants.USER)).thenReturn(null);
    when(request.getPathInfo()).thenReturn("/widget-update");
    for (Map.Entry<String, String> e : baseParams("x").entrySet()) {
      when(request.getParameter(e.getKey())).thenReturn(e.getValue());
    }

    Recorded result = runWidgetUpdate(request);

    assertEquals(401, result.status);
    assertFalse(result.body.contains("success\":true"));
  }
}
