package com.simisinc.platform.domain.model;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * P5.1: Media Asset model
 * Represents uploaded media files (images, PDFs) in the library
 */
@Entity
@Table(name = "media_assets")
public class MediaAsset {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  @Column(name = "asset_id", nullable = false, unique = true, length = 128)
  private String assetId;

  @Column(name = "asset_name", nullable = false, length = 512)
  private String assetName;

  @Column(name = "asset_type", nullable = false, length = 32)
  private String assetType;

  @Column(name = "mime_type", length = 64)
  private String mimeType;

  @Column(name = "file_size_bytes", nullable = false)
  private long fileSizeBytes;

  @Column(name = "storage_path", nullable = false)
  private String storagePath;

  @Column(name = "alt_text", nullable = false)
  private String altText;

  @Column(name = "tags")
  private String tags;

  @Column(name = "created_by", nullable = false)
  private long createdBy;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  public MediaAsset() {
  }

  public MediaAsset(String assetId, String assetName, String assetType, String mimeType,
      long fileSizeBytes, String storagePath, String altText, long createdBy) {
    this.assetId = assetId;
    this.assetName = assetName;
    this.assetType = assetType;
    this.mimeType = mimeType;
    this.fileSizeBytes = fileSizeBytes;
    this.storagePath = storagePath;
    this.altText = altText;
    this.createdBy = createdBy;
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getAssetId() {
    return assetId;
  }

  public void setAssetId(String assetId) {
    this.assetId = assetId;
  }

  public String getAssetName() {
    return assetName;
  }

  public void setAssetName(String assetName) {
    this.assetName = assetName;
  }

  public String getAssetType() {
    return assetType;
  }

  public void setAssetType(String assetType) {
    this.assetType = assetType;
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public long getFileSizeBytes() {
    return fileSizeBytes;
  }

  public void setFileSizeBytes(long fileSizeBytes) {
    this.fileSizeBytes = fileSizeBytes;
  }

  public String getStoragePath() {
    return storagePath;
  }

  public void setStoragePath(String storagePath) {
    this.storagePath = storagePath;
  }

  public String getAltText() {
    return altText;
  }

  public void setAltText(String altText) {
    this.altText = altText;
  }

  public String getTags() {
    return tags;
  }

  public void setTags(String tags) {
    this.tags = tags;
  }

  public long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(long createdBy) {
    this.createdBy = createdBy;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }

  @Override
  public String toString() {
    return "MediaAsset{" + "id=" + id + ", assetId='" + assetId + '\'' + ", assetName='"
        + assetName + '\'' + ", assetType='" + assetType + '\'' + ", fileSizeBytes="
        + fileSizeBytes + ", createdBy=" + createdBy + ", createdAt=" + createdAt + '}';
  }
}
