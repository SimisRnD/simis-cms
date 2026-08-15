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

import java.lang.reflect.InvocationTargetException;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.AddImageToLibraryCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.cms.Image;
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

    // Find the file in the request
    Part filePart;
    try {
      filePart = context.getRequest().getPart("file");
    } catch (Exception e) {
      // Report what actually went wrong rather than swallowing it -- both audiences for this
      // endpoint are admin/content-manager gated, so the underlying reason is safe to show them and
      // is the whole point: it is the difference between "use a .jpg" and "the mount is read-only"
      LOG.error("The uploaded file part could not be read", e);
      return respondWithError(context, "The file could not be saved: " + e.getMessage());
    }
    if (filePart == null) {
      LOG.warn("File part was not found in request");
      return respondWithError(context, "A file was not found, please choose a file and try again");
    }

    // Store it in the image library. AddImageToLibraryCommand is the single write path into the
    // library (issue #1197), so an image added from the folder drop zone's "also add to the Image
    // Library" option lands a record indistinguishable from one uploaded here.
    Image image;
    try {
      image = AddImageToLibraryCommand.addFromPart(filePart, context.getUserId());
    } catch (DataException e) {
      return respondWithError(context, e.getMessage());
    }

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

  /**
   * The configured ceiling in whole megabytes, for Dropzone.js's maxFilesize. Read from
   * {@link AddImageToLibraryCommand}, which is what actually enforces the cap, so the number shown
   * in the browser cannot drift from the one the upload is rejected against.
   */
  private static long resolveMaxUploadMegabytes() {
    return AddImageToLibraryCommand.toMegabytes(AddImageToLibraryCommand.resolveMaxUploadBytes());
  }
}
