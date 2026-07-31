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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.EditorPermissionCommand;
import com.simisinc.platform.application.cms.LoadWebPageCommand;
import com.simisinc.platform.application.cms.MutateLayoutCommand;
import com.simisinc.platform.domain.model.MediaAsset;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.MediaAssetRepository;
import com.simisinc.platform.presentation.widgets.cms.ImageWidget;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.UUID;

/**
 * P5.2: Media Library API endpoints for the visual editor panel
 *
 * GET /visual-editor/media?search=query&limit=20&offset=0 - List media assets with pagination and search
 * POST /visual-editor/media - Create stub asset record
 * POST /visual-editor/media/upload - Handle file upload from drag-and-drop or file picker (not yet implemented)
 * POST /visual-editor/media/widget-update - Apply a selected asset to a widget's preference on a page's draft
 * layout. Requires builder-tier permission and the session CSRF token, and identifies the target
 * widget the same way {@link PageServlet}'s {@code mutateDraftLayout} action does -- by structural
 * position, not by a render-time {@code widgetContext.uniqueId} -- so it can safely delegate to
 * {@link MutateLayoutCommand#setWidgetPreferences}. Before delegating, it independently re-resolves
 * the widget actually at that position via {@link MutateLayoutCommand#getWidgetName} and confirms it
 * is really an {@link ImageWidget#WIDGET_NAME} and that {@code prefKey} is really
 * {@link ImageWidget#IMAGE_URL_PREF_KEY} -- the visual editor's client-side "this is an image
 * widget" gate is UI-only, so this is the check that actually stops a crafted request from
 * overwriting some other widget's unrelated preference (issue #772 follow-up). Params: assetId,
 * pagePath, sectionIdx, columnIdx, widgetIdx, prefKey, token.
 *
 * @author claude
 * @created 7/26/26
 */
@WebServlet(name = "MediaApi", urlPatterns = {"/visual-editor/media", "/visual-editor/media/*"})
public class MediaApiController extends HttpServlet {

  private static Log LOG = LogFactory.getLog(MediaApiController.class);
  // MediaAsset.createdAt/updatedAt/deletedAt are LocalDateTime; without the JSR-310 module
  // registered, Jackson has no serializer for that type and throws InvalidDefinitionException
  // the instant a response includes a MediaAsset with a non-null timestamp -- every list/create/
  // widget-update response path below. WRITE_DATES_AS_TIMESTAMPS is disabled so the field comes
  // out as a normal ISO-8601 string instead of a [year,month,day,...] array.
  private static ObjectMapper objectMapper = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    response.setContentType("application/json");

