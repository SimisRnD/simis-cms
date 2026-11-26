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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.FeatureFlagCommand;
import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.domain.model.cms.FormUpload;
import com.simisinc.platform.infrastructure.persistence.cms.FormUploadRepository;

/**
 * The rules for a form builder "file" field (issue #2130): when one may be defined, which kinds of
 * file it may accept, and how large a file may be.
 *
 * <p>This is the first step of #2130. The editor can define a file field, but no live form accepts a
 * file yet: {@code FormWidget} leaves file fields out of every form it renders or processes until the
 * upload pipeline (type check, sanitize, malware scan, gated download) is built. The feature flag
 * keeps the type out of the editor until a site turns it on.
 *
 * @author SimIS Inc.
 */
public class FormFileFieldCommand {

  /** The form_fields.field_type value for a file field */
  public static final String TYPE = "file";

  /** The feature flag, {@code features.form-file-uploads}, that offers the type in the editor */
  public static final String FEATURE_FLAG = "form-file-uploads";

  public static final String KIND_IMAGE = "image";
  public static final String KIND_PDF = "pdf";

  /** Default per-field limit, 10 MB, unless the site's own upload limit is lower */
  static final long DEFAULT_MAX_BYTES = 10_485_760L;

  static final long BYTES_PER_MEGABYTE = 1_048_576L;

  /**
   * What one kind of file accepts: the label the editor and the visitor see, the filename extensions
   * allowed, and the content types DetectContentTypeCommand must find in the file's own bytes. A file
   * passes only when both its extension and its content are in the lists (ValidateFormUploadCommand).
   */
  public record FileKind(String label, Set<String> extensions, Set<String> contentTypes) {
  }

  // The kinds an editor may choose today. GIF is left off the image list, as #2130 specifies. WebP is
  // left off too: JPEG and PNG are decoded in-process by ImageIO, while WebP would have to go through
  // ImageMagick and libwebp, the decoder behind CVE-2023-4863, so leaving it off keeps every visitor
  // upload away from that native decoder. Phone photos and screenshots are JPEG or PNG. PDF is part of
  // the design but needs its own sanitize step before it can be offered. SVG, HTML, Office files,
  // archives and executables are never listed.
  private static final Map<String, FileKind> KINDS = new LinkedHashMap<>();
  private static final Map<String, String> AVAILABLE_KINDS = new LinkedHashMap<>();

  static {
    KINDS.put(KIND_IMAGE, new FileKind("Images (JPG, PNG)",
        Set.of("jpg", "jpeg", "png"),
        Set.of("image/jpeg", "image/png")));
    for (Map.Entry<String, FileKind> kind : KINDS.entrySet()) {
      AVAILABLE_KINDS.put(kind.getKey(), kind.getValue().label());
    }
  }

  private FormFileFieldCommand() {
  }

  /** @return true when this site has turned on {@code features.form-file-uploads} */
  public static boolean isEnabled() {
    return FeatureFlagCommand.isEnabled(FEATURE_FLAG);
  }

  public static boolean isFileField(FormField field) {
    return field != null && TYPE.equals(field.getType());
  }

  /** @return the kinds an editor may choose, mapped to the label the editor shows, in display order */
  /**
   * The file a submission's file field points at, or null when none was attached or it is gone. The
   * field's stored value is the upload's id; the upload must belong to that same submission, so a
   * value can never reach another submission's file.
   */
  public static FormUpload attachedUpload(FormData formData, FormField field) {
    if (formData == null || formData.getId() == null || formData.getId() <= 0 || !isFileField(field)) {
      return null;
    }
    String value = StringUtils.trimToEmpty(field.getUserValue());
    if (!value.matches("[0-9]{1,18}")) {
      return null;
    }
    FormUpload upload = FormUploadRepository.findById(Long.parseLong(value));
    return upload != null && upload.getFormDataId() == formData.getId() ? upload : null;
  }

  /** The visitor's name for an attached file, for display; never used to name a stored file */
  public static String attachedFilename(FormUpload upload) {
    return upload == null ? null : StringUtils.defaultIfBlank(upload.getOriginalFilename(), "attachment");
  }

  public static Map<String, String> availableKinds() {
    return AVAILABLE_KINDS;
  }

  /** @return what the named kind accepts, or null for a kind this site does not offer */
  public static FileKind kind(String key) {
    return key == null ? null : KINDS.get(key.trim().toLowerCase(Locale.ROOT));
  }

