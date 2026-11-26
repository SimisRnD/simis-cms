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

package com.simisinc.platform.domain.model.cms;

import java.sql.Timestamp;

import com.simisinc.platform.domain.model.Entity;

/**
 * A file a visitor attached to a form (issue #2130), after it passed the type check, was re-encoded
 * and was scanned. Kept apart from the CMS file library: see the form_uploads table.
 *
 * @author SimIS Inc.
 */
public class FormUpload extends Entity {

  public static final String SCAN_PENDING = "pending";
  public static final String SCAN_CLEAN = "clean";
  public static final String SCAN_REJECTED = "rejected";
  /**
   * Stored without a malware scan, because the file is an image rebuilt from its pixels and the site
   * does not require images to be scanned ({@code security.formUploads.imageScanRequired}). Recorded
   * as itself rather than as clean, so the review screen and the record say what actually happened.
   */
  public static final String SCAN_NOT_REQUIRED = "not_required";

  private Long id = -1L;
  private String formUniqueId = null;
  private long formDataId = -1;
  private String fieldName = null;
  private String originalFilename = null;
  private String filePath = null;
  private String contentType = null;
  private long fileLength = 0;
  private int width = -1;
  private int height = -1;
  private String thumbnailPath = null;
  private String thumbnailContentType = null;
  private String scanStatus = SCAN_PENDING;
  private Timestamp scanChecked = null;
  private Timestamp created = null;

  public FormUpload() {
  }

  /** Whether a malware scan ran and passed the file */
  public boolean isClean() {
    return SCAN_CLEAN.equals(scanStatus);
  }

  /** Whether the file was stored without a scan because none was required for it */
  public boolean isScanNotRequired() {
    return SCAN_NOT_REQUIRED.equals(scanStatus);
  }

  /**
   * Only a file the scan passed, or one stored when no scan was required, may be downloaded. A
   * pending or rejected file never is.
   */
  public boolean isDownloadable() {
    return isClean() || isScanNotRequired();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getFormUniqueId() {
    return formUniqueId;
  }

  public void setFormUniqueId(String formUniqueId) {
    this.formUniqueId = formUniqueId;
  }

  public long getFormDataId() {
    return formDataId;
  }

  public void setFormDataId(long formDataId) {
    this.formDataId = formDataId;
  }

  public String getFieldName() {
    return fieldName;
  }

  public void setFieldName(String fieldName) {
    this.fieldName = fieldName;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public void setOriginalFilename(String originalFilename) {
    this.originalFilename = originalFilename;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public long getFileLength() {
    return fileLength;
  }

  public void setFileLength(long fileLength) {
    this.fileLength = fileLength;
  }

  public int getWidth() {
    return width;
  }

  public void setWidth(int width) {
    this.width = width;
  }

  public int getHeight() {
    return height;
  }

  public void setHeight(int height) {
    this.height = height;
  }

  public String getThumbnailPath() {
    return thumbnailPath;
  }

  public void setThumbnailPath(String thumbnailPath) {
    this.thumbnailPath = thumbnailPath;
  }

  public String getThumbnailContentType() {
    return thumbnailContentType;
  }

  public void setThumbnailContentType(String thumbnailContentType) {
    this.thumbnailContentType = thumbnailContentType;
  }

  public String getScanStatus() {
    return scanStatus;
  }

  public void setScanStatus(String scanStatus) {
    this.scanStatus = scanStatus;
  }

  public Timestamp getScanChecked() {
    return scanChecked;
  }

  public void setScanChecked(Timestamp scanChecked) {
    this.scanChecked = scanChecked;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }
}
