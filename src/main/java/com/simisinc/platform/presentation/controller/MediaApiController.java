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
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.EditorPermissionCommand;
import com.simisinc.platform.application.cms.LoadWebPageCommand;
import com.simisinc.platform.application.cms.MutateLayoutCommand;
import com.simisinc.platform.application.cms.ValidateFileCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.MediaAsset;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.MediaAssetRepository;
import com.simisinc.platform.presentation.widgets.cms.ImageWidget;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.UUID;

/**
 * P5.2: Media Library API endpoints for the visual editor panel
 *
 * GET /visual-editor/media?search=query&limit=20&offset=0 - List media assets with pagination and search
 * GET /visual-editor/media/file/{assetId} - Stream a previously uploaded asset's bytes. Deliberately not
 * gated behind login (see {@link #handleServeFile}) -- once an asset is embedded into a page via
 * widget-update, anonymous site visitors need to load it too, the same posture {@code StreamImageWidget}
 * already uses for every other uploaded image in this app.
 * POST /visual-editor/media - Create asset record (assetName, assetType, mimeType, storagePath, altText, tags)
 * POST /visual-editor/media/upload - Handle a real file upload from drag-and-drop or the file picker
 * (issue #773): validates the file server-side, stores it via {@link FileSystemCommand}'s conventions, and
 * creates the media_assets row through the same path as {@link #handleCreateAsset}.
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
 * DELETE /visual-editor/media/{assetId} - Soft-delete a media asset from the picker (see
 * {@link #doDelete}). Requires the session CSRF token. Does not affect an asset already embedded
 * into a live page -- {@link #handleServeFile} keeps serving it regardless.
 *
 * @author claude
 * @created 7/26/26
 */
