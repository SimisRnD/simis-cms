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

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.URLDecoder;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/18/18 1:45 PM
 */
public class StreamDatasetWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  private static Log LOG = LogFactory.getLog(StreamDatasetWidget.class);

  public WidgetContext execute(WidgetContext context) {

    // GET uri /assets/dataset/20180503171549-5/something.csv
    // yyyyMMddHHmmss
    LOG.debug("Found request uri: " + context.getUri());

    int startIdx = context.getUri().indexOf("-") + 1;
    int endIdx = context.getUri().indexOf("/", startIdx);
    String fileIdValue = context.getUri().substring(startIdx, endIdx);
    long fileId = Long.parseLong(fileIdValue);

    Dataset record = DatasetRepository.findById(fileId);
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

    context.getResponse().setHeader("Content-Disposition", "attachment; filename=\"" + record.getFilename() + "\"");
    context.getResponse().setHeader("Content-Transfer-Encoding", "binary");
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
