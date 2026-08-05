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

import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;
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
class FormsListWidgetTest extends WidgetBase {

  @Test
  void executeListsFormsWithFieldCounts() {
    FormDefinition contactUs = new FormDefinition();
    contactUs.setId(1L);
    contactUs.setName("Contact Us");
    List<FormDefinition> formDefinitionList = new ArrayList<>();
    formDefinitionList.add(contactUs);

    List<FormField> fieldList = new ArrayList<>();
    fieldList.add(new FormField());
    fieldList.add(new FormField());

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      formDefinitionRepository.when(FormDefinitionRepository::findAll).thenReturn(formDefinitionList);
      formFieldRepository.when(() -> FormFieldRepository.findAllByFormDefinitionId(1L)).thenReturn(fieldList);

      WidgetContext result = new FormsListWidget().execute(widgetContext);

      Assertions.assertEquals(FormsListWidget.JSP, result.getJsp());
      List<FormDefinition> requestList = (List) request.getAttribute("formDefinitionList");
      Assertions.assertEquals(1, requestList.size());
      Map<Long, Integer> fieldCountMap = (Map) request.getAttribute("fieldCountMap");
      Assertions.assertEquals(2, fieldCountMap.get(1L));
    }
  }

  @Test
  void deleteRemovesTheFormAndItsFields() {
    FormDefinition contactUs = new FormDefinition();
    contactUs.setId(1L);
    contactUs.setName("Contact Us");

    addQueryParameter(widgetContext, "formDefinitionId", "1");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(1L)).thenReturn(contactUs);
      formDefinitionRepository.when(() -> FormDefinitionRepository.remove(contactUs)).thenReturn(true);

      WidgetContext result = new FormsListWidget().delete(widgetContext);

      Assertions.assertEquals("Form deleted", result.getSuccessMessage());
      Assertions.assertEquals("/admin/forms", result.getRedirect());
      formDefinitionRepository.verify(() -> FormDefinitionRepository.remove(contactUs));
    }
  }

  @Test
  void deleteOfAMissingFormSetsAnErrorInsteadOfThrowing() {
    addQueryParameter(widgetContext, "formDefinitionId", "99");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(99L)).thenReturn(null);

      WidgetContext result = new FormsListWidget().delete(widgetContext);

      Assertions.assertEquals("Form not found", result.getErrorMessage());
      Assertions.assertEquals("/admin/forms", result.getRedirect());
    }
  }
}
