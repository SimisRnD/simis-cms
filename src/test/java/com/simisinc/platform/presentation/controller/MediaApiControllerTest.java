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
import static org.mockito.ArgumentMatchers.anyLong;
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
import java.time.LocalDateTime;
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
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.presentation.widgets.cms.ImageWidget;

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
    // The endpoint's real contract (post-#772 follow-up): the only prefKey it will ever honor is
    // the image widget's own imageUrl preference. Individual tests below that need to exercise the
    // rejection path (wrong prefKey / wrong widget type) override this explicitly.
    params.put("prefKey", ImageWidget.IMAGE_URL_PREF_KEY);
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

  private static HttpServletRequest getRequestWithSession(UserSession userSession, Map<String, String> params) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.USER)).thenReturn(userSession);
    when(request.getSession()).thenReturn(session);
    for (Map.Entry<String, String> e : params.entrySet()) {
      when(request.getParameter(e.getKey())).thenReturn(e.getValue());
    }
    return request;
  }

  private static Recorded runGet(HttpServletRequest request) throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));
    Recorded recorded = new Recorded();
    doAnswer(inv -> {
      recorded.status = inv.getArgument(0);
      return null;
    }).when(response).setStatus(anyInt());

    new MediaApiController().doGet(request, response);
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
      mutate.when(() -> MutateLayoutCommand.getWidgetName(webPage, 0, 1, 2))
          .thenReturn(ImageWidget.WIDGET_NAME);

      Recorded result = runWidgetUpdate(request);

      assertEquals(200, result.status);
      assertTrue(result.body.contains("\"success\":true"));

      ArgumentCaptor<String> prefsJsonCaptor = ArgumentCaptor.forClass(String.class);
      mutate.verify(() -> MutateLayoutCommand.setWidgetPreferences(
          eq(webPage), eq(0), eq(1), eq(2), prefsJsonCaptor.capture(), eq(userSession.getUserId())));
      JsonNode prefs = MAPPER.readTree(prefsJsonCaptor.getValue());
      assertEquals("/assets/photo.jpg", prefs.get(ImageWidget.IMAGE_URL_PREF_KEY).asText());
    }
  }

  // ── issue #772 follow-up: the server must independently verify the target is an image widget ──
  //
  // The visual editor's "Choose image from Media Library" trigger only renders client-side for a
  // widget whose data-editor-widget-name is "image" (platform-editor.js) -- that gate is UI-only.
  // A prior live-verify pass crafted the identical request shape the JS sends (valid session, valid
  // CSRF token, a real assetId/pagePath) but aimed at a *different* widget's structural position with
  // that widget's own real preference key, and the server accepted it (200) and silently overwrote
  // that widget's real preference with an image path. These two tests recreate that attack and its
  // sibling (right widget, wrong prefKey), and deliberately do NOT mock MutateLayoutCommand or
  // WebPageRepository as no-ops -- they must prove the *real* getWidgetName resolution against real
  // page XML rejects the request, not merely that some mock was configured to reject it.

  @Test
  void rejectsWidgetUpdateTargetingANonImageWidgetAndLeavesItsRealPreferenceUntouched() throws Exception {
    UserSession userSession = loggedInSession("admin");
    Map<String, String> params = baseParams(userSession.getFormToken());
    // The attacker reuses a real, non-image widget's own real preference key ("url" on a
    // remoteContent widget) at the structural position that widget actually occupies.
    params.put("prefKey", "url");
    params.put("sectionIdx", "0");
    params.put("columnIdx", "0");
    params.put("widgetIdx", "0");
    HttpServletRequest request = requestWithSession(userSession, params);

    MediaAsset asset = new MediaAsset();
    asset.setAssetId("asset-123");
    asset.setStoragePath("/assets/photo.jpg");

    WebPage webPage = new WebPage();
    webPage.setId(55);
    webPage.setLink("/about");
    webPage.setPageXml(
        "<page>\n" +
        "  <section>\n" +
        "    <column class=\"small-12 cell\">\n" +
        "      <widget name=\"remoteContent\">\n" +
        "        <url>https://legit.example.com/feed.json</url>\n" +
        "      </widget>\n" +
        "    </column>\n" +
        "  </section>\n" +
        "</page>");

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class);
         MockedStatic<LoadWebPageCommand> pages = mockStatic(LoadWebPageCommand.class);
         MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class)) {
      assets.when(() -> MediaAssetRepository.findByAssetId("asset-123")).thenReturn(asset);
      pages.when(() -> LoadWebPageCommand.loadByLink("/about")).thenReturn(webPage);

      Recorded result = runWidgetUpdate(request);

      assertEquals(400, result.status);
      assertTrue(result.body.contains("not an image widget"),
          "must be rejected for targeting the wrong widget type, not silently applied");

      // The real setWidgetPreferences -- and the WebPageRepository.save it would call to persist --
      // must never be reached: rejection has to happen before any write is attempted.
      repo.verify(() -> WebPageRepository.save(any()), never());

      // The widget actually on the page, as it would really render, is completely unchanged: no
      // draft was created, and the published XML still holds the original url.
      assertTrue(webPage.getDraftPageXml() == null || webPage.getDraftPageXml().isEmpty(),
          "no draft mutation should have been written");
      assertTrue(webPage.getPageXml().contains("<url>https://legit.example.com/feed.json</url>"),
          "the remoteContent widget's real url preference must be completely unchanged");
    }
  }

  @Test
  void rejectsWidgetUpdateWithAPrefKeyOtherThanImageUrlEvenAgainstARealImageWidget() throws Exception {
    // Sibling of the above: even when the target genuinely is an image widget, this endpoint must
    // only ever be able to touch its imageUrl -- never some other preference of that same widget
    // (e.g. altText) via the same request shape.
    UserSession userSession = loggedInSession("admin");
    Map<String, String> params = baseParams(userSession.getFormToken());
    params.put("prefKey", "altText");
    params.put("sectionIdx", "0");
    params.put("columnIdx", "0");
    params.put("widgetIdx", "0");
    HttpServletRequest request = requestWithSession(userSession, params);

    MediaAsset asset = new MediaAsset();
    asset.setAssetId("asset-123");
    asset.setStoragePath("/assets/photo.jpg");

    WebPage webPage = new WebPage();
    webPage.setId(55);
    webPage.setLink("/about");
    webPage.setPageXml(
        "<page>\n" +
        "  <section>\n" +
        "    <column class=\"small-12 cell\">\n" +
        "      <widget name=\"image\">\n" +
        "        <imageUrl>/media/original.png</imageUrl>\n" +
        "        <altText>A real, curated description</altText>\n" +
        "      </widget>\n" +
        "    </column>\n" +
        "  </section>\n" +
        "</page>");

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class);
         MockedStatic<LoadWebPageCommand> pages = mockStatic(LoadWebPageCommand.class);
         MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class)) {
      assets.when(() -> MediaAssetRepository.findByAssetId("asset-123")).thenReturn(asset);
      pages.when(() -> LoadWebPageCommand.loadByLink("/about")).thenReturn(webPage);

      Recorded result = runWidgetUpdate(request);

      assertEquals(400, result.status);
      assertTrue(result.body.contains(ImageWidget.IMAGE_URL_PREF_KEY),
          "must be rejected for using the wrong prefKey, not silently applied to altText");

      repo.verify(() -> WebPageRepository.save(any()), never());
      assertTrue(webPage.getPageXml().contains("<altText>A real, curated description</altText>"),
          "the widget's real altText must be completely unchanged");
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
      mutate.when(() -> MutateLayoutCommand.getWidgetName(webPage, 0, 1, 2))
          .thenReturn(ImageWidget.WIDGET_NAME);
      mutate.when(() -> MutateLayoutCommand.setWidgetPreferences(
          any(), anyInt(), anyInt(), anyInt(), anyString(), anyLong()))
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
      mutate.when(() -> MutateLayoutCommand.getWidgetName(webPage, 0, 1, 2))
          .thenReturn(ImageWidget.WIDGET_NAME);

      new MediaApiController().doPost(request, response);

      verify(response, never()).setStatus(anyInt());
    }
  }

  // ── Jackson JSR-310 regression coverage (issue #771) ───────────────────────────────────────
  //
  // None of the tests above ever set createdAt on a MediaAsset, so they never exercised Jackson
  // actually serializing a LocalDateTime -- without the jsr310 module registered on the shared
  // ObjectMapper, that throws InvalidDefinitionException the instant a non-null LocalDateTime
  // hits the wire, which the surrounding catch(Exception) turns into a generic 500. These three
  // cover every response path in this controller that can serialize a MediaAsset.

  @Test
  void listEndpointSerializesAssetsWithNonNullTimestampsWithoutThrowing() throws Exception {
    UserSession userSession = loggedInSession("admin");
    HttpServletRequest request = getRequestWithSession(userSession, new HashMap<>());

    MediaAsset asset = new MediaAsset();
    asset.setAssetId("asset-123");
    asset.setAssetName("photo.jpg");
    asset.setCreatedAt(LocalDateTime.now());

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class)) {
      assets.when(() -> MediaAssetRepository.findAll(null)).thenReturn(List.of(asset));

      Recorded result = runGet(request);

      assertEquals(200, result.status);
      assertTrue(result.body.contains("\"success\":true"));
      assertFalse(result.body.contains("Failed to retrieve media assets"));
    }
  }

  @Test
  void widgetUpdateSerializesAnAssetWithNonNullTimestampsWithoutThrowing() throws Exception {
    UserSession userSession = loggedInSession("admin");
    HttpServletRequest request = requestWithSession(userSession, baseParams(userSession.getFormToken()));
    MediaAsset asset = new MediaAsset();
    asset.setAssetId("asset-123");
    asset.setStoragePath("/assets/photo.jpg");
    asset.setCreatedAt(LocalDateTime.now());
    WebPage webPage = new WebPage();
    webPage.setId(55);
    webPage.setLink("/about");

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class);
         MockedStatic<LoadWebPageCommand> pages = mockStatic(LoadWebPageCommand.class);
         MockedStatic<MutateLayoutCommand> mutate = mockStatic(MutateLayoutCommand.class)) {
      assets.when(() -> MediaAssetRepository.findByAssetId("asset-123")).thenReturn(asset);
      pages.when(() -> LoadWebPageCommand.loadByLink("/about")).thenReturn(webPage);
      mutate.when(() -> MutateLayoutCommand.getWidgetName(webPage, 0, 1, 2))
          .thenReturn(ImageWidget.WIDGET_NAME);

      Recorded result = runWidgetUpdate(request);

      assertEquals(200, result.status);
      assertTrue(result.body.contains("\"success\":true"));
      assertFalse(result.body.contains("Failed to process request"));
    }
  }

  @Test
  void createAssetEndpointSerializesTheStampedCreatedAtWithoutThrowing() throws Exception {
    // handleCreateAsset always stamps createdAt = LocalDateTime.now() on the new asset before
    // saving, then echoes that same object back in the response -- so this path always hits the
    // Jackson gap, with no test setup required to force a non-null timestamp.
    UserSession userSession = loggedInSession("admin");
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.USER)).thenReturn(userSession);
    when(request.getSession()).thenReturn(session);
    when(request.getPathInfo()).thenReturn(null);
    when(request.getParameter("assetName")).thenReturn("photo.jpg");
    when(request.getParameter("assetType")).thenReturn("image");
    when(request.getParameter("mimeType")).thenReturn("image/jpeg");

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class)) {
      assets.when(() -> MediaAssetRepository.save(any())).thenAnswer(inv -> {
        MediaAsset saved = inv.getArgument(0);
        saved.setId(42);
        return saved;
      });

      Recorded result = runWidgetUpdate(request);

      assertEquals(200, result.status);
      assertTrue(result.body.contains("\"success\":true"));
      assertFalse(result.body.contains("Failed to process request"));
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
