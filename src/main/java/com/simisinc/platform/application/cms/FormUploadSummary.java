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

package com.simisinc.platform.application.cms;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.domain.model.cms.FormUpload;
import com.simisinc.platform.infrastructure.persistence.cms.FormUploadRepository;

/**
 * What an admin is shown about a file a visitor attached to a form (issue #2130): on the Form Data
 * review screen and in the submission notification email.
 *
 * <p>A submission's file field stores the upload's id as its value. These are looked up by
 * submission, never by that id alone, so a value can only ever show a file that was attached to
 * that same submission.
 *
 * @author SimIS Inc.
 */
public class FormUploadSummary {

  /** Where the review screen downloads a file from; the page itself restricts it to reviewers */
  public static final String DOWNLOAD_PATH = "/assets/form-upload?id=";

  private static final long KILOBYTE = 1024L;
  private static final long MEGABYTE = 1024L * 1024L;

  private static final Map<String, String> TYPE_LABELS = Map.of(
      "image/jpeg", "JPEG image",
      "image/png", "PNG image",
      "image/webp", "WebP image",
      "application/pdf", "PDF");

  private final FormUpload upload;

  FormUploadSummary(FormUpload upload) {
    this.upload = upload;
  }

  /**
   * The summaries for a page of submissions, by submission id and then by upload id (the file field's
   * value). Only queries when one of the submissions has a file field with a value.
   */
  public static Map<Long, Map<String, FormUploadSummary>> forSubmissions(List<FormData> submissions) {
    Map<Long, Map<String, FormUploadSummary>> bySubmission = new HashMap<>();
    if (submissions == null) {
      return bySubmission;
    }
    List<Long> withFiles = new ArrayList<>();
    for (FormData submission : submissions) {
      if (submission != null && hasAttachedFile(submission)) {
        withFiles.add(submission.getId());
      }
    }
    if (withFiles.isEmpty()) {
      return bySubmission;
    }
    for (FormUpload upload : FormUploadRepository.findAllByFormDataIds(withFiles)) {
      bySubmission.computeIfAbsent(upload.getFormDataId(), id -> new HashMap<>())
          .put(String.valueOf(upload.getId()), new FormUploadSummary(upload));
    }
    return bySubmission;
  }

  /** The summaries for one submission, by upload id (the file field's value) */
  public static Map<String, FormUploadSummary> forSubmission(long formDataId) {
    Map<String, FormUploadSummary> byUpload = new HashMap<>();
    for (FormUpload upload : FormUploadRepository.findAllByFormDataId(formDataId)) {
      byUpload.put(String.valueOf(upload.getId()), new FormUploadSummary(upload));
    }
    return byUpload;
  }

  static boolean hasAttachedFile(FormData submission) {
    if (submission.getFormFieldList() == null) {
      return false;
    }
    for (FormField field : submission.getFormFieldList()) {
      if (FormFileFieldCommand.isFileField(field) && StringUtils.isNotBlank(field.getUserValue())) {
        return true;
      }
    }
    return false;
  }

  /** A file size for reading: "640 bytes", "88 KB", "1.9 MB" */
  public static String formatSize(long bytes) {
    if (bytes < KILOBYTE) {
      return Math.max(0, bytes) + " bytes";
    }
    if (bytes < MEGABYTE) {
      return Math.round((double) bytes / KILOBYTE) + " KB";
    }
    return String.format(Locale.ROOT, "%.1f MB", (double) bytes / MEGABYTE);
  }

  public long getId() {
    return upload.getId();
  }

  public String getFieldName() {
    return upload.getFieldName();
  }

  /** The visitor's name for the file, for display only */
  public String getFilename() {
    return StringUtils.defaultIfBlank(upload.getOriginalFilename(), "attachment");
  }

  public String getSize() {
    return formatSize(upload.getFileLength());
  }

  /** The stored file's type, as detected and re-encoded: "PNG image" */
  public String getType() {
    String contentType = StringUtils.defaultString(upload.getContentType()).toLowerCase(Locale.ROOT);
    return TYPE_LABELS.getOrDefault(contentType, StringUtils.defaultIfBlank(contentType, "file"));
  }

  /** "640 × 320 px", or null when the file has no dimensions */
  public String getDimensions() {
    if (upload.getWidth() <= 0 || upload.getHeight() <= 0) {
      return null;
    }
    return upload.getWidth() + " × " + upload.getHeight() + " px";
  }

  /** Whether a malware scan ran and passed the file */
  public boolean isClean() {
    return upload.isClean();
  }

  /** Whether the file was stored without a scan because none was required for images */
  public boolean isScanNotRequired() {
    return upload.isScanNotRequired();
  }

  /** Whether the file may be downloaded: scanned clean, or stored when no scan was required */
  public boolean isDownloadable() {
    return upload.isDownloadable();
  }

  public Timestamp getScanChecked() {
    return upload.getScanChecked();
  }

  /** The gated download, relative to the site; null unless the file is downloadable */
  public String getDownloadUrl() {
    return isDownloadable() ? DOWNLOAD_PATH + upload.getId() : null;
  }

  /** The thumbnail, relative to the site; null unless the file is downloadable and has one */
  public String getThumbnailUrl() {
    return isDownloadable() && StringUtils.isNotBlank(upload.getThumbnailPath()) ? DOWNLOAD_PATH + upload.getId() + "&thumbnail=true" : null;
  }
}
