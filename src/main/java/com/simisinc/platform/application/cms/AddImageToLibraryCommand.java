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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import jakarta.servlet.http.Part;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.jobrunr.scheduling.BackgroundJobRequest;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.scheduler.cms.ImageVariantJob;

/**
 * The single write path into the image library -- the store the image picker, {@code /assets/img/},
 * {@code image:srcset()}, and image variants all read from.
 *
 * <p>Extracted from {@code ImageUploadWidget} for issue #1197, where a second entry point was
 * needed: the folder drop zone can now also register an uploaded image here. Everything the library
 * requires lives in one place rather than being re-implemented per caller -- the date-partitioned
 * {@code images/} sub-path, a collision-proof filename, root-contained path resolution, the upload
 * size cap, mime/dimension validation, deleting the bytes again if the record can't be saved, and
 * enqueuing srcset variant generation (issue #411).
 *
 * <p>Callers hand over bytes in one of two ways and get back the saved {@link Image}:
 * {@link #addFromPart} consumes a multipart part straight from a request, and {@link #addFromFile}
 * copies a file that is already on the file server. The copy in {@code addFromFile} is deliberate:
 * pointing an image record at another record's bytes would make either record's delete silently
 * break the other.
 *
 * @author SimIS Inc.
 * @created 8/14/2026
 */
public class AddImageToLibraryCommand {

  private static Log LOG = LogFactory.getLog(AddImageToLibraryCommand.class);

  /**
   * Adds an uploaded multipart file to the image library.
   *
   * @param filePart the part holding the uploaded bytes
   * @param userId the user the image is created by
   * @return the saved image record
   * @throws DataException when the file cannot be stored, is not a readable image, or the record cannot be saved
   */
  public static Image addFromPart(Part filePart, long userId) throws DataException {

    // Determine the name the browser submitted
    String submittedFilename = Paths.get(filePart.getSubmittedFileName()).getFileName().toString(); // MSIE fix.
    if (submittedFilename.startsWith("mceclip0")) {
      // TinyMCE names every pasted clipboard image "mceclip0", so the library would fill up with
      // records that all look identical
      submittedFilename = StringUtils.replace(submittedFilename, "mceclip0", "clip");
    }

    long fileLength = filePart.getSize();
    checkUploadSize(fileLength);

    String serverPath = generateServerPath(submittedFilename, userId);
    File destination = resolveDestination(serverPath);
    try {
      filePart.write(destination.getAbsolutePath());
    } catch (Exception e) {
      // Carry the underlying reason, don't flatten it: a storage-layer failure (an unwritable
      // volume, a full disk, a permissions problem on the mounted file server root) is otherwise
      // indistinguishable from a bad file. Every caller of this is admin/content-manager gated, so
      // it is the difference between "use a .jpg" and "the mount is read-only"
      LOG.error("The uploaded file could not be saved to " + destination.getPath(), e);
      deleteFile(destination);
      throw new DataException("The file could not be saved: " + e.getMessage());
    }
    return register(destination, serverPath, submittedFilename, fileLength, userId);
  }

  /**
   * Adds a copy of a file that is already stored on the file server to the image library. The
   * source file is left untouched and keeps its own bytes.
   *
   * @param sourceFile the existing file to copy into the library
   * @param submittedFilename the name to show in the library
   * @param userId the user the image is created by
   * @return the saved image record
   * @throws DataException when the file cannot be stored, is not a readable image, or the record cannot be saved
   */
  public static Image addFromFile(File sourceFile, String submittedFilename, long userId) throws DataException {

    if (sourceFile == null || !sourceFile.isFile()) {
      throw new DataException("The file could not be found");
    }

    long fileLength = sourceFile.length();
    checkUploadSize(fileLength);

    String serverPath = generateServerPath(submittedFilename, userId);
    File destination = resolveDestination(serverPath);
    try {
      Files.copy(sourceFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception e) {
      // Same reasoning as addFromPart: keep the storage-layer reason rather than flattening it
      LOG.error("The file could not be copied into the image library at " + destination.getPath(), e);
      deleteFile(destination);
      throw new DataException("The file could not be saved: " + e.getMessage());
    }
    return register(destination, serverPath, submittedFilename, fileLength, userId);
  }

  /**
   * Validates the stored bytes as an image, saves the record, and queues variant generation. The
   * bytes are removed again if no record ends up pointing at them, since nothing could reach them.
   */
  private static Image register(File destination, String serverPath, String submittedFilename, long fileLength,
      long userId) throws DataException {

    Image imageBean = new Image();
    imageBean.setFilename(submittedFilename);
    imageBean.setFileLength(fileLength);
    imageBean.setFileServerPath(serverPath);
    imageBean.setCreatedBy(userId);

    Image image;
    try {
      // Sets the mime type and dimensions, and rejects anything that isn't a readable image
      ValidateImageCommand.checkFile(imageBean);
      image = SaveImageCommand.saveImage(imageBean);
      if (image == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      deleteFile(destination);
      throw e;
    }

    // Generate srcset-ready variants in the background (issue #411) -- not inline, so upload
    // response time does not depend on ImageMagick's speed
    BackgroundJobRequest.enqueue(new ImageVariantJob(image.getId()));
    return image;
  }

  /**
   * Builds the root-relative path the bytes will be written to and stored on the record: a
   * date-partitioned path under {@code images/}, with a unique filename so two uploads of the same
   * name cannot overwrite each other.
   */
  private static String generateServerPath(String submittedFilename, long userId) {
    String serverSubPath = FileSystemCommand.generateFileServerSubPath("images");
    String uniqueFilename = FileSystemCommand.generateUniqueFilename(userId);
    String extension = FileSystemCommand.cleanExtension(FilenameUtils.getExtension(submittedFilename));
    return serverSubPath + uniqueFilename + "." + extension;
  }

  /** Resolves the target inside the file server root so user-derived values cannot traverse outside it */
  private static File resolveDestination(String serverPath) throws DataException {
    File destination = FileSystemCommand.resolveWithinRoot(FileSystemCommand.getFileServerRootPath(), serverPath);
    if (destination == null) {
      throw new DataException("The file could not be saved");
    }
    return destination;
  }

  private static void checkUploadSize(long fileLength) throws DataException {
    if (fileLength <= 0) {
      throw new DataException("The file size was 0 and could not be saved");
    }
    long maxBytes = resolveMaxUploadBytes();
    if (fileLength > maxBytes) {
      throw new DataException("The file exceeds the maximum allowed upload size of " + toMegabytes(maxBytes) + " MB");
    }
  }

  /** The configured ceiling for an image upload, in bytes. Public so a drop zone can show the same
   *  number in the browser that this command rejects against. */
  public static long resolveMaxUploadBytes() {
    long maxBytes = 10_485_760L; // 10MB default
    String prop = LoadSitePropertyCommand.loadByName("system.upload.maxBytes");
    if (prop != null && !prop.isBlank()) {
      try {
        maxBytes = Long.parseLong(prop.trim());
      } catch (NumberFormatException ignored) {
      }
    }
    return maxBytes;
  }

  /**
   * A byte count in whole megabytes, for Dropzone.js's maxFilesize and the too-large message.
   * Rounds down, but never below 1, so a sub-megabyte limit still leaves the drop zone with a
   * usable number instead of 0.
   */
  public static long toMegabytes(long bytes) {
    return Math.max(1L, bytes / 1_048_576L);
  }

  private static void deleteFile(File file) {
    if (file != null && file.exists()) {
      LOG.warn("Deleting an uploaded file: " + file.getPath());
      file.delete();
    }
  }
}
