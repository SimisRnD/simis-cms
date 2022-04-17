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

package com.simisinc.platform.application.admin;

import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.datasets.Dataset;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Checks and processes RSS Feed files
 *
 * @author matt rajkowski
 * @created 1/29/2019 3:20 PM
 */
public class ValidateRSSDatasetCommand {

  private static Log LOG = LogFactory.getLog(ValidateRSSDatasetCommand.class);

  public static void checkFile(Dataset datasetBean) throws DataException {

    // Get a file handle
    File datasetFile = DatasetFileCommand.getFile(datasetBean);
    if (datasetFile == null) {
      return;
    }

    // Check mime type
    String mimeType = null;
    try {
      mimeType = Files.probeContentType(datasetFile.toPath());
      LOG.debug("MimeType: " + mimeType);
    } catch (Exception e) {
      LOG.error("Error checking file for data", e);
    }
    if (mimeType == null) {
      String extension = FilenameUtils.getExtension(datasetFile.getAbsolutePath());
      if ("xml".equalsIgnoreCase(extension)) {
        mimeType = "application/rss+xml";
      } else {
        throw new DataException("Could not determine type");
      }
    }
    LOG.debug("MimeType: " + mimeType);
    datasetBean.setFileType(mimeType);

    // Validate the content before continuing
    SyndFeed feed = null;
    try {
      feed = new SyndFeedInput().build(datasetFile);
    } catch (Exception e) {
      throw new DataException("The content could not be parsed: " + e.getMessage());
    }
    if (StringUtils.isBlank(datasetBean.getName()) && StringUtils.isNotBlank(feed.getTitle())) {
      datasetBean.setName(feed.getTitle());
    }
    if (StringUtils.isBlank(datasetBean.getSourceInfo()) && StringUtils.isNotBlank(feed.getDescription())) {
      datasetBean.setSourceInfo(feed.getDescription());
    }
    datasetBean.setRowCount(feed.getEntries().size());
    datasetBean.setColumnCount(4);
    datasetBean.setColumnNames(new String[]{"title", "link", "pubDate", "description"});
  }

  public static boolean validateAllRows(Dataset dataset) throws DataException {

    // Load the rows
    List<String[]> rows = LoadRSSFeedCommand.loadRows(dataset, Integer.MAX_VALUE);

    // Use the field mappings
    List<String> fieldMappings = dataset.getFieldMappingsList();

    // Read the file and validate the records
    int rowCount = 0;
    for (String[] row : rows) {
      ++rowCount;
      String errorMessage = DatasetRecordCommand.validateRow(row, fieldMappings);
      if (errorMessage != null) {
        throw new DataException("Row validation error at row " + rowCount + ": " + errorMessage);
      }
    }
    return true;
  }
}
