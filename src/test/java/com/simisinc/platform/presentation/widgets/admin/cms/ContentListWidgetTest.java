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

package com.simisinc.platform.presentation.widgets.admin.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.application.cms.ContentUsageCommand;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ContentSpecification;

/**
 * Tests the /admin/content-list widget (issue #499): search/filter request parameters map onto the
 * query specification, pagination carries the filters forward, and the usage map (the "Used on:" /
 * "Orphaned" display) is built and exposed to the JSP. The usage-detection scanning logic itself
 * (widget-family scoping, filesystem-template detection) is covered separately in
 * {@code ContentUsageCommandTest}.
 *
 * @author SimIS Inc.
 */
class ContentListWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"contentList\">\n" +
        "  <title>Content</title>\n" +
        "</widget>");

    List<Content> contentList = new ArrayList<>();
    Content content = new Content();
    content.setId(1L);
    contentList.add(content);

    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class);
        MockedStatic<ContentUsageCommand> usageCommand = mockStatic(ContentUsageCommand.class)) {
      repository.when(() -> ContentRepository.findAll(any(ContentSpecification.class), any(DataConstraints.class)))
          .thenReturn(contentList);
      usageCommand.when(() -> ContentUsageCommand.findUsageMap(any())).thenReturn(new LinkedHashMap<>());

      // Execute the widget
      ContentListWidget widget = new ContentListWidget();
      widget.execute(widgetContext);
    }

    // Verify
    assertEquals(ContentListWidget.JSP, widgetContext.getJsp());
    assertEquals("Content", request.getAttribute("title"));
    List<Content> contentListRequest = (List) request.getAttribute("contentList");
    assertEquals(content.getId(), contentListRequest.get(0).getId());
  }

  @Test
  void searchAndFilterParametersMapOntoTheSpecification() {
    addQueryParameter(widgetContext, "q", "cmmc header");
    addQueryParameter(widgetContext, "fromDate", "2026-07-01");
    addQueryParameter(widgetContext, "toDate", "2026-07-20");
    addQueryParameter(widgetContext, "minLength", "100");
    addQueryParameter(widgetContext, "maxLength", "5000");

    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class);
        MockedStatic<ContentUsageCommand> usageCommand = mockStatic(ContentUsageCommand.class)) {
      repository.when(() -> ContentRepository.findAll(any(ContentSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      usageCommand.when(() -> ContentUsageCommand.findUsageMap(any())).thenReturn(new LinkedHashMap<>());

      new ContentListWidget().execute(widgetContext);

      ArgumentCaptor<ContentSpecification> captor = ArgumentCaptor.forClass(ContentSpecification.class);
      repository.verify(() -> ContentRepository.findAll(captor.capture(), any(DataConstraints.class)));
      ContentSpecification spec = captor.getValue();

      assertEquals("cmmc header", spec.getSearchTerm());
      assertEquals(Timestamp.valueOf(LocalDate.parse("2026-07-01").atStartOfDay()), spec.getDateModifiedAfter());
      // The "to" bound is half-open: the start of the day AFTER the picked date, so that whole day is included
      assertEquals(Timestamp.valueOf(LocalDate.parse("2026-07-21").atStartOfDay()), spec.getDateModifiedBefore());
      assertEquals(100, spec.getMinLength());
      assertEquals(5000, spec.getMaxLength());

      // Pagination must carry the filters forward (URL-encoded) so page 2+ stays filtered
      String pagingParams = (String) widgetContext.getRequest().getAttribute("recordPagingParams");
      assertTrue(pagingParams.contains("q=cmmc+header")); // space is URL-encoded
      assertTrue(pagingParams.contains("fromDate=2026-07-01"));
      assertTrue(pagingParams.contains("toDate=2026-07-20"));
      assertTrue(pagingParams.contains("minLength=100"));
      assertTrue(pagingParams.contains("maxLength=5000"));
    }
  }

  @Test
  void blankFiltersLeaveTheSpecificationUnset() {
    // No query parameters at all -- every ContentSpecification field should stay at its default,
    // and the "empty search" case must not accidentally apply a filter.
    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class);
        MockedStatic<ContentUsageCommand> usageCommand = mockStatic(ContentUsageCommand.class)) {
      repository.when(() -> ContentRepository.findAll(any(ContentSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      usageCommand.when(() -> ContentUsageCommand.findUsageMap(any())).thenReturn(new LinkedHashMap<>());

      new ContentListWidget().execute(widgetContext);

      ArgumentCaptor<ContentSpecification> captor = ArgumentCaptor.forClass(ContentSpecification.class);
      repository.verify(() -> ContentRepository.findAll(captor.capture(), any(DataConstraints.class)));
      ContentSpecification spec = captor.getValue();

      assertNull(spec.getSearchTerm());
      assertNull(spec.getDateModifiedAfter());
      assertNull(spec.getDateModifiedBefore());
      assertEquals(-1, spec.getMinLength());
      assertEquals(-1, spec.getMaxLength());
    }
  }

  @Test
  void anInvalidDateIsIgnoredRatherThanBreakingTheQuery() {
    addQueryParameter(widgetContext, "fromDate", "not-a-date");

    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class);
        MockedStatic<ContentUsageCommand> usageCommand = mockStatic(ContentUsageCommand.class)) {
      repository.when(() -> ContentRepository.findAll(any(ContentSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      usageCommand.when(() -> ContentUsageCommand.findUsageMap(any())).thenReturn(new LinkedHashMap<>());

      new ContentListWidget().execute(widgetContext);

      ArgumentCaptor<ContentSpecification> captor = ArgumentCaptor.forClass(ContentSpecification.class);
      repository.verify(() -> ContentRepository.findAll(captor.capture(), any(DataConstraints.class)));
      assertNull(captor.getValue().getDateModifiedAfter());
    }
  }

  @Test
  void theUsageMapIsBuiltAndExposedToTheRequest() {
    Map<String, List<String>> usageMap = new LinkedHashMap<>();
    usageMap.put("site-footer", List.of("/WEB-INF/web-layouts/footer/footer-layout.xml"));
    usageMap.put("cmmc-header", List.of("/careers", "/about-us"));

    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class);
        MockedStatic<ContentUsageCommand> usageCommand = mockStatic(ContentUsageCommand.class)) {
      repository.when(() -> ContentRepository.findAll(any(ContentSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      usageCommand.when(() -> ContentUsageCommand.findUsageMap(any())).thenReturn(usageMap);

      new ContentListWidget().execute(widgetContext);

      Object exposed = widgetContext.getRequest().getAttribute("contentUsageMap");
      assertSame(usageMap, exposed, "the widget must expose exactly the map ContentUsageCommand built, for the JSP's Used-on/Orphaned display");
    }
  }

  @Test
  void statusParameterMapsOntoTheSpecificationAndPagingParams() {
    addQueryParameter(widgetContext, "status", ContentReviewCommand.LIST_STATUS_PENDING_REVIEW);

    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class);
        MockedStatic<ContentUsageCommand> usageCommand = mockStatic(ContentUsageCommand.class)) {
      repository.when(() -> ContentRepository.findAll(any(ContentSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      usageCommand.when(() -> ContentUsageCommand.findUsageMap(any())).thenReturn(new LinkedHashMap<>());

      new ContentListWidget().execute(widgetContext);

      ArgumentCaptor<ContentSpecification> captor = ArgumentCaptor.forClass(ContentSpecification.class);
      repository.verify(() -> ContentRepository.findAll(captor.capture(), any(DataConstraints.class)));
      assertEquals(ContentReviewCommand.LIST_STATUS_PENDING_REVIEW, captor.getValue().getStatus());

      // Carried through pagination, URL-encoded (the space in "Pending Review" becomes '+')
      String pagingParams = (String) widgetContext.getRequest().getAttribute("recordPagingParams");
      assertTrue(pagingParams.contains("status=Pending+Review"));
    }
  }

  @Test
  void blankStatusParameterLeavesTheSpecificationUnset() {
    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class);
        MockedStatic<ContentUsageCommand> usageCommand = mockStatic(ContentUsageCommand.class)) {
      repository.when(() -> ContentRepository.findAll(any(ContentSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      usageCommand.when(() -> ContentUsageCommand.findUsageMap(any())).thenReturn(new LinkedHashMap<>());

      new ContentListWidget().execute(widgetContext);

      ArgumentCaptor<ContentSpecification> captor = ArgumentCaptor.forClass(ContentSpecification.class);
      repository.verify(() -> ContentRepository.findAll(captor.capture(), any(DataConstraints.class)));
      assertNull(captor.getValue().getStatus());
    }
  }

  @Test
  void theStatusMapIsBuiltFromEachContentRecordAndExposedToTheRequest() {
    // A Live block (no draft) and a Draft block (a draft, never submitted) -- exercises
    // ContentReviewCommand.listStatusLabel through the widget rather than mocking it away, so this
    // also catches a regression that stops calling it.
    Content live = new Content();
    live.setUniqueId("block-live");
    Content draft = new Content();
    draft.setUniqueId("block-draft");
    draft.setDraftContent("<p>editing</p>");
    List<Content> contentList = new ArrayList<>(List.of(live, draft));

    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class);
        MockedStatic<ContentUsageCommand> usageCommand = mockStatic(ContentUsageCommand.class)) {
      repository.when(() -> ContentRepository.findAll(any(ContentSpecification.class), any(DataConstraints.class)))
          .thenReturn(contentList);
      usageCommand.when(() -> ContentUsageCommand.findUsageMap(any())).thenReturn(new LinkedHashMap<>());

      new ContentListWidget().execute(widgetContext);

      Map<String, String> statusMap = (Map<String, String>) widgetContext.getRequest().getAttribute("contentStatusMap");
      assertEquals(ContentReviewCommand.LIST_STATUS_LIVE, statusMap.get("block-live"));
      assertEquals(ContentReviewCommand.LIST_STATUS_DRAFT, statusMap.get("block-draft"));
    }
  }
}
