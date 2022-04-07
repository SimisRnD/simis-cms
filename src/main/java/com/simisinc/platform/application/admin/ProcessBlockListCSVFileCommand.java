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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.DeleteBlockedIPListCommand;
import com.simisinc.platform.application.cms.SaveBlockedIPCommand;
import com.simisinc.platform.application.cms.SaveFilePartCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.BlockedIP;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.infrastructure.persistence.BlockedIPRepository;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.conversions.Conversions;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/25/20 4:10 PM
 */
public class ProcessBlockListCSVFileCommand {

  private static Log LOG = LogFactory.getLog(ProcessBlockListCSVFileCommand.class);

  public static int processCSV(WidgetContext context) throws DataException {

    int recordCount = 0;
    int removeCount = 0;

    FileItem fileItemBean = null;
    try {
      // Check for a file
      fileItemBean = SaveFilePartCommand.saveFile(context);
      if (fileItemBean == null) {
        throw new DataException("Valid file not found");
      }
      String serverRootPath = FileSystemCommand.getFileServerRootPath();
      File csvFile = new File(serverRootPath + fileItemBean.getFileServerPath());
      if (!csvFile.exists()) {
        throw new DataException("Valid file not found");
      }

      // Determine the CSV configuration
      CsvParserSettings parserSettings = new CsvParserSettings();
      parserSettings.setLineSeparatorDetectionEnabled(true);
      parserSettings.setHeaderExtractionEnabled(true);

      // Parses all records in one go
      CsvParser parser = new CsvParser(parserSettings);
      List<Record> recordList = parser.parseAllRecords(csvFile);
      parser.getRecordMetadata().convertFields(Conversions.toDate("yyyy-MM-dd hh:mm:ss")).set("Date");

      // Validate the results
      if (!parser.getRecordMetadata().containsColumn("IP Address")) {
        throw new DataException("CSV requires: IP Address column; optionally Date, Reason, Remove");
      }

      // Process the records
      for (Record record : recordList) {

        // IP Address is required
        String ipAddress = record.getString("IP Address");

        String reason = null;
        String remove = null;
        if (parser.getRecordMetadata().containsColumn("Reason")) {
          reason = record.getString("Reason");
        }
        if (parser.getRecordMetadata().containsColumn("Remove")) {
          remove = record.getString("Remove");
        }

        // Handle deleted records
        BlockedIP blockedIP = BlockedIPRepository.findByIpAddress(ipAddress);
        if ("true".equalsIgnoreCase(remove)) {
          if (blockedIP != null) {
            if (DeleteBlockedIPListCommand.delete(blockedIP)) {
              ++removeCount;
            }
          }
          continue;
        }

        // Skip duplicates
        if (blockedIP != null) {
          if ((StringUtils.isBlank(blockedIP.getReason()) &&
              StringUtils.isBlank(reason)) ||
              (blockedIP.getReason() != null && reason != null &&
                  blockedIP.getReason().equals(reason))) {
            continue;
          }
        } else {
          blockedIP = new BlockedIP();
          blockedIP.setIpAddress(ipAddress);
        }

        // Don't add your own IP
        if (ipAddress.equals(context.getRequest().getRemoteAddr())) {
          continue;
        }

        // Optional fields
        Date date = null;
        if (parser.getRecordMetadata().containsColumn("Date")) {
          date = record.getDate("Date");
        }

        // Prepare the new record
        blockedIP.setReason(reason);
        if (date != null) {
          blockedIP.setCreated(new Timestamp(date.getTime()));
        }
        SaveBlockedIPCommand.save(blockedIP);
        ++recordCount;
      }

    } catch (DataException data) {
      LOG.debug("An exception occurred: " + data.getMessage());
      // Let the user know
      context.setErrorMessage(data.getMessage());
      throw data;
    } finally {
      // Clean up the file if it exists
      SaveFilePartCommand.cleanupFile(fileItemBean);
    }
    LOG.debug("Records removed: " + removeCount);
    return recordCount;
  }
}
