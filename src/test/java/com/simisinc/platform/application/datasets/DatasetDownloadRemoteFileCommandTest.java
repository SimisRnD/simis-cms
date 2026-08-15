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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.application.http.HttpDownloadFileCommand;
import com.simisinc.platform.application.http.HttpGetCommand;
import com.simisinc.platform.application.http.RemoteUrlValidationCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;

/**
 * Proves that a "next page" url discovered mid-pagination -- fully controlled by whoever runs
 * the source server, not by the admin who configured the dataset -- is routed through the
 * guarded {@link HttpGetCommand#executeUserUrl(String)} entry point and never reaches the raw,
 * unguarded {@link HttpGetCommand#execute(String)}. Mirrors the static-mocking pattern already
 * established in {@code RemoteContentWidgetTest} for the same class of concern: the guard's own
 * address classification is covered directly by {@code RemoteUrlValidationCommandTest}, so this
 * is a call-site routing proof, not a re-test of the guard logic itself.
 *
 * @author Elizabeth Houser
 * @created 2026-07-31
 */
class DatasetDownloadRemoteFileCommandTest {

  private static final String FIRST_PAGE_URL = "http://93.184.216.34/page1";

  // Loopback: the SSRF guard blocks this regardless of whether anything listens there --
  // exactly the shape of a malicious "next" url a hostile/compromised source server could
  // hand back mid-pagination to reach the app's own internal network.
  private static final String MALICIOUS_NEXT_URL = "http://127.0.0.1:65535/internal-page2";

