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

package com.simisinc.platform.presentation.controller.cms;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.URLDecoder;

import javax.servlet.http.HttpServletResponse;

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/3/18 4:00 PM
 */
public class StreamImageWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  private static Log LOG = LogFactory.getLog(StreamImageWidget.class);

  public WidgetContext execute(WidgetContext context) {

    // GET uri /assets/img/20180503171549-5/logo.png
    // yyyyMMddHHmmss
    LOG.debug("Found request uri: " + context.getUri());

    int startIdx = context.getUri().indexOf("-") + 1;
    int endIdx = context.getUri().indexOf("/", startIdx);
    if (endIdx == -1) {
      return null;
    }
    String fileIdValue = context.getUri().substring(startIdx, endIdx);
    long fileId = Long.parseLong(fileIdValue);

    Image record = ImageRepository.findById(fileId);
    if (record == null) {
      LOG.warn("Server image record does not exist: " + fileId);
      return null;
    }
    File file = new File(FileSystemCommand.getFileServerRootPath() + record.getFileServerPath());
    if (!file.isFile()) {
      LOG.warn("Server file does not exist: " + record.getFileServerPath());
      return null;
    }

    String requestedFile = context.getUri().substring(endIdx + 1);
    if (requestedFile.contains("?")) {
      requestedFile = requestedFile.substring(0, requestedFile.indexOf("?"));
    }

    try {
      requestedFile = URLDecoder.decode(requestedFile, "UTF-8");
    } catch (Exception e) {
      LOG.warn("Could not url decode: " + requestedFile);
    }

    if (!requestedFile.equals(record.getFilename())) {
      LOG.warn("Filename requested did not match saved filename");
      return null;
    }

    // Check for a last-modified header and return 304 if possible
    long lastModified = record.getCreated().getTime();
    long headerValue = context.getRequest().getDateHeader("If-Modified-Since");
    if (lastModified <= headerValue + 1000) {
      context.getResponse().setStatus(HttpServletResponse.SC_NOT_MODIFIED);
      context.setHandledResponse(true);
      return context;
    }

    // Send the file
    context.getResponse().setDateHeader("Last-Modified", lastModified);
    context.getResponse().setContentType(record.getFileType());
    context.getResponse().setContentLength((int) file.length());

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
    context.setHandledResponse(true);
    return context;
  }
}
