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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;

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
class FormFieldFormWidgetTest extends WidgetBase {

  @Test
  void executeLoadsAnExistingFieldAndFormatsItsOptionsForEditing() {
    FormField colorField = new FormField();
    colorField.setId(10L);
    colorField.setFormDefinitionId(1L);
    colorField.setLabel("Favorite Color");
    Map<String, String> options = new LinkedHashMap<>();
    options.put("red", "Red");
    options.put("blue", "Blue");
    colorField.setListOfOptions(options);

    addQueryParameter(widgetContext, "fieldId", "10");

    try (MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      formFieldRepository.when(() -> FormFieldRepository.findById(10L)).thenReturn(colorField);

      WidgetContext result = new FormFieldFormWidget().execute(widgetContext);

      Assertions.assertEquals(FormFieldFormWidget.JSP, result.getJsp());
      Assertions.assertEquals(colorField, request.getAttribute("field"));
      Assertions.assertEquals("red=Red,blue=Blue", request.getAttribute("optionsText"));
    }
  }

  @Test
  void executeWithNoFieldIdFallsBackToABlankFieldForTheGivenForm() {
    addQueryParameter(widgetContext, "formDefinitionId", "1");

    try (MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      formFieldRepository.when(() -> FormFieldRepository.findById(-1L)).thenReturn(null);

      WidgetContext result = new FormFieldFormWidget().execute(widgetContext);

      FormField field = (FormField) request.getAttribute("field");
      Assertions.assertEquals(-1L, field.getId());
      Assertions.assertEquals(1L, field.getFormDefinitionId());
      Assertions.assertNull(request.getAttribute("optionsText"));
    }
  }

  @Test
  void postSavesANewFieldParsingItsOptionsAndRedirectsToTheFormEditor()
      throws InvocationTargetException, IllegalAccessException {
    FormDefinition contactUs = new FormDefinition();
    contactUs.setId(1L);

    addQueryParameter(widgetContext, "id", "-1");
    addQueryParameter(widgetContext, "formDefinitionId", "1");
    addQueryParameter(widgetContext, "label", "Favorite Color");
    addQueryParameter(widgetContext, "type", "select");
    addQueryParameter(widgetContext, "options", "red=Red, blue=Blue");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(1L)).thenReturn(contactUs);
      formFieldRepository.when(() -> FormFieldRepository.save(any())).thenAnswer(invocation -> {
        FormField savedRecord = invocation.getArgument(0);
        savedRecord.setId(20L);
        return savedRecord;
      });

      WidgetContext result = new FormFieldFormWidget().post(widgetContext);

      Assertions.assertEquals("Field was saved", result.getSuccessMessage());
      Assertions.assertEquals("/admin/forms-editor?formDefinitionId=1", result.getRedirect());
      formFieldRepository.verify(() -> FormFieldRepository.save(org.mockito.ArgumentMatchers.argThat(field -> "Red".equals(field.getListOfOptions().get("red"))
          && "Blue".equals(field.getListOfOptions().get("blue")))));
    }
  }

  @Test
  void postWithABlankLabelFailsValidationAndDoesNotSave() throws InvocationTargetException, IllegalAccessException {
    FormDefinition contactUs = new FormDefinition();
    contactUs.setId(1L);

    addQueryParameter(widgetContext, "id", "-1");
    addQueryParameter(widgetContext, "formDefinitionId", "1");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(1L)).thenReturn(contactUs);

      WidgetContext result = new FormFieldFormWidget().post(widgetContext);

      Assertions.assertNotNull(result.getErrorMessage());
      Assertions.assertEquals("/admin/forms-editor?formDefinitionId=1", result.getRedirect());
      formFieldRepository.verify(() -> FormFieldRepository.save(any()), never());
    }
  }

  @Test
  void postWithAnUnrecognizedFieldTypeFailsValidationAndDoesNotSave() throws InvocationTargetException, IllegalAccessException {
    // form-field-form.jsp's "Type" select only ever submits text/email/textarea/select/checkbox/date
    // -- this simulates a tampered or stale request submitting something else, which
    // NEW_10010__new_cms.sql's field_type column comment promises is "validated in application code".
    FormDefinition contactUs = new FormDefinition();
    contactUs.setId(1L);

    addQueryParameter(widgetContext, "id", "-1");
    addQueryParameter(widgetContext, "formDefinitionId", "1");
    addQueryParameter(widgetContext, "label", "Favorite Color");
    addQueryParameter(widgetContext, "type", "not-a-real-type");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(1L)).thenReturn(contactUs);

      WidgetContext result = new FormFieldFormWidget().post(widgetContext);

      Assertions.assertNotNull(result.getErrorMessage());
      Assertions.assertEquals("/admin/forms-editor?formDefinitionId=1", result.getRedirect());
      formFieldRepository.verify(() -> FormFieldRepository.save(any()), never());
    }
  }

  @Test
  void postSavesANewFieldWithTheRepositorysNextFieldOrder() throws InvocationTargetException, IllegalAccessException {
    // Reproduces issue #409's field_order duplicate-value gap at the widget level: a brand new
    // field must get FormFieldRepository's own next-order value explicitly set on it, rather than
    // being left for the field_order column's own DEFAULT 100 to (collision-prone-ly) apply.
    FormDefinition contactUs = new FormDefinition();
    contactUs.setId(1L);

    addQueryParameter(widgetContext, "id", "-1");
    addQueryParameter(widgetContext, "formDefinitionId", "1");
    addQueryParameter(widgetContext, "label", "Favorite Color");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(1L)).thenReturn(contactUs);
      formFieldRepository.when(() -> FormFieldRepository.getNextFieldOrder(1L)).thenReturn(21);
      formFieldRepository.when(() -> FormFieldRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      new FormFieldFormWidget().post(widgetContext);

      formFieldRepository.verify(() -> FormFieldRepository.save(
          org.mockito.ArgumentMatchers.argThat(field -> field.getFieldOrder() == 21)));
    }
  }
}
