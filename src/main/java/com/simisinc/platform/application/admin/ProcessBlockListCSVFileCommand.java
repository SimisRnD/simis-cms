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
import com.simisinc.platform.application.cms.IpRangeCommand;
import com.simisinc.platform.application.cms.SaveBlockedIPCommand;
import com.simisinc.platform.application.cms.SaveFilePartCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.BlockedIP;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.infrastructure.persistence.BlockedIPRepository;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Handles uploaded CSV file
 *
 * @author matt rajkowski
 * @created 3/25/20 4:10 PM
 */
public class ProcessBlockListCSVFileCommand {

  private static Log LOG = LogFactory.getLog(ProcessBlockListCSVFileCommand.class);

  /**
   * The outcome of a CSV import: how many rows were saved, removed, or skipped, plus a
   * ready-to-display summary. A single malformed row no longer aborts the whole import (see
   * processCSV()), so the caller needs more than a bare success count to tell the admin what
   * actually happened.
   */
  public static final class ImportResult {
    private final int savedCount;
    private final int removedCount;
    private final int skippedCount;
    private final List<String> skipDetails;
    private final List<String> conflictWarnings;

    ImportResult(int savedCount, int removedCount, int skippedCount, List<String> skipDetails,
        List<String> conflictWarnings) {
      this.savedCount = savedCount;
      this.removedCount = removedCount;
      this.skippedCount = skippedCount;
      this.skipDetails = Collections.unmodifiableList(skipDetails);
      this.conflictWarnings = Collections.unmodifiableList(conflictWarnings);
    }

    public int getSavedCount() {
      return savedCount;
    }

    public int getRemovedCount() {
      return removedCount;
    }

    public int getSkippedCount() {
      return skippedCount;
    }

    /** One entry per skipped row, e.g. "Row 3 (not-an-ip): A valid IPv4 or IPv6 address ... is required" */
    public List<String> getSkipDetails() {
      return skipDetails;
    }

    /**
     * One entry per saved row that shadows (or is shadowed by) an entry on the other list --
     * Allowed always wins over Blocked (BlockedIPListCommand.passesCheck), so a block saved here
     * that's already covered by an Allowed entry can never actually fire.
     */
    public List<String> getConflictWarnings() {
      return conflictWarnings;
    }

    public String getSummaryMessage() {
      int attemptedCount = savedCount + skippedCount;
      StringBuilder message = new StringBuilder();
      if (attemptedCount > 0) {
        message.append("Imported ").append(savedCount).append(" of ").append(attemptedCount)
            .append(" row").append(attemptedCount != 1 ? "s" : "").append(" successfully.");
      } else {
        message.append("No rows required an import.");
      }
      if (removedCount > 0) {
        message.append(" ").append(removedCount).append(" record").append(removedCount != 1 ? "s" : "")
            .append(" removed.");
      }
      if (skippedCount > 0) {
        message.append(" ").append(skippedCount).append(" row").append(skippedCount != 1 ? "s" : "")
            .append(" were skipped -- see the application log for details.");
      }
      if (!conflictWarnings.isEmpty()) {
        message.append(" ").append(conflictWarnings.size()).append(" saved row")
            .append(conflictWarnings.size() != 1 ? "s" : "")
            .append(" conflict with an entry already on the Allowed list, so that block can never actually fire --")
            .append(" see the application log for details.");
      }
      return message.toString();
    }
  }

  public static ImportResult processCSV(WidgetContext context) throws DataException {

    int savedCount = 0;
    int removedCount = 0;
    int skippedCount = 0;
    List<String> skipDetails = new ArrayList<>();
    List<String> conflictWarnings = new ArrayList<>();

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
      if (!parser.getRecordMetadata().containsColumn("IP Address")) {
        throw new DataException("CSV requires: IP Address column; optionally Date, Reason, Remove");
      }

      // Process the records; each row is isolated in its own try/catch so one malformed row
      // (e.g. an invalid address) is skipped and logged instead of aborting every row after it --
      // rows before the failure are already saved and live by the time a thrown exception would
      // otherwise be seen, so silently stopping partway through is worse than continuing
      int rowNumber = 0;
      for (Record record : recordList) {
        ++rowNumber;
        String ipAddress = null;
        try {
          // IP Address is required
          ipAddress = record.getString("IP Address");

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
                ++removedCount;
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

          // Don't add your own IP, whether as an exact match or as part of a submitted CIDR range.
          // Trimmed to match SaveBlockedIPCommand.save()'s own trim-before-validate -- otherwise
          // incidental CSV-cell whitespace slips past this guard and gets saved as a real block
          // on the admin's own current IP.
          String trimmedIpAddress = StringUtils.trimToNull(ipAddress);
          if (trimmedIpAddress != null
              && IpRangeCommand.matches(trimmedIpAddress, context.getRequest().getRemoteAddr())) {
            ++skippedCount;
            skipDetails.add("Row " + rowNumber + " (" + ipAddress + "): matches your own current IP, skipped");
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
          ++savedCount;
          // Surface the cross-list shadowing warning the same way the single-entry form does --
          // an Allowed entry covering this address means this block can never actually fire.
          String conflictWarning = SaveBlockedIPCommand.getLastConflictWarning();
          if (conflictWarning != null) {
            String conflictDetail = "Row " + rowNumber + " (" + ipAddress + "): " + conflictWarning;
            conflictWarnings.add(conflictDetail);
            LOG.warn("Blocked IP CSV import saved a shadowed entry -- " + conflictDetail);
          }
        } catch (Exception rowException) {
          ++skippedCount;
          String reasonMessage = rowException.getMessage() != null
              ? rowException.getMessage()
              : rowException.getClass().getSimpleName();
          String detail = "Row " + rowNumber + (ipAddress != null ? " (" + ipAddress + ")" : "") + ": " + reasonMessage;
          skipDetails.add(detail);
          LOG.warn("Skipped a row while importing the block list -- " + detail);
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
    LOG.debug("Records saved: " + savedCount + ", removed: " + removedCount + ", skipped: " + skippedCount);
    return new ImportResult(savedCount, removedCount, skippedCount, skipDetails, conflictWarnings);
  }
}
