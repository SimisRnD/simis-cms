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

package com.simisinc.platform.infrastructure.scheduler.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.datasets.DatasetFileCommand;
import com.simisinc.platform.application.datasets.DeleteDatasetItemsCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import com.simisinc.platform.infrastructure.scheduler.admin.ProcessDatasetJob.ProcessDatasetJobRequestHandler;

/**
 * Covers two of the dataset-sync gaps this change closes:
 * <p>
 * Bug A -- a reordered source column (e.g. Phone and Email swapping position) used to be
 * cross-wired silently, because the mapping was only checked for the right column *count*, never
 * against the *names* recorded the last time the mapping was confirmed. Fixed with a best-effort,
 * non-blocking check that surfaces a warning on syncMessage instead.
 * <p>
 * Bug B -- a mid-file failure used to leave syncMessage as either a bare exception string or,
 * worse, completely blank, with no indication that rows before the failure were already
 * committed, the rest were never attempted, or that the separate stale-record cleanup pass never
 * ran. Fixed by building an explicit message covering all three facts.
 *
 * @author elizabeth houser
 */
class ProcessDatasetJobTest {

  private final ProcessDatasetJobRequestHandler handler = new ProcessDatasetJobRequestHandler();

  private static Dataset dataset(String fileType) {
    Dataset dataset = new Dataset();
    dataset.setId(55L);
    dataset.setName("Contacts");
    dataset.setCollectionUniqueId("contacts-collection");
    dataset.setFileType(fileType);
    dataset.setRowCount(1000);
    return dataset;
  }

  private static ProcessDatasetJob jobRequestFor(Dataset dataset) {
    ProcessDatasetJob jobRequest = new ProcessDatasetJob();
    jobRequest.setDatasetId(dataset.getId());
    jobRequest.setModifiedByUserId(9L);
    return jobRequest;
  }

  private static Path writeTempCsv(String content) throws Exception {
    Path file = Files.createTempFile("process-dataset-job-test", ".csv");
    Files.writeString(file, content);
    file.toFile().deleteOnExit();
    return file;
  }

