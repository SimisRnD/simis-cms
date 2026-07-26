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

package com.simisinc.platform.application.datasets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.io.File;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.admin.SaveTextFileCommand;
import com.simisinc.platform.application.http.HttpGetCommand;
import com.simisinc.platform.application.http.RemoteUrlValidationCommand;

/**
 * Verifies that the paged dataset download respects the row cap.
 */
class DatasetDownloadRemoteFileCommandTest {

  @TempDir
  File tempDir;

  // Three pages; each has 3 records. "next" at the top level points to the next URL.
  private static final String PAGE_1 =
      "{\"data\":[{\"id\":1},{\"id\":2},{\"id\":3}],\"next\":\"https://example.com/page2\"}";
  private static final String PAGE_2 =
      "{\"data\":[{\"id\":4},{\"id\":5},{\"id\":6}],\"next\":\"https://example.com/page3\"}";
  private static final String PAGE_3 =
      "{\"data\":[{\"id\":7},{\"id\":8},{\"id\":9}]}";

  @Test
  void rowCapStopsAccumulationBeforeThirdPage() throws Exception {
    File output = new File(tempDir, "out.json");

    try (MockedStatic<HttpGetCommand> httpMock = mockStatic(HttpGetCommand.class);
         MockedStatic<RemoteUrlValidationCommand> validMock = mockStatic(RemoteUrlValidationCommand.class);
         MockedStatic<LoadSitePropertyCommand> propMock = mockStatic(LoadSitePropertyCommand.class);
         MockedStatic<SaveTextFileCommand> saveMock = mockStatic(SaveTextFileCommand.class)) {

      // Pages returned in sequence; page 3 should never be fetched when cap=5
      httpMock.when(() -> HttpGetCommand.execute(any())).thenReturn(PAGE_1, PAGE_2, PAGE_3);
      validMock.when(() -> RemoteUrlValidationCommand.isFetchAllowed(any())).thenReturn(true);
      propMock.when(() -> LoadSitePropertyCommand.loadByName("dataset.maxRows")).thenReturn("5");
      saveMock.when(() -> SaveTextFileCommand.save(any(), any())).thenReturn(output);

      boolean result = DatasetDownloadRemoteFileCommand.downloadPagedFile(
          "https://example.com/page1", "next", "data", output);

      Assertions.assertTrue(result, "downloadPagedFile should return true");
      // Page 1 fetched by downloadPagedFile itself; page 2 fetched inside appendNextUrls.
      // After page 2's records are appended (total 6 >= cap 5), the recursive call returns
      // immediately — page 3 must NOT be fetched.
      httpMock.verify(() -> HttpGetCommand.execute(any()), times(2));
    }
  }

  @Test
  void withoutCapDefaultAllowsMultiplePages() throws Exception {
    // When dataset.maxRows is absent, the default (100_000) applies and all pages accumulate normally.
    File output = new File(tempDir, "out-default.json");

    try (MockedStatic<HttpGetCommand> httpMock = mockStatic(HttpGetCommand.class);
         MockedStatic<RemoteUrlValidationCommand> validMock = mockStatic(RemoteUrlValidationCommand.class);
         MockedStatic<LoadSitePropertyCommand> propMock = mockStatic(LoadSitePropertyCommand.class);
         MockedStatic<SaveTextFileCommand> saveMock = mockStatic(SaveTextFileCommand.class)) {

      // PAGE_3 has no "next", so paging stops naturally after 3 pages
      httpMock.when(() -> HttpGetCommand.execute(any())).thenReturn(PAGE_1, PAGE_2, PAGE_3);
      validMock.when(() -> RemoteUrlValidationCommand.isFetchAllowed(any())).thenReturn(true);
      propMock.when(() -> LoadSitePropertyCommand.loadByName("dataset.maxRows")).thenReturn(null);
      saveMock.when(() -> SaveTextFileCommand.save(any(), any())).thenReturn(output);

      boolean result = DatasetDownloadRemoteFileCommand.downloadPagedFile(
          "https://example.com/page1", "next", "data", output);

      Assertions.assertTrue(result);
      // All three pages should be fetched: initial URL + page2 + page3
      httpMock.verify(() -> HttpGetCommand.execute(any()), times(3));
    }
  }
}
