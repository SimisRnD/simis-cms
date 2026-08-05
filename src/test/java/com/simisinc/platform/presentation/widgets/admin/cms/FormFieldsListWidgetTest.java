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

package com.simisinc.platform.presentation.widgets.admin.cms;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.cms.FormDefinition;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.infrastructure.persistence.cms.FormDefinitionRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormFieldRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * @author SimIS Inc.
 */
class FormFieldsListWidgetTest extends WidgetBase {

  @Test
  void executeLoadsTheFormAndItsFields() {
    FormDefinition contactUs = new FormDefinition();
    contactUs.setId(1L);
    contactUs.setName("Contact Us");

    List<FormField> fieldList = new ArrayList<>();
    FormField email = new FormField();
    email.setId(10L);
    fieldList.add(email);

    addQueryParameter(widgetContext, "formDefinitionId", "1");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(1L)).thenReturn(contactUs);
      formFieldRepository.when(() -> FormFieldRepository.findAllByFormDefinitionId(1L)).thenReturn(fieldList);

      WidgetContext result = new FormFieldsListWidget().execute(widgetContext);

      Assertions.assertEquals(FormFieldsListWidget.JSP, result.getJsp());
      Assertions.assertEquals(contactUs, request.getAttribute("formDefinition"));
      Assertions.assertEquals(fieldList, request.getAttribute("fieldList"));
    }
  }

  @Test
  void executeWithAnUnknownFormRedirectsToTheFormsList() {
    addQueryParameter(widgetContext, "formDefinitionId", "404");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(404L)).thenReturn(null);

      WidgetContext result = new FormFieldsListWidget().execute(widgetContext);

      Assertions.assertEquals("/admin/forms", result.getRedirect());
      Assertions.assertNotNull(result.getErrorMessage());
    }
  }

  @Test
  void postParsesTheCommaJoinedFieldOrderAndPersistsIt() throws InvocationTargetException, IllegalAccessException {
    addQueryParameter(widgetContext, "formDefinitionId", "1");
    addQueryParameter(widgetContext, "fieldOrder", "30,10,20");

    try (MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      WidgetContext result = new FormFieldsListWidget().post(widgetContext);

      formFieldRepository.verify(() -> FormFieldRepository.reorderFields(1L, Arrays.asList(30L, 10L, 20L)));
      Assertions.assertEquals("/admin/forms-editor?formDefinitionId=1", result.getRedirect());
    }
  }

  @Test
  void postWithABlankFieldOrderDoesNotCallReorder() throws InvocationTargetException, IllegalAccessException {
    addQueryParameter(widgetContext, "formDefinitionId", "1");

    try (MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      new FormFieldsListWidget().post(widgetContext);

      formFieldRepository.verify(() -> FormFieldRepository.reorderFields(eq(1L), org.mockito.ArgumentMatchers.any()), never());
    }
  }

  @Test
  void deleteRemovesTheFieldAndRedirectsBackToItsForm() {
    FormField email = new FormField();
    email.setId(10L);
    email.setFormDefinitionId(1L);

    addQueryParameter(widgetContext, "fieldId", "10");

    try (MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      formFieldRepository.when(() -> FormFieldRepository.findById(10L)).thenReturn(email);
      formFieldRepository.when(() -> FormFieldRepository.remove(email)).thenReturn(true);

      WidgetContext result = new FormFieldsListWidget().delete(widgetContext);

      Assertions.assertEquals("Field deleted", result.getSuccessMessage());
      Assertions.assertEquals("/admin/forms-editor?formDefinitionId=1", result.getRedirect());
    }
  }
}