  @Test
  void runWarnsWithoutFailingWhenTheSourceColumnOrderHasChangedSinceTheLastConfirmedMapping() throws Exception {
    // The mapping was last confirmed against Name, Phone, Email -- the live file now has
    // Phone and Email swapped
    Dataset dataset = dataset(DatasetFileCommand.CSV_TYPE);
    dataset.setColumnNames(new String[] { "Name", "Phone", "Email" });
    dataset.setFieldMappings(new String[] { "name", "phoneNumber", "email" });
    Path csvFile = writeTempCsv("Name,Email,Phone\nAlice,alice@example.com,555-1234\n");
    Collection collection = new Collection();
    collection.setId(9L);

    try (MockedStatic<DatasetRepository> repo = mockStatic(DatasetRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class);
        MockedStatic<DatasetFileCommand> fileCommand = mockStatic(DatasetFileCommand.class);
        MockedStatic<DeleteDatasetItemsCommand> deleteCommand = mockStatic(DeleteDatasetItemsCommand.class)) {
      repo.when(() -> DatasetRepository.findById(55L)).thenReturn(dataset);
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByUniqueId("contacts-collection"))
          .thenReturn(collection);
      repo.when(() -> DatasetRepository.markAsProcessStarted(dataset)).thenReturn(true);
      fileCommand.when(() -> DatasetFileCommand.type(DatasetFileCommand.CSV_TYPE)).thenReturn(DatasetFileCommand.CSV);
      fileCommand.when(() -> DatasetFileCommand.getFile(dataset)).thenReturn(csvFile.toFile());
      fileCommand.when(() -> DatasetFileCommand.convertFileToCollection(dataset, collection)).thenAnswer(invocation -> {
        dataset.setRowsProcessed(1);
        return true;
      });
      deleteCommand.when(() -> DeleteDatasetItemsCommand.deleteItemsForDataset(any(), any(Timestamp.class)))
          .thenReturn(0);

      handler.run(jobRequestFor(dataset));

      ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
      repo.verify(() -> DatasetRepository.saveSyncResult(any(), messageCaptor.capture()));
      String message = messageCaptor.getValue();
      assertTrue(message != null && message.contains("column order may have changed"),
          "expected a column-order warning, got: " + message);
      assertTrue(message.contains("Phone") && message.contains("Email"),
          "expected the warning to name the affected columns, got: " + message);
      // The warning must not have blocked add/update or the stale-cleanup pass
      deleteCommand.verify(() -> DeleteDatasetItemsCommand.deleteItemsForDataset(any(), any(Timestamp.class)));
    }
  }

  @Test
  void runDoesNotWarnWhenTheSourceColumnOrderIsUnchanged() throws Exception {
    Dataset dataset = dataset(DatasetFileCommand.CSV_TYPE);
    dataset.setColumnNames(new String[] { "Name", "Phone", "Email" });
    dataset.setFieldMappings(new String[] { "name", "phoneNumber", "email" });
    Path csvFile = writeTempCsv("Name,Phone,Email\nAlice,555-1234,alice@example.com\n");
    Collection collection = new Collection();
    collection.setId(9L);

    try (MockedStatic<DatasetRepository> repo = mockStatic(DatasetRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class);
        MockedStatic<DatasetFileCommand> fileCommand = mockStatic(DatasetFileCommand.class);
        MockedStatic<DeleteDatasetItemsCommand> deleteCommand = mockStatic(DeleteDatasetItemsCommand.class)) {
      repo.when(() -> DatasetRepository.findById(55L)).thenReturn(dataset);
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByUniqueId("contacts-collection"))
          .thenReturn(collection);
      repo.when(() -> DatasetRepository.markAsProcessStarted(dataset)).thenReturn(true);
      fileCommand.when(() -> DatasetFileCommand.type(DatasetFileCommand.CSV_TYPE)).thenReturn(DatasetFileCommand.CSV);
      fileCommand.when(() -> DatasetFileCommand.getFile(dataset)).thenReturn(csvFile.toFile());
      fileCommand.when(() -> DatasetFileCommand.convertFileToCollection(dataset, collection)).thenAnswer(invocation -> {
        dataset.setRowsProcessed(1);
        return true;
      });
      deleteCommand.when(() -> DeleteDatasetItemsCommand.deleteItemsForDataset(any(), any(Timestamp.class)))
          .thenReturn(0);

      handler.run(jobRequestFor(dataset));

      ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
      repo.verify(() -> DatasetRepository.saveSyncResult(any(), messageCaptor.capture()));
      assertNull(messageCaptor.getValue(), "unchanged column order must not produce a warning");
    }
  }

  @Test
  void runReportsRowsProcessedTotalAndSkipsCleanupWhenConversionFailsMidFile() throws Exception {
    Dataset dataset = dataset(DatasetFileCommand.JSON_TYPE);
    Collection collection = new Collection();
    collection.setId(9L);

    try (MockedStatic<DatasetRepository> repo = mockStatic(DatasetRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class);
        MockedStatic<DatasetFileCommand> fileCommand = mockStatic(DatasetFileCommand.class);
        MockedStatic<DeleteDatasetItemsCommand> deleteCommand = mockStatic(DeleteDatasetItemsCommand.class)) {
      repo.when(() -> DatasetRepository.findById(55L)).thenReturn(dataset);
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByUniqueId("contacts-collection"))
          .thenReturn(collection);
      repo.when(() -> DatasetRepository.markAsProcessStarted(dataset)).thenReturn(true);
      // Not CSV/TSV -- the column-order check should no-op without needing a real file
      fileCommand.when(() -> DatasetFileCommand.type(DatasetFileCommand.JSON_TYPE)).thenReturn(DatasetFileCommand.JSON);
      fileCommand.when(() -> DatasetFileCommand.convertFileToCollection(dataset, collection)).thenAnswer(invocation -> {
        dataset.setRowsProcessed(499);
        throw new Exception("Row 500: invalid value");
      });

      handler.run(jobRequestFor(dataset));

      ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
      repo.verify(() -> DatasetRepository.saveSyncResult(any(), messageCaptor.capture()));
      String message = messageCaptor.getValue();
      assertTrue(message != null, "a mid-file failure must not leave syncMessage blank");
      assertTrue(message.contains("499"), "expected the rows-processed count, got: " + message);
      assertTrue(message.contains("1000"), "expected the total row count, got: " + message);
      assertTrue(message.contains("stopped early"), "expected the message to say the run stopped early, got: " + message);
      assertTrue(message.contains("stale-record cleanup pass was skipped"),
          "expected the message to say cleanup was skipped, got: " + message);
      assertTrue(message.contains("Row 500: invalid value"), "expected the underlying error, got: " + message);
      // The whole point of the fix: cleanup must never run after a mid-file failure
      deleteCommand.verifyNoInteractions();
    }
  }

  @Test
  void runReportsWhenStaleRecordCleanupFailsAfterASuccessfulConversion() throws Exception {
    Dataset dataset = dataset(DatasetFileCommand.JSON_TYPE);
    Collection collection = new Collection();
    collection.setId(9L);

    try (MockedStatic<DatasetRepository> repo = mockStatic(DatasetRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class);
        MockedStatic<DatasetFileCommand> fileCommand = mockStatic(DatasetFileCommand.class);
        MockedStatic<DeleteDatasetItemsCommand> deleteCommand = mockStatic(DeleteDatasetItemsCommand.class)) {
      repo.when(() -> DatasetRepository.findById(55L)).thenReturn(dataset);
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByUniqueId("contacts-collection"))
          .thenReturn(collection);
      repo.when(() -> DatasetRepository.markAsProcessStarted(dataset)).thenReturn(true);
      fileCommand.when(() -> DatasetFileCommand.type(DatasetFileCommand.JSON_TYPE)).thenReturn(DatasetFileCommand.JSON);
      fileCommand.when(() -> DatasetFileCommand.convertFileToCollection(dataset, collection)).thenAnswer(invocation -> {
        dataset.setRowsProcessed(200);
        return true;
      });
      deleteCommand.when(() -> DeleteDatasetItemsCommand.deleteItemsForDataset(any(), any(Timestamp.class)))
          .thenThrow(new Exception("connection reset"));

      handler.run(jobRequestFor(dataset));

      ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
      repo.verify(() -> DatasetRepository.saveSyncResult(any(), messageCaptor.capture()));
      String message = messageCaptor.getValue();
      assertTrue(message != null, "a cleanup-pass failure must not leave syncMessage blank");
      assertTrue(message.contains("200"), "expected the rows-processed count, got: " + message);
      assertTrue(message.contains("stale-record cleanup pass failed"), "got: " + message);
      assertTrue(message.contains("connection reset"), "expected the underlying error, got: " + message);
      assertFalse(message.contains("stopped early"),
          "add/update completed fully here -- only cleanup failed, so this must read differently "
              + "from a mid-file conversion failure; got: " + message);
    }
  }

  @Test
  void runReportsWhenNoRowsWereProcessedInsteadOfStayingSilent() throws Exception {
    Dataset dataset = dataset(DatasetFileCommand.JSON_TYPE);
    Collection collection = new Collection();
    collection.setId(9L);

    try (MockedStatic<DatasetRepository> repo = mockStatic(DatasetRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class);
        MockedStatic<DatasetFileCommand> fileCommand = mockStatic(DatasetFileCommand.class);
        MockedStatic<DeleteDatasetItemsCommand> deleteCommand = mockStatic(DeleteDatasetItemsCommand.class)) {
      repo.when(() -> DatasetRepository.findById(55L)).thenReturn(dataset);
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByUniqueId("contacts-collection"))
          .thenReturn(collection);
      repo.when(() -> DatasetRepository.markAsProcessStarted(dataset)).thenReturn(true);
      fileCommand.when(() -> DatasetFileCommand.type(DatasetFileCommand.JSON_TYPE)).thenReturn(DatasetFileCommand.JSON);
      // Completes without error or exception, but never advances rowsProcessed above 0
      fileCommand.when(() -> DatasetFileCommand.convertFileToCollection(dataset, collection)).thenReturn(true);

      handler.run(jobRequestFor(dataset));

      ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
      repo.verify(() -> DatasetRepository.saveSyncResult(any(), messageCaptor.capture()));
      String message = messageCaptor.getValue();
      assertTrue(message != null && message.contains("No rows were processed"),
          "a zero-row run must not look identical to a normal successful sync; got: " + message);
      deleteCommand.verifyNoInteractions();
    }
  }
}
