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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.cms.FormFileFieldCommand.FileKind;
import com.simisinc.platform.domain.model.cms.FormField;

/**
 * The first check on a file a visitor attaches to a form (issue #2130, step 1 of the upload
 * pipeline): its size against the field's limit, and its type against the kinds the field accepts.
 * It runs before anything is written to disk, reading no more than the file's first 12 bytes.
 *
 * <p>A file passes only when <em>both</em> its filename extension and the content type found in its
 * own bytes are on the field's lists. The extension alone is the visitor's claim, and the bytes
 * alone would let an allowed image be uploaded under a name a browser or desktop treats as something
 * else. When the two disagree but both are allowed (PNG data named {@code .jpg}), the file passes as
 * what its bytes are, and {@link Result#extension()} names it for that. Content this site does not
 * recognize (SVG, HTML, PDF on an image field, anything else) never passes, whatever it is called.
 *
 * <p>Passing here is not the same as being safe. A file can begin with a valid image header and
 * still carry something else; the re-encode and malware-scan steps that follow are what make an
 * accepted file safe to store and show.
 *
 * @author SimIS Inc.
 */
public class ValidateFormUploadCommand {

  private static Log LOG = LogFactory.getLog(ValidateFormUploadCommand.class);

  /** The rejection reasons, recorded with a rejected submission once uploads are accepted */
  public static final String REASON_EMPTY = "empty";
  public static final String REASON_TOO_LARGE = "too_large";
  public static final String REASON_TYPE_NOT_ALLOWED = "type_not_allowed";
  public static final String REASON_UNREADABLE = "unreadable";

  /**
   * The outcome of {@link #check}.
   *
   * @param accepted whether the file may go on to the next step
   * @param reason one of the {@code REASON_} constants when rejected, otherwise null
   * @param message a plain sentence for the visitor naming the rule that failed, otherwise null. It
   *     never includes the detected type, a server path, or anything the visitor did not send
   * @param contentType the type found in the file's bytes when accepted, e.g. "image/png"
   * @param extension the extension that type is stored under when accepted, e.g. "png"
   */
  public record Result(boolean accepted, String reason, String message, String contentType, String extension) {

    static Result accept(String contentType, String extension) {
      return new Result(true, null, null, contentType, extension);
    }

    static Result reject(String reason, String message) {
      return new Result(false, reason, message, null, null);
    }
  }

  private ValidateFormUploadCommand() {
  }

  /**
   * Checks one file attached to a file field.
   *
   * @param field the file field, with its accepted kinds and size limit
   * @param submittedFileName the name the visitor's browser sent; only its extension is used
   * @param size the file's size in bytes, as the servlet container counted it
   * @param content the file's content, positioned at its first byte. At most 12 bytes are read and
   *     the stream is left open; the caller owns it
   */
  public static Result check(FormField field, String submittedFileName, long size, InputStream content) {
    String label = field != null && StringUtils.isNotBlank(field.getLabel()) ? field.getLabel().trim() : "The file";

    // Size first: it needs nothing read
    if (size <= 0) {
      return Result.reject(REASON_EMPTY, label + ": the file is empty. Choose another file.");
    }
    long limit = effectiveMaxBytes(field);
    if (size > limit) {
      return Result.reject(REASON_TOO_LARGE, label + ": files can be up to " + formatSize(limit) + ".");
    }

    List<FileKind> kinds = acceptedKinds(field);
    String typeMessage = label + ": this type of file isn't accepted. Choose " + describe(kinds) + ".";

    String extension = extensionOf(submittedFileName);
    if (extension == null || kinds.stream().noneMatch(kind -> kind.extensions().contains(extension))) {
      return Result.reject(REASON_TYPE_NOT_ALLOWED, typeMessage);
    }

    String contentType;
    try {
      contentType = DetectContentTypeCommand.detectStream(content);
    } catch (IOException e) {
      LOG.warn("Could not read an uploaded file's header bytes: " + e.getMessage());
      return Result.reject(REASON_UNREADABLE, label + ": the file could not be read. Try attaching it again.");
    }
    if (contentType == null || kinds.stream().noneMatch(kind -> kind.contentTypes().contains(contentType))) {
      return Result.reject(REASON_TYPE_NOT_ALLOWED, typeMessage);
    }

    // Named for what the bytes are, not for what the visitor called it
    String storedExtension = DetectContentTypeCommand.imageExtensionFor(contentType);
    return Result.accept(contentType, storedExtension != null ? storedExtension : extension);
  }

  /**
   * The field's own limit, but never above the site's upload limit: the site limit can be lowered
   * after a field was saved with a higher one.
   */
  static long effectiveMaxBytes(FormField field) {
    long fieldLimit = field != null && field.getMaxBytes() > 0 ? field.getMaxBytes() : FormFileFieldCommand.defaultMaxBytes();
    return Math.min(fieldLimit, FormFileFieldCommand.maxBytesCeiling());
  }

  /**
   * The field's kinds that this site still offers. A field saved with no kinds, or with only kinds
   * the site no longer offers, accepts nothing.
   */
  static List<FileKind> acceptedKinds(FormField field) {
    List<FileKind> kinds = new ArrayList<>();
    if (field == null || StringUtils.isBlank(field.getAcceptedKinds())) {
      return kinds;
    }
    for (String key : field.getAcceptedKinds().split(",")) {
      FileKind kind = FormFileFieldCommand.kind(key);
      if (kind != null && !kinds.contains(kind)) {
        kinds.add(kind);
      }
    }
    return kinds;
  }

  /**
   * The lowercase extension after the last dot of the file's own name, or null when there is none.
   * Browsers send a bare name, but some older ones send a full client path (C:\fakepath\shot.png), so
   * only the part after the last slash or backslash counts.
   */
  static String extensionOf(String submittedFileName) {
    if (StringUtils.isBlank(submittedFileName)) {
      return null;
    }
    String name = submittedFileName.trim();
    int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
    if (slash > -1) {
      name = name.substring(slash + 1);
    }
    int dot = name.lastIndexOf('.');
    if (dot < 0 || dot == name.length() - 1) {
      return null;
    }
    return name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static String describe(List<FileKind> kinds) {
    if (kinds.isEmpty()) {
      return "a different file";
    }
    List<String> labels = new ArrayList<>();
    for (FileKind kind : kinds) {
      labels.add(kind.label());
    }
    return String.join(" or ", labels);
  }

  /** Whole megabytes when the limit is at least 1 MB, otherwise whole kilobytes */
  static String formatSize(long bytes) {
    if (bytes >= FormFileFieldCommand.BYTES_PER_MEGABYTE) {
      return FormFileFieldCommand.toMegabytes(bytes) + " MB";
    }
    return Math.max(1, bytes / 1024) + " KB";
  }
}
