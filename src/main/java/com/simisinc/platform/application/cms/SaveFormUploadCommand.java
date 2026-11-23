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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.domain.model.cms.FormUpload;
import com.simisinc.platform.infrastructure.persistence.cms.FormUploadRepository;

import jakarta.servlet.http.Part;

/**
 * Takes one file a visitor attached to a form through the whole upload pipeline (issue #2130), and
 * stores it only if it passes every step:
 *
 * <ol>
 * <li>Size and type, before anything is written ({@link ValidateFormUploadCommand}).</li>
 * <li>A quarantine copy under {@code forms/quarantine/} in the file-server root, with a name the
 * server chose, stopping at the size limit rather than trusting the size the browser declared.</li>
 * <li>Re-encoding from the pixels ({@link SanitizeFormUploadCommand}).</li>
 * <li>A malware scan of the file as it arrived, then of the re-encoded image that will be stored
 * ({@link MalwareScanCommand}): a file matching a signature is rejected and logged even though the
 * re-encode might have made it harmless, and what is stored is what was scanned.</li>
 * <li>The re-encoded image and its thumbnail, written under {@code forms/<form>/} with server-chosen
 * names, and a form_uploads row marked clean.</li>
 * </ol>
 *
 * <p>The quarantine copy is deleted whatever happens. Any rejection deletes everything written for
 * the file and returns the step's own plain message and reason; nothing is stored for it.
 *
 * <p>The caller runs this only after the form's other gates (honeypot, required fields, captcha,
 * rate limit) have passed, so nothing reaches disk for a submission that was going to be refused.
 *
 * @author SimIS Inc.
 */
public class SaveFormUploadCommand {

  private static Log LOG = LogFactory.getLog(SaveFormUploadCommand.class);

  /** The visitor's file could not be written or recorded: a server problem, not the file */
  public static final String REASON_SYSTEM_ERROR = "system_error";

  static final String QUARANTINE_DIRECTORY = "forms/quarantine/";
  static final String UPLOAD_DIRECTORY = "forms/";

  /** Where an uploaded file comes from: a servlet Part in a request, or bytes in a test */
  public interface UploadSource {

    /** The name the browser sent, used for its extension and kept for display */
    String submittedFileName();

    /** The size the servlet container counted */
    long size();

    /** A new stream positioned at the file's first byte; the caller closes it */
    InputStream openStream() throws IOException;
  }

  /**
   * @param accepted whether the file was stored
   * @param reason the failing step's reason when rejected, e.g. "too_large" or "infected"
   * @param message a plain sentence for the visitor when rejected
   * @param upload the stored file's record when accepted
   */
  public record Result(boolean accepted, String reason, String message, FormUpload upload) {

    static Result reject(String reason, String message) {
      return new Result(false, reason, message, null);
    }
  }

  private SaveFormUploadCommand() {
  }

  public static UploadSource fromPart(Part part) {
    return new UploadSource() {
      @Override
      public String submittedFileName() {
        return part.getSubmittedFileName();
      }

      @Override
      public long size() {
        return part.getSize();
      }

      @Override
      public InputStream openStream() throws IOException {
        return part.getInputStream();
      }
    };
  }

