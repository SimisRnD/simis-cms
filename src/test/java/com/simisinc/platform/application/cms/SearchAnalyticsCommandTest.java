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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DoNotTrackCommand;
import com.simisinc.platform.domain.model.cms.SearchAnalytics;
import com.simisinc.platform.infrastructure.persistence.cms.SearchAnalyticsRepository;

/**
 * @author SimIS
 * @created 7/29/2026
 */
class SearchAnalyticsCommandTest extends WidgetBase {

  @Test
  void recordSavesANormalizedEvent() {
    try (MockedStatic<SearchAnalyticsRepository> repository = mockStatic(SearchAnalyticsRepository.class);
        MockedStatic<DoNotTrackCommand> dnt = mockStatic(DoNotTrackCommand.class)) {
      dnt.when(() -> DoNotTrackCommand.isDoNotTrack(any(), any())).thenReturn(false);

      SearchAnalyticsCommand.record(widgetContext, "  Widgets  ", "pages", 3);

      ArgumentCaptor<SearchAnalytics> captor = ArgumentCaptor.forClass(SearchAnalytics.class);
      repository.verify(() -> SearchAnalyticsRepository.save(captor.capture()));
      SearchAnalytics saved = captor.getValue();
      assertEqualsAll(saved, "widgets", "pages", 3, "/example/path");
    }
  }

  @Test
  void recordDoesNothingForABlankQuery() {
    try (MockedStatic<SearchAnalyticsRepository> repository = mockStatic(SearchAnalyticsRepository.class)) {
      SearchAnalyticsCommand.record(widgetContext, null, "pages", 3);
      SearchAnalyticsCommand.record(widgetContext, "", "pages", 3);
      SearchAnalyticsCommand.record(widgetContext, "   ", "pages", 3);

      repository.verify(() -> SearchAnalyticsRepository.save(any()), never());
    }
  }

  @Test
  void recordSkipsALoggedInAdmin() {
    setRoles(widgetContext, ADMIN);

    try (MockedStatic<SearchAnalyticsRepository> repository = mockStatic(SearchAnalyticsRepository.class)) {
      SearchAnalyticsCommand.record(widgetContext, "widgets", "pages", 3);

      repository.verify(() -> SearchAnalyticsRepository.save(any()), never());
    }
  }

  @Test
  void recordSkipsALoggedInContentManager() {
    setRoles(widgetContext, CONTENT_MANAGER);

    try (MockedStatic<SearchAnalyticsRepository> repository = mockStatic(SearchAnalyticsRepository.class)) {
      SearchAnalyticsCommand.record(widgetContext, "widgets", "pages", 3);

      repository.verify(() -> SearchAnalyticsRepository.save(any()), never());
    }
  }

  @Test
  void recordDoesNotSkipAnAnonymousSearcher() {
    logout(widgetContext);

    try (MockedStatic<SearchAnalyticsRepository> repository = mockStatic(SearchAnalyticsRepository.class);
        MockedStatic<DoNotTrackCommand> dnt = mockStatic(DoNotTrackCommand.class)) {
      dnt.when(() -> DoNotTrackCommand.isDoNotTrack(any(), any())).thenReturn(false);

      SearchAnalyticsCommand.record(widgetContext, "widgets", "pages", 3);

      repository.verify(() -> SearchAnalyticsRepository.save(any()), times(1));
    }
  }

  @Test
  void recordSkipsWhenDoNotTrackIsSignaled() {
    try (MockedStatic<SearchAnalyticsRepository> repository = mockStatic(SearchAnalyticsRepository.class);
        MockedStatic<DoNotTrackCommand> dnt = mockStatic(DoNotTrackCommand.class)) {
      dnt.when(() -> DoNotTrackCommand.isDoNotTrack(any(), any())).thenReturn(true);

      SearchAnalyticsCommand.record(widgetContext, "widgets", "pages", 3);

      repository.verify(() -> SearchAnalyticsRepository.save(any()), never());
    }
  }

  @Test
  void recordFloorsANegativeResultCountToZero() {
    try (MockedStatic<SearchAnalyticsRepository> repository = mockStatic(SearchAnalyticsRepository.class);
        MockedStatic<DoNotTrackCommand> dnt = mockStatic(DoNotTrackCommand.class)) {
      dnt.when(() -> DoNotTrackCommand.isDoNotTrack(any(), any())).thenReturn(false);

      SearchAnalyticsCommand.record(widgetContext, "widgets", "pages", -1);

      ArgumentCaptor<SearchAnalytics> captor = ArgumentCaptor.forClass(SearchAnalytics.class);
      repository.verify(() -> SearchAnalyticsRepository.save(captor.capture()));
      assertEqualsAll(captor.getValue(), "widgets", "pages", 0, "/example/path");
    }
  }

  private static void assertEqualsAll(SearchAnalytics saved, String query, String searchType, int resultCount, String pagePath) {
    assertEquals(query, saved.getQuery());
    assertEquals(searchType, saved.getSearchType());
    assertEquals(resultCount, saved.getResultCount());
    assertEquals(pagePath, saved.getPagePath());
  }
}
