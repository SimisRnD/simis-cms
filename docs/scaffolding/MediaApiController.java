package com.simis.cms.controller;

import com.simis.cms.model.MediaAsset;
import com.simis.cms.service.MediaUploadService;
import com.simis.cms.service.MediaListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * P5: Media Library API Controller
 * Endpoints for uploading, listing, and managing media assets
 *
 * Routes:
 * - POST /api/media/upload — Upload new media file
 * - GET /api/media/list — List media with pagination and filters
 * - DELETE /api/media/{assetId} — Soft-delete media
 */

@RestController
@RequestMapping("/api/media")
@PreAuthorize("hasRole('EDITOR')")
public class MediaApiController {

  @Autowired
  private MediaUploadService mediaUploadService;

  @Autowired
  private MediaListService mediaListService;

  /**
   * POST /api/media/upload
   * Upload a new media file (image or PDF)
   *
   * Request:
   *   - file (MultipartFile) — The file to upload (required)
   *   - altText (String) — Alt text for accessibility (required)
   *   - tags (String) — Comma-separated tags (optional)
   *
   * Response: { assetId, assetName, fileSize, mimeType, altText, tags }
   * Errors:
   *   - 400: File size > 50MB, unsupported MIME type, missing altText
   *   - 413: Payload too large
   *   - 500: Upload failed (storage error)
   */
  @PostMapping("/upload")
  public ResponseEntity<?> uploadMedia(
      @RequestParam("file") MultipartFile file,
      @RequestParam("altText") String altText,
      @RequestParam(value = "tags", required = false) String tags) {

    try {
      // Validate inputs
      if (file.isEmpty()) {
        return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
      }

      if (altText == null || altText.trim().isEmpty()) {
        return ResponseEntity.badRequest().body(Map.of("error", "Alt text is required"));
      }

      // Upload via service
      MediaAsset asset = mediaUploadService.uploadMedia(file, altText, tags);

      // Return asset metadata
      return ResponseEntity.ok(Map.of(
          "assetId", asset.getAssetId(),
          "assetName", asset.getAssetName(),
          "fileSize", asset.getFileSizeBytes(),
          "mimeType", asset.getMimeType(),
          "altText", asset.getAltText(),
          "tags", asset.getTags() != null ? asset.getTags() : "",
          "createdAt", asset.getCreatedAt()
      ));

    } catch (IllegalArgumentException e) {
      // Validation error (file size, MIME type, etc.)
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    } catch (IOException e) {
      // Storage error
      return ResponseEntity.status(500).body(Map.of("error", "Upload failed: " + e.getMessage()));
    }
  }

  /**
   * GET /api/media/list
   * List media assets with pagination and filtering
   *
   * Query Params:
   *   - page (int, default 0) — Page number (0-indexed)
   *   - size (int, default 50) — Items per page (max 100)
   *   - type (String, optional) — Filter by type: 'image', 'pdf', or null for all
   *   - search (String, optional) — Search by name or tags (full-text)
   *   - tags (String, optional) — Filter by tag (comma-separated)
   *   - sortBy (String, default 'createdAt') — Sort field: 'name', 'size', 'createdAt'
   *   - sortOrder (String, default 'desc') — Sort direction: 'asc' or 'desc'
   *
   * Response:
   *   {
   *     total: 42,
   *     page: 0,
   *     size: 50,
   *     items: [
   *       { assetId, assetName, fileSize, mimeType, altText, tags, createdAt, createdBy },
   *       ...
   *     ]
   *   }
   */
  @GetMapping("/list")
  public ResponseEntity<?> listMedia(
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "50") int size,
      @RequestParam(value = "type", required = false) String type,
      @RequestParam(value = "search", required = false) String search,
      @RequestParam(value = "tags", required = false) String tags,
      @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
      @RequestParam(value = "sortOrder", defaultValue = "desc") String sortOrder) {

    try {
      // Validate pagination
      if (page < 0) page = 0;
      if (size < 1 || size > 100) size = 50;

      // Query media with filters
      Map<String, Object> result = mediaListService.listMediaAssets(
          page, size, type, search, tags, sortBy, sortOrder);

      return ResponseEntity.ok(result);

    } catch (Exception e) {
      return ResponseEntity.status(500).body(Map.of("error", "Failed to list media: " + e.getMessage()));
    }
  }

  /**
   * DELETE /api/media/{assetId}
   * Soft-delete a media asset
   *
   * Response: { message: "Media deleted" }
   * Errors:
   *   - 404: Asset not found
   *   - 403: User doesn't own the asset (TODO: check ownership)
   *   - 500: Deletion failed
   */
  @DeleteMapping("/{assetId}")
  public ResponseEntity<?> deleteMedia(@PathVariable String assetId) {

    try {
      mediaUploadService.deleteMedia(assetId);
      return ResponseEntity.ok(Map.of("message", "Media deleted"));

    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(404).body(Map.of("error", "Asset not found"));
    } catch (Exception e) {
      return ResponseEntity.status(500).body(Map.of("error", "Deletion failed: " + e.getMessage()));
    }
  }

  /**
   * POST /api/media/{assetId}/alt-text
   * Update alt text for an existing media asset
   * (Allows users to fix alt text after upload)
   *
   * Request: { altText: "..." }
   * Response: { message: "Alt text updated" }
   */
  @PostMapping("/{assetId}/alt-text")
  public ResponseEntity<?> updateAltText(
      @PathVariable String assetId,
      @RequestBody Map<String, String> body) {

    try {
      String altText = body.get("altText");
      if (altText == null || altText.trim().isEmpty()) {
        return ResponseEntity.badRequest().body(Map.of("error", "Alt text cannot be empty"));
      }

      mediaUploadService.updateAltText(assetId, altText);
      return ResponseEntity.ok(Map.of("message", "Alt text updated"));

    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(404).body(Map.of("error", "Asset not found"));
    } catch (Exception e) {
      return ResponseEntity.status(500).body(Map.of("error", "Update failed: " + e.getMessage()));
    }
  }
}
