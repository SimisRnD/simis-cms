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

package com.simisinc.platform.application.datasets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;

/**
 * Regression tests for {@link DatasetFileCommand#convertFileToCollection}: RSS, GeoJSON, and
 * "JSON API" used to have no real case in that method's switch. The real parsers were commented
 * out, and none of those three {@code case}s had a {@code break} or {@code return}, so execution
 * fell through into the TSV case and parsed raw XML/JSON as tab-separated text -- silently
 * corrupting the imported Items on a real "Save & Sync" run, even though Preview (which routes
 * through {@link DatasetFileCommand#loadRows}) already used the correct parser and looked fine.
 * These tests prove a real sync now reuses that same, already-correct Load*Command parser for
 * each of the three types, and never falls through to the TSV parser.
 *
 * @author Elizabeth Houser
 * @created 2026-08-06
 */
class DatasetFileCommandTest {

  private static Dataset datasetOfType(String fileType) {
    Dataset dataset = new Dataset();
    dataset.setId(77L);
    dataset.setFileType(fileType);
    return dataset;
  }

  @Test
  void jsonApiConvertUsesTheRealJsonParserNotTheTsvParser() throws Exception {
    Dataset dataset = datasetOfType(DatasetFileCommand.JSON_API_TYPE);
    Collection collection = new Collection();
    collection.setId(1L);
    List<String[]> rows = List.of(new String[] { "one" }, new String[] { "two" });

    try (MockedStatic<LoadJsonCommand> loadJson = mockStatic(LoadJsonCommand.class);
        MockedStatic<ConvertTSVFileCommand> convertTsv = mockStatic(ConvertTSVFileCommand.class);
        MockedStatic<SaveDatasetRowCommand> saveRow = mockStatic(SaveDatasetRowCommand.class);
        MockedStatic<DatasetRepository> repo = mockStatic(DatasetRepository.class)) {
      loadJson.when(() -> LoadJsonCommand.loadRecords(dataset, Integer.MAX_VALUE, false)).thenReturn(rows);
      saveRow.when(() -> SaveDatasetRowCommand.saveRecord(any(), any(), any())).thenReturn(true);

      boolean result = DatasetFileCommand.convertFileToCollection(dataset, collection);

      assertTrue(result);
      saveRow.verify(() -> SaveDatasetRowCommand.saveRecord(rows.get(0), dataset, collection));
      saveRow.verify(() -> SaveDatasetRowCommand.saveRecord(rows.get(1), dataset, collection));
      // Must never have fallen through into the TSV parser
      convertTsv.verify(() -> ConvertTSVFileCommand.convertFileToCollection(any(), any()), never());
      // The per-dataset skipDuplicates tracking state must be released once conversion finishes
      saveRow.verify(() -> SaveDatasetRowCommand.clearDuplicateTracking(dataset));
    }
  }

  @Test
  void geoJsonConvertUsesTheRealGeoJsonParserNotTheTsvParser() throws Exception {
    Dataset dataset = datasetOfType(DatasetFileCommand.GEO_JSON_TYPE);
    Collection collection = new Collection();
    collection.setId(1L);
    List<String[]> rows = List.of(new String[][] { { "-87.6", "41.8" } });

    try (MockedStatic<LoadGeoJsonFeedCommand> loadGeoJson = mockStatic(LoadGeoJsonFeedCommand.class);
        MockedStatic<ConvertTSVFileCommand> convertTsv = mockStatic(ConvertTSVFileCommand.class);
        MockedStatic<SaveDatasetRowCommand> saveRow = mockStatic(SaveDatasetRowCommand.class);
        MockedStatic<DatasetRepository> repo = mockStatic(DatasetRepository.class)) {
      loadGeoJson.when(() -> LoadGeoJsonFeedCommand.loadRows(dataset, Integer.MAX_VALUE)).thenReturn(rows);
      saveRow.when(() -> SaveDatasetRowCommand.saveRecord(any(), any(), any())).thenReturn(true);

      boolean result = DatasetFileCommand.convertFileToCollection(dataset, collection);

      assertTrue(result);
      saveRow.verify(() -> SaveDatasetRowCommand.saveRecord(rows.get(0), dataset, collection));
      convertTsv.verify(() -> ConvertTSVFileCommand.convertFileToCollection(any(), any()), never());
      saveRow.verify(() -> SaveDatasetRowCommand.clearDuplicateTracking(dataset));
    }
  }

  @Test
  void rssConvertUsesTheRealRssParserNotTheTsvParser() throws Exception {
    Dataset dataset = datasetOfType(DatasetFileCommand.RSS_TYPE);
    Collection collection = new Collection();
    collection.setId(1L);
    List<String[]> rows = List.of(new String[][] { { "Headline", "http://example.com/a" } });

    try (MockedStatic<LoadRSSFeedCommand> loadRss = mockStatic(LoadRSSFeedCommand.class);
        MockedStatic<ConvertTSVFileCommand> convertTsv = mockStatic(ConvertTSVFileCommand.class);
        MockedStatic<SaveDatasetRowCommand> saveRow = mockStatic(SaveDatasetRowCommand.class);
        MockedStatic<DatasetRepository> repo = mockStatic(DatasetRepository.class)) {
      loadRss.when(() -> LoadRSSFeedCommand.loadRows(dataset, Integer.MAX_VALUE)).thenReturn(rows);
      saveRow.when(() -> SaveDatasetRowCommand.saveRecord(any(), any(), any())).thenReturn(true);

      boolean result = DatasetFileCommand.convertFileToCollection(dataset, collection);

      assertTrue(result);
      saveRow.verify(() -> SaveDatasetRowCommand.saveRecord(rows.get(0), dataset, collection));
      convertTsv.verify(() -> ConvertTSVFileCommand.convertFileToCollection(any(), any()), never());
      saveRow.verify(() -> SaveDatasetRowCommand.clearDuplicateTracking(dataset));
    }
  }

  /**
   * The skipDuplicates tracking cleanup must happen on a failed conversion too (not just a
   * successful one), so a later retry of the same dataset doesn't inherit stale state from the
   * run that failed.
   */
  @Test
  void duplicateTrackingIsClearedEvenWhenConversionFails() {
    Dataset dataset = datasetOfType(DatasetFileCommand.JSON_API_TYPE);
    Collection collection = new Collection();
    collection.setId(1L);

    try (MockedStatic<LoadJsonCommand> loadJson = mockStatic(LoadJsonCommand.class);
        MockedStatic<SaveDatasetRowCommand> saveRow = mockStatic(SaveDatasetRowCommand.class)) {
      loadJson.when(() -> LoadJsonCommand.loadRecords(dataset, Integer.MAX_VALUE, false))
          .thenThrow(new RuntimeException("boom"));

      assertThrows(RuntimeException.class, () -> DatasetFileCommand.convertFileToCollection(dataset, collection));

      saveRow.verify(() -> SaveDatasetRowCommand.clearDuplicateTracking(dataset));
    }
  }
}
