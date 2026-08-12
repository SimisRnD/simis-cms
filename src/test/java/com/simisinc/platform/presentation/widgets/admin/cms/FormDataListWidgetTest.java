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

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.FunnelEventCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataSpecification;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.DataConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * @author matt rajkowski
 * @created 5/8/2022 7:00 AM
 *
 * markAsProcessedViaPostDispatchesThroughAction guards a real regression: the form-data list submits
 * archive/claim/markAsProcessed via a real HTTP POST (issue #358 moved state-changing admin actions off GET
 * query strings), so WebContainerContext routes the request to post(), not action() below -- action()'s
 * dispatch table was correct but unreachable, and this widget had no post() override at all, so the request
 * silently no-opped (redirect back to the same page, no error, no repository call). This test calls post()
 * directly, the same method a real request now reaches, so it fails if that dispatch gap reopens.
 */
class FormDataListWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"formDataList\">\n" +
        "  <title>Submitted Forms</title>\n" +
        "</widget>");

    List<FormData> formDataList = new ArrayList<>();
    FormData formData = new FormData();
    formData.setId(1L);
    formDataList.add(formData);

    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findAll(any(), any())).thenReturn(formDataList);

      // Use admin
      setRoles(widgetContext, ADMIN);

      // Execute the widget
      FormDataListWidget widget = new FormDataListWidget();
      widget.execute(widgetContext);
    }

    // Verify
    Assertions.assertEquals(FormDataListWidget.JSP, widgetContext.getJsp());
    Assertions.assertEquals("Submitted Forms", request.getAttribute("title"));
    List<FormData> formDataListRequest = (List) request.getAttribute("formDataList");
    Assertions.assertEquals(formData.getId(), formDataListRequest.get(0).getId());
  }

  @Test
  void actionFail() {
    addQueryParameter(widgetContext, "dataId", String.valueOf(1L));

    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findById(anyInt())).thenReturn(null);

      // Execute the widget
      FormDataListWidget widget = new FormDataListWidget();
      widget.action(widgetContext);

      // Verify
      Assertions.assertNotNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void action() {
    FormData formData = new FormData();
    formData.setId(1L);

    addQueryParameter(widgetContext, "dataId", String.valueOf(formData.getId()));
    addQueryParameter(widgetContext, "action", "archive");

    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findById(formData.getId())).thenReturn(formData);
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.tryToMarkAsClaimed(formData, widgetContext.getUserId())).thenReturn(true);

      // Use admin
      setRoles(widgetContext, ADMIN);

      // Execute the widget
      FormDataListWidget widget = new FormDataListWidget();
      widget.action(widgetContext);
    }
  }

  @Test
  void markAsProcessedViaPostDispatchesThroughAction() throws Exception {
    FormData formData = new FormData();
    formData.setId(1L);

    addQueryParameter(widgetContext, "dataId", String.valueOf(formData.getId()));
    addQueryParameter(widgetContext, "action", "markAsProcessed");

    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class);
        // issue #565 phase 1 -- markAsProcessed() now also offers the record to FunnelEventCommand;
        // mocked here (this test isn't about funnel tracking) so it never falls through to a real,
        // unmocked LoadSitePropertyCommand -> CacheManager -> DB round trip
        MockedStatic<FunnelEventCommand> funnelEventCommand = mockStatic(FunnelEventCommand.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findById(anyLong())).thenReturn(formData);
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.markAsProcessed(formData, widgetContext.getUserId())).thenReturn(true);

      setRoles(widgetContext, ADMIN);

      FormDataListWidget widget = new FormDataListWidget();
      widget.post(widgetContext);

      formDataRepositoryMockedStatic.verify(() -> FormDataRepository.markAsProcessed(formData, widgetContext.getUserId()), times(1));
    }
  }

  @Test
  void markAsProcessedRecordsAFunnelEventUsingTheOriginalSubmissionsOwnSessionId() throws Exception {
    // issue #565 phase 1 -- "processed" must be attributed to the ORIGINAL submitter's session, not
    // the admin's own (this handler runs from an admin's session, often days after the submission).
    FormData formData = new FormData();
    formData.setId(1L);
    formData.setFormUniqueId("contact-us");
    formData.setSessionId("original-submitter-session");

    addQueryParameter(widgetContext, "dataId", String.valueOf(formData.getId()));
    addQueryParameter(widgetContext, "action", "markAsProcessed");

    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class);
        MockedStatic<FunnelEventCommand> funnelEventCommand = mockStatic(FunnelEventCommand.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findById(anyLong())).thenReturn(formData);
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.markAsProcessed(formData, widgetContext.getUserId())).thenReturn(true);

      setRoles(widgetContext, ADMIN);

      FormDataListWidget widget = new FormDataListWidget();
      widget.post(widgetContext);

      funnelEventCommand.verify(() -> FunnelEventCommand.recordContactFormProcessed("contact-us", "original-submitter-session"));
    }
  }

  @Test
  void markAsProcessedDoesNotRecordAFunnelEventWhenTheUpdateFails() throws Exception {
    // Guards against double-counting/incorrect stage recording if the repository update didn't
    // actually take (e.g. a concurrent modification) -- FormDataRepository.markAsProcessed() returning
    // false must not still fire the funnel event.
    FormData formData = new FormData();
    formData.setId(1L);
    formData.setFormUniqueId("contact-us");
    formData.setSessionId("original-submitter-session");

    addQueryParameter(widgetContext, "dataId", String.valueOf(formData.getId()));
    addQueryParameter(widgetContext, "action", "markAsProcessed");

    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class);
        MockedStatic<FunnelEventCommand> funnelEventCommand = mockStatic(FunnelEventCommand.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findById(anyLong())).thenReturn(formData);
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.markAsProcessed(formData, widgetContext.getUserId())).thenReturn(false);

      setRoles(widgetContext, ADMIN);

      FormDataListWidget widget = new FormDataListWidget();
      widget.post(widgetContext);

      funnelEventCommand.verifyNoInteractions();
    }
  }

  @Test
  void executeDefaultsToAwaitingReview() {
    // No status param -- issue #563's filter form defaults to the page's original hardcoded behavior
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"formDataList\">\n" +
        "  <title>Submitted Forms</title>\n" +
        "</widget>");

    ArgumentCaptor<FormDataSpecification> specCaptor = ArgumentCaptor.forClass(FormDataSpecification.class);
    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findAll(specCaptor.capture(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      FormDataListWidget widget = new FormDataListWidget();
      widget.execute(widgetContext);
    }

    FormDataSpecification specification = specCaptor.getValue();
    Assertions.assertEquals(DataConstants.FALSE, specification.getDismissed());
    Assertions.assertEquals(DataConstants.FALSE, specification.getProcessed());
    Assertions.assertEquals("awaiting", request.getAttribute("status"));
  }

  @Test
  void executeStatusClaimedSetsClaimedTrue() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"formDataList\">\n" +
        "  <title>Submitted Forms</title>\n" +
        "</widget>");
    addQueryParameter(widgetContext, "status", "claimed");

    ArgumentCaptor<FormDataSpecification> specCaptor = ArgumentCaptor.forClass(FormDataSpecification.class);
    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findAll(specCaptor.capture(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      FormDataListWidget widget = new FormDataListWidget();
      widget.execute(widgetContext);
    }

    FormDataSpecification specification = specCaptor.getValue();
    Assertions.assertEquals(DataConstants.TRUE, specification.getClaimed());
    Assertions.assertEquals(DataConstants.UNDEFINED, specification.getDismissed(), "only the requested status should be set");
    Assertions.assertEquals(DataConstants.UNDEFINED, specification.getProcessed());
  }

  @Test
  void executeStatusProcessedSetsProcessedTrue() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"formDataList\">\n" +
        "  <title>Submitted Forms</title>\n" +
        "</widget>");
    addQueryParameter(widgetContext, "status", "processed");

    ArgumentCaptor<FormDataSpecification> specCaptor = ArgumentCaptor.forClass(FormDataSpecification.class);
    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findAll(specCaptor.capture(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      FormDataListWidget widget = new FormDataListWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(DataConstants.TRUE, specCaptor.getValue().getProcessed());
  }

  @Test
  void executeStatusDismissedSetsDismissedTrue() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"formDataList\">\n" +
        "  <title>Submitted Forms</title>\n" +
        "</widget>");
    addQueryParameter(widgetContext, "status", "dismissed");

    ArgumentCaptor<FormDataSpecification> specCaptor = ArgumentCaptor.forClass(FormDataSpecification.class);
    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findAll(specCaptor.capture(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      FormDataListWidget widget = new FormDataListWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(DataConstants.TRUE, specCaptor.getValue().getDismissed());
  }

  @Test
  void executeSpamFlaggedSetsFlaggedAsSpamTrue() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"formDataList\">\n" +
        "  <title>Submitted Forms</title>\n" +
        "</widget>");
    addQueryParameter(widgetContext, "spam", "flagged");

    ArgumentCaptor<FormDataSpecification> specCaptor = ArgumentCaptor.forClass(FormDataSpecification.class);
    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findAll(specCaptor.capture(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      FormDataListWidget widget = new FormDataListWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(DataConstants.TRUE, specCaptor.getValue().getFlaggedAsSpam());
    Assertions.assertEquals("flagged", request.getAttribute("spam"));
  }

  @Test
  void executeSpamExcludedSetsFlaggedAsSpamFalse() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"formDataList\">\n" +
        "  <title>Submitted Forms</title>\n" +
        "</widget>");
    addQueryParameter(widgetContext, "spam", "excluded");

    ArgumentCaptor<FormDataSpecification> specCaptor = ArgumentCaptor.forClass(FormDataSpecification.class);
    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findAll(specCaptor.capture(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      FormDataListWidget widget = new FormDataListWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(DataConstants.FALSE, specCaptor.getValue().getFlaggedAsSpam());
    Assertions.assertEquals("excluded", request.getAttribute("spam"));
  }

  @Test
  void executeDefaultSpamAppliesNoSpamFilter() {
    // No spam param -- "All" is the default, and unlike status this applies NO filter at all
    // (flaggedAsSpam stays DataConstants.UNDEFINED so the repository doesn't add a WHERE clause).
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"formDataList\">\n" +
        "  <title>Submitted Forms</title>\n" +
        "</widget>");

    ArgumentCaptor<FormDataSpecification> specCaptor = ArgumentCaptor.forClass(FormDataSpecification.class);
    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findAll(specCaptor.capture(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      FormDataListWidget widget = new FormDataListWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(DataConstants.UNDEFINED, specCaptor.getValue().getFlaggedAsSpam());
  }

  @Test
  void executeSpamFilterIsCarriedThroughPagingParams() {
    // Pagination must preserve the spam filter the same way it preserves formUniqueId/status/dates
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"formDataList\">\n" +
        "  <title>Submitted Forms</title>\n" +
        "</widget>");
    addQueryParameter(widgetContext, "spam", "flagged");

    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      FormDataListWidget widget = new FormDataListWidget();
      widget.execute(widgetContext);
    }

    String pagingParams = (String) request.getAttribute("recordPagingParams");
    Assertions.assertNotNull(pagingParams);
    Assertions.assertTrue(pagingParams.contains("spam=flagged"));
  }

  @Test
  void executeFormUniqueIdAndDateRangeAreAppliedAndEchoedBack() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"formDataList\">\n" +
        "  <title>Submitted Forms</title>\n" +
        "</widget>");
    addQueryParameter(widgetContext, "formUniqueId", "contact-us");
    addQueryParameter(widgetContext, "fromDate", "2026-07-01");
    addQueryParameter(widgetContext, "toDate", "2026-07-31");

    ArgumentCaptor<FormDataSpecification> specCaptor = ArgumentCaptor.forClass(FormDataSpecification.class);
    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findAll(specCaptor.capture(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      FormDataListWidget widget = new FormDataListWidget();
      widget.execute(widgetContext);
    }

    FormDataSpecification specification = specCaptor.getValue();
    Assertions.assertEquals("contact-us", specification.getFormUniqueId());
    Assertions.assertNotNull(specification.getOccurredAfter());
    Assertions.assertNotNull(specification.getOccurredBefore());
    // The toDate boundary is exclusive of the following day, so it must be after fromDate
    Assertions.assertTrue(specification.getOccurredBefore().after(specification.getOccurredAfter()));

    // The filter form and paging links both need these echoed back
    Assertions.assertEquals("contact-us", request.getAttribute("formUniqueId"));
    Assertions.assertEquals("2026-07-01", request.getAttribute("fromDate"));
    Assertions.assertEquals("2026-07-31", request.getAttribute("toDate"));
    String pagingParams = (String) request.getAttribute("recordPagingParams");
    Assertions.assertNotNull(pagingParams);
    Assertions.assertTrue(pagingParams.contains("formUniqueId=contact-us"));
  }

  @Test
  void downloadCSVFilePassesTheActiveFiltersThroughToExport() throws Exception {
    // Regression test: downloadCSVFile() previously called FormDataRepository.export(null, tempFile)
    // with a hardcoded null, so filtering the on-screen list (formUniqueId/status/fromDate/toDate)
    // and clicking "Download CSV" silently exported the whole table instead of the filtered view.
    // The specification passed to export() must be built from the same request parameters as
    // execute(), matching its filtering exactly.
    addQueryParameter(widgetContext, "command", "downloadCSVFile");
    addQueryParameter(widgetContext, "formUniqueId", "contact-us");
    addQueryParameter(widgetContext, "status", "processed");
    addQueryParameter(widgetContext, "fromDate", "2026-07-01");
    addQueryParameter(widgetContext, "toDate", "2026-07-31");

    setRoles(widgetContext, ADMIN);

    ArgumentCaptor<FormDataSpecification> specCaptor = ArgumentCaptor.forClass(FormDataSpecification.class);
    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class);
        MockedStatic<AuditEventCommand> auditEventCommand = mockStatic(AuditEventCommand.class);
        MockedStatic<FileSystemCommand> fileSystemCommand = mockStatic(FileSystemCommand.class)) {
      // generateTempFile() otherwise chases LoadSitePropertyCommand -> SitePropertyRepository -> a
      // real DB connection this unit test doesn't have; only the specification passed to export()
      // matters here, so stub it to a plain File with no filesystem/DB round trip.
      fileSystemCommand.when(() -> FileSystemCommand.generateTempFile(any(), anyLong(), any()))
          .thenReturn(new File("/tmp/does-not-exist.csv"));

      FormDataListWidget widget = new FormDataListWidget();
      widget.post(widgetContext);

      formDataRepositoryMockedStatic.verify(
          () -> FormDataRepository.export(specCaptor.capture(), any(), any(File.class)));
    }

    FormDataSpecification specification = specCaptor.getValue();
    Assertions.assertNotNull(specification,
        "export() must receive a specification built from the request's filters, not a hardcoded null");
    Assertions.assertEquals("contact-us", specification.getFormUniqueId());
    Assertions.assertEquals(DataConstants.TRUE, specification.getProcessed());
    Assertions.assertNotNull(specification.getOccurredAfter());
    Assertions.assertNotNull(specification.getOccurredBefore());
    Assertions.assertTrue(specification.getOccurredBefore().after(specification.getOccurredAfter()));
  }

  @Test
  void downloadCSVFileWithNoFiltersAppliedStillDefaultsToTheAwaitingReviewSpecification() throws Exception {
    // No formUniqueId/status/fromDate/toDate params -- mirrors execute()'s own default (issue #563:
    // the page's original hardcoded "awaiting review" view), so the export continues to match
    // whatever the on-screen list is showing by default rather than silently reverting to "everything".
    addQueryParameter(widgetContext, "command", "downloadCSVFile");

    setRoles(widgetContext, ADMIN);

    ArgumentCaptor<FormDataSpecification> specCaptor = ArgumentCaptor.forClass(FormDataSpecification.class);
    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class);
        MockedStatic<AuditEventCommand> auditEventCommand = mockStatic(AuditEventCommand.class);
        MockedStatic<FileSystemCommand> fileSystemCommand = mockStatic(FileSystemCommand.class)) {
      fileSystemCommand.when(() -> FileSystemCommand.generateTempFile(any(), anyLong(), any()))
          .thenReturn(new File("/tmp/does-not-exist.csv"));

      FormDataListWidget widget = new FormDataListWidget();
      widget.post(widgetContext);

      formDataRepositoryMockedStatic.verify(
          () -> FormDataRepository.export(specCaptor.capture(), any(), any(File.class)));
    }

    FormDataSpecification specification = specCaptor.getValue();
    Assertions.assertNull(specification.getFormUniqueId());
    Assertions.assertEquals(DataConstants.FALSE, specification.getDismissed());
    Assertions.assertEquals(DataConstants.FALSE, specification.getProcessed());
  }

  @Test
  void executeWithFormDataIdScopesToThatOneRecordRegardlessOfStatus() {
    // issue #1162 -- a direct link from the notification email must find the submission even if it's
    // no longer "awaiting" (the default status filter would otherwise silently exclude it)
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"formDataList\">\n" +
        "  <title>Submitted Forms</title>\n" +
        "</widget>");
    addQueryParameter(widgetContext, "formDataId", "42");

    ArgumentCaptor<FormDataSpecification> specCaptor = ArgumentCaptor.forClass(FormDataSpecification.class);
    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findAll(specCaptor.capture(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      FormDataListWidget widget = new FormDataListWidget();
      widget.execute(widgetContext);
    }

    FormDataSpecification specification = specCaptor.getValue();
    Assertions.assertEquals(42L, specification.getId());
    Assertions.assertNull(specification.getFormUniqueId(), "no other filter should be applied alongside a formDataId scope");
    Assertions.assertEquals(DataConstants.UNDEFINED, specification.getDismissed());
    Assertions.assertEquals(DataConstants.UNDEFINED, specification.getProcessed());
    Assertions.assertEquals(Boolean.TRUE, request.getAttribute("singleSubmissionView"));
  }

  @Test
  void executeWithoutFormDataIdLeavesSingleSubmissionViewFalse() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"formDataList\">\n" +
        "  <title>Submitted Forms</title>\n" +
        "</widget>");

    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      FormDataListWidget widget = new FormDataListWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(Boolean.FALSE, request.getAttribute("singleSubmissionView"));
  }

  @Test
  void downloadCSVFileWithFormDataIdScopesTheExportToThatOneRecord() throws Exception {
    // The CSV export must stay consistent with whatever's on screen -- if an admin is looking at the
    // single-submission view from an emailed link, exporting must not silently fall back to exporting
    // every submission (the same drift this shared-specification helper already guards against for
    // the formUniqueId/status/date filters).
    addQueryParameter(widgetContext, "command", "downloadCSVFile");
    addQueryParameter(widgetContext, "formDataId", "42");

    setRoles(widgetContext, ADMIN);

    ArgumentCaptor<FormDataSpecification> specCaptor = ArgumentCaptor.forClass(FormDataSpecification.class);
    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class);
        MockedStatic<AuditEventCommand> auditEventCommand = mockStatic(AuditEventCommand.class);
        MockedStatic<FileSystemCommand> fileSystemCommand = mockStatic(FileSystemCommand.class)) {
      fileSystemCommand.when(() -> FileSystemCommand.generateTempFile(any(), anyLong(), any()))
          .thenReturn(new File("/tmp/does-not-exist.csv"));

      FormDataListWidget widget = new FormDataListWidget();
      widget.post(widgetContext);

      formDataRepositoryMockedStatic.verify(
          () -> FormDataRepository.export(specCaptor.capture(), any(), any(File.class)));
    }

    Assertions.assertEquals(42L, specCaptor.getValue().getId());
  }

  @Test
  void postRejectsCallersWithoutTheRequiredRole() {
    // Logged in by default (WidgetBase.login()), but no admin/community-manager role granted
    addQueryParameter(widgetContext, "command", "downloadCSVFile");

    try {
      try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
        FormDataListWidget widget = new FormDataListWidget();
        WidgetContext result = widget.post(widgetContext);

        // The role gate must return before export() (and therefore any file download) is reached
        formDataRepositoryMockedStatic.verify(
            () -> FormDataRepository.export(any(), any(DataConstraints.class), any(File.class)), never());
        Assertions.assertFalse(widgetContext.handledResponse(), "no file should be streamed back");
        Assertions.assertSame(widgetContext, result);
      }
    } catch (InvocationTargetException | IllegalAccessException e) {
      fail(e.getMessage());
    }
  }
}
