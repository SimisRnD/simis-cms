package com.simis.cms.service;

import com.simis.cms.model.MediaAsset;
import com.simis.cms.repository.MediaAssetRepository;
import com.simis.cms.storage.StorageProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * P5: Media Upload Service
 * Handles file upload, validation, storage, and database persistence
 *
 * Integration points:
 * - StorageProvider: Abstracts local/Azure storage
 * - MediaAssetRepository: Database persistence
 * - AuditLog: Track uploads for compliance
 */

@Service
public class MediaUploadService {

  @Autowired
  private StorageProvider storageProvider;

  @Autowired
  private MediaAssetRepository mediaAssetRepository;

  @Autowired
  private AuditLogService auditLogService;

  private static final long MAX_FILE_SIZE = 52_428_800L; // 50MB
  private static final String[] ALLOWED_MIME_TYPES = {
    "image/jpeg", "image/png", "image/gif", "image/webp",
    "application/pdf"
  };

  /**
   * Upload a media file
   * - Validate file size, MIME type, alt text
   * - Store file (local dev, Azure prod)
   * - Create MediaAsset record
   * - Log to audit trail
   *
   * @param file The file to upload
   * @param altText Alt text for accessibility (required)
   * @param tags Comma-separated tags (optional)
   * @return MediaAsset with assetId, storagePath, etc.
   * @throws IllegalArgumentException if validation fails
   * @throws IOException if storage fails
   */
  public MediaAsset uploadMedia(MultipartFile file, String altText, String tags)
      throws IOException, IllegalArgumentException {

    // Validate file size
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new IllegalArgumentException(
          String.format("File size exceeds 50MB limit (got %.1f MB)", file.getSize() / 1_000_000.0));
    }

    // Validate MIME type
    String mimeType = file.getContentType();
    if (mimeType == null || !isAllowedMimeType(mimeType)) {
      throw new IllegalArgumentException("File type not allowed. Allowed: JPEG, PNG, GIF, WebP, PDF");
    }

    // Validate alt text
    if (altText == null || altText.trim().isEmpty()) {
      throw new IllegalArgumentException("Alt text is required for accessibility");
    }

    // Generate unique asset ID and storage path
    String assetId = UUID.randomUUID().toString();
    String storagePath = storageProvider.uploadFile(file, assetId);

    // Create MediaAsset record
    MediaAsset asset = new MediaAsset();
    asset.setAssetId(assetId);
    asset.setAssetName(file.getOriginalFilename());
    asset.setAssetType(determineAssetType(mimeType));
    asset.setMimeType(mimeType);
    asset.setFileSizeBytes(file.getSize());
    asset.setStoragePath(storagePath);
    asset.setAltText(altText.trim());
    asset.setTags(tags != null ? tags.trim() : null);
    asset.setCreatedBy(getCurrentUserId());
    asset.setCreatedAt(LocalDateTime.now());
    asset.setUpdatedAt(LocalDateTime.now());

    // Persist to database
    MediaAsset saved = mediaAssetRepository.save(asset);

    // Log upload to audit trail
    auditLogService.logAction("MEDIA_UPLOAD", "Uploaded media: " + file.getOriginalFilename(), saved.getId());

    return saved;
  }

  /**
   * Soft-delete a media asset
   * Sets deleted_at timestamp
   */
  public void deleteMedia(String assetId) throws IllegalArgumentException {
    MediaAsset asset = mediaAssetRepository.findByAssetId(assetId)
        .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

    asset.setDeletedAt(LocalDateTime.now());
    mediaAssetRepository.save(asset);

    auditLogService.logAction("MEDIA_DELETE", "Deleted media: " + asset.getAssetName(), asset.getId());
  }

  /**
   * Update alt text for an existing asset
   */
  public void updateAltText(String assetId, String altText) throws IllegalArgumentException {
    MediaAsset asset = mediaAssetRepository.findByAssetId(assetId)
        .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

    asset.setAltText(altText.trim());
    asset.setUpdatedAt(LocalDateTime.now());
    mediaAssetRepository.save(asset);

    auditLogService.logAction("MEDIA_UPDATE_ALT", "Updated alt text: " + assetId, asset.getId());
  }

  /**
   * Helper: Check if MIME type is in allowlist
   */
  private boolean isAllowedMimeType(String mimeType) {
    for (String allowed : ALLOWED_MIME_TYPES) {
      if (allowed.equalsIgnoreCase(mimeType)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Helper: Determine asset type from MIME type
   */
  private String determineAssetType(String mimeType) {
    if (mimeType.startsWith("image/")) {
      return "image";
    } else if (mimeType.equals("application/pdf")) {
      return "pdf";
    }
    return "other";
  }

  /**
   * Helper: Get current user ID from security context
   * (Stub — implement with Spring Security)
   */
  private long getCurrentUserId() {
    // TODO: Extract from SecurityContextHolder
    return 1L; // Placeholder
  }
}
