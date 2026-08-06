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

package com.simisinc.platform.presentation.widgets.admin.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.audit.AuditLogIntegrityCommand;
import com.simisinc.platform.application.audit.AuditLogIntegrityCommand.AuditIntegrityResult;
import com.simisinc.platform.domain.model.audit.AuditLog;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogRepository;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogSpecification;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Tests the audit review widget: filters map onto the query specification, and the log is admin-only.
 *
 * @author SimIS Inc.
 */
class AuditLogListWidgetTest extends WidgetBase {

  @Test
  void filterParametersMapOntoTheSpecification() {
    setRoles(widgetContext, "admin");
    addQueryParameter(widgetContext, "category", "user_management");
    addQueryParameter(widgetContext, "eventType", "user.disable");
    addQueryParameter(widgetContext, "outcome", "failure");
    addQueryParameter(widgetContext, "actor", "Admin@Example.com");
    addQueryParameter(widgetContext, "fromDate", "2026-07-01");
    addQueryParameter(widgetContext, "toDate", "2026-07-20");

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("audit.retentionDays")).thenReturn(null);

      new AuditLogListWidget().execute(widgetContext);

      ArgumentCaptor<AuditLogSpecification> captor = ArgumentCaptor.forClass(AuditLogSpecification.class);
      repository.verify(() -> AuditLogRepository.findAll(captor.capture(), any(DataConstraints.class)));
      AuditLogSpecification spec = captor.getValue();

      assertEquals("user_management", spec.getEventCategory());
      assertEquals("user.disable", spec.getEventType());
      assertEquals("failure", spec.getOutcome());
      assertEquals("Admin@Example.com", spec.getActorUsername());
      assertEquals(Timestamp.valueOf(LocalDate.parse("2026-07-01").atStartOfDay()), spec.getOccurredAfter());
      // The "to" bound is half-open: the start of the day AFTER the picked date, so that whole day is included
      assertEquals(Timestamp.valueOf(LocalDate.parse("2026-07-21").atStartOfDay()), spec.getOccurredBefore());

