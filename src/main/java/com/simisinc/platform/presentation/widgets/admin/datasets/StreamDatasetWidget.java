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

package com.simisinc.platform.presentation.widgets.admin.datasets;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.FileDownloadCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Streams previously uploaded datasets
 *
 * @author matt rajkowski
 * @created 5/18/18 1:45 PM
 */
public class StreamDatasetWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  private static Log LOG = LogFactory.getLog(StreamDatasetWidget.class);

  public WidgetContext execute(WidgetContext context) {

    // GET uri /assets/dataset/20180503171549-5/something.csv

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

    // Retrieve the dataset and file handle
    Dataset record = DatasetRepository.findByWebPathAndId(webPath, fileId);
    File file = new File(FileSystemCommand.getFileServerRootPath() + record.getFileServerPath());
    if (!file.isFile()) {
      LOG.warn("Server file does not exist: " + record.getFileServerPath());
      return null;
    }

    // Set header info (served as a download; nosniff so the stored type cannot be re-sniffed to active content)
    context.getResponse().setHeader("X-Content-Type-Options", "nosniff");
    String safeName = FileDownloadCommand.sanitizeFilename(record.getFilename());
    context.getResponse().setHeader("Content-Disposition",
        safeName != null ? "attachment; filename=\"" + safeName + "\"" : "attachment");
    context.getResponse().setHeader("Content-Transfer-Encoding", "binary");
    context.getResponse().setContentType(record.getFileType());
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
      // Record the dataset download -- the full dataset content leaves the system. Access here is
      // gated by page configuration rather than a role check, so the actor may be anonymous.
      AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, "data.export", AuditEventCommand.SUCCESS,
          "dataset_file", String.valueOf(fileId), record.getFilename(), "format=" + record.getFileType());
    } catch (Exception e) {
      LOG.debug("Stream error: " + e.getMessage());
      AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, "data.export", AuditEventCommand.FAILURE,
          "dataset_file", String.valueOf(fileId), record.getFilename(), "format=" + record.getFileType());
    }
    context.setHandledResponse(true);
    return context;
  }
}
