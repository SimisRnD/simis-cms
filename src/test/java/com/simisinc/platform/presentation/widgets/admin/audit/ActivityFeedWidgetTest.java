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

package com.simisinc.platform.presentation.widgets.admin.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.audit.AuditLog;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogRepository;
import com.simisinc.platform.presentation.controller.UserSession;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Tests the new /admin/activity feed widget (issue #1006): role gate, the multi-category checkbox filter
 * mapping onto findRecentActivity's category set, the day-range window, and the plain-language descriptions
 * attached to each row.
 *
 * @author SimIS Inc.
 */
class ActivityFeedWidgetTest extends WidgetBase {

  @Test
  void anyOfTheFourRolesThePageIsGatedToCanSeeTheFeed() {
    // WidgetBase#setRoles only recognizes its own named constants (ADMIN/CONTENT_MANAGER/CONTENT_EDITOR/
    // COMMUNITY_MANAGER/DATA_MANAGER); "ecommerce-manager" isn't one of them, so it's set directly here.
    for (String role : new String[] { ADMIN, CONTENT_MANAGER, COMMUNITY_MANAGER, "ecommerce-manager" }) {
      if ("ecommerce-manager".equals(role)) {
        UserSession userSession = widgetContext.getUserSession();
        userSession.setRoleList(List.of(new Role("E-commerce Manager", "ecommerce-manager")));
      } else {
        setRoles(widgetContext, role);
      }
      try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class)) {
        repository.when(() -> AuditLogRepository.findRecentActivity(
            any(), any(Timestamp.class), any(), any(DataConstraints.class)))
            .thenReturn(new ArrayList<>());

        WidgetContext result = new ActivityFeedWidget().execute(widgetContext);

        assertEquals(ActivityFeedWidget.JSP, result.getJsp(), "role " + role + " must reach the feed");
      }
    }
  }

  @Test
  void aRoleNotOnThePagesGateSeesNothing() {
    setRoles(widgetContext, DATA_MANAGER);

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class)) {
      new ActivityFeedWidget().execute(widgetContext);

      repository.verify(() -> AuditLogRepository.findRecentActivity(
          any(), any(Timestamp.class), any(), any(DataConstraints.class)), never());
      assertNull(widgetContext.getRequest().getAttribute("activityList"));
    }
  }

  @Test
  void noCategoryCheckedMeansEveryCategoryNotNoCategory() {
    setRoles(widgetContext, ADMIN);

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class)) {
      repository.when(() -> AuditLogRepository.findRecentActivity(
          isNull(), any(Timestamp.class), any(), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());

      new ActivityFeedWidget().execute(widgetContext);

      repository.verify(() -> AuditLogRepository.findRecentActivity(
          isNull(), any(Timestamp.class), any(), any(DataConstraints.class)));
    }
  }

  @Test
  void checkedCategoriesBecomeTheFilterSet() {
    setRoles(widgetContext, ADMIN);
    widgetContext.getParameterMap().put("category", new String[] { "content", "configuration" });

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class)) {
      repository.when(() -> AuditLogRepository.findRecentActivity(
          any(), any(Timestamp.class), any(), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());

      new ActivityFeedWidget().execute(widgetContext);

      ArgumentCaptor<Set<String>> categoriesCaptor = ArgumentCaptor.forClass(Set.class);
      repository.verify(() -> AuditLogRepository.findRecentActivity(
          categoriesCaptor.capture(), any(Timestamp.class), any(), any(DataConstraints.class)));
      assertEquals(Set.of("content", "configuration"), categoriesCaptor.getValue());

      @SuppressWarnings("unchecked")
      Set<String> selected = (Set<String>) widgetContext.getRequest().getAttribute("selectedCategories");
      assertEquals(Set.of("content", "configuration"), selected);

      String pagingParams = (String) widgetContext.getRequest().getAttribute("recordPagingParams");
      assertTrue(pagingParams.contains("category=content"));
      assertTrue(pagingParams.contains("category=configuration"));
    }
  }

  @Test
  void defaultWindowIsTheSharedTrailingWindowConstant() {
    setRoles(widgetContext, ADMIN);

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class)) {
      repository.when(() -> AuditLogRepository.findRecentActivity(
          any(), any(Timestamp.class), any(), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());

      Instant before = Instant.now().minus(AuditLogRepository.DEFAULT_TRAILING_WINDOW_DAYS, ChronoUnit.DAYS);
      new ActivityFeedWidget().execute(widgetContext);
      Instant after = Instant.now().minus(AuditLogRepository.DEFAULT_TRAILING_WINDOW_DAYS, ChronoUnit.DAYS);

      ArgumentCaptor<Timestamp> sinceCaptor = ArgumentCaptor.forClass(Timestamp.class);
      repository.verify(() -> AuditLogRepository.findRecentActivity(
          any(), sinceCaptor.capture(), any(), any(DataConstraints.class)));
      assertTrue(!sinceCaptor.getValue().toInstant().isBefore(before) && !sinceCaptor.getValue().toInstant().isAfter(after),
          "expected the default cutoff to be ~" + AuditLogRepository.DEFAULT_TRAILING_WINDOW_DAYS
              + " days ago, got " + sinceCaptor.getValue());
      assertEquals("7d", widgetContext.getRequest().getAttribute("range"));
    }
  }

  @Test
  void aWidenedRangeMovesTheWindow() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "range", "30d");

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class)) {
      repository.when(() -> AuditLogRepository.findRecentActivity(
          any(), any(Timestamp.class), any(), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());

      Instant before = Instant.now().minus(30, ChronoUnit.DAYS);
      new ActivityFeedWidget().execute(widgetContext);
      Instant after = Instant.now().minus(30, ChronoUnit.DAYS);

      ArgumentCaptor<Timestamp> sinceCaptor = ArgumentCaptor.forClass(Timestamp.class);
      repository.verify(() -> AuditLogRepository.findRecentActivity(
          any(), sinceCaptor.capture(), any(), any(DataConstraints.class)));
      assertTrue(!sinceCaptor.getValue().toInstant().isBefore(before) && !sinceCaptor.getValue().toInstant().isAfter(after));

      String pagingParams = (String) widgetContext.getRequest().getAttribute("recordPagingParams");
      assertTrue(pagingParams.contains("range=30d"));
    }
  }

  @Test
  void anUnrecognizedRangeFallsBackToTheDefaultWindow() {
    assertEquals(ActivityFeedWidget.DEFAULT_WINDOW_DAYS, ActivityFeedWidget.resolveWindowDays("banana"));
    assertEquals(ActivityFeedWidget.DEFAULT_WINDOW_DAYS, ActivityFeedWidget.resolveWindowDays(null));
    assertEquals(ActivityFeedWidget.DEFAULT_WINDOW_DAYS, ActivityFeedWidget.resolveWindowDays(""));
    assertEquals(7, ActivityFeedWidget.resolveWindowDays("7d"));
    assertEquals(14, ActivityFeedWidget.resolveWindowDays("14d"));
    assertEquals(30, ActivityFeedWidget.resolveWindowDays("30d"));
    assertEquals(90, ActivityFeedWidget.resolveWindowDays("90d"));
  }

  @Test
  void categoryListAndActivityListAreRealArrayListsNotAnArraysAsListView() {
    // Same ClassCastException trap AuditLogListWidgetTest guards against: activity-feed.jsp's
    // <jsp:useBean class="java.util.ArrayList"/> casts the request attribute directly.
    setRoles(widgetContext, ADMIN);

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class)) {
      repository.when(() -> AuditLogRepository.findRecentActivity(
          any(), any(Timestamp.class), any(), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());

      new ActivityFeedWidget().execute(widgetContext);

      Object categoryList = widgetContext.getRequest().getAttribute("categoryList");
      Object activityList = widgetContext.getRequest().getAttribute("activityList");
      assertTrue(categoryList instanceof java.util.ArrayList,
          "categoryList must be a real java.util.ArrayList: was " + (categoryList == null ? "null" : categoryList.getClass()));
      assertTrue(activityList instanceof java.util.ArrayList,
          "activityList must be a real java.util.ArrayList: was " + (activityList == null ? "null" : activityList.getClass()));
    }
  }

  @Test
  void eachEntryCarriesAPlainLanguageDescriptionAndTheRowFieldsTheJspRenders() {
    setRoles(widgetContext, ADMIN);

    AuditLog record = new AuditLog();
    record.setOccurred(new Timestamp(System.currentTimeMillis()));
    record.setEventCategory("content");
    record.setEventType("content.publish");
    record.setOutcome("success");
    record.setActorUsername("editor@example.com");
    record.setTargetType("web_page");
    record.setTargetLabel("Homepage");

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class)) {
      repository.when(() -> AuditLogRepository.findRecentActivity(
          any(), any(Timestamp.class), any(), any(DataConstraints.class)))
          .thenReturn(List.of(record));

      new ActivityFeedWidget().execute(widgetContext);

      @SuppressWarnings("unchecked")
      List<ActivityFeedWidget.ActivityEntry> entries =
          (List<ActivityFeedWidget.ActivityEntry>) widgetContext.getRequest().getAttribute("activityList");
      assertEquals(1, entries.size());
      ActivityFeedWidget.ActivityEntry entry = entries.get(0);
      assertEquals("content", entry.getEventCategory());
      assertEquals("content.publish", entry.getEventType());
      assertEquals("published a page", entry.getDescription());
      assertEquals("success", entry.getOutcome());
      assertEquals("editor@example.com", entry.getActorUsername());
      assertEquals("web_page", entry.getTargetType());
      assertEquals("Homepage", entry.getTargetLabel());
    }
  }

  @Test
  void aNullRepositoryResultBecomesAnEmptyListRatherThanANullPointerException() {
    setRoles(widgetContext, ADMIN);

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class)) {
      repository.when(() -> AuditLogRepository.findRecentActivity(
          any(), any(Timestamp.class), any(), any(DataConstraints.class)))
          .thenReturn(null);

      new ActivityFeedWidget().execute(widgetContext);

      Object activityList = widgetContext.getRequest().getAttribute("activityList");
      assertTrue(activityList instanceof List);
      assertTrue(((List<?>) activityList).isEmpty());
    }
  }

  @Test
  void categoryListReusesAuditLogListWidgetsCanonicalTaxonomy() {
    setRoles(widgetContext, ADMIN);

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class)) {
      repository.when(() -> AuditLogRepository.findRecentActivity(
          any(), any(Timestamp.class), any(), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());

      new ActivityFeedWidget().execute(widgetContext);

      assertEquals(AuditLogListWidget.CATEGORY_LIST, widgetContext.getRequest().getAttribute("categoryList"));
      assertFalse(AuditLogListWidget.CATEGORY_LIST.isEmpty());
    }
  }
}
