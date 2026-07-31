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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.http.HttpGetCommand;

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
}
