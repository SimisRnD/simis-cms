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
import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataSpecification;
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
  void postRejectsCallersWithoutTheRequiredRole() {
    // Logged in by default (WidgetBase.login()), but no admin/community-manager role granted
    addQueryParameter(widgetContext, "command", "downloadCSVFile");

    try {
      try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
        FormDataListWidget widget = new FormDataListWidget();
        WidgetContext result = widget.post(widgetContext);

        // The role gate must return before export() (and therefore any file download) is reached
        formDataRepositoryMockedStatic.verify(
            () -> FormDataRepository.export(any(DataConstraints.class), any(File.class)), never());
        Assertions.assertFalse(widgetContext.handledResponse(), "no file should be streamed back");
        Assertions.assertSame(widgetContext, result);
      }
    } catch (InvocationTargetException | IllegalAccessException e) {
      fail(e.getMessage());
    }
  }
}
