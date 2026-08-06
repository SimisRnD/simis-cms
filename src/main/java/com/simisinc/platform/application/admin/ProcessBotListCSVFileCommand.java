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

package com.simisinc.platform.application.admin;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.DeleteBotUserAgentListCommand;
import com.simisinc.platform.application.cms.SaveBotUserAgentCommand;
import com.simisinc.platform.application.cms.SaveFilePartCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.BotUserAgent;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.infrastructure.persistence.BotUserAgentRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
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
 * Handles an uploaded bot-list CSV file
 *
 * @author elizabeth houser
 */
public class ProcessBotListCSVFileCommand {

  private static Log LOG = LogFactory.getLog(ProcessBotListCSVFileCommand.class);

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
      File csvFile = FileSystemCommand.resolveWithinRoot(serverRootPath, fileItemBean.getFileServerPath());
      if (csvFile == null || !csvFile.exists()) {
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
      if (!parser.getRecordMetadata().containsColumn("Partial User Agent")) {
        throw new DataException("CSV requires: Partial User Agent column; optionally Label, Date, Remove");
      }

      // Process the records
      for (Record record : recordList) {

        // Partial User Agent is required
        String userAgent = record.getString("Partial User Agent");

        String label = null;
        String remove = null;
        if (parser.getRecordMetadata().containsColumn("Label")) {
          label = record.getString("Label");
        }
        if (parser.getRecordMetadata().containsColumn("Remove")) {
          remove = record.getString("Remove");
        }

        // Handle deleted records
        BotUserAgent botUserAgent = BotUserAgentRepository.findByUserAgent(userAgent);
        if ("true".equalsIgnoreCase(remove)) {
          if (botUserAgent != null) {
            if (DeleteBotUserAgentListCommand.delete(botUserAgent)) {
              ++removeCount;
            }
          }
          continue;
        }

        // Skip duplicates
        if (botUserAgent != null) {
          if ((StringUtils.isBlank(botUserAgent.getLabel()) &&
              StringUtils.isBlank(label)) ||
              (botUserAgent.getLabel() != null && label != null &&
                  botUserAgent.getLabel().equals(label))) {
            continue;
          }
        } else {
          botUserAgent = new BotUserAgent();
          botUserAgent.setUserAgent(userAgent);
        }

        // Optional fields
        Date date = null;
        if (parser.getRecordMetadata().containsColumn("Date")) {
          date = record.getDate("Date");
        }

        // Prepare the new record
        botUserAgent.setLabel(label);
        if (date != null) {
          botUserAgent.setCreated(new Timestamp(date.getTime()));
        }
        SaveBotUserAgentCommand.save(botUserAgent);
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
