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

package com.simisinc.platform.infrastructure.scheduler.admin;

import com.simisinc.platform.application.datasets.DatasetFileCommand;
import com.simisinc.platform.application.datasets.DeleteDatasetItemsCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.List;

/**
 * Imports records as items belonging to a collection
 *
 * @author matt rajkowski
 * @created 5/21/18 12:50 PM
 */
@NoArgsConstructor
public class ProcessDatasetJob implements JobRequest {

  private static Log LOG = LogFactory.getLog(ProcessDatasetJob.class);

  @Getter
  @Setter
  private long datasetId = -1;

  @Getter
  @Setter
  private long modifiedByUserId = -1;

  public ProcessDatasetJob(Dataset dataset) {
    datasetId = dataset.getId();
    modifiedByUserId = dataset.getModifiedBy();
  }

  public long getDatasetId() {
    return datasetId;
  }

  public void setDatasetId(long datasetId) {
    this.datasetId = datasetId;
  }

  public long getModifiedByUserId() {
    return modifiedByUserId;
  }

  public void setModifiedByUserId(long modifiedByUserId) {
    this.modifiedByUserId = modifiedByUserId;
  }

  @Override
  public Class<ProcessDatasetJobRequestHandler> getJobRequestHandler() {
    return ProcessDatasetJobRequestHandler.class;
  }

  public static class ProcessDatasetJobRequestHandler implements JobRequestHandler<ProcessDatasetJob> {
    @Override
    @Job(name = "Process a dataset", retries = 1)
    public void run(ProcessDatasetJob jobRequest) {

      // Load dataset
      Dataset dataset = DatasetRepository.findById(jobRequest.getDatasetId());
      if (dataset == null) {
        LOG.error("Dataset not found: " + jobRequest.getDatasetId());
        return;
      }

      // Load collection
      Collection collection = LoadCollectionCommand.loadCollectionByUniqueId(dataset.getCollectionUniqueId());
      if (collection == null) {
        LOG.error("Collection not found: " + dataset.getCollectionUniqueId());
        return;
      }

      // Handle non-persisted values
      long modifiedByUserId = jobRequest.getModifiedByUserId();
      dataset.setModifiedBy(modifiedByUserId);

      // Run the conversion
      LOG.info("Processing the dataset... " + dataset.getName());
      boolean didProcessStart = false;
      String message = null;
      long startProcessTime = System.currentTimeMillis();
      try {
        if (DatasetRepository.markAsProcessStarted(dataset)) {
          // Set a sync timestamp for this sync
          didProcessStart = true;
          Timestamp timestamp = new Timestamp(System.currentTimeMillis());
          DatasetRepository.resetSyncTimestamp(dataset, timestamp);

          // Best-effort, non-blocking check: warn if the source file's column order may have
          // drifted since this dataset's field mapping was last confirmed. Positional formats
          // (CSV/TSV) map source columns to target fields purely by index, so two columns
          // swapping position (e.g. Phone and Email) silently cross-wires values with no other
          // symptom. This never fails the sync -- headers legitimately aren't available for
          // every source type -- it only augments syncMessage so it's visible on the Sync tab.
          String columnOrderWarning = detectColumnOrderWarning(dataset);

          // Start this run's progress counter fresh. dataset was just loaded from the
          // repository and may still carry rows_processed left over from a previous run;
          // without resetting it, a failure very early in this run (before the first
          // in-loop checkpoint) would report stale progress from last time instead of this
          // run's true (near-zero) progress.
          dataset.setRowsProcessed(0);

          // Add/Update records
          boolean conversionCompleted = false;
          Exception conversionError = null;
          try {
            conversionCompleted = DatasetFileCommand.convertFileToCollection(dataset, collection);
          } catch (Exception e) {
            conversionError = e;
          }

          int rowsProcessed = dataset.getRowsProcessed();
          int totalRows = dataset.getRowCount();
          String totalRowsSuffix = totalRows > -1 ? " of " + totalRows : "";

          if (conversionError == null && conversionCompleted && rowsProcessed > 0) {
            // Remove/Hide inactive/stale records
            try {
              int deleteCount = DeleteDatasetItemsCommand.deleteItemsForDataset(dataset, timestamp);
              LOG.debug("Deleted stale dataset records: " + deleteCount);
              message = columnOrderWarning;
            } catch (Exception e) {
              // The add/update pass completed, but the separate stale-record cleanup pass
              // failed -- say so explicitly instead of leaving syncMessage as just this one
              // exception string, so it's clear the sync is not fully reconciled.
              LOG.error("Stale record cleanup error", e);
              message = appendMessage(columnOrderWarning,
                  "Processed " + rowsProcessed + totalRowsSuffix + " row(s) successfully, but the "
                      + "stale-record cleanup pass failed and did not complete (" + e.getMessage()
                      + "). Records no longer present in the source may not have been removed -- "
                      + "please review and reconcile manually.");
            }
          } else if (conversionError != null) {
            // A mid-file failure: rows processed before the error are already committed, the
            // remaining rows were never attempted, and stale-record cleanup never ran. Make all
            // three of those facts explicit so an admin reading the Sync tab knows to
            // reconcile manually rather than assuming the sync simply finished.
            LOG.error("Processing Error", conversionError);
            message = appendMessage(columnOrderWarning,
                "Sync stopped early after processing " + rowsProcessed + totalRowsSuffix
                    + " row(s) due to an error: " + conversionError.getMessage()
                    + ". The stale-record cleanup pass was skipped as a result -- please review "
                    + "the synced records manually to reconcile.");
          } else if (!conversionCompleted) {
            // convertFileToCollection() returned false without throwing (e.g. an unsupported
            // file type) -- previously this was silent (LOG.debug only, no syncMessage at all).
            LOG.debug("Conversion error, records processed: " + rowsProcessed);
            message = appendMessage(columnOrderWarning,
                "The sync could not process this file (processed " + rowsProcessed + totalRowsSuffix
                    + " row(s) before stopping). The stale-record cleanup pass was skipped as a "
                    + "result -- please review the synced records manually to reconcile.");
          } else {
            // Conversion completed without error but processed zero rows (e.g. an empty file).
            // Call this out explicitly -- left silent, it looks identical to a normal, fully
            // successful sync on the Sync tab.
            LOG.debug("Conversion completed with zero rows processed");
            message = appendMessage(columnOrderWarning,
                "No rows were processed during this sync" + (totalRows > 0 ? " (source reports " + totalRows
                    + " row(s))" : "") + ". The stale-record cleanup pass was skipped as a precaution -- "
                    + "please check the source file if this is unexpected.");
          }
        }
      } catch (Exception e) {
        LOG.error("Processing Error", e);
        message = e.getMessage();
      }

      // Mark the process as finished
      long endProcessTime = System.currentTimeMillis();
      long totalTime = endProcessTime - startProcessTime;
      if (didProcessStart) {
        dataset.setTotalProcessTime(totalTime);
        // @todo use a SyncResult object
        DatasetRepository.saveSyncResult(dataset, message);
        DatasetRepository.markAsProcessFinished(dataset, message);
        LOG.debug("Finished " + totalTime + "ms");
      }
    }

