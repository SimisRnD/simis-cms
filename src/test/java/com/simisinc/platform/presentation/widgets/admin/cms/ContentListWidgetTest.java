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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

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
      usageCommand.when(() -> ContentUsageCommand.scanUsage(any()))
          .thenReturn(new ContentUsageCommand.UsageScan(new LinkedHashMap<>(), new LinkedHashMap<>()));

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
      usageCommand.when(() -> ContentUsageCommand.scanUsage(any()))
          .thenReturn(new ContentUsageCommand.UsageScan(new LinkedHashMap<>(), new LinkedHashMap<>()));

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
      usageCommand.when(() -> ContentUsageCommand.scanUsage(any()))
          .thenReturn(new ContentUsageCommand.UsageScan(new LinkedHashMap<>(), new LinkedHashMap<>()));

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
      usageCommand.when(() -> ContentUsageCommand.scanUsage(any()))
          .thenReturn(new ContentUsageCommand.UsageScan(new LinkedHashMap<>(), new LinkedHashMap<>()));

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
      usageCommand.when(() -> ContentUsageCommand.scanUsage(any()))
          .thenReturn(new ContentUsageCommand.UsageScan(usageMap, new LinkedHashMap<>()));

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
      usageCommand.when(() -> ContentUsageCommand.scanUsage(any()))
          .thenReturn(new ContentUsageCommand.UsageScan(new LinkedHashMap<>(), new LinkedHashMap<>()));

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
      usageCommand.when(() -> ContentUsageCommand.scanUsage(any()))
          .thenReturn(new ContentUsageCommand.UsageScan(new LinkedHashMap<>(), new LinkedHashMap<>()));

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
      usageCommand.when(() -> ContentUsageCommand.scanUsage(any()))
          .thenReturn(new ContentUsageCommand.UsageScan(new LinkedHashMap<>(), new LinkedHashMap<>()));

      new ContentListWidget().execute(widgetContext);

      Map<String, String> statusMap = (Map<String, String>) widgetContext.getRequest().getAttribute("contentStatusMap");
      assertEquals(ContentReviewCommand.LIST_STATUS_LIVE, statusMap.get("block-live"));
      assertEquals(ContentReviewCommand.LIST_STATUS_DRAFT, statusMap.get("block-draft"));
    }
  }

  @Test
  void sharedUniqueIdsIncludesMultiLocationAndSingleFilesystemTemplateButNotASingleWebPageLocation() {
    // Bug fix (issue #499 follow-up): a block with exactly one usage location is only "Shared" when
    // that lone location is a site-wide filesystem template (e.g. footer-layout.xml) -- a raw count
    // of 1 must not be treated as Shared when the lone location is an ordinary web_pages page. See
    // ContentUsageCommandTest for the underlying isShared()/isFilesystemTemplateLocation() logic --
    // this test only proves the widget wires that logic into the "sharedUniqueIds" request attribute
    // the JSP's Shared badge reads.
    Map<String, List<String>> usageMap = new LinkedHashMap<>();
    usageMap.put("multi-page-block", List.of("/careers", "/about-us"));
    usageMap.put("site-footer", List.of("/WEB-INF/web-layouts/footer/footer-layout.xml"));
    usageMap.put("solo-page-block", List.of("/careers"));

    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class);
        MockedStatic<ContentUsageCommand> usageCommand = mockStatic(ContentUsageCommand.class)) {
      repository.when(() -> ContentRepository.findAll(any(ContentSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      usageCommand.when(() -> ContentUsageCommand.scanUsage(any()))
          .thenReturn(new ContentUsageCommand.UsageScan(usageMap, new LinkedHashMap<>()));
      // isShared/isFilesystemTemplateLocation are simple pure logic, not what this test is targeting
      // -- run the real implementation rather than stubbing it away (default would be a silent
      // always-false for both, since mockStatic() with no answer configured returns the primitive
      // default for an unstubbed boolean method).
      usageCommand.when(() -> ContentUsageCommand.isShared(any())).thenCallRealMethod();
      usageCommand.when(() -> ContentUsageCommand.isFilesystemTemplateLocation(any())).thenCallRealMethod();

      new ContentListWidget().execute(widgetContext);

      Set<String> sharedUniqueIds = (Set<String>) widgetContext.getRequest().getAttribute("sharedUniqueIds");
      assertTrue(sharedUniqueIds.contains("multi-page-block"), "used on 2 pages must be Shared");
      assertTrue(sharedUniqueIds.contains("site-footer"), "a single filesystem-template location must be Shared");
      assertFalse(sharedUniqueIds.contains("solo-page-block"), "a single ordinary web_pages location must not be Shared");
    }
  }

  @Test
  void templatedContentLocationsMatchesAnOrphanedLookingBlockButSkipsAKnownUsedBlock() {
    // Bug fix (issue #499 follow-up): a Content row with no real usage entry, but whose uniqueId
    // starts with a literal prefix seen behind an unresolved EL placeholder (e.g.
    // product-details-${item.uniqueId} in products-layout.xml), must be exposed as "Templated"
    // rather than left to render as plain "Orphaned".
    Content templated = new Content();
    templated.setUniqueId("product-details-abc123");
    Content alreadyUsed = new Content();
    alreadyUsed.setUniqueId("product-details-already-known");
    Content genuinelyOrphaned = new Content();
    genuinelyOrphaned.setUniqueId("nobody-references-me");
    List<Content> contentList = new ArrayList<>(List.of(templated, alreadyUsed, genuinelyOrphaned));

    Map<String, List<String>> usageMap = new LinkedHashMap<>();
    // alreadyUsed happens to also match the templated prefix, but it already has a real usage entry
    // -- it must not appear in templatedContentLocations too (that map distinguishes Orphaned from
    // Templated; it is not an extra badge for something already confirmed used).
    usageMap.put("product-details-already-known", List.of("/some-page"));

    Map<String, List<String>> templatedPrefixLocations = new LinkedHashMap<>();
    templatedPrefixLocations.put("product-details-", List.of("/WEB-INF/web-layouts/collection/products-layout.xml"));

    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class);
        MockedStatic<ContentUsageCommand> usageCommand = mockStatic(ContentUsageCommand.class)) {
      repository.when(() -> ContentRepository.findAll(any(ContentSpecification.class), any(DataConstraints.class)))
          .thenReturn(contentList);
      usageCommand.when(() -> ContentUsageCommand.scanUsage(any()))
          .thenReturn(new ContentUsageCommand.UsageScan(usageMap, templatedPrefixLocations));

      new ContentListWidget().execute(widgetContext);

      Map<String, List<String>> templatedContentLocations =
          (Map<String, List<String>>) widgetContext.getRequest().getAttribute("templatedContentLocations");
      assertEquals(List.of("/WEB-INF/web-layouts/collection/products-layout.xml"),
          templatedContentLocations.get("product-details-abc123"));
      assertFalse(templatedContentLocations.containsKey("product-details-already-known"),
          "a block with a real usage entry must not also be flagged Templated");
      assertFalse(templatedContentLocations.containsKey("nobody-references-me"),
          "a block matching no templated prefix stays plain Orphaned");
    }
  }

  @Test
  void deleteRequiresEditorPermission() {
    // Default WidgetBase login has no roles at all -- EditorPermissionCommand.canEditContent must
    // deny, the same permission tier every other content-mutating action uses (issue #499 follow-up:
    // ContentHtmlCommand#deleteContent was already fully implemented and audited, but nothing in the
    // UI ever called it; this proves the new delete() entry point is still permission-gated).
    addQueryParameter(widgetContext, "uniqueId", "some-block");

    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class)) {
      WidgetContext result = new ContentListWidget().delete(widgetContext);

      repository.verify(() -> ContentRepository.findByUniqueId(anyString()), never());
      assertNotNull(result.getWarningMessage());
      assertEquals("/example/path", result.getRedirect());
    }
  }

  @Test
  void deleteRemovesTheContentAndRedirectsBackToThisPage() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "uniqueId", "old-promo-banner");

    Content content = new Content();
    content.setId(42L);
    content.setUniqueId("old-promo-banner");

    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> ContentRepository.findByUniqueId("old-promo-banner")).thenReturn(content);
      repository.when(() -> ContentRepository.remove(content)).thenReturn(true);

      WidgetContext result = new ContentListWidget().delete(widgetContext);

      repository.verify(() -> ContentRepository.remove(content));
      audit.verify(() -> AuditEventCommand.record(eq(widgetContext), eq(AuditEventCommand.CONTENT), eq("content.delete"),
          eq(AuditEventCommand.SUCCESS), eq("content"), eq("42"), eq("old-promo-banner"), any()));
      assertEquals("The content was deleted", result.getSuccessMessage());
      assertEquals("/example/path", result.getRedirect());
    }
  }

  @Test
  void deleteWhenRemoveFailsRecordsAFailureAuditEventAndSetsAnErrorMessage() {
    // ContentHtmlCommand#deleteContent's ContentRepository.remove()==false branch was previously
    // reachable only from performWebAction's now-defunct dead code path and had zero coverage from
    // this new UI entry point -- a regression here (e.g. swallowing the failure, dropping the
    // FAILURE audit record, or losing the error message on the redirect back to this page) would
    // ship undetected.
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "uniqueId", "stubborn-block");

    Content content = new Content();
    content.setId(7L);
    content.setUniqueId("stubborn-block");

    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> ContentRepository.findByUniqueId("stubborn-block")).thenReturn(content);
      repository.when(() -> ContentRepository.remove(content)).thenReturn(false);

      WidgetContext result = new ContentListWidget().delete(widgetContext);

      repository.verify(() -> ContentRepository.remove(content));
      audit.verify(() -> AuditEventCommand.record(eq(widgetContext), eq(AuditEventCommand.CONTENT), eq("content.delete"),
          eq(AuditEventCommand.FAILURE), eq("content"), eq("7"), eq("stubborn-block"), any()));
      assertEquals("The content could not be deleted", result.getErrorMessage());
      assertEquals("/example/path", result.getRedirect());
    }
  }

  @Test
  void deleteWithAnUnknownUniqueIdWarnsWithoutCallingRemove() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "uniqueId", "already-gone");

    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class)) {
      repository.when(() -> ContentRepository.findByUniqueId("already-gone")).thenReturn(null);

      WidgetContext result = new ContentListWidget().delete(widgetContext);

      repository.verify(() -> ContentRepository.remove(any()), never());
      assertNotNull(result.getWarningMessage());
      assertEquals("/example/path", result.getRedirect());
    }
  }
}