  /**
   * @param formUniqueId the form the file was attached to
   * @param field the file field
   * @param source the file
   */
  public static Result save(String formUniqueId, FormField field, UploadSource source) {
    String label = field != null && StringUtils.isNotBlank(field.getLabel()) ? field.getLabel().trim() : "The file";
    Result systemError = Result.reject(REASON_SYSTEM_ERROR, label + ": the file couldn't be saved. Try again.");

    // 1. Size and type, from the declared size and the first bytes, before any write
    ValidateFormUploadCommand.Result check;
    try (InputStream in = source.openStream()) {
      check = ValidateFormUploadCommand.check(field, source.submittedFileName(), source.size(), in);
    } catch (IOException e) {
      LOG.warn("Could not open an uploaded file: " + e.getMessage());
      return Result.reject(ValidateFormUploadCommand.REASON_UNREADABLE,
          label + ": the file could not be read. Try attaching it again.");
    }
    if (!check.accepted()) {
      return Result.reject(check.reason(), check.message());
    }

    String root = FileSystemCommand.getFileServerRootPath();
    File quarantined = root != null ? FileSystemCommand.resolveWithinRoot(root, QUARANTINE_DIRECTORY + UUID.randomUUID() + ".upload") : null;
    if (quarantined == null) {
      LOG.error("Form uploads cannot be saved: the file-server root is not available");
      return systemError;
    }
    List<File> written = new ArrayList<>();
    try {
      // 2. Quarantine copy, stopped at the limit
      long limit = ValidateFormUploadCommand.effectiveMaxBytes(field);
      Files.createDirectories(quarantined.getParentFile().toPath());
      long copied;
      try (InputStream in = source.openStream(); OutputStream out = Files.newOutputStream(quarantined.toPath())) {
        copied = copyAtMost(in, out, limit + 1);
      }
      if (copied > limit) {
        return Result.reject(ValidateFormUploadCommand.REASON_TOO_LARGE,
            label + ": files can be up to " + ValidateFormUploadCommand.formatSize(limit) + ".");
      }

      // 3. Re-encode
      SanitizeFormUploadCommand.Result sanitized = SanitizeFormUploadCommand.sanitize(field, quarantined);
      if (!sanitized.accepted()) {
        return Result.reject(sanitized.reason(), sanitized.message());
      }
      SanitizeFormUploadCommand.Sanitized image = sanitized.image();

      // 4. Scan what arrived, then what will be stored
      MalwareScanCommand.Result scan = MalwareScanCommand.scan(field, quarantined);
      if (scan.accepted()) {
        scan = MalwareScanCommand.scan(field, image.bytes());
      }
      if (!scan.accepted()) {
        return Result.reject(scan.reason(), scan.message());
      }

      // 5. Store the re-encoded image and its thumbnail under names the server chose
      String directory = UPLOAD_DIRECTORY + directoryName(formUniqueId) + "/";
      String baseName = UUID.randomUUID().toString();
      String filePath = directory + baseName + "." + image.extension();
      String thumbnailPath = directory + baseName + "-thumbnail." + image.thumbnailExtension();
      File file = FileSystemCommand.resolveWithinRoot(root, filePath);
      File thumbnail = FileSystemCommand.resolveWithinRoot(root, thumbnailPath);
      if (file == null || thumbnail == null) {
        return systemError;
      }
      Files.createDirectories(file.getParentFile().toPath());
      written.add(file);
      Files.write(file.toPath(), image.bytes());
      written.add(thumbnail);
      Files.write(thumbnail.toPath(), image.thumbnail());

      FormUpload upload = new FormUpload();
      upload.setFormUniqueId(formUniqueId);
      upload.setFieldName(field != null ? field.getName() : null);
      upload.setOriginalFilename(displayName(source.submittedFileName()));
      upload.setFilePath(filePath);
      upload.setContentType(image.contentType());
      upload.setFileLength(image.bytes().length);
      upload.setWidth(image.width());
      upload.setHeight(image.height());
      upload.setThumbnailPath(thumbnailPath);
      upload.setThumbnailContentType(image.thumbnailContentType());
      upload.setScanStatus(FormUpload.SCAN_CLEAN);
      upload.setScanChecked(new Timestamp(System.currentTimeMillis()));
      if (FormUploadRepository.add(upload) == null) {
        deleteQuietly(written);
        return systemError;
      }
      written.clear();
      return new Result(true, null, null, upload);
    } catch (IOException e) {
      LOG.error("Could not save a form upload: " + e.getMessage());
      return systemError;
    } finally {
      deleteQuietly(written);
      deleteQuietly(List.of(quarantined));
    }
  }

  /**
   * Removes uploads already stored for a submission that then failed -- a later file was rejected,
   * or the submission itself could not be saved -- so nothing is kept for a submission that was not.
   */
  public static void discard(List<FormUpload> uploads) {
    if (uploads == null) {
      return;
    }
    String root = FileSystemCommand.getFileServerRootPath();
    for (FormUpload upload : uploads) {
      List<File> files = new ArrayList<>();
      if (root != null) {
        files.add(FileSystemCommand.resolveWithinRoot(root, upload.getFilePath()));
        if (StringUtils.isNotBlank(upload.getThumbnailPath())) {
          files.add(FileSystemCommand.resolveWithinRoot(root, upload.getThumbnailPath()));
        }
      }
      deleteQuietly(files);
      FormUploadRepository.remove(upload);
    }
  }

  /**
   * The visitor's own filename, kept only to show an admin: the last path segment, without control
   * characters, quotes or backslashes, at most 255 characters. It is never used to name a file.
   */
  static String displayName(String submittedFileName) {
    if (StringUtils.isBlank(submittedFileName)) {
      return null;
    }
    String name = submittedFileName.replaceAll(".*[/\\\\]", "").replaceAll("[\\x00-\\x1f\\x7f\"\\\\]", "").trim();
    return name.isEmpty() ? null : StringUtils.left(name, 255);
  }

  /** The form's unique id reduced to a safe directory name: lowercase letters, digits and dashes */
  static String directoryName(String formUniqueId) {
    String name = StringUtils.defaultString(formUniqueId).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-");
    name = StringUtils.left(StringUtils.strip(name, "-"), 100);
    return StringUtils.isBlank(name) ? "form" : name;
  }

  /** Copies until the stream ends or {@code max} bytes have been copied; returns the count */
  static long copyAtMost(InputStream in, OutputStream out, long max) throws IOException {
    byte[] buffer = new byte[64 * 1024];
    long copied = 0;
    int read;
    while (copied < max && (read = in.read(buffer, 0, (int) Math.min(buffer.length, max - copied))) > 0) {
      out.write(buffer, 0, read);
      copied += read;
    }
    return copied;
  }

  private static void deleteQuietly(List<File> files) {
    for (File file : files) {
      if (file == null) {
        continue;
      }
      try {
        Files.deleteIfExists(file.toPath());
      } catch (IOException e) {
        LOG.error("Could not delete a form upload file: " + file.getPath() + " (" + e.getMessage() + ")");
      }
    }
  }
}