    /**
     * Joins a (possibly null) leading warning with a (possibly null) detail message, so a
     * column-order warning can be prepended onto whatever message the run's outcome produces
     * without either side needing to worry about the other being blank.
     */
    private static String appendMessage(String warning, String detail) {
      if (StringUtils.isBlank(warning)) {
        return detail;
      }
      if (StringUtils.isBlank(detail)) {
        return warning;
      }
      return warning + " " + detail;
    }

    /**
     * Best-effort check for whether the source file's column order may have drifted since this
     * dataset's field mapping was last confirmed. Compares the header names captured at that
     * last confirmation (dataset.getColumnNamesList(), persisted alongside the field mappings)
     * against the header row of the file about to be processed now. Only applies to positional
     * formats (CSV/TSV) -- JSON/GEO_JSON/RSS/JSON API sources are keyed by field name rather
     * than column position, so a reordered source doesn't cross-wire values the same way.
     * Returns null (no warning) whenever the check can't be performed, rather than failing the
     * sync -- headers legitimately aren't available for every source type.
     *
     * @param dataset the dataset about to be processed
     * @return a warning message describing the mismatch, or null if none was detected
     */
    private static String detectColumnOrderWarning(Dataset dataset) {
      try {
        List<String> lastKnownColumns = dataset.getColumnNamesList();
        if (lastKnownColumns == null || lastKnownColumns.isEmpty()) {
          // No prior mapping recorded to compare against
          return null;
        }
        int type = DatasetFileCommand.type(dataset.getFileType());
        if (type != DatasetFileCommand.CSV && type != DatasetFileCommand.TSV) {
          return null;
        }
        File file = DatasetFileCommand.getFile(dataset);
        if (file == null) {
          return null;
        }
        String[] currentHeaders = readHeaderRow(file, type);
        if (currentHeaders == null || currentHeaders.length == 0) {
          return null;
        }
        boolean changed = currentHeaders.length != lastKnownColumns.size();
        if (!changed) {
          for (int i = 0; i < currentHeaders.length; i++) {
            String expected = StringUtils.trimToEmpty(lastKnownColumns.get(i));
            String actual = StringUtils.trimToEmpty(currentHeaders[i]);
            if (!expected.equalsIgnoreCase(actual)) {
              changed = true;
              break;
            }
          }
        }
        if (!changed) {
          return null;
        }
        return "Warning: source column order may have changed since this mapping was last confirmed "
            + "(expected [" + String.join(", ", lastKnownColumns) + "], found ["
            + String.join(", ", currentHeaders) + "]). Field mappings are applied by column position, "
            + "so please review the mapping on the Sync tab.";
      } catch (Exception e) {
        LOG.warn("Could not verify source column order", e);
        return null;
      }
    }

    /**
     * Reads just the header row of a CSV/TSV dataset file, without processing the rest of the
     * file. Returns null (rather than throwing) on any failure -- this backs a best-effort
     * warning check and must never be the reason a sync fails.
     */
    private static String[] readHeaderRow(File file, int type) {
      try (InputStream inputStream = new FileInputStream(file)) {
        if (type == DatasetFileCommand.TSV) {
          TsvParserSettings settings = new TsvParserSettings();
          settings.setLineSeparatorDetectionEnabled(true);
          settings.setHeaderExtractionEnabled(true);
          settings.setNumberOfRecordsToRead(1);
          TsvParser parser = new TsvParser(settings);
          try {
            parser.beginParsing(inputStream, "ISO-8859-1");
            parser.parseNext();
            return parser.getRecordMetadata().headers();
          } finally {
            parser.stopParsing();
          }
        }
        CsvParserSettings settings = new CsvParserSettings();
        settings.setLineSeparatorDetectionEnabled(true);
        settings.setHeaderExtractionEnabled(true);
        settings.setNumberOfRecordsToRead(1);
        CsvParser parser = new CsvParser(settings);
        try {
          parser.beginParsing(inputStream, "ISO-8859-1");
          parser.parseNext();
          return parser.getRecordMetadata().headers();
        } finally {
          parser.stopParsing();
        }
      } catch (Exception e) {
        LOG.warn("Could not read header row from dataset file: " + file.getName(), e);
        return null;
      }
    }
  }
}
