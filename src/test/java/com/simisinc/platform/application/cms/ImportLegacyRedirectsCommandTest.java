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

package com.simisinc.platform.application.cms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.cms.WebRedirect;
import com.simisinc.platform.infrastructure.persistence.cms.WebRedirectRepository;

/**
 * Verifies {@link ImportLegacyRedirectsCommand}'s CSV-to-database import (issue #408): new rows are
 * inserted, rows whose from_path already exists in web_redirects are left alone, and the import is
 * safe to run more than once.
 */
class ImportLegacyRedirectsCommandTest {

  @Test
  void importsEveryRowFromANewCsvFile() {
    Map<String, String> legacyRedirects = new LinkedHashMap<>();
    legacyRedirects.put("/old-a", "/new-a");
    legacyRedirects.put("/old-b", "/new-b");

    try (MockedStatic<LoadRedirectsCommand> loadRedirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {

      loadRedirects.when(LoadRedirectsCommand::load).thenReturn(legacyRedirects);
      repository.when(() -> WebRedirectRepository.findByFromPath(any())).thenReturn(null);
      repository.when(() -> WebRedirectRepository.add(any(WebRedirect.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      ImportLegacyRedirectsCommand.importFromCsv();

      repository.verify(() -> WebRedirectRepository.add(argThat(
          r -> "/old-a".equals(r.getFromPath()) && "/new-a".equals(r.getToUrl())
              && r.getStatusCode() == WebRedirect.PERMANENT && r.getEnabled())));
      repository.verify(() -> WebRedirectRepository.add(argThat(
          r -> "/old-b".equals(r.getFromPath()) && "/new-b".equals(r.getToUrl()))));
      repository.verify(() -> WebRedirectRepository.add(any()), times(2));
    }
  }

  @Test
  void skipsARowWhoseFromPathAlreadyExistsInTheDatabase() {
    Map<String, String> legacyRedirects = new LinkedHashMap<>();
    legacyRedirects.put("/already-migrated", "/csv-target");

    WebRedirect existing = new WebRedirect();
    existing.setFromPath("/already-migrated");
    existing.setToUrl("/admin-chosen-target");

    try (MockedStatic<LoadRedirectsCommand> loadRedirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {

      loadRedirects.when(LoadRedirectsCommand::load).thenReturn(legacyRedirects);
      repository.when(() -> WebRedirectRepository.findByFromPath("/already-migrated")).thenReturn(existing);

      ImportLegacyRedirectsCommand.importFromCsv();

      // Must not clobber whatever is already there (whether from a prior import or an admin edit)
      repository.verify(() -> WebRedirectRepository.add(any()), never());
    }
  }

  @Test
  void runningTheImportTwiceOnlyInsertsEachRowOnce() {
    Map<String, String> legacyRedirects = new LinkedHashMap<>();
    legacyRedirects.put("/repeat-run", "/target");

    try (MockedStatic<LoadRedirectsCommand> loadRedirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {

      loadRedirects.when(LoadRedirectsCommand::load).thenReturn(legacyRedirects);

      // First run: nothing exists yet
      repository.when(() -> WebRedirectRepository.findByFromPath("/repeat-run")).thenReturn(null);
      repository.when(() -> WebRedirectRepository.add(any(WebRedirect.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));
      ImportLegacyRedirectsCommand.importFromCsv();
      repository.verify(() -> WebRedirectRepository.add(any()), times(1));

      // Second run: the row from the first run is now "in the database"
      WebRedirect nowExists = new WebRedirect();
      nowExists.setFromPath("/repeat-run");
      repository.when(() -> WebRedirectRepository.findByFromPath("/repeat-run")).thenReturn(nowExists);
      ImportLegacyRedirectsCommand.importFromCsv();

      // Still only ever inserted once, across both runs
      repository.verify(() -> WebRedirectRepository.add(any()), times(1));
    }
  }

  @Test
  void doesNothingWhenNoLegacyFileIsPresent() {
    try (MockedStatic<LoadRedirectsCommand> loadRedirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {

      loadRedirects.when(LoadRedirectsCommand::load).thenReturn(null);

      ImportLegacyRedirectsCommand.importFromCsv();

      repository.verify(() -> WebRedirectRepository.findByFromPath(any()), never());
      repository.verify(() -> WebRedirectRepository.add(any()), never());
    }
  }

  @Test
  void aFromPathMissingALeadingSlashIsNormalizedBeforeItIsChecked() {
    Map<String, String> legacyRedirects = new LinkedHashMap<>();
    legacyRedirects.put("no-leading-slash", "/target");

    try (MockedStatic<LoadRedirectsCommand> loadRedirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {

      loadRedirects.when(LoadRedirectsCommand::load).thenReturn(legacyRedirects);
      repository.when(() -> WebRedirectRepository.findByFromPath("/no-leading-slash")).thenReturn(null);
      repository.when(() -> WebRedirectRepository.add(any(WebRedirect.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      ImportLegacyRedirectsCommand.importFromCsv();

      repository.verify(() -> WebRedirectRepository.add(argThat(r -> "/no-leading-slash".equals(r.getFromPath()))));
    }
  }

  @Test
  void aToUrlThatFailsUrlSanitizationIsSkippedRatherThanImported() {
    Map<String, String> legacyRedirects = new LinkedHashMap<>();
    // A protocol-relative value is rejected by UrlCommand.sanitizeUrl -- it must not reach a
    // Location header unsanitized
    legacyRedirects.put("/unsafe-target", "//evil.example.net");

    try (MockedStatic<LoadRedirectsCommand> loadRedirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {

      loadRedirects.when(LoadRedirectsCommand::load).thenReturn(legacyRedirects);

      ImportLegacyRedirectsCommand.importFromCsv();

      repository.verify(() -> WebRedirectRepository.add(any()), never());
    }
  }
}
