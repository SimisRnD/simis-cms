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
 * P5.1: Media Library API endpoints
 *
 * GET /api/media - List media assets
 * POST /api/media - Upload new media asset (stub - actual upload handled separately)
 *
 * @author claude
 * @created 7/26/26
 */
@WebServlet(name = "MediaApi", urlPatterns = "/api/media")
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

      List<MediaAsset> assets = MediaAssetRepository.findAll(null);

      Map<String, Object> result = new HashMap<>();
      result.put("success", true);
      result.put("assets", assets);

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

      // Extract parameters from multipart or form data
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

      // Create media asset record
      MediaAsset asset = new MediaAsset();
      asset.setAssetId(UUID.randomUUID().toString());
      asset.setAssetName(assetName);
      asset.setAssetType(assetType != null ? assetType : "unknown");
      asset.setMimeType(mimeType);
      asset.setAltText(altText);
      asset.setTags(tags);
      asset.setCreatedBy(user.getId());
      asset.setCreatedAt(LocalDateTime.now());

      // File upload and storage would be handled by P5.2
      // For now, storage_path is null - to be filled by actual upload handler
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

    } catch (Exception e) {
      LOG.error("Error creating media asset: " + e.getMessage(), e);
      response.setStatus(500);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "Failed to create media asset");
      try {
        response.getWriter().write(objectMapper.writeValueAsString(result));
      } catch (Exception ex) {
        LOG.error("Error writing error response", ex);
      }
    }
  }
}