  /**
   * The file input's accept attribute for a field: its kinds' content types and extensions, e.g.
   * "image/jpeg,image/png,.jpeg,.jpg,.png". A browser uses it to filter the file
   * picker; the server never relies on it.
   */
  public static String acceptAttribute(FormField field) {
    java.util.TreeSet<String> types = new java.util.TreeSet<>();
    java.util.TreeSet<String> extensions = new java.util.TreeSet<>();
    if (field != null && StringUtils.isNotBlank(field.getAcceptedKinds())) {
      for (String key : field.getAcceptedKinds().split(",")) {
        FileKind kind = kind(key);
        if (kind != null) {
          types.addAll(kind.contentTypes());
          for (String extension : kind.extensions()) {
            extensions.add("." + extension);
          }
        }
      }
    }
    List<String> accept = new ArrayList<>(types);
    accept.addAll(extensions);
    return String.join(",", accept);
  }

  /** What a field accepts, for the visitor, e.g. "Images (JPG, PNG), up to 10 MB." */
  public static String describeLimits(FormField field) {
    List<String> labels = new ArrayList<>();
    if (field != null && StringUtils.isNotBlank(field.getAcceptedKinds())) {
      for (String key : field.getAcceptedKinds().split(",")) {
        FileKind kind = kind(key);
        if (kind != null && !labels.contains(kind.label())) {
          labels.add(kind.label());
        }
      }
    }
    String size = "up to " + ValidateFormUploadCommand.formatSize(ValidateFormUploadCommand.effectiveMaxBytes(field)) + ".";
    return labels.isEmpty() ? StringUtils.capitalize(size) : String.join(" or ", labels) + ", " + size;
  }

  /** @return the site's own upload limit, which no file field may exceed */
  public static long maxBytesCeiling() {
    return SaveFilePartCommand.resolveMaxUploadBytes();
  }

  /** @return the limit a new file field gets: 10 MB, or the site's upload limit if that is lower */
  public static long defaultMaxBytes() {
    return Math.min(DEFAULT_MAX_BYTES, maxBytesCeiling());
  }

  /** @return whole megabytes, rounded down, for showing a byte limit in the editor */
  public static long toMegabytes(long bytes) {
    return bytes / BYTES_PER_MEGABYTE;
  }

  public static long fromMegabytes(long megabytes) {
    return megabytes * BYTES_PER_MEGABYTE;
  }

  /**
   * Checks and tidies the kinds an editor chose for a file field.
   *
   * @param requested a comma-separated list, e.g. "image" or "image,pdf"
   * @return the chosen kinds in display order with duplicates removed, e.g. "image"
   * @throws IllegalArgumentException with a message for the editor when nothing was chosen or a kind
   *     is not one this site offers
   */
  public static String normalizeKinds(String requested) {
    List<String> chosen = new ArrayList<>();
    if (StringUtils.isNotBlank(requested)) {
      for (String value : requested.split(",")) {
        String kind = value.trim().toLowerCase(Locale.ROOT);
        if (kind.isEmpty()) {
          continue;
        }
        if (!AVAILABLE_KINDS.containsKey(kind)) {
          throw new IllegalArgumentException("\"" + value.trim() + "\" is not a kind of file this site accepts");
        }
        if (!chosen.contains(kind)) {
          chosen.add(kind);
        }
      }
    }
    if (chosen.isEmpty()) {
      throw new IllegalArgumentException("Choose at least one kind of file to accept");
    }
    // Display order, so the same choice is always stored the same way
    List<String> ordered = new ArrayList<>();
    for (String kind : AVAILABLE_KINDS.keySet()) {
      if (chosen.contains(kind)) {
        ordered.add(kind);
      }
    }
    return String.join(",", ordered);
  }

  /**
   * Checks a file field's size limit.
   *
   * @param maxBytes the requested limit, or -1 to use {@link #defaultMaxBytes()}
   * @return the limit to store
   * @throws IllegalArgumentException with a message for the editor when the limit is not between
   *     1 MB and the site's upload limit
   */
  public static long normalizeMaxBytes(long maxBytes) {
    if (maxBytes == -1) {
      return defaultMaxBytes();
    }
    long ceiling = maxBytesCeiling();
    if (maxBytes < BYTES_PER_MEGABYTE || maxBytes > ceiling) {
      throw new IllegalArgumentException(
          "Maximum size must be between 1 MB and " + toMegabytes(ceiling) + " MB, the site's upload limit");
    }
    return maxBytes;
  }
}
