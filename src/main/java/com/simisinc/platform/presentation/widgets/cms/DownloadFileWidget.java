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
import java.io.FileInputStream;
import java.io.OutputStream;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.cms.LoadFileCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;
import com.simisinc.platform.presentation.controller.MultipartFileSender;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Streams previously uploaded files and videos, supports resume 
 *
 * @author matt rajkowski
 * @created 12/13/18 2:50 PM
 */
public class DownloadFileWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  private static Log LOG = LogFactory.getLog(DownloadFileWidget.class);

  public WidgetContext execute(WidgetContext context) {

    // GET uri /assets/file/20180503171549-5/something.pdf
    // GET uri /assets/view/20180503171549-5/something.pdf

    // Use the request uri
    String resourceValue = context.getUri().substring(context.getResourcePath().length() + 1);
    if (resourceValue.contains("/")) {
      resourceValue = resourceValue.substring(0, resourceValue.indexOf("/"));
    }
    LOG.debug("Using resource value: " + resourceValue);
    int dashIdx = resourceValue.lastIndexOf("-");
    if (dashIdx == -1) {
      return null;
    }

    // Determine the file id and web path
    String webPath = resourceValue.substring(0, dashIdx);
    String fileIdValue = resourceValue.substring(dashIdx + 1);
    long fileId = Long.parseLong(fileIdValue);
    if (fileId <= 0) {
      return null;
    }

    // Determine the file and access permissions
    FileItem record;
    if (context.hasRole("admin")) {
      // The file can be downloaded
      record = LoadFileCommand.loadItemById(fileId);
    } else {
      // User must have view access in the folder's user group
      record = LoadFileCommand.loadLatestFileByIdForAuthorizedUser(webPath, fileId, context.getUserId());
    }
    if (record == null) {
      LOG.warn("File record does not exist or no access: " + fileId);
      return null;
    }

    // Determine if this file is a remote URL
    if (record.getFileType() != null && record.getFileType().equals("URL")) {
      String url = record.getFilename();
      if (url.startsWith("http://") || url.startsWith("https://")) {
        // Update the download counter
        FileItemRepository.incrementDownloadCount(record);
        // Redirect to the URL
        context.setRedirect(url);
        return context;
      }
    }

    // Make sure it exists
    File file = FileSystemCommand.getFileServerRootPath(record.getFileServerPath());
    if (!file.isFile()) {
      LOG.warn("Server file does not exist: " + record.getFileServerPath());
      return null;
    }

    // Determine if the file should be sent with the mime type
    boolean doView = "true".equals(context.getPreferences().get("view"));

    // Determine if the file is being viewed or downloaded
    String mimeType = record.getMimeType();
    long lastModified = record.getModified().getTime();
    if (doView && StringUtils.isNotBlank(mimeType)) {

      // @todo go through this and use for all downloads so pause/resume works on large files

      if (mimeType.startsWith("video/")) {
        try {
          MultipartFileSender.fromFile(file)
              .with(context.getRequest())
              .with(context.getResponse())
              .withMimeType(mimeType)
              .withFilename(record.getFilename())
              .serveResource();
        } catch (Exception e) {
          LOG.debug("Video aborted");
        } finally {
          context.setHandledResponse(true);
          // @todo determine if whole range or end was viewed to register a download count
          // Update the download counter
          //    FileItemRepository.incrementDownloadCount(record);
        }
        return context;
      }

      // The file is being viewed (in a new window)
      context.getResponse().setHeader("Content-Disposition", "inline; filename=" + record.getFilename() + ";");

      // Check for a last-modified header and return 304 if possible
      long headerValue = context.getRequest().getDateHeader("If-Modified-Since");
      if (lastModified <= headerValue + 1000) {
        context.getResponse().setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        context.setHandledResponse(true);
        return context;
      }

    } else {
      // Force file to be downloaded
      mimeType = "application/octet-stream";
    }
    LOG.debug("Using mime type: " + mimeType);

    // Set header info
    context.getResponse().setDateHeader("Last-Modified", lastModified);
    context.getResponse().setContentType(mimeType);
    context.getResponse().setContentLength((int) file.length());

    // Check for head method
    if ("head".equalsIgnoreCase(context.getRequest().getMethod())) {
      context.setHandledResponse(true);
      return context;
    }
    
    // Stream the file
    try {
      FileInputStream in = new FileInputStream(file);
      OutputStream out = context.getResponse().getOutputStream();

      // Copy the contents of the file to the output stream
      byte[] buf = new byte[1024];
      int count = 0;
      while ((count = in.read(buf)) >= 0) {
        out.write(buf, 0, count);
      }
      out.close();
      in.close();
    } catch (Exception e) {
      LOG.debug("Stream error: " + e.getMessage());
    }

    // Update the download counter
    FileItemRepository.incrementDownloadCount(record);

    // Return success
    context.setHandledResponse(true);
    return context;
  }
}