      // Pagination must carry the filters forward (URL-encoded) so page 2+ stays filtered
      String pagingParams = (String) widgetContext.getRequest().getAttribute("recordPagingParams");
      assertTrue(pagingParams.contains("category=user_management"));
      assertTrue(pagingParams.contains("eventType=user.disable"));
      assertTrue(pagingParams.contains("outcome=failure"));
      assertTrue(pagingParams.contains("actor=Admin%40Example.com")); // '@' is URL-encoded
      assertTrue(pagingParams.contains("fromDate=2026-07-01"));
      assertTrue(pagingParams.contains("toDate=2026-07-20"));
    }
  }

  @Test
  void categoryListIsARealArrayListNotTheArraysAsListView() {
    // audit-log-list.jsp declares <jsp:useBean id="categoryList" class="java.util.ArrayList" .../>,
    // which casts the request attribute directly to that concrete class. Arrays.asList() returns
    // java.util.Arrays$ArrayList -- a different class despite the name -- and that mismatch threw a
    // ClassCastException on every single page load, filtered or not, until CATEGORY_LIST was wrapped
    // in a real ArrayList. A plain "is it a List" assertion would not have caught this.
    setRoles(widgetContext, "admin");

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("audit.retentionDays")).thenReturn(null);

      new AuditLogListWidget().execute(widgetContext);

      Object categoryList = widgetContext.getRequest().getAttribute("categoryList");
      assertTrue(categoryList instanceof java.util.ArrayList,
          "categoryList must be a real java.util.ArrayList, not just any List implementation, "
              + "to satisfy the JSP's <jsp:useBean> cast: was " + (categoryList == null ? "null"
                  : categoryList.getClass()));
    }
  }

  @Test
  void aNonAdminIsNotShownTheAuditLog() {
    setRoles(widgetContext); // logged in, but no admin role

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class)) {
      new AuditLogListWidget().execute(widgetContext);

      // The widget must return before querying or exposing any records
      repository.verify(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)),
          never());
      assertNull(widgetContext.getRequest().getAttribute("auditLogList"));
    }
  }

  @Test
  void sourceIpAndTargetTypeMapOntoTheSpecification() {
    setRoles(widgetContext, "admin");
    addQueryParameter(widgetContext, "sourceIp", "203.0.113.4");
    addQueryParameter(widgetContext, "targetType", "user");

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("audit.retentionDays")).thenReturn(null);

      new AuditLogListWidget().execute(widgetContext);

      ArgumentCaptor<AuditLogSpecification> captor = ArgumentCaptor.forClass(AuditLogSpecification.class);
      repository.verify(() -> AuditLogRepository.findAll(captor.capture(), any(DataConstraints.class)));
      AuditLogSpecification spec = captor.getValue();

      assertEquals("203.0.113.4", spec.getSourceIp());
      assertEquals("user", spec.getTargetType());

      String pagingParams = (String) widgetContext.getRequest().getAttribute("recordPagingParams");
      assertTrue(pagingParams.contains("sourceIp=203.0.113.4"));
      assertTrue(pagingParams.contains("targetType=user"));
    }
  }

  @Test
  void targetLabelMapsOntoTheSpecification() {
    // Backs the per-row "History" link on /admin/blocked-ip-list and /admin/allowed-ip-list
    // (?targetType=blocked_ip&targetLabel=<ip address>) - proves the link's query params actually
    // reach the filter, not just that the JSP builds a URL.
    setRoles(widgetContext, "admin");
    addQueryParameter(widgetContext, "targetType", "blocked_ip");
    addQueryParameter(widgetContext, "targetLabel", "203.0.113.5");

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("audit.retentionDays")).thenReturn(null);

      new AuditLogListWidget().execute(widgetContext);

      ArgumentCaptor<AuditLogSpecification> captor = ArgumentCaptor.forClass(AuditLogSpecification.class);
      repository.verify(() -> AuditLogRepository.findAll(captor.capture(), any(DataConstraints.class)));
      AuditLogSpecification spec = captor.getValue();

      assertEquals("blocked_ip", spec.getTargetType());
      assertEquals("203.0.113.5", spec.getTargetLabel());

      String pagingParams = (String) widgetContext.getRequest().getAttribute("recordPagingParams");
      assertTrue(pagingParams.contains("targetType=blocked_ip"));
      assertTrue(pagingParams.contains("targetLabel=203.0.113.5"));
    }
  }

  @Test
  void aQuickRangePresetTakesPrecedenceOverAnExplicitDateRange() {
    setRoles(widgetContext, "admin");
    addQueryParameter(widgetContext, "range", "24h");
    // These would normally set a half-open [from, to) window -- the preset must win over them.
    addQueryParameter(widgetContext, "fromDate", "2020-01-01");
    addQueryParameter(widgetContext, "toDate", "2020-01-02");

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("audit.retentionDays")).thenReturn(null);

      Instant before = Instant.now().minus(24, ChronoUnit.HOURS);
      new AuditLogListWidget().execute(widgetContext);
      Instant after = Instant.now().minus(24, ChronoUnit.HOURS);

      ArgumentCaptor<AuditLogSpecification> captor = ArgumentCaptor.forClass(AuditLogSpecification.class);
      repository.verify(() -> AuditLogRepository.findAll(captor.capture(), any(DataConstraints.class)));
      AuditLogSpecification spec = captor.getValue();

      // The cutoff is "now" at execute() time, minus 24h -- bracket it rather than pin an exact instant.
      assertTrue(!spec.getOccurredAfter().toInstant().isBefore(before) && !spec.getOccurredAfter().toInstant().isAfter(after),
          "expected a cutoff around 24h ago, got " + spec.getOccurredAfter());
      assertNull(spec.getOccurredBefore(), "a range preset has no upper bound, unlike the explicit date-range fields");
    }
  }

  @Test
  void retentionDaysIsExposedToTheView() {
    setRoles(widgetContext, "admin");

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      repository.when(() -> AuditLogRepository.resolveRetentionDays("365")).thenReturn(365);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("audit.retentionDays")).thenReturn("365");

      new AuditLogListWidget().execute(widgetContext);

      assertEquals(365, widgetContext.getRequest().getAttribute("retentionDays"));
    }
  }

  @Test
  void exportIsRefusedWithoutAdminRole() {
    setRoles(widgetContext); // logged in, but no admin role
    addQueryParameter(widgetContext, "command", "downloadCSVFile");

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class)) {
      WidgetContext result = new AuditLogListWidget().post(widgetContext);

      repository.verifyNoInteractions();
      assertEquals(widgetContext, result);
    }
  }

  @Test
  void anUnrecognizedCommandIsIgnored() {
    setRoles(widgetContext, "admin");
    addQueryParameter(widgetContext, "command", "somethingElse");

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class)) {
      WidgetContext result = new AuditLogListWidget().post(widgetContext);

      repository.verifyNoInteractions();
      assertNull(result);
    }
  }

  @Test
  void resolveRangeCutoffRecognizesEveryPreset() {
    Instant now = Instant.now();
    assertTrue(AuditLogListWidget.resolveRangeCutoff("1h").toInstant().isBefore(now));
    assertTrue(AuditLogListWidget.resolveRangeCutoff("24h").toInstant().isBefore(now.minus(1, ChronoUnit.HOURS)));
    assertTrue(AuditLogListWidget.resolveRangeCutoff("7d").toInstant().isBefore(now.minus(6, ChronoUnit.DAYS)));
    assertTrue(AuditLogListWidget.resolveRangeCutoff("30d").toInstant().isBefore(now.minus(29, ChronoUnit.DAYS)));
  }

  @Test
  void resolveRangeCutoffReturnsNullForBlankOrUnrecognizedValues() {
    assertNull(AuditLogListWidget.resolveRangeCutoff(null));
    assertNull(AuditLogListWidget.resolveRangeCutoff(""));
    assertNull(AuditLogListWidget.resolveRangeCutoff("3 weeks ago"));
  }

  @Test
  void integrityCheckBannerIsShownWhenTheMostRecentCheckFailed() {
    setRoles(widgetContext, "admin");

    AuditLog failedCheck = new AuditLog();
    failedCheck.setOutcome("failure");
    Timestamp occurred = Timestamp.valueOf("2026-08-05 04:30:00");
    failedCheck.setOccurred(occurred);
    failedCheck.setDetails("checked=120;reason=record_hash mismatch (the record was altered)");

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      repository.when(() -> AuditLogRepository.findMostRecentByEventType("configuration", "audit.integrity.check"))
          .thenReturn(failedCheck);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("audit.retentionDays")).thenReturn(null);

      new AuditLogListWidget().execute(widgetContext);

      assertEquals(Boolean.TRUE, widgetContext.getRequest().getAttribute("integrityCheckFailed"));
      assertEquals(occurred, widgetContext.getRequest().getAttribute("integrityCheckFailedAt"));
      assertEquals("checked=120;reason=record_hash mismatch (the record was altered)",
          widgetContext.getRequest().getAttribute("integrityCheckFailedDetails"));
    }
  }

  @Test
  void integrityCheckBannerIsNotShownWhenTheMostRecentCheckSucceeded() {
    setRoles(widgetContext, "admin");

    AuditLog passedCheck = new AuditLog();
    passedCheck.setOutcome("success");
    passedCheck.setOccurred(new Timestamp(System.currentTimeMillis()));

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      repository.when(() -> AuditLogRepository.findMostRecentByEventType("configuration", "audit.integrity.check"))
          .thenReturn(passedCheck);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("audit.retentionDays")).thenReturn(null);

      new AuditLogListWidget().execute(widgetContext);

      assertNull(widgetContext.getRequest().getAttribute("integrityCheckFailed"),
          "a passing check must not trigger the warning banner (avoids a false 'chain healthy' banner too)");
    }
  }

  @Test
  void integrityCheckBannerIsNotShownWhenNoCheckHasEverRun() {
    setRoles(widgetContext, "admin");

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      repository.when(() -> AuditLogRepository.findMostRecentByEventType(anyString(), anyString()))
          .thenReturn(null);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("audit.retentionDays")).thenReturn(null);

      new AuditLogListWidget().execute(widgetContext);

      assertNull(widgetContext.getRequest().getAttribute("integrityCheckFailed"),
          "no integrity-check event has ever run, so nothing should be flagged as failed");
    }
  }

  @Test
  void onDemandIntegrityCheckCallsVerifyAndShowsASuccessMessageWhenTheChainIsIntact() {
    setRoles(widgetContext, "admin");
    addQueryParameter(widgetContext, "runIntegrityCheck", "true");

    AuditIntegrityResult intactResult = mock(AuditIntegrityResult.class);
    when(intactResult.isIntact()).thenReturn(true);
    when(intactResult.getCheckedCount()).thenReturn(42L);

    try (MockedStatic<AuditLogIntegrityCommand> integrityCommand = mockStatic(AuditLogIntegrityCommand.class)) {
      integrityCommand.when(AuditLogIntegrityCommand::verify).thenReturn(intactResult);

      WidgetContext result = new AuditLogListWidget().post(widgetContext);

      integrityCommand.verify(AuditLogIntegrityCommand::verify);
      assertNotNull(result);
      assertNotNull(result.getSuccessMessage());
      assertTrue(result.getSuccessMessage().contains("42"), "expected the checked-record count in the message: "
          + result.getSuccessMessage());
      assertNull(result.getErrorMessage());
      assertEquals("/admin/audit-log", result.getRedirect());
    }
  }

  @Test
  void onDemandIntegrityCheckCallsVerifyAndShowsAnErrorMessageWhenTheChainIsBroken() {
    setRoles(widgetContext, "admin");
    addQueryParameter(widgetContext, "runIntegrityCheck", "true");

    AuditIntegrityResult brokenResult = mock(AuditIntegrityResult.class);
    when(brokenResult.isIntact()).thenReturn(false);
    when(brokenResult.getFirstInvalidAuditId()).thenReturn(7L);
    when(brokenResult.getCheckedCount()).thenReturn(6L);
    when(brokenResult.getReason()).thenReturn("record_hash mismatch (the record was altered)");

    try (MockedStatic<AuditLogIntegrityCommand> integrityCommand = mockStatic(AuditLogIntegrityCommand.class)) {
      integrityCommand.when(AuditLogIntegrityCommand::verify).thenReturn(brokenResult);

      WidgetContext result = new AuditLogListWidget().post(widgetContext);

      integrityCommand.verify(AuditLogIntegrityCommand::verify);
      assertNotNull(result);
      assertNotNull(result.getErrorMessage());
      assertTrue(result.getErrorMessage().contains("audit_id=7"), "expected the failing audit_id in the message: "
          + result.getErrorMessage());
      assertTrue(result.getErrorMessage().contains("record_hash mismatch"));
      assertNull(result.getSuccessMessage());
      assertEquals("/admin/audit-log", result.getRedirect());
    }
  }

  @Test
  void onDemandIntegrityCheckIsRefusedWithoutAdminRole() {
    setRoles(widgetContext); // logged in, but no admin role
    addQueryParameter(widgetContext, "runIntegrityCheck", "true");

    try (MockedStatic<AuditLogIntegrityCommand> integrityCommand = mockStatic(AuditLogIntegrityCommand.class)) {
      WidgetContext result = new AuditLogListWidget().post(widgetContext);

      integrityCommand.verifyNoInteractions();
      assertEquals(widgetContext, result);
    }
  }
}
