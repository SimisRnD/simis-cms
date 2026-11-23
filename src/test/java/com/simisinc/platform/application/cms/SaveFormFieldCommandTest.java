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

package com.simisinc.platform.application.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.cms.FormDefinition;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.infrastructure.persistence.cms.FormDefinitionRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormFieldRepository;

/**
 * Verifies {@link SaveFormFieldCommand}, including the sibling-name dedup guard added to stop two
 * fields on the same form from silently sharing an html/database Name -- on the live form, a
 * repeated field name only ever yields its first submitted value, so a collision was previously
 * silent data loss, not just a display quirk.
 */
class SaveFormFieldCommandTest {

  private static FormField field(long id, long formDefinitionId, String label, String name) {
    FormField field = new FormField();
    field.setId(id);
    field.setFormDefinitionId(formDefinitionId);
    field.setLabel(label);
    field.setName(name);
    return field;
  }

  private static List<FormField> siblings(FormField... fields) {
    return new ArrayList<>(List.of(fields));
  }

  @Test
  void savingANewFieldWithNoSiblingsKeepsItsRequestedName() throws DataException {
    FormField bean = field(-1L, 5L, "Email Address", "email");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(5L)).thenReturn(new FormDefinition());
      formFieldRepository.when(() -> FormFieldRepository.findAllByFormDefinitionId(5L)).thenReturn(siblings());
      formFieldRepository.when(() -> FormFieldRepository.getNextFieldOrder(5L)).thenReturn(10);
      formFieldRepository.when(() -> FormFieldRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      FormField saved = SaveFormFieldCommand.saveField(bean);

      assertEquals("email", saved.getName());
    }
  }

  @Test
  void aSecondFieldWithAnExplicitNameAlreadyUsedBySiblingGetsANumberedSuffix() throws DataException {
    FormField existingEmailField = field(1L, 5L, "Email", "email");
    FormField bean = field(-1L, 5L, "Confirm Email", "email");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(5L)).thenReturn(new FormDefinition());
      formFieldRepository.when(() -> FormFieldRepository.findAllByFormDefinitionId(5L)).thenReturn(siblings(existingEmailField));
      formFieldRepository.when(() -> FormFieldRepository.getNextFieldOrder(5L)).thenReturn(20);
      formFieldRepository.when(() -> FormFieldRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      FormField saved = SaveFormFieldCommand.saveField(bean);

      assertEquals("email-2", saved.getName());
    }
  }

  @Test
  void twoFieldsWithBlankNamesAndTheSameLabelGetDistinctAutoSlugs() throws DataException {
    FormField existingEmailField = field(1L, 5L, "Email", "email");
    // Second field leaves Name blank and happens to reuse the same Label -- the auto-slug fallback
    // must go through the same dedup check as an explicitly-typed Name, not just the request bean's
    // own value.
    FormField bean = field(-1L, 5L, "Email", "");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(5L)).thenReturn(new FormDefinition());
      formFieldRepository.when(() -> FormFieldRepository.findAllByFormDefinitionId(5L)).thenReturn(siblings(existingEmailField));
      formFieldRepository.when(() -> FormFieldRepository.getNextFieldOrder(5L)).thenReturn(20);
      formFieldRepository.when(() -> FormFieldRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      FormField saved = SaveFormFieldCommand.saveField(bean);

      assertEquals("email-2", saved.getName());
    }
  }

  @Test
  void editingAFieldAndKeepingItsOwnNameIsNotTreatedAsACollisionWithItself() throws DataException {
    FormField existing = field(7L, 5L, "Email", "email");
    FormField editBean = field(7L, 5L, "Email Address", "email");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(5L)).thenReturn(new FormDefinition());
      formFieldRepository.when(() -> FormFieldRepository.findById(7L)).thenReturn(existing);
      // The sibling list returned by the repository includes the field's own current row --
      // the command must exclude it by id, not just by identity, since this is a fresh object.
      formFieldRepository.when(() -> FormFieldRepository.findAllByFormDefinitionId(5L)).thenReturn(siblings(existing));
      formFieldRepository.when(() -> FormFieldRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      FormField saved = SaveFormFieldCommand.saveField(editBean);

      assertEquals("email", saved.getName());
    }
  }

  @Test
  void editingAFieldToCollideWithADifferentSiblingsNameGetsANumberedSuffix() throws DataException {
    FormField existing = field(7L, 5L, "Confirm Email", "confirm-email");
    FormField otherSibling = field(1L, 5L, "Email", "email");
    FormField editBean = field(7L, 5L, "Confirm Email", "email");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(5L)).thenReturn(new FormDefinition());
      formFieldRepository.when(() -> FormFieldRepository.findById(7L)).thenReturn(existing);
      formFieldRepository.when(() -> FormFieldRepository.findAllByFormDefinitionId(5L)).thenReturn(siblings(existing, otherSibling));
      formFieldRepository.when(() -> FormFieldRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      FormField saved = SaveFormFieldCommand.saveField(editBean);

      assertEquals("email-2", saved.getName());
    }
  }

  @Test
  void threeFieldsCollidingOnTheSameNameEachGetADistinctSuffix() throws DataException {
    FormField first = field(1L, 5L, "Phone", "phone");
    FormField second = field(2L, 5L, "Phone", "phone-2");
    FormField bean = field(-1L, 5L, "Phone", "phone");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(5L)).thenReturn(new FormDefinition());
      formFieldRepository.when(() -> FormFieldRepository.findAllByFormDefinitionId(5L)).thenReturn(siblings(first, second));
      formFieldRepository.when(() -> FormFieldRepository.getNextFieldOrder(5L)).thenReturn(30);
      formFieldRepository.when(() -> FormFieldRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      FormField saved = SaveFormFieldCommand.saveField(bean);

      assertEquals("phone-3", saved.getName());
    }
  }

  @Test
  void aBlankLabelIsRejected() {
    FormField bean = field(-1L, 5L, "", "name");
    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(5L)).thenReturn(new FormDefinition());

      assertThrows(DataException.class, () -> SaveFormFieldCommand.saveField(bean));
      formFieldRepository.verify(() -> FormFieldRepository.save(any()), never());
    }
  }

  @Test
  void anInvalidTypeIsRejected() {
    FormField bean = field(-1L, 5L, "Label", "name");
    bean.setType("radio");
    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(5L)).thenReturn(new FormDefinition());

      DataException e = assertThrows(DataException.class, () -> SaveFormFieldCommand.saveField(bean));
      org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("valid field type"));
      formFieldRepository.verify(() -> FormFieldRepository.save(any()), never());
    }
  }

  @Test
  void aMissingFormIsRejected() {
    FormField bean = field(-1L, -1L, "Label", "name");
    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {

      assertThrows(DataException.class, () -> SaveFormFieldCommand.saveField(bean));
      formFieldRepository.verify(() -> FormFieldRepository.save(any()), never());
    }
  }

  // ---------------------------------------------------------------------------------------------
  // File upload fields (issue #2130)
  // ---------------------------------------------------------------------------------------------

  private static final long MB = 1_048_576L;

  private static FormField fileField(long id, String kinds, long maxBytes) {
    FormField bean = field(id, 5L, "Screenshot", "screenshot");
    bean.setType("file");
    bean.setAcceptedKinds(kinds);
    bean.setMaxBytes(maxBytes);
    return bean;
  }

  /** Mocks the repositories and site properties a save reads, with a 50 MB site upload limit */
  private static void stubSave(MockedStatic<FormDefinitionRepository> formDefinitionRepository,
      MockedStatic<FormFieldRepository> formFieldRepository, MockedStatic<LoadSitePropertyCommand> siteProperty,
      boolean flagOn) {
    formDefinitionRepository.when(() -> FormDefinitionRepository.findById(5L)).thenReturn(new FormDefinition());
    formFieldRepository.when(() -> FormFieldRepository.findAllByFormDefinitionId(5L)).thenReturn(siblings());
    formFieldRepository.when(() -> FormFieldRepository.getNextFieldOrder(5L)).thenReturn(10);
    formFieldRepository.when(() -> FormFieldRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("features.form-file-uploads")).thenReturn(flagOn);
    siteProperty.when(() -> LoadSitePropertyCommand.loadByName("system.upload.maxBytes")).thenReturn(String.valueOf(50 * MB));
  }

  @Test
  void aNewFileFieldIsRejectedWhileTheFlagIsOff() {
    FormField bean = fileField(-1L, "image", -1);
    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      stubSave(formDefinitionRepository, formFieldRepository, siteProperty, false);

      DataException e = assertThrows(DataException.class, () -> SaveFormFieldCommand.saveField(bean));
      assertTrue(e.getMessage().contains("File upload fields are turned off for this site"));
      formFieldRepository.verify(() -> FormFieldRepository.save(any()), never());
    }
  }

  @Test
  void aNewFileFieldIsSavedWithItsKindsAndTheDefaultLimitWhileTheFlagIsOn() throws DataException {
    FormField bean = fileField(-1L, "image", -1);
    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      stubSave(formDefinitionRepository, formFieldRepository, siteProperty, true);

      FormField saved = SaveFormFieldCommand.saveField(bean);

      assertEquals("file", saved.getType());
      assertEquals("image", saved.getAcceptedKinds());
      assertEquals(10 * MB, saved.getMaxBytes(), "an unset limit takes the 10 MB default");
    }
  }

  @Test
  void aFileFieldLimitAboveTheSiteUploadLimitIsRejected() {
    FormField bean = fileField(-1L, "image", 50 * MB + 1);
    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      stubSave(formDefinitionRepository, formFieldRepository, siteProperty, true);

      DataException e = assertThrows(DataException.class, () -> SaveFormFieldCommand.saveField(bean));
      assertTrue(e.getMessage().contains("between 1 MB and 50 MB"));
      formFieldRepository.verify(() -> FormFieldRepository.save(any()), never());
    }
  }

  @Test
  void aFileFieldWithNoKindOrAKindTheSiteDoesNotOfferIsRejected() {
    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      stubSave(formDefinitionRepository, formFieldRepository, siteProperty, true);

      DataException none = assertThrows(DataException.class, () -> SaveFormFieldCommand.saveField(fileField(-1L, null, -1)));
      assertTrue(none.getMessage().contains("Choose at least one kind of file to accept"));
      DataException pdf = assertThrows(DataException.class, () -> SaveFormFieldCommand.saveField(fileField(-1L, "pdf", -1)));
      assertTrue(pdf.getMessage().contains("\"pdf\" is not a kind of file this site accepts"));
      formFieldRepository.verify(() -> FormFieldRepository.save(any()), never());
    }
  }

  @Test
  void aFileFieldDropsOptionsAndADefaultValue() throws DataException {
    // Options are checked before Type when form.jsp renders a field, so keeping them would turn a
    // file field into a dropdown
    FormField bean = fileField(-1L, "image", -1);
    Map<String, String> options = new LinkedHashMap<>();
    options.put("a", "A");
    bean.setListOfOptions(options);
    bean.setDefaultValue("something");
    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      stubSave(formDefinitionRepository, formFieldRepository, siteProperty, true);

      FormField saved = SaveFormFieldCommand.saveField(bean);

      assertNull(saved.getListOfOptions());
      assertNull(saved.getDefaultValue());
    }
  }

  @Test
  void anExistingFileFieldCanStillBeEditedWhileTheFlagIsOff() throws DataException {
    FormField existing = fileField(7L, "image", 10 * MB);
    FormField bean = fileField(7L, "image", 5 * MB);
    bean.setLabel("Attach a screenshot");
    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      stubSave(formDefinitionRepository, formFieldRepository, siteProperty, false);
      formFieldRepository.when(() -> FormFieldRepository.findById(7L)).thenReturn(existing);

      FormField saved = SaveFormFieldCommand.saveField(bean);

      assertEquals("Attach a screenshot", saved.getLabel());
      assertEquals(5 * MB, saved.getMaxBytes());
    }
  }

  @Test
  void changingAFileFieldToAnotherTypeClearsItsFileLimits() throws DataException {
    FormField existing = fileField(7L, "image", 10 * MB);
    FormField bean = field(7L, 5L, "Screenshot link", "screenshot");
    bean.setType("text");
    // The editor still submits the file settings box when another type is chosen
    bean.setAcceptedKinds("image");
    bean.setMaxBytes(10 * MB);
    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      stubSave(formDefinitionRepository, formFieldRepository, siteProperty, true);
      formFieldRepository.when(() -> FormFieldRepository.findById(7L)).thenReturn(existing);

      FormField saved = SaveFormFieldCommand.saveField(bean);

      assertEquals("text", saved.getType());
      assertNull(saved.getAcceptedKinds());
      assertEquals(-1, saved.getMaxBytes());
    }
  }
}