  @Test
  void maliciousNextPageUrlIsRejectedAndNeverFetchedUnguarded(@TempDir File tempDir) {
    File tempFile = new File(tempDir, "out.json");
    String firstPageJson = "{\"records\":[{\"id\":1}],\"next\":\"" + MALICIOUS_NEXT_URL + "\"}";

    try (MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class)) {
      httpGet.when(() -> HttpGetCommand.executeUserUrl(FIRST_PAGE_URL)).thenReturn(firstPageJson);
      // MALICIOUS_NEXT_URL is deliberately left unstubbed: the guard inside executeUserUrl()
      // is what's responsible for refusing it (proven for real in RemoteUrlValidationCommandTest).

      boolean result = DatasetDownloadRemoteFileCommand.downloadPagedFile(FIRST_PAGE_URL, "next", "records", tempFile);

      assertFalse(result, "a malicious mid-pagination url must abort the download");
      assertFalse(tempFile.exists(), "no partial/merged output should be written");

      // The malicious url was routed through the guarded entry point...
      httpGet.verify(() -> HttpGetCommand.executeUserUrl(MALICIOUS_NEXT_URL));
      // ...and the raw, unguarded fetch was never reached at all -- for any url.
      httpGet.verify(() -> HttpGetCommand.execute(anyString()), never());
    }
  }

  @Test
  void maliciousSourceUrlIsRejectedWithoutRelyingOnTheCaller(@TempDir File tempDir) {
    // downloadPagedFile() is public static and must not depend on a caller having already
    // validated the url -- calling it directly with a malicious url must still route through
    // the guard itself rather than merely failing to connect (a real, unmocked loopback call
    // would return false either way, since nothing listens there -- that would pass both
    // before and after the fix and prove nothing, so this verifies routing explicitly instead).
    File tempFile = new File(tempDir, "out.json");

    try (MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class)) {
      boolean result = DatasetDownloadRemoteFileCommand.downloadPagedFile(MALICIOUS_NEXT_URL, "next", "records", tempFile);

      assertFalse(result);
      assertFalse(tempFile.exists());
      httpGet.verify(() -> HttpGetCommand.executeUserUrl(MALICIOUS_NEXT_URL));
      httpGet.verify(() -> HttpGetCommand.execute(anyString()), never());
    }
  }

  @Test
  void legitimatePagingStillMerges(@TempDir File tempDir) throws Exception {
    File tempFile = new File(tempDir, "out.json");
    String secondPageUrl = "http://93.184.216.34/page2";
    String firstPageJson = "{\"records\":[{\"id\":1}],\"next\":\"" + secondPageUrl + "\"}";
    String secondPageJson = "{\"records\":[{\"id\":2}],\"next\":null}";

    try (MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class)) {
      httpGet.when(() -> HttpGetCommand.executeUserUrl(FIRST_PAGE_URL)).thenReturn(firstPageJson);
      httpGet.when(() -> HttpGetCommand.executeUserUrl(secondPageUrl)).thenReturn(secondPageJson);

      boolean result = DatasetDownloadRemoteFileCommand.downloadPagedFile(FIRST_PAGE_URL, "next", "records", tempFile);

      assertTrue(result);
      JsonNode written = JsonLoader.fromFile(tempFile);
      JsonNode records = written.get("records");
      assertEquals(2, records.size());
      assertEquals(1, records.get(0).get("id").asInt());
      assertEquals(2, records.get(1).get("id").asInt());

      // Both the source page and the discovered paging url went through the guarded entry
      // point, and the raw, unguarded fetch was never reached.
      httpGet.verify(() -> HttpGetCommand.executeUserUrl(FIRST_PAGE_URL));
      httpGet.verify(() -> HttpGetCommand.executeUserUrl(secondPageUrl));
      httpGet.verify(() -> HttpGetCommand.execute(anyString()), never());
    }
  }

  /**
   * Issue #1211 / #363: the accumulated-row cap must be re-evaluated as pages accumulate.
   *
   * <p>This is the case the original implementation could not catch. Its check sat above the
   * append loop, reading the size of the records node while it still held only page one -- so it
   * fired only if the very first page already exceeded the cap, and never truncated
   * mid-pagination. Here each page is comfortably under the cap on its own and only their sum
   * exceeds it, so a check evaluated once on entry would let every row through.
   */
  @Test
  void pagedDownloadStopsAccumulatingAtTheConfiguredRowCap(@TempDir File tempDir) throws Exception {
    File tempFile = new File(tempDir, "out.json");
    String secondPageUrl = "http://93.184.216.34/page2";
    String thirdPageUrl = "http://93.184.216.34/page3";
    // 2 rows per page, cap of 3: page one is under the cap, page two crosses it mid-page, and
    // page three must never be fetched at all.
    String firstPageJson = "{\"records\":[{\"id\":1},{\"id\":2}],\"next\":\"" + secondPageUrl + "\"}";
    String secondPageJson = "{\"records\":[{\"id\":3},{\"id\":4}],\"next\":\"" + thirdPageUrl + "\"}";

    try (MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("dataset.maxRows")).thenReturn("3");
      httpGet.when(() -> HttpGetCommand.executeUserUrl(FIRST_PAGE_URL)).thenReturn(firstPageJson);
      httpGet.when(() -> HttpGetCommand.executeUserUrl(secondPageUrl)).thenReturn(secondPageJson);

      boolean result = DatasetDownloadRemoteFileCommand.downloadPagedFile(FIRST_PAGE_URL, "next", "records", tempFile);

      assertTrue(result);
      JsonNode records = JsonLoader.fromFile(tempFile).get("records");
      assertEquals(3, records.size(), "accumulation must stop exactly at the cap, mid-page");
      assertEquals(1, records.get(0).get("id").asInt());
      assertEquals(2, records.get(1).get("id").asInt());
      assertEquals(3, records.get(2).get("id").asInt());

      // Having hit the cap while merging page two, the third page is never requested.
      httpGet.verify(() -> HttpGetCommand.executeUserUrl(thirdPageUrl), never());
    }
  }

  /** A blank or unparseable dataset.maxRows must fall back to the default, not remove the bound. */
  @Test
  void rowCapFallsBackToTheDefaultWhenThePropertyIsUnusable() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("dataset.maxRows")).thenReturn(null);
      assertEquals(DatasetDownloadRemoteFileCommand.DEFAULT_MAX_ROWS,
          DatasetDownloadRemoteFileCommand.resolveMaxRows(), "null property");

      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("dataset.maxRows")).thenReturn("not-a-number");
      assertEquals(DatasetDownloadRemoteFileCommand.DEFAULT_MAX_ROWS,
          DatasetDownloadRemoteFileCommand.resolveMaxRows(), "unparseable property");

      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("dataset.maxRows")).thenReturn("0");
      assertEquals(DatasetDownloadRemoteFileCommand.DEFAULT_MAX_ROWS,
          DatasetDownloadRemoteFileCommand.resolveMaxRows(), "a non-positive cap must not disable the bound");

      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("dataset.maxRows")).thenReturn(" 500 ");
      assertEquals(500, DatasetDownloadRemoteFileCommand.resolveMaxRows(), "a valid value is honored");
    }
  }

  /**
   * Regression test for {@link DatasetDownloadRemoteFileCommand#handleRemoteFileDownload}
   * hardcoding a fetch to a specific PERLS e-learning endpoint for every "JSON API"
   * (application/vnd.api+json) dataset, ignoring whatever source url the admin configured on
   * the dataset's Source tab. This deliberately fails the download (the stub returns false) so
   * the test can stay focused on proving *which url gets fetched* -- a normal successful sync is
   * exercised elsewhere -- while still verifying the admin's own configured url, not a
   * substituted one, is what reached the download call.
   */
  @Test
  void jsonApiDatasetFetchesTheAdminsConfiguredSourceUrlNotAHardcodedIntegration(@TempDir File tempDir) {
    Dataset dataset = new Dataset();
    dataset.setId(55L);
    dataset.setFileType("application/vnd.api+json");
    dataset.setSourceUrl(FIRST_PAGE_URL);
    // No paging configured -- this exercises the single-file download path, like every other
    // remote dataset type

    try (MockedStatic<RemoteUrlValidationCommand> urlValidation = mockStatic(RemoteUrlValidationCommand.class);
        MockedStatic<FileSystemCommand> fileSystem = mockStatic(FileSystemCommand.class);
        MockedStatic<HttpDownloadFileCommand> httpDownload = mockStatic(HttpDownloadFileCommand.class)) {
      urlValidation.when(() -> RemoteUrlValidationCommand.isFetchAllowed(FIRST_PAGE_URL)).thenReturn(true);
      fileSystem.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.getAbsolutePath() + "/");
      fileSystem.when(() -> FileSystemCommand.generateFileServerSubPath("datasets"))
          .thenReturn("datasets/2026/08/06/");
      fileSystem.when(() -> FileSystemCommand.generateUniqueFilename(1L)).thenReturn("unique123");
      httpDownload.when(() -> HttpDownloadFileCommand.executeUserUrl(anyString(), any(File.class))).thenReturn(false);

      DataException thrown = assertThrows(DataException.class,
          () -> DatasetDownloadRemoteFileCommand.handleRemoteFileDownload(dataset, 1L));

      assertTrue(thrown.getMessage().contains(FIRST_PAGE_URL),
          "the failure must reference the admin's own configured source url, proving that's "
              + "what was fetched instead of a hardcoded integration endpoint");
      httpDownload.verify(() -> HttpDownloadFileCommand.executeUserUrl(eq(FIRST_PAGE_URL), any(File.class)));
    }
  }

  /**
   * "JSON API" must respect the admin's paging configuration exactly like the plain JSON type
   * does -- before the fix, the hardcoded PERLS branch never even looked at
   * {@code pagingUrlPath}.
   */
  @Test
  void jsonApiDatasetWithPagingConfiguredUsesThePagedDownloadPathLikeOtherTypes(@TempDir File tempDir) {
    Dataset dataset = new Dataset();
    dataset.setId(56L);
    dataset.setFileType("application/vnd.api+json");
    dataset.setSourceUrl(FIRST_PAGE_URL);
    dataset.setPagingUrlPath("/next");
    dataset.setRecordsPath("/records");

    try (MockedStatic<RemoteUrlValidationCommand> urlValidation = mockStatic(RemoteUrlValidationCommand.class);
        MockedStatic<FileSystemCommand> fileSystem = mockStatic(FileSystemCommand.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class)) {
      urlValidation.when(() -> RemoteUrlValidationCommand.isFetchAllowed(FIRST_PAGE_URL)).thenReturn(true);
      fileSystem.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.getAbsolutePath() + "/");
      fileSystem.when(() -> FileSystemCommand.generateFileServerSubPath("datasets"))
          .thenReturn("datasets/2026/08/06/");
      fileSystem.when(() -> FileSystemCommand.generateUniqueFilename(1L)).thenReturn("unique456");
      // Fails fast so the test only needs to prove the paged path (and thus the admin's url) was
      // used, not exercise a full successful merge/save (covered elsewhere)
      httpGet.when(() -> HttpGetCommand.executeUserUrl(FIRST_PAGE_URL)).thenReturn(null);

      assertThrows(DataException.class,
          () -> DatasetDownloadRemoteFileCommand.handleRemoteFileDownload(dataset, 1L));

      httpGet.verify(() -> HttpGetCommand.executeUserUrl(FIRST_PAGE_URL));
    }
  }
}