    try {
      UserSession userSession = (UserSession) request.getSession().getAttribute(SessionConstants.USER);
      if (userSession == null || !userSession.isLoggedIn()) {
        response.setStatus(401);
        Map<String, Object> result = new HashMap<>();
        result.put("error", "Not authenticated");
        response.getWriter().write(objectMapper.writeValueAsString(result));
        return;
      }

      String search = request.getParameter("search");
      String limit = request.getParameter("limit");
      String offset = request.getParameter("offset");

      int pageLimit = limit != null ? Integer.parseInt(limit) : 20;
      int pageOffset = offset != null ? Integer.parseInt(offset) : 0;

      List<MediaAsset> assets = MediaAssetRepository.findAll(null);

      if (search != null && !search.isEmpty()) {
        String searchLower = search.toLowerCase();
        assets = assets.stream()
            .filter(a -> a.getAssetName() != null && a.getAssetName().toLowerCase().contains(searchLower))
            .toList();
      }

      int total = assets.size();
      List<MediaAsset> paginated = assets.stream()
          .skip(pageOffset)
          .limit(pageLimit)
          .toList();

      Map<String, Object> result = new HashMap<>();
      result.put("success", true);
      result.put("assets", paginated);
      result.put("total", total);
      result.put("limit", pageLimit);
      result.put("offset", pageOffset);

      response.getWriter().write(objectMapper.writeValueAsString(result));

    } catch (Exception e) {
      LOG.error("Error retrieving media assets: " + e.getMessage(), e);
      response.setStatus(500);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "Failed to retrieve media assets");
      try {
        response.getWriter().write(objectMapper.writeValueAsString(result));
      } catch (Exception ex) {
        LOG.error("Error writing error response", ex);
      }
    }
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    String pathInfo = request.getPathInfo();
    response.setContentType("application/json");

    try {
      UserSession userSession = (UserSession) request.getSession().getAttribute(SessionConstants.USER);
      if (userSession == null || !userSession.isLoggedIn()) {
        response.setStatus(401);
        Map<String, Object> result = new HashMap<>();
        result.put("error", "Not authenticated");
        response.getWriter().write(objectMapper.writeValueAsString(result));
        return;
      }

      if ("/upload".equals(pathInfo)) {
        handleUpload(request, response, userSession);
      } else if ("/widget-update".equals(pathInfo)) {
        handleWidgetUpdate(request, response, userSession);
      } else {
        handleCreateAsset(request, response, userSession);
      }

    } catch (Exception e) {
      LOG.error("Error processing media request: " + e.getMessage(), e);
      response.setStatus(500);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "Failed to process request");
      try {
        response.getWriter().write(objectMapper.writeValueAsString(result));
      } catch (Exception ex) {
        LOG.error("Error writing error response", ex);
      }
    }
  }

  private void handleCreateAsset(HttpServletRequest request, HttpServletResponse response, UserSession userSession)
      throws IOException {
    String assetName = request.getParameter("assetName");
    String assetType = request.getParameter("assetType");
    String mimeType = request.getParameter("mimeType");
    String altText = request.getParameter("altText");
    String tags = request.getParameter("tags");

    if (assetName == null || assetName.isEmpty()) {
      response.setStatus(400);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "assetName is required");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    MediaAsset asset = new MediaAsset();
    asset.setAssetId(UUID.randomUUID().toString());
    asset.setAssetName(assetName);
    asset.setAssetType(assetType != null ? assetType : "unknown");
    asset.setMimeType(mimeType);
    asset.setAltText(altText);
    asset.setTags(tags);
    asset.setCreatedBy(userSession.getUserId());
    asset.setCreatedAt(LocalDateTime.now());
    asset.setFileSizeBytes(0);

    MediaAsset saved = MediaAssetRepository.save(asset);
    if (saved == null) {
      response.setStatus(500);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "Failed to save media asset");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    Map<String, Object> result = new HashMap<>();
    result.put("success", true);
    result.put("asset", saved);
    response.getWriter().write(objectMapper.writeValueAsString(result));
  }

  private void handleUpload(HttpServletRequest request, HttpServletResponse response, UserSession userSession)
      throws IOException {
    Map<String, Object> result = new HashMap<>();
    result.put("success", false);
    result.put("error", "File upload not yet implemented");
    response.setStatus(501);
    response.getWriter().write(objectMapper.writeValueAsString(result));
  }

  /**
   * Applies a selected media asset to a widget's preference on a page's draft layout.
   *
   * <p>Unlike a {@code widgetContext.uniqueId} (which is only meaningful mid-render, since it is
   * derived from a role/group-filtered widget count computed live by {@link WebContainerCommand}),
   * the visual editor's layout builder already knows and sends the widget's structural position --
   * {@code sectionIdx}/{@code columnIdx}/{@code widgetIdx} -- exactly as {@link PageServlet}'s
   * existing {@code mutateDraftLayout} action does for every other layout-builder mutation
   * (addWidget, removeWidget, setWidgetPreferences, etc.). This reuses that same, already-trusted
   * contract instead of re-deriving a position from an opaque id: it is safer (no duplicated
   * traversal logic to drift out of sync with the real renderer) and it is what
   * {@link MutateLayoutCommand#setWidgetPreferences} already requires. Requires the builder-tier
   * permission and the session's CSRF form token, matching every other draft-layout mutation.
   *
   * <p>Unlike every other {@code setWidgetPreferences} caller, this endpoint's entire contract is
   * narrow: it only ever sets an image widget's {@code imageUrl}. platform-editor.js's "Choose image
   * from Media Library" trigger only renders for a widget whose {@code data-editor-widget-name} is
   * {@code "image"}, but that is a client-side heuristic an attacker simply need not honor -- nothing
   * stops a request naming any {@code widgetIdx} with any {@code prefKey}. So before delegating, this
   * method independently resolves the real widget at {@code sectionIdx}/{@code columnIdx}/
   * {@code widgetIdx} from the page's own XML (via {@link MutateLayoutCommand#getWidgetName}, the
   * same structural resolution {@code setWidgetPreferences} itself uses) and refuses to proceed
   * unless that widget really is {@link ImageWidget#WIDGET_NAME} and {@code prefKey} really is
   * {@link ImageWidget#IMAGE_URL_PREF_KEY} -- otherwise a crafted request could silently overwrite an
   * unrelated widget's real preference (e.g. a remoteContent widget's own {@code url} preference)
   * with an image path.
   */
  private void handleWidgetUpdate(HttpServletRequest request, HttpServletResponse response, UserSession userSession)
      throws IOException {
    if (!EditorPermissionCommand.canBuildLayout(userSession)) {
      response.setStatus(403);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "Insufficient permission to modify the page layout");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    String token = request.getParameter("token");
    if (token == null || !token.equals(userSession.getFormToken())) {
      LOG.warn("widget-update CSRF token mismatch from " + request.getRemoteAddr());
      response.setStatus(403);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "Session expired");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    String assetId = request.getParameter("assetId");
    String pagePath = request.getParameter("pagePath");
    String prefKey = request.getParameter("prefKey");
    String sectionIdxParam = request.getParameter("sectionIdx");
    String columnIdxParam = request.getParameter("columnIdx");
    String widgetIdxParam = request.getParameter("widgetIdx");

    if (StringUtils.isBlank(assetId) || StringUtils.isBlank(pagePath) || StringUtils.isBlank(prefKey)
        || StringUtils.isBlank(sectionIdxParam) || StringUtils.isBlank(columnIdxParam)
        || StringUtils.isBlank(widgetIdxParam)) {
      response.setStatus(400);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "assetId, pagePath, prefKey, sectionIdx, columnIdx, and widgetIdx are required");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    int sectionIdx;
    int columnIdx;
    int widgetIdx;
    try {
      sectionIdx = Integer.parseInt(sectionIdxParam);
      columnIdx = Integer.parseInt(columnIdxParam);
      widgetIdx = Integer.parseInt(widgetIdxParam);
    } catch (NumberFormatException e) {
      response.setStatus(400);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "sectionIdx, columnIdx, and widgetIdx must be integers");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    MediaAsset asset = MediaAssetRepository.findByAssetId(assetId);
    if (asset == null) {
      response.setStatus(404);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "Media asset not found");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    WebPage webPage = LoadWebPageCommand.loadByLink(pagePath);
    if (webPage == null || webPage.getId() == -1) {
      response.setStatus(400);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "Page not found");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    try {
      // The "Choose image from Media Library" trigger is only rendered client-side for a widget
      // whose data-editor-widget-name is "image" (platform-editor.js) -- that is a UI convenience,
      // not an authorization check, and a request naming any sectionIdx/columnIdx/widgetIdx with any
      // prefKey reaches this far regardless. Re-resolve the widget that is *actually* at this
      // position from the page's own XML -- not from anything the client sent -- and refuse to
      // proceed unless it really is an image widget and prefKey really is its imageUrl preference.
      // Without this, a crafted request could silently overwrite an unrelated widget's real
      // preference (e.g. a remoteContent widget's own "url") with an image path.
      String targetWidgetName = MutateLayoutCommand.getWidgetName(webPage, sectionIdx, columnIdx, widgetIdx);
      if (!ImageWidget.WIDGET_NAME.equals(targetWidgetName)) {
        throw new DataException("Target widget is not an image widget (found '" + targetWidgetName + "')");
      }
      if (!ImageWidget.IMAGE_URL_PREF_KEY.equals(prefKey)) {
        throw new DataException(
            "prefKey must be '" + ImageWidget.IMAGE_URL_PREF_KEY + "' when targeting an image widget");
      }

      Map<String, String> prefs = new HashMap<>();
      prefs.put(prefKey, asset.getStoragePath());
      String prefsJson = objectMapper.writeValueAsString(prefs);
      MutateLayoutCommand.setWidgetPreferences(webPage, sectionIdx, columnIdx, widgetIdx, prefsJson,
          userSession.getUserId());
    } catch (DataException e) {
      LOG.warn("widget-update rejected for " + pagePath + " " + sectionIdx + ":" + columnIdx + ":" + widgetIdx
          + ": " + e.getMessage());
      response.setStatus(400);
      Map<String, Object> result = new HashMap<>();
      result.put("error", e.getMessage());
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    Map<String, Object> result = new HashMap<>();
    result.put("success", true);
    result.put("asset", asset);
    result.put("message", "Widget preference updated");
    response.getWriter().write(objectMapper.writeValueAsString(result));
  }
}
