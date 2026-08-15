/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.presentation.widgets.cms;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.thymeleaf.util.StringUtils;

import org.jobrunr.scheduling.BackgroundJobRequest;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.SaveImageCommand;
import com.simisinc.platform.application.cms.ValidateImageCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.scheduler.cms.ImageVariantJob;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/3/18 4:00 PM
 */
public class ImageUploadWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  static String JSP = "/cms/image-upload-drop-zone.jsp";
  private static Log LOG = LogFactory.getLog(ImageUploadWidget.class);

  /**
   * Renders the drag-and-drop uploader that feeds this widget's own post() below (issue #1189).
   *
   * <p>Until this existed the widget was POST-only, so an image could only enter the library as a
   * side effect of uploading one from some other editor -- /admin/images itself held nothing but
   * the browser, and had no "add an image" path at all. A GET also reached
   * GenericWidget.execute()'s "MUST OVERRIDE THE DEFAULT EXECUTE METHOD" error branch.
   */
  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Give the drop zone the same ceiling post() enforces, in the whole megabytes Dropzone.js
    // expects, so an oversized file is rejected in the browser with the real limit in the message
    // rather than after a pointless round trip
    context.getRequest().setAttribute("maxUploadSize", String.valueOf(resolveMaxUploadMegabytes()));

    // Show the drop zone
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Prepare to save the file
    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    String serverSubPath = FileSystemCommand.generateFileServerSubPath("images");
    String uniqueFilename = FileSystemCommand.generateUniqueFilename(context.getUserId());

    // Find the file in the request and save it
    String submittedFilename = null;
    String extension = null;
    long fileLength = 0;
    File tempFile = null;
    try {
      Part filePart = context.getRequest().getPart("file");
      if (filePart == null) {
        LOG.warn("File part was not found in request");
        return respondWithError(context, "A file was not found, please choose a file and try again");
      }
      submittedFilename = Paths.get(filePart.getSubmittedFileName()).getFileName().toString(); // MSIE fix.
      if (submittedFilename.startsWith("mceclip0")) {
        submittedFilename = StringUtils.replace(submittedFilename, "mceclip0", "clip");
      }
      extension = FileSystemCommand.cleanExtension(FilenameUtils.getExtension(submittedFilename));
      // Resolve the target inside the file server root so user-derived values cannot traverse outside it
      tempFile = FileSystemCommand.resolveWithinRoot(serverRootPath, serverSubPath + uniqueFilename + "." + extension);
      if (tempFile == null) {
        LOG.warn("The upload target resolved outside the file server root: " + serverSubPath);
        return respondWithError(context, "The file could not be saved");
      }
      fileLength = filePart.getSize();
      long maxBytes = resolveMaxUploadBytes();
      if (fileLength > maxBytes) {
        return respondWithError(context,
            "The file exceeds the maximum allowed upload size of " + toMegabytes(maxBytes) + " MB");
      }
      if (fileLength > 0) {
        filePart.write(tempFile.getAbsolutePath());
      }
    } catch (Exception e) {
      // Report what actually went wrong. This branch previously swallowed the exception entirely --
      // no log, no message, no error status -- so a storage-layer failure (an unwritable volume, a
      // full disk, a permissions problem on the mounted file server root) was indistinguishable
      // from a bad file, and the browser was told nothing at all. Both audiences for this endpoint
      // are already admin/content-manager gated, so the underlying reason is safe to show them and
      // is the whole point: it is the difference between "use a .jpg" and "the mount is read-only"
      LOG.error("The uploaded file could not be saved to " + (tempFile != null ? tempFile.getPath() : "(unresolved)"), e);
      // Clean up the file
      if (tempFile != null && tempFile.exists()) {
        LOG.warn("Deleting an uploaded file: " + tempFile.getPath());
        tempFile.delete();
      }
      return respondWithError(context, "The file could not be saved: " + e.getMessage());
    }

    // Make sure a file was processed
    if (fileLength <= 0) {
      if (tempFile.exists()) {
        LOG.warn("Deleting an uploaded file: " + tempFile.getPath());
        tempFile.delete();
      }
      return respondWithError(context, "The file size was 0 and could not be saved");
    }

    // Populate the fields
    Image imageBean = new Image();
    imageBean.setFilename(submittedFilename);
    imageBean.setFileLength(fileLength);
    imageBean.setFileServerPath(serverSubPath + uniqueFilename + "." + extension);
    imageBean.setCreatedBy(context.getUserId());

    // Save the record
    Image image = null;
    try {
      ValidateImageCommand.checkFile(imageBean);
      image = SaveImageCommand.saveImage(imageBean);
      if (image == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      // Clean up the file
      if (tempFile.exists()) {
        LOG.warn("Deleting an uploaded file: " + tempFile.getPath());
        tempFile.delete();
      }
      context.setRequestObject(imageBean);
      return respondWithError(context, e.getMessage());
    }

    // Generate srcset-ready variants in the background (issue #411) -- not inline, so upload
    // response time does not depend on ImageMagick's speed
    BackgroundJobRequest.enqueue(new ImageVariantJob(image.getId()));

    // Return Json with the new image's URL
    context.setJson("{\"location\": \"" + "/assets/img/" + image.getUrl() + "\"}");
    return context;
  }

  /**
   * Answers a failed upload with a real HTTP 400 and the reason, rather than letting it fall
   * through to the container's redirect.
   *
   * <p>Every caller of this endpoint is an XMLHttpRequest -- the drop zone added for issue #1189
   * and the image pickers embedded in the site properties, web page, product, and blog editors --
   * and all of them decide success or failure from the status code. A widget POST that sets only
   * an error message produces no response body, so the container falls through to a redirect; the
   * XHR follows that redirect transparently and reports the reloaded page's HTTP 200 back to the
   * caller. A rejected upload was therefore indistinguishable from an accepted one. Setting an
   * explicit error status plus a JSON body gives both kinds of caller something unambiguous to
   * read, and matches what FolderFileDropZoneWidget already does for folder files.
   */
  private WidgetContext respondWithError(WidgetContext context, String message) {
    context.setErrorMessage(message);
    context.getResponse().setStatus(HttpServletResponse.SC_BAD_REQUEST);
    context.setJson("{\"error\": \"" + JsonCommand.toJson(message) + "\"}");
    return context;
  }

  private static long resolveMaxUploadBytes() {
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
   * The configured ceiling in whole megabytes, for Dropzone.js's maxFilesize and for the
   * too-large message. Rounds down, but never below 1, so a sub-megabyte limit still leaves the
   * drop zone with a usable number instead of 0.
   */
  private static long resolveMaxUploadMegabytes() {
    return toMegabytes(resolveMaxUploadBytes());
  }

  private static long toMegabytes(long bytes) {
    return Math.max(1L, bytes / 1_048_576L);
  }
}
