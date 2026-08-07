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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.LoadWebPageCommand;
import com.simisinc.platform.application.cms.MutateLayoutCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.MediaAsset;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.MediaAssetRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.presentation.widgets.cms.ImageWidget;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

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
  void appliesTheAssetServingUrlToTheTargetWidgetPreference() throws Exception {
    // asset.getStoragePath() ("/assets/photo.jpg" here) is the internal FileSystemCommand-relative
    // disk path, not a browser URL (see MediaApiController#handleServeFile's javadoc) -- the
    // persisted preference must be the real serving route built from the asset's id, matching the
    // exact path convention the browse grid's own thumbnails already use, or the widget's <img>
    // renders broken the moment a client actually loads it.
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
      assertEquals("/visual-editor/media/file/asset-123", prefs.get(ImageWidget.IMAGE_URL_PREF_KEY).asText());
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
    when(request.getParameter("storagePath")).thenReturn("media-library/2026/07/26/photo.jpg");

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

  // ── handleCreateAsset's storagePath NOT NULL fix (issue #773) ─────────────────────────────

  @Test
  void createAssetRejectsAMissingStoragePath() throws Exception {
    // media_assets.storage_path is NOT NULL; before the #773 fix this endpoint never set it at all,
    // so any real call would have failed the insert with a generic 500 instead of a clear 400.
    UserSession userSession = loggedInSession("admin");
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.USER)).thenReturn(userSession);
    when(request.getSession()).thenReturn(session);
    when(request.getPathInfo()).thenReturn(null);
    when(request.getParameter("assetName")).thenReturn("photo.jpg");

    Recorded result = runWidgetUpdate(request);

    assertEquals(400, result.status);
    assertTrue(result.body.contains("storagePath is required"));
  }

  @Test
  void createAssetDefaultsAltTextToTheAssetNameWhenNotProvided() throws Exception {
    // alt_text is also NOT NULL; rather than adding a second required param, default it.
    UserSession userSession = loggedInSession("admin");
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.USER)).thenReturn(userSession);
    when(request.getSession()).thenReturn(session);
    when(request.getPathInfo()).thenReturn(null);
    when(request.getParameter("assetName")).thenReturn("photo.jpg");
    when(request.getParameter("storagePath")).thenReturn("media-library/2026/07/26/photo.jpg");

    ArgumentCaptor<MediaAsset> savedCaptor = ArgumentCaptor.forClass(MediaAsset.class);
    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class)) {
      assets.when(() -> MediaAssetRepository.save(savedCaptor.capture())).thenAnswer(inv -> {
        MediaAsset saved = inv.getArgument(0);
        saved.setId(42);
        return saved;
      });

      Recorded result = runWidgetUpdate(request);

      assertEquals(200, result.status);
      assertEquals("media-library/2026/07/26/photo.jpg", savedCaptor.getValue().getStoragePath());
      assertEquals("photo.jpg", savedCaptor.getValue().getAltText());
    }
  }

  @Test
  void createAssetRejectsAUserWithNoEditPermission() throws Exception {
    // Regression test for the missing permission check (issue #773 follow-up): handleCreateAsset is
    // reached from doPost for any path other than /upload or /widget-update -- unlike those two, it
    // never checked EditorPermissionCommand.canEditContent, so any logged-in user of any role could
    // create arbitrary media_assets rows. A session with no granting role must be rejected with a 403
    // before any parameter validation or repository call.
    UserSession userSession = loggedInSession(); // no roles at all
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.USER)).thenReturn(userSession);
    when(request.getSession()).thenReturn(session);
    when(request.getPathInfo()).thenReturn(null);
    when(request.getParameter("assetName")).thenReturn("photo.jpg");
    when(request.getParameter("storagePath")).thenReturn("media-library/2026/07/26/photo.jpg");

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class)) {
      Recorded result = runWidgetUpdate(request);

      assertEquals(403, result.status);
      assertTrue(result.body.contains("Insufficient permission"));
      assets.verify(() -> MediaAssetRepository.save(any()), never());
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

  // ── handleUpload (issue #773) ──────────────────────────────────────────────────────────────
  //
  // FileSystemCommand and LoadSitePropertyCommand are mocked statically so these tests are
  // hermetic (no real CMS_PATH/site-property/DB dependency) while still exercising the real
  // disk write through a JUnit @TempDir, matching this file's existing convention of mocking the
  // static collaborators (MediaAssetRepository, LoadWebPageCommand, MutateLayoutCommand) above.

  // Real format signatures ("magic bytes"), used to build fixture payloads that the content-sniffing
  // fix (issue #773) will actually recognize, mirroring the constants in MediaApiController itself.

  private static byte[] concat(byte[] prefix, byte[] rest) {
    byte[] all = new byte[prefix.length + rest.length];
    System.arraycopy(prefix, 0, all, 0, prefix.length);
    System.arraycopy(rest, 0, all, prefix.length, rest.length);
    return all;
  }

  private static byte[] pngBytes(String payload) {
    byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    return concat(signature, payload.getBytes());
  }

  private static byte[] jpegBytes(String payload) {
    byte[] signature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    return concat(signature, payload.getBytes());
  }

  private static Part mockFilePart(String filename, byte[] content) throws Exception {
    Part part = mock(Part.class);
    when(part.getSubmittedFileName()).thenReturn(filename);
    when(part.getSize()).thenReturn((long) content.length);
    doAnswer(inv -> {
      String path = inv.getArgument(0);
      Files.write(Paths.get(path), content);
      return null;
    }).when(part).write(anyString());
    return part;
  }

  private static HttpServletRequest uploadRequestWithSession(UserSession userSession, String token, Part filePart)
      throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.USER)).thenReturn(userSession);
    when(request.getSession()).thenReturn(session);
    when(request.getPathInfo()).thenReturn("/upload");
    when(request.getParameter("token")).thenReturn(token);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    if (filePart != null) {
      when(request.getPart("file")).thenReturn(filePart);
    }
    return request;
  }

  private static void stubFileSystemCommand(MockedStatic<FileSystemCommand> fsc, Path tempDir) {
    fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString() + "/");
    fsc.when(() -> FileSystemCommand.generateFileServerSubPath(anyString())).thenReturn("media-library/2026/07/31/");
    fsc.when(() -> FileSystemCommand.generateUniqueFilename(anyLong())).thenReturn("unique-name");
    fsc.when(() -> FileSystemCommand.cleanExtension(anyString())).thenAnswer(inv -> inv.getArgument(0));
    fsc.when(() -> FileSystemCommand.resolveWithinRoot(anyString(), anyString())).thenAnswer(inv -> {
      String root = inv.getArgument(0);
      String rel = inv.getArgument(1);
      File f = new File(root, rel);
      f.getParentFile().mkdirs();
      return f;
    });
  }

  @Test
  void uploadRejectsWhenNotAuthenticated() throws Exception {
    HttpServletRequest request = uploadRequestWithSession(null, "whatever", null);

    Recorded result = runWidgetUpdate(request);

    assertEquals(401, result.status);
    assertTrue(result.body.contains("Not authenticated"));
  }

  @Test
  void uploadRejectsWhenUserLacksEditPermission() throws Exception {
    // No session role at all is below even the content-editor tier canEditContent requires.
    UserSession userSession = loggedInSession();
    HttpServletRequest request = uploadRequestWithSession(userSession, userSession.getFormToken(), null);

    Recorded result = runWidgetUpdate(request);

    assertEquals(403, result.status);
    assertTrue(result.body.contains("Insufficient permission"));
  }

  @Test
  void uploadRejectsCsrfTokenMismatch() throws Exception {
    UserSession userSession = loggedInSession("content-editor");
    HttpServletRequest request = uploadRequestWithSession(userSession, "not-the-real-token", null);

    Recorded result = runWidgetUpdate(request);

    assertEquals(403, result.status);
    assertTrue(result.body.contains("Session expired"));
  }

  @Test
  void uploadRejectsWhenNoFilePartPresent() throws Exception {
    UserSession userSession = loggedInSession("content-editor");
    HttpServletRequest request = uploadRequestWithSession(userSession, userSession.getFormToken(), null);

    try (MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class)) {
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);

      Recorded result = runWidgetUpdate(request);

      assertEquals(400, result.status);
      assertTrue(result.body.contains("A file is required"));
    }
  }

  @Test
  void uploadRejectsABlockedDangerousExtension() throws Exception {
    // Reaches ValidateFileCommand.checkFileExtension before any FileSystemCommand call is made.
    UserSession userSession = loggedInSession("content-editor");
    Part filePart = mockFilePart("payload.exe", "not really an executable".getBytes());
    HttpServletRequest request = uploadRequestWithSession(userSession, userSession.getFormToken(), filePart);

    try (MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class)) {
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);

      Recorded result = runWidgetUpdate(request);

      assertEquals(400, result.status);
      assertTrue(result.body.contains("not allowed"));
    }
  }

  @Test
  void uploadRejectsAnOversizedFile() throws Exception {
    UserSession userSession = loggedInSession("content-editor");
    Part filePart = mock(Part.class);
    when(filePart.getSubmittedFileName()).thenReturn("huge.jpg");
    when(filePart.getSize()).thenReturn(11_000_000L); // over the 10MB default
    HttpServletRequest request = uploadRequestWithSession(userSession, userSession.getFormToken(), filePart);

    try (MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class)) {
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);

      Recorded result = runWidgetUpdate(request);

      assertEquals(400, result.status);
      assertTrue(result.body.contains("exceeds the maximum"));
      verify(filePart, never()).write(anyString());
    }
  }

  @Test
  void uploadRejectsAFileWhoseDetectedTypeIsNotImageOrPdf(@TempDir Path tempDir) throws Exception {
    UserSession userSession = loggedInSession("content-editor");
    Part filePart = mockFilePart("notes.txt", "just plain text".getBytes());
    HttpServletRequest request = uploadRequestWithSession(userSession, userSession.getFormToken(), filePart);

    try (MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class);
         MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class)) {
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      stubFileSystemCommand(fsc, tempDir);

      Recorded result = runWidgetUpdate(request);

      assertEquals(400, result.status);
      assertTrue(result.body.contains("Only image and PDF files are supported"));
      assertFalse(Files.exists(tempDir.resolve("media-library/2026/07/31/unique-name.txt")),
          "the rejected file should not be left on disk");
    }
  }

  @Test
  void uploadRejectsAFileRenamedToMatchAnAllowedExtensionButWhoseBytesAreNotThatFormat(@TempDir Path tempDir)
      throws Exception {
    // Regression test for the content-type-validation bypass (issue #773 follow-up): a plain-text
    // file renamed to ".png" must be rejected based on its real header bytes, not accepted because
    // the extension is on the image allowlist and Files.probeContentType falls back to guessing from
    // the extension on a minimal-JDK/container deployment with no mime-magic database.
    UserSession userSession = loggedInSession("content-editor");
    byte[] notActuallyPng = "#!/bin/sh\necho not a real png".getBytes();
    Part filePart = mockFilePart("totally-a-real.png", notActuallyPng);
    HttpServletRequest request = uploadRequestWithSession(userSession, userSession.getFormToken(), filePart);

    try (MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class);
         MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
         MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class)) {
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      stubFileSystemCommand(fsc, tempDir);

      Recorded result = runWidgetUpdate(request);

      assertEquals(400, result.status);
      assertTrue(result.body.contains("Only image and PDF files are supported"));
      assertFalse(Files.exists(tempDir.resolve("media-library/2026/07/31/unique-name.png")),
          "the disguised file should not be left on disk");
      assets.verify(() -> MediaAssetRepository.save(any()), never());
    }
  }

  @Test
  void uploadSavesTheFileAndCreatesARealMediaAssetRecord(@TempDir Path tempDir) throws Exception {
    UserSession userSession = loggedInSession("content-editor");
    // Real JPEG magic bytes (0xFF 0xD8 0xFF) -- the content-sniffing fix inspects actual bytes, so a
    // fake payload like "fake-jpeg-bytes" would now be correctly rejected as not a real JPEG.
    byte[] content = jpegBytes("real jpeg payload");
    Part filePart = mockFilePart("photo.jpg", content);
    HttpServletRequest request = uploadRequestWithSession(userSession, userSession.getFormToken(), filePart);

    ArgumentCaptor<MediaAsset> savedCaptor = ArgumentCaptor.forClass(MediaAsset.class);
    try (MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class);
         MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
         MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class)) {
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      stubFileSystemCommand(fsc, tempDir);
      assets.when(() -> MediaAssetRepository.save(savedCaptor.capture())).thenAnswer(inv -> {
        MediaAsset saved = inv.getArgument(0);
        saved.setId(99);
        return saved;
      });

      Recorded result = runWidgetUpdate(request);

      assertEquals(200, result.status);
      assertTrue(result.body.contains("\"success\":true"));

      MediaAsset saved = savedCaptor.getValue();
      assertEquals("photo.jpg", saved.getAssetName());
      assertEquals("image", saved.getAssetType());
      assertEquals((long) content.length, saved.getFileSizeBytes());
      assertEquals("media-library/2026/07/31/unique-name.jpg", saved.getStoragePath());
      assertEquals(userSession.getUserId(), saved.getCreatedBy());
      assertTrue(Files.exists(tempDir.resolve("media-library/2026/07/31/unique-name.jpg")),
          "the uploaded bytes should actually be on disk at the stored path");
    }
  }

  @Test
  void uploadCleansUpTheDiskFileWhenTheDatabaseSaveFails(@TempDir Path tempDir) throws Exception {
    UserSession userSession = loggedInSession("content-editor");
    // Real PNG magic bytes -- see uploadSavesTheFileAndCreatesARealMediaAssetRecord for why.
    Part filePart = mockFilePart("photo.png", pngBytes("real png payload"));
    HttpServletRequest request = uploadRequestWithSession(userSession, userSession.getFormToken(), filePart);

    try (MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class);
         MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
         MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class)) {
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      stubFileSystemCommand(fsc, tempDir);
      // Simulates a DB-write failure after the disk write already succeeded.
      assets.when(() -> MediaAssetRepository.save(any())).thenReturn(null);

      Recorded result = runWidgetUpdate(request);

      assertEquals(500, result.status);
      assertTrue(result.body.contains("Failed to save media asset"));
      assertFalse(Files.exists(tempDir.resolve("media-library/2026/07/31/unique-name.png")),
          "an orphaned file with no discoverable DB row should be cleaned up, not left behind");
    }
  }

  @Test
  void uploadReturns500AndDoesNotAttemptASaveWhenTheDiskWriteFails(@TempDir Path tempDir) throws Exception {
    UserSession userSession = loggedInSession("content-editor");
    Part filePart = mock(Part.class);
    when(filePart.getSubmittedFileName()).thenReturn("photo.jpg");
    when(filePart.getSize()).thenReturn(1024L);
    doAnswer(inv -> {
      throw new IOException("simulated disk-full error");
    }).when(filePart).write(anyString());
    HttpServletRequest request = uploadRequestWithSession(userSession, userSession.getFormToken(), filePart);

    try (MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class);
         MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
         MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class)) {
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      stubFileSystemCommand(fsc, tempDir);

      Recorded result = runWidgetUpdate(request);

      assertEquals(500, result.status);
      assertTrue(result.body.contains("The file could not be saved"));
      assets.verify(() -> MediaAssetRepository.save(any()), never());
    }
  }

  // ── GET /visual-editor/media/file/{assetId} (handleServeFile, issue #773) ──────────────────

  private static class CapturingOutputStream extends ServletOutputStream {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
      // not needed for this synchronous test double
    }

    @Override
    public void write(int b) {
      buffer.write(b);
    }

    byte[] toByteArray() {
      return buffer.toByteArray();
    }
  }

  @Test
  void serveFileReturns404WhenAssetDoesNotExist() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getPathInfo()).thenReturn("/file/missing-asset");

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class)) {
      assets.when(() -> MediaAssetRepository.findByAssetId("missing-asset")).thenReturn(null);

      Recorded result = runGet(request);

      assertEquals(404, result.status);
      assertTrue(result.body.contains("Media file not found"));
    }
  }

  @Test
  void serveFileReturns404WhenFileMissingFromDisk(@TempDir Path tempDir) throws Exception {
    MediaAsset asset = new MediaAsset();
    asset.setAssetId("asset-123");
    asset.setStoragePath("media-library/2026/07/31/does-not-exist.jpg");

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getPathInfo()).thenReturn("/file/asset-123");

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class);
         MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class)) {
      assets.when(() -> MediaAssetRepository.findByAssetId("asset-123")).thenReturn(asset);
      fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString() + "/");
      fsc.when(() -> FileSystemCommand.resolveWithinRoot(anyString(), anyString()))
          .thenAnswer(inv -> new File((String) inv.getArgument(0), (String) inv.getArgument(1)));

      Recorded result = runGet(request);

      assertEquals(404, result.status);
      assertTrue(result.body.contains("Media file not found"));
    }
  }

  @Test
  void serveFileStreamsTheAssetWithInlineHeadersAndRequiresNoLogin(@TempDir Path tempDir) throws Exception {
    byte[] fileBytes = "fake-image-bytes".getBytes();
    Path assetFile = tempDir.resolve("media-library/2026/07/31/unique-name.jpg");
    Files.createDirectories(assetFile.getParent());
    Files.write(assetFile, fileBytes);

    MediaAsset asset = new MediaAsset();
    asset.setAssetId("asset-123");
    asset.setStoragePath("media-library/2026/07/31/unique-name.jpg");
    asset.setMimeType("image/jpeg");

    // Deliberately no session/getSession() stubbing at all -- this route must not require login.
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getPathInfo()).thenReturn("/file/asset-123");

    HttpServletResponse response = mock(HttpServletResponse.class);
    CapturingOutputStream out = new CapturingOutputStream();
    when(response.getOutputStream()).thenReturn(out);
    Map<String, String> headers = new HashMap<>();
    doAnswer(inv -> {
      headers.put(inv.getArgument(0), inv.getArgument(1));
      return null;
    }).when(response).setHeader(anyString(), anyString());
    String[] contentType = new String[1];
    doAnswer(inv -> {
      contentType[0] = inv.getArgument(0);
      return null;
    }).when(response).setContentType(anyString());

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class);
         MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class)) {
      assets.when(() -> MediaAssetRepository.findByAssetId("asset-123")).thenReturn(asset);
      fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString() + "/");
      fsc.when(() -> FileSystemCommand.resolveWithinRoot(anyString(), anyString()))
          .thenAnswer(inv -> new File((String) inv.getArgument(0), (String) inv.getArgument(1)));

      new MediaApiController().doGet(request, response);
    }

    assertEquals("image/jpeg", contentType[0]);
    assertEquals("nosniff", headers.get("X-Content-Type-Options"));
    assertNull(headers.get("Content-Disposition"), "inline media headers never force a download");
    assertArrayEquals(fileBytes, out.toByteArray());
    verify(response, never()).setStatus(anyInt());
  }

  // ── doDelete (Media Library delete feature) ────────────────────────────────────────────────

  private static HttpServletRequest deleteRequestWithSession(UserSession userSession, String assetId, String token) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.USER)).thenReturn(userSession);
    when(request.getSession()).thenReturn(session);
    when(request.getPathInfo()).thenReturn(assetId == null ? null : "/" + assetId);
    when(request.getParameter("token")).thenReturn(token);
    return request;
  }

  private static Recorded runDelete(HttpServletRequest request) throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));
    Recorded recorded = new Recorded();
    doAnswer(inv -> {
      recorded.status = inv.getArgument(0);
      return null;
    }).when(response).setStatus(anyInt());

    new MediaApiController().doDelete(request, response);
    recorded.body = body.toString();
    return recorded;
  }

  @Test
  void deleteRejectsWhenNotAuthenticated() throws Exception {
    HttpServletRequest request = deleteRequestWithSession(null, "asset-123", "whatever");

    Recorded result = runDelete(request);

    assertEquals(401, result.status);
    assertTrue(result.body.contains("Not authenticated"));
  }

  @Test
  void deleteRejectsWhenUserLacksEditPermission() throws Exception {
    UserSession userSession = loggedInSession(); // no roles at all
    HttpServletRequest request = deleteRequestWithSession(userSession, "asset-123", userSession.getFormToken());

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class)) {
      Recorded result = runDelete(request);

      assertEquals(403, result.status);
      assertTrue(result.body.contains("Insufficient permission"));
      assets.verify(() -> MediaAssetRepository.softDelete(anyLong()), never());
    }
  }

  @Test
  void deleteRejectsCsrfTokenMismatch() throws Exception {
    UserSession userSession = loggedInSession("admin");
    HttpServletRequest request = deleteRequestWithSession(userSession, "asset-123", "not-the-real-token");

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class)) {
      Recorded result = runDelete(request);

      assertEquals(403, result.status);
      assertTrue(result.body.contains("Invalid or missing CSRF token"));
      assets.verify(() -> MediaAssetRepository.softDelete(anyLong()), never());
    }
  }

  @Test
  void deleteRejectsAMissingAssetId() throws Exception {
    UserSession userSession = loggedInSession("admin");
    HttpServletRequest request = deleteRequestWithSession(userSession, null, userSession.getFormToken());

    Recorded result = runDelete(request);

    assertEquals(400, result.status);
    assertTrue(result.body.contains("assetId is required"));
  }

  @Test
  void deleteReturns404ForAnUnknownAssetId() throws Exception {
    UserSession userSession = loggedInSession("admin");
    HttpServletRequest request = deleteRequestWithSession(userSession, "does-not-exist", userSession.getFormToken());

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class)) {
      assets.when(() -> MediaAssetRepository.findByAssetId("does-not-exist")).thenReturn(null);

      Recorded result = runDelete(request);

      assertEquals(404, result.status);
      assertTrue(result.body.contains("Media asset not found"));
      assets.verify(() -> MediaAssetRepository.softDelete(anyLong()), never());
    }
  }

  @Test
  void deleteSoftDeletesTheAssetAndReturnsSuccess() throws Exception {
    UserSession userSession = loggedInSession("admin");
    HttpServletRequest request = deleteRequestWithSession(userSession, "asset-123", userSession.getFormToken());

    MediaAsset asset = new MediaAsset();
    asset.setId(42L);
    asset.setAssetId("asset-123");

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class)) {
      assets.when(() -> MediaAssetRepository.findByAssetId("asset-123")).thenReturn(asset);
      assets.when(() -> MediaAssetRepository.softDelete(42L)).thenReturn(true);

      Recorded result = runDelete(request);

      assertEquals(200, result.status);
      assertTrue(result.body.contains("\"success\":true"));
      assets.verify(() -> MediaAssetRepository.softDelete(42L));
    }
  }

  @Test
  void deleteReturns500WhenTheSoftDeleteFails() throws Exception {
    UserSession userSession = loggedInSession("admin");
    HttpServletRequest request = deleteRequestWithSession(userSession, "asset-123", userSession.getFormToken());

    MediaAsset asset = new MediaAsset();
    asset.setId(42L);
    asset.setAssetId("asset-123");

    try (MockedStatic<MediaAssetRepository> assets = mockStatic(MediaAssetRepository.class)) {
      assets.when(() -> MediaAssetRepository.findByAssetId("asset-123")).thenReturn(asset);
      assets.when(() -> MediaAssetRepository.softDelete(42L)).thenReturn(false);

      Recorded result = runDelete(request);

      assertEquals(500, result.status);
      assertTrue(result.body.contains("Failed to delete media asset"));
    }
  }
}
