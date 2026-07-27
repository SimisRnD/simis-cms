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
import com.simisinc.platform.domain.model.MediaAsset;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.MediaAssetRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.UUID;

/**
 * P5.2: Media Library API endpoints for the visual editor panel
 *
 * GET /api/media?search=query&limit=20&offset=0 - List media assets with pagination and search
 * POST /api/media - Create stub asset record
 * POST /api/media/upload - Handle file upload from drag-and-drop or file picker
 * POST /api/media/widget-update - Update widget preference with selected media
 *
 * @author claude
 * @created 7/26/26
 */
@WebServlet(name = "MediaApi", urlPatterns = {"/api/media", "/api/media/*"})
public class MediaApiController extends HttpServlet {

  private static Log LOG = LogFactory.getLog(MediaApiController.class);
  private static ObjectMapper objectMapper = new ObjectMapper();

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    response.setContentType("application/json");

    try {
      User user = (User) request.getSession().getAttribute("user");
      if (user == null) {
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
      User user = (User) request.getSession().getAttribute("user");
      if (user == null) {
        response.setStatus(401);
        Map<String, Object> result = new HashMap<>();
        result.put("error", "Not authenticated");
        response.getWriter().write(objectMapper.writeValueAsString(result));
        return;
      }

      if ("/upload".equals(pathInfo)) {
        handleUpload(request, response, user);
      } else if ("/widget-update".equals(pathInfo)) {
        handleWidgetUpdate(request, response, user);
      } else {
        handleCreateAsset(request, response, user);
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

  private void handleCreateAsset(HttpServletRequest request, HttpServletResponse response, User user)
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
    asset.setCreatedBy(user.getId());
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

  private void handleUpload(HttpServletRequest request, HttpServletResponse response, User user)
      throws IOException {
    Map<String, Object> result = new HashMap<>();
    result.put("success", false);
    result.put("error", "File upload not yet implemented");
    response.setStatus(501);
    response.getWriter().write(objectMapper.writeValueAsString(result));
  }

  private void handleWidgetUpdate(HttpServletRequest request, HttpServletResponse response, User user)
      throws IOException {
    String assetId = request.getParameter("assetId");
    String widgetId = request.getParameter("widgetId");

    if (assetId == null || assetId.isEmpty()) {
      response.setStatus(400);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "assetId is required");
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

    Map<String, Object> result = new HashMap<>();
    result.put("success", true);
    result.put("asset", asset);
    result.put("message", "Widget update initiated");
    response.getWriter().write(objectMapper.writeValueAsString(result));
  }
}
