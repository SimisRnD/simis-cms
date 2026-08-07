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

  /**
   * Summarizes the outcome of a CSV import so a caller can tell a full success from a partial one.
   * Previously a single bad row (e.g. a blank "Partial User Agent" cell) would throw out of the
   * per-row loop entirely -- silently abandoning every row after it with no accounting -- while the
   * rows processed before it stayed committed (no transaction spans the loop). Each row is now
   * handled independently, so this reports genuine successes and skips instead of just a raw count
   * that assumed every row succeeded.
   */
  public static class ImportResult {
    private final int recordCount;
    private final int skippedCount;
    private final int removedCount;
    private final int totalRowCount;

    public ImportResult(int recordCount, int skippedCount, int removedCount, int totalRowCount) {
      this.recordCount = recordCount;
      this.skippedCount = skippedCount;
      this.removedCount = removedCount;
      this.totalRowCount = totalRowCount;
    }

    public int getRecordCount() {
      return recordCount;
    }

    public int getSkippedCount() {
      return skippedCount;
    }

    public int getRemovedCount() {
      return removedCount;
    }

    public int getTotalRowCount() {
      return totalRowCount;
    }
  }

  public static ImportResult processCSV(WidgetContext context) throws DataException {

    int recordCount = 0;
    int removeCount = 0;
    int skippedCount = 0;
    int totalRowCount = 0;

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
      totalRowCount = recordList.size();
      parser.getRecordMetadata().convertFields(Conversions.toDate("yyyy-MM-dd hh:mm:ss")).set("Date");

      // Validate the results
      if (!parser.getRecordMetadata().containsColumn("Partial User Agent")) {
        throw new DataException("CSV requires: Partial User Agent column; optionally Label, Date, Remove");
      }

      // Process the records; a problem with one row (e.g. a blank "Partial User Agent" cell) must
      // not abort the rows that follow it, since rows are committed individually as they're
      // processed (no transaction spans this loop) -- so each row gets its own try/catch
      for (Record record : recordList) {
        try {

          // Partial User Agent is required. Trimmed here, before the duplicate/removal lookup --
          // univocity's CsvParserSettings only auto-trims unquoted cells by default, so a quoted
          // cell with incidental whitespace (e.g. round-tripped through this page's own "Download
          // CSV File" -> edit -> "Upload CSV File" flow) would otherwise miss an existing row here
          // even though SaveBotUserAgentCommand.save() trims before its own DB lookup -- mirrors
          // the same fix already applied in BotUserAgentFormWidget.post().
          String userAgent = StringUtils.trimToNull(record.getString("Partial User Agent"));

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
          // Check the actual outcome rather than assuming success -- save() can throw (e.g. a
          // blank/too-short value) or return null on a DB-layer failure
          BotUserAgent savedRecord = SaveBotUserAgentCommand.save(botUserAgent);
          if (savedRecord != null) {
            ++recordCount;
          } else {
            ++skippedCount;
            LOG.warn("A bot-list CSV row was not saved: " + userAgent);
          }
        } catch (Exception rowException) {
          ++skippedCount;
          LOG.warn("Skipping a bot-list CSV row due to an error: " + rowException.getMessage());
        }
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
    LOG.debug("Records removed: " + removeCount + ", skipped: " + skippedCount);
    return new ImportResult(recordCount, skippedCount, removeCount, totalRowCount);
  }
}
