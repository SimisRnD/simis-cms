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
import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
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

    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findById(anyLong())).thenReturn(formData);
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.markAsProcessed(formData, widgetContext.getUserId())).thenReturn(true);

      setRoles(widgetContext, ADMIN);

      FormDataListWidget widget = new FormDataListWidget();
      widget.post(widgetContext);

      formDataRepositoryMockedStatic.verify(() -> FormDataRepository.markAsProcessed(formData, widgetContext.getUserId()), times(1));
    }
  }
}