@WebServlet(name = "MediaApi", urlPatterns = {"/visual-editor/media", "/visual-editor/media/*"})
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 2, // 2MB
    maxFileSize = 1024 * 1024 * 30,       // 30MB (hard ceiling; the app-level cap enforced in
                                           // handleUpload defaults to 10MB, see resolveMaxUploadBytes)
    maxRequestSize = 1024 * 1024 * 35)    // 35MB
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
      String pathInfo = request.getPathInfo();
      if (pathInfo != null && pathInfo.startsWith("/file/")) {
        // Not login-gated -- see the class javadoc and handleServeFile for why.
        handleServeFile(request, response, pathInfo.substring("/file/".length()));
        return;
      }

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

  /**
   * DELETE /visual-editor/media/{assetId} - Soft-delete a media asset (issue #773 follow-up).
   * {@code MediaAssetRepository.softDelete} already existed, and {@code findAll} already excludes
   * soft-deleted rows above, but until now nothing in this controller ever called it -- there was no
   * way to actually remove a mistaken upload from the picker.
   *
   * <p>Soft-delete only hides the asset from the picker/listing; it deliberately does NOT stop
   * {@link #handleServeFile} from continuing to serve the file. An asset already embedded into a live
   * page via {@link #handleWidgetUpdate} depends on that same URL resolving indefinitely (see this
   * class's javadoc on why {@code handleServeFile} is unauthenticated) -- a soft-delete initiated from
   * the picker, on an asset the admin may not realize is in use elsewhere, must not silently break an
   * already-published page.
   */
  @Override
  protected void doDelete(HttpServletRequest request, HttpServletResponse response)
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

      // Same tier as handleCreateAsset: removing a media asset is the same class of
      // content-authoring action as creating one.
      if (!EditorPermissionCommand.canEditContent(userSession)) {
        response.setStatus(403);
        Map<String, Object> result = new HashMap<>();
        result.put("error", "Insufficient permission to delete media assets");
        response.getWriter().write(objectMapper.writeValueAsString(result));
        return;
      }

      // Matches handleUpload/handleWidgetUpdate's CSRF check -- deleting is at least as
      // consequential as either of those mutations.
      String token = request.getParameter("token");
      if (token == null || !token.equals(userSession.getFormToken())) {
        LOG.warn("media delete CSRF token mismatch from " + request.getRemoteAddr());
        response.setStatus(403);
        Map<String, Object> result = new HashMap<>();
        result.put("error", "Invalid or missing CSRF token");
        response.getWriter().write(objectMapper.writeValueAsString(result));
        return;
      }

      String pathInfo = request.getPathInfo();
      if (pathInfo == null || pathInfo.length() <= 1) {
        response.setStatus(400);
        Map<String, Object> result = new HashMap<>();
        result.put("error", "assetId is required");
        response.getWriter().write(objectMapper.writeValueAsString(result));
        return;
      }
      String assetId = pathInfo.substring(1);

      MediaAsset asset = MediaAssetRepository.findByAssetId(assetId);
      if (asset == null) {
        response.setStatus(404);
        Map<String, Object> result = new HashMap<>();
        result.put("error", "Media asset not found");
        response.getWriter().write(objectMapper.writeValueAsString(result));
        return;
      }

      if (!MediaAssetRepository.softDelete(asset.getId())) {
        response.setStatus(500);
        Map<String, Object> result = new HashMap<>();
        result.put("error", "Failed to delete media asset");
        response.getWriter().write(objectMapper.writeValueAsString(result));
        return;
      }

      Map<String, Object> result = new HashMap<>();
      result.put("success", true);
      response.getWriter().write(objectMapper.writeValueAsString(result));

    } catch (Exception e) {
      LOG.error("Error deleting media asset: " + e.getMessage(), e);
      response.setStatus(500);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "Failed to delete media asset");
      try {
        response.getWriter().write(objectMapper.writeValueAsString(result));
      } catch (Exception ex) {
        LOG.error("Error writing error response", ex);
      }
    }
  }

  private void handleCreateAsset(HttpServletRequest request, HttpServletResponse response, UserSession userSession)
      throws IOException {
    // This is the same class of content-authoring action as handleUpload (it creates a media_assets
    // row directly), so it requires the same tier of permission -- without this check, any logged-in
    // user of any role could create arbitrary media asset records once the id-routing bug above stopped
    // masking every call here as a 500.
    if (!EditorPermissionCommand.canEditContent(userSession)) {
      response.setStatus(403);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "Insufficient permission to create media assets");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    String assetName = request.getParameter("assetName");
    String assetType = request.getParameter("assetType");
    String mimeType = request.getParameter("mimeType");
    // Issue #773 fix: this record-creation path never set storagePath, and media_assets.storage_path
    // is NOT NULL -- any real call here would have failed the insert. Callers must now supply it.
    String storagePath = request.getParameter("storagePath");
    String altText = request.getParameter("altText");
    String tags = request.getParameter("tags");

    if (assetName == null || assetName.isEmpty()) {
      response.setStatus(400);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "assetName is required");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    if (StringUtils.isBlank(storagePath)) {
      response.setStatus(400);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "storagePath is required");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    MediaAsset asset = new MediaAsset();
    asset.setAssetId(UUID.randomUUID().toString());
    asset.setAssetName(assetName);
    asset.setAssetType(assetType != null ? assetType : "unknown");
    asset.setMimeType(mimeType);
    asset.setStoragePath(storagePath);
    // alt_text is also NOT NULL (required for accessibility per the media_assets migration); fall
    // back to the asset name rather than adding a second required-field round trip for callers.
    asset.setAltText(StringUtils.isNotBlank(altText) ? altText : assetName);
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

  // 10MB default, matching ImageUploadWidget/SaveFilePartCommand's own hardcoded default for the
  // same "system.upload.maxBytes" site property -- there is no shared constant for this in the
  // codebase today, so this mirrors their convention rather than inventing a new limit.
  private static final long DEFAULT_MAX_UPLOAD_BYTES = 10_485_760L;

  private static long resolveMaxUploadBytes() {
    long maxBytes = DEFAULT_MAX_UPLOAD_BYTES;
    String prop = LoadSitePropertyCommand.loadByName("system.upload.maxBytes");
    if (prop != null && !prop.isBlank()) {
      try {
        maxBytes = Long.parseLong(prop.trim());
      } catch (NumberFormatException ignored) {
      }
    }
    return maxBytes;
  }

  // Real format signatures ("magic bytes") for the file types this endpoint accepts (images + PDF,
  // per the isImage/isPdf check below). Files.probeContentType() alone is not trustworthy here: on a
  // minimal JDK/container with no mime-magic database it silently falls back to guessing from the
  // extension, so a plain-text file renamed to "malware.png" would be accepted, stored, and served
  // back as "image/png" -- identical in effect to trusting the filename with no real verification.
  // These signatures are checked directly against the uploaded bytes on disk instead.
  private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
  private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
  private static final byte[] GIF87A_SIGNATURE = {'G', 'I', 'F', '8', '7', 'a'};
  private static final byte[] GIF89A_SIGNATURE = {'G', 'I', 'F', '8', '9', 'a'};
  private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};
  // WebP is a RIFF container: bytes 0-3 are "RIFF", bytes 4-7 are a little-endian chunk size specific
  // to each file (not checked here), bytes 8-11 are "WEBP".
  private static final byte[] RIFF_SIGNATURE = {'R', 'I', 'F', 'F'};
  private static final byte[] WEBP_SIGNATURE = {'W', 'E', 'B', 'P'};
  private static final int SNIFF_HEADER_BYTES = 12;

  private static boolean headerStartsWith(byte[] header, int headerLength, byte[] signature) {
    if (headerLength < signature.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if (header[i] != signature[i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Detects a file's real content type by inspecting its actual header bytes on disk -- never the
   * submitted filename, its extension, or the client-declared {@code Part} content type. Only the
   * formats this endpoint supports are recognized; anything else (including a disguised file whose
   * extension claims one of these types but whose bytes don't match, e.g. a script renamed to
   * ".png") returns {@code null} so the caller rejects it.
   */
  private static String sniffContentType(File file) {
    byte[] header = new byte[SNIFF_HEADER_BYTES];
    int headerLength;
    try (InputStream in = Files.newInputStream(file.toPath())) {
      headerLength = in.readNBytes(header, 0, header.length);
    } catch (Exception e) {
      LOG.warn("Could not read the uploaded file's header bytes: " + e.getMessage());
      return null;
    }
    if (headerStartsWith(header, headerLength, PNG_SIGNATURE)) {
      return "image/png";
    }
    if (headerStartsWith(header, headerLength, JPEG_SIGNATURE)) {
      return "image/jpeg";
    }
    if (headerStartsWith(header, headerLength, GIF87A_SIGNATURE) || headerStartsWith(header, headerLength, GIF89A_SIGNATURE)) {
      return "image/gif";
    }
    if (headerStartsWith(header, headerLength, PDF_SIGNATURE)) {
      return "application/pdf";
    }
    if (headerLength >= SNIFF_HEADER_BYTES && headerStartsWith(header, headerLength, RIFF_SIGNATURE)
        && header[8] == WEBP_SIGNATURE[0] && header[9] == WEBP_SIGNATURE[1]
        && header[10] == WEBP_SIGNATURE[2] && header[11] == WEBP_SIGNATURE[3]) {
      return "image/webp";
    }
    return null;
  }

  /**
   * Handles a real multipart file upload from the media library panel's drag-and-drop area or file
   * picker (issue #773). Follows the same parsing shape as {@code ImageUploadWidget}/
   * {@code SaveFilePartCommand} ({@code request.getPart("file")}, MSIE-safe filename handling,
   * extension cleaning) and the same {@link FileSystemCommand} storage convention every other
   * upload in this app uses: a date-partitioned sub-path under a module name
   * ({@code generateFileServerSubPath}), a collision-proof filename ({@code generateUniqueFilename}),
   * and root-contained path resolution ({@code resolveWithinRoot}).
   *
   * <p>Never trusts the client alone: file size, blocked/dangerous extensions, and the file's actual
   * detected content type are all re-checked here even though the panel's JS validates first too.
   *
   * <p>If the media_assets row can't be saved after the file is already on disk, the file is deleted
   * rather than left as an orphan -- it would be reachable by nothing (no row means no assetId to
   * look it up by, see {@link #handleServeFile}), so keeping it around is pure leaked disk space with
   * no compensating benefit. This mirrors ImageUploadWidget's existing behavior of deleting its temp
   * file whenever the record-side save fails.
   */
  private void handleUpload(HttpServletRequest request, HttpServletResponse response, UserSession userSession)
      throws IOException {
    if (!EditorPermissionCommand.canEditContent(userSession)) {
      response.setStatus(403);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "Insufficient permission to upload media");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    String token = request.getParameter("token");
    if (token == null || !token.equals(userSession.getFormToken())) {
      LOG.warn("media upload CSRF token mismatch from " + request.getRemoteAddr());
      response.setStatus(403);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "Session expired");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    Part filePart;
    try {
      filePart = request.getPart("file");
    } catch (Exception e) {
      LOG.warn("Could not read the uploaded file part: " + e.getMessage());
      response.setStatus(400);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "The upload could not be read");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    if (filePart == null) {
      response.setStatus(400);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "A file is required");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    long fileLength = filePart.getSize();
    if (fileLength <= 0) {
      response.setStatus(400);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "The file is empty");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    long maxBytes = resolveMaxUploadBytes();
    if (fileLength > maxBytes) {
      response.setStatus(400);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "The file exceeds the maximum allowed upload size");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    String submittedFilename = Paths.get(filePart.getSubmittedFileName()).getFileName().toString(); // MSIE fix
    String extension = FileSystemCommand.cleanExtension(FilenameUtils.getExtension(submittedFilename));

    try {
      ValidateFileCommand.checkFileExtension(extension);
    } catch (DataException e) {
      response.setStatus(400);
      Map<String, Object> result = new HashMap<>();
      result.put("error", e.getMessage());
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    String serverSubPath = FileSystemCommand.generateFileServerSubPath("media-library");
    String uniqueFilename = FileSystemCommand.generateUniqueFilename(userSession.getUserId());
    File targetFile = FileSystemCommand.resolveWithinRoot(serverRootPath, serverSubPath + uniqueFilename + "." + extension);
    if (targetFile == null) {
      response.setStatus(500);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "The file could not be saved");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    try {
      filePart.write(targetFile.getAbsolutePath());
    } catch (Exception e) {
      // Storage-write failure (disk full, permission error, etc.) -- nothing was persisted, so
      // there's nothing to roll back beyond a possible partial file.
      LOG.error("Could not write uploaded media file: " + e.getMessage(), e);
      if (targetFile.exists()) {
        targetFile.delete();
      }
      response.setStatus(500);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "The file could not be saved");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    // Never trust the client-declared Part content type or the file extension alone -- inspect the
    // actual bytes written to disk. See sniffContentType's javadoc for why Files.probeContentType()
    // alone is not sufficient here.
    String sniffedMimeType = sniffContentType(targetFile);
    boolean isImage = sniffedMimeType != null && sniffedMimeType.startsWith("image/");
    boolean isPdf = "application/pdf".equals(sniffedMimeType);
    if (!isImage && !isPdf) {
      LOG.warn("Rejected a media upload with an unsupported detected type (" + sniffedMimeType + "): " + submittedFilename);
      if (targetFile.exists()) {
        targetFile.delete();
      }
      response.setStatus(400);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "Only image and PDF files are supported");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    String altText = request.getParameter("altText");

    MediaAsset asset = new MediaAsset();
    asset.setAssetId(UUID.randomUUID().toString());
    asset.setAssetName(submittedFilename);
    asset.setAssetType(isImage ? "image" : "pdf");
    asset.setMimeType(sniffedMimeType);
    asset.setFileSizeBytes(fileLength);
    asset.setStoragePath(serverSubPath + uniqueFilename + "." + extension);
    asset.setAltText(StringUtils.isNotBlank(altText) ? altText : submittedFilename);
    asset.setTags(request.getParameter("tags"));
    asset.setCreatedBy(userSession.getUserId());
    asset.setCreatedAt(LocalDateTime.now());

    MediaAsset saved;
    try {
      saved = MediaAssetRepository.save(asset);
    } catch (Exception e) {
      LOG.error("Error saving media asset record: " + e.getMessage(), e);
      saved = null;
    }

    if (saved == null) {
      if (targetFile.exists()) {
        LOG.warn("Deleting an uploaded media file after a failed DB save: " + targetFile.getPath());
        targetFile.delete();
      }
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

  /**
   * Streams a previously uploaded media asset's bytes by its opaque {@code assetId}. Deliberately
   * public (no login check) -- once an asset is applied to a widget via widget-update it becomes
   * part of a real page's content, which anonymous visitors must be able to load, exactly like every
   * other uploaded image already served by this app's StreamImageWidget at {@code /assets/img/*}.
   * Safety comes from the same place theirs does: {@link FileSystemCommand#resolveWithinRoot} keeps
   * the resolved path inside the file server root, the asset must already exist as a database row
   * (no arbitrary path traversal via assetId, which is never used as a path itself), and
   * {@link FileDownloadCommand#applyInlineMediaHeaders} sends {@code nosniff} plus a sandboxed CSP so
   * an uploaded SVG/HTML-like file still renders as an image but cannot execute script in this origin.
   */
  private void handleServeFile(HttpServletRequest request, HttpServletResponse response, String assetId)
      throws IOException {
    MediaAsset asset = StringUtils.isBlank(assetId) ? null : MediaAssetRepository.findByAssetId(assetId);
    if (asset == null || StringUtils.isBlank(asset.getStoragePath())) {
      response.setStatus(404);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "Media file not found");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    File file = FileSystemCommand.resolveWithinRoot(serverRootPath, asset.getStoragePath());
    if (file == null || !file.isFile()) {
      LOG.warn("Media asset file missing from disk: " + assetId);
      response.setStatus(404);
      Map<String, Object> result = new HashMap<>();
      result.put("error", "Media file not found");
      response.getWriter().write(objectMapper.writeValueAsString(result));
      return;
    }

    FileDownloadCommand.applyInlineMediaHeaders(response, asset.getMimeType());
    response.setContentLengthLong(file.length());
    Files.copy(file.toPath(), response.getOutputStream());
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

      // asset.getStoragePath() is the internal FileSystemCommand-relative disk path, not a
      // browser URL (see handleServeFile's javadoc) -- persisting it directly regressed to a
      // broken <img src> the moment issue #773 introduced the dedicated serving route. The site-
      // relative serving URL is what ImageWidget's imageUrl preference actually needs, matching
      // the exact path convention the browse grid's own thumbnails already use.
      Map<String, String> prefs = new HashMap<>();
      prefs.put(prefKey, "/visual-editor/media/file/" + asset.getAssetId());
      String prefsJson = objectMapper.writeValueAsString(prefs);
      MutateLayoutCommand.setWidgetPreferences(webPage, sectionIdx, columnIdx, widgetIdx, prefsJson,
          userSession.getUserId());
      AuditEventCommand.record(request, userSession, AuditEventCommand.CONTENT, "page_layout.setWidgetPreferences",
          AuditEventCommand.SUCCESS, "web_page", String.valueOf(webPage.getId()), webPage.getLink(),
          "via media library: assetId=" + assetId + " widget=" + targetWidgetName
              + " s=" + sectionIdx + " c=" + columnIdx + " w=" + widgetIdx);
    } catch (DataException e) {
      LOG.warn("widget-update rejected for " + pagePath + " " + sectionIdx + ":" + columnIdx + ":" + widgetIdx
          + ": " + e.getMessage());
      AuditEventCommand.record(request, userSession, AuditEventCommand.CONTENT, "page_layout.setWidgetPreferences",
          AuditEventCommand.FAILURE, "web_page", String.valueOf(webPage.getId()), webPage.getLink(),
          "via media library: assetId=" + assetId + " s=" + sectionIdx + " c=" + columnIdx + " w=" + widgetIdx
              + " error=" + e.getMessage());
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
