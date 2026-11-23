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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.domain.model.cms.FormUpload;
import com.simisinc.platform.infrastructure.persistence.cms.FormUploadRepository;

/**
 * Verifies {@link FormFileFieldCommand}, the rules for a form builder "file" field (issue #2130).
 */
class FormFileFieldCommandTest {

  private static final long MB = 1_048_576L;

  private static FormField fieldOfType(String type) {
    FormField field = new FormField();
    field.setType(type);
    return field;
  }

  @Test
  void onlyAFieldOfTypeFileIsAFileField() {
    assertTrue(FormFileFieldCommand.isFileField(fieldOfType("file")));
    assertFalse(FormFileFieldCommand.isFileField(fieldOfType("text")));
    assertFalse(FormFileFieldCommand.isFileField(fieldOfType(null)));
    assertFalse(FormFileFieldCommand.isFileField(null));
  }

  @Test
  void theFlagIsReadFromFeaturesFormFileUploads() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      assertFalse(FormFileFieldCommand.isEnabled(), "a flag that was never set is off");

      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("features.form-file-uploads")).thenReturn(true);
      assertTrue(FormFileFieldCommand.isEnabled());
    }
  }

  @Test
  void imagesAreTheOnlyKindOfferedForNow() {
    assertEquals(1, FormFileFieldCommand.availableKinds().size());
    assertTrue(FormFileFieldCommand.availableKinds().containsKey(FormFileFieldCommand.KIND_IMAGE));
  }

  @Test
  void theImageKindAcceptsJpgPngAndWebpByNameAndByContentAndNothingElse() {
    FormFileFieldCommand.FileKind image = FormFileFieldCommand.kind(" Image ");

    assertEquals(java.util.Set.of("jpg", "jpeg", "png", "webp"), image.extensions());
    assertEquals(java.util.Set.of("image/jpeg", "image/png", "image/webp"), image.contentTypes());
    assertEquals(FormFileFieldCommand.availableKinds().get("image"), image.label(), "the editor and the check share one label");
    org.junit.jupiter.api.Assertions.assertNull(FormFileFieldCommand.kind("pdf"));
    org.junit.jupiter.api.Assertions.assertNull(FormFileFieldCommand.kind(null));
  }

  @Test
  void kindsAreTrimmedLowercasedAndDeduplicated() {
    assertEquals("image", FormFileFieldCommand.normalizeKinds("image"));
    assertEquals("image", FormFileFieldCommand.normalizeKinds(" Image , image,,"));
  }

  @Test
  void choosingNoKindIsRejected() {
    IllegalArgumentException blank = assertThrows(IllegalArgumentException.class, () -> FormFileFieldCommand.normalizeKinds(null));
    assertEquals("Choose at least one kind of file to accept", blank.getMessage());
    assertThrows(IllegalArgumentException.class, () -> FormFileFieldCommand.normalizeKinds(" , "));
  }

  @Test
  void aKindThisSiteDoesNotOfferIsRejectedByName() {
    // PDF is in the #2130 design but has no sanitize step yet; anything else (an executable, SVG,
    // HTML) is never on the list
    IllegalArgumentException pdf = assertThrows(IllegalArgumentException.class, () -> FormFileFieldCommand.normalizeKinds("image,pdf"));
    assertEquals("\"pdf\" is not a kind of file this site accepts", pdf.getMessage());
    assertThrows(IllegalArgumentException.class, () -> FormFileFieldCommand.normalizeKinds("svg"));
  }

  @Test
  void theDefaultLimitIsTenMegabytesUnderALargerSiteLimit() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("system.upload.maxBytes")).thenReturn(String.valueOf(50 * MB));

      assertEquals(10 * MB, FormFileFieldCommand.defaultMaxBytes());
      assertEquals(50 * MB, FormFileFieldCommand.maxBytesCeiling());
    }
  }

  @Test
  void theDefaultLimitDropsToASiteLimitBelowTenMegabytes() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("system.upload.maxBytes")).thenReturn(String.valueOf(5 * MB));

      assertEquals(5 * MB, FormFileFieldCommand.defaultMaxBytes());
    }
  }

  @Test
  void anUnsetLimitTakesTheDefaultAndASetOneMustFitBetweenOneMegabyteAndTheSiteLimit() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("system.upload.maxBytes")).thenReturn(String.valueOf(50 * MB));

      assertEquals(10 * MB, FormFileFieldCommand.normalizeMaxBytes(-1));
      assertEquals(MB, FormFileFieldCommand.normalizeMaxBytes(MB));
      assertEquals(50 * MB, FormFileFieldCommand.normalizeMaxBytes(50 * MB));

      IllegalArgumentException overCap = assertThrows(IllegalArgumentException.class,
          () -> FormFileFieldCommand.normalizeMaxBytes(50 * MB + 1));
      assertEquals("Maximum size must be between 1 MB and 50 MB, the site's upload limit", overCap.getMessage());
      assertThrows(IllegalArgumentException.class, () -> FormFileFieldCommand.normalizeMaxBytes(0));
      assertThrows(IllegalArgumentException.class, () -> FormFileFieldCommand.normalizeMaxBytes(MB - 1));
    }
  }

  @Test
  void theAcceptAttributeListsTheKindsTypesAndExtensionsInAStableOrder() {
    FormField field = fieldOfType("file");
    field.setAcceptedKinds("image");

    assertEquals("image/jpeg,image/png,image/webp,.jpeg,.jpg,.png,.webp", FormFileFieldCommand.acceptAttribute(field));
    field.setAcceptedKinds(null);
    assertEquals("", FormFileFieldCommand.acceptAttribute(field));
  }

  @Test
  void theLimitsAreDescribedForTheVisitor() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("system.upload.maxBytes")).thenReturn(String.valueOf(50 * MB));
      FormField field = fieldOfType("file");
      field.setAcceptedKinds("image");
      field.setMaxBytes(5 * MB);

      assertEquals("Images (JPG, PNG, WebP), up to 5 MB.", FormFileFieldCommand.describeLimits(field));
      field.setAcceptedKinds(null);
      assertEquals("Up to 5 MB.", FormFileFieldCommand.describeLimits(field));
    }
  }

  @Test
  void megabytesConvertBothWaysAndRoundDownForDisplay() {
    assertEquals(10 * MB, FormFileFieldCommand.fromMegabytes(10));
    assertEquals(10, FormFileFieldCommand.toMegabytes(10 * MB));
    assertEquals(9, FormFileFieldCommand.toMegabytes(10 * MB - 1));
  }

  private static FormData submission(long id) {
    FormData formData = new FormData();
    formData.setId(id);
    return formData;
  }

  private static FormField fileField(String value) {
    FormField field = new FormField();
    field.setName("screenshot");
    field.setType(FormFileFieldCommand.TYPE);
    field.setUserValue(value);
    return field;
  }

  private static FormUpload upload(long id, long formDataId, String originalFilename) {
    FormUpload upload = new FormUpload();
    upload.setId(id);
    upload.setFormDataId(formDataId);
    upload.setOriginalFilename(originalFilename);
    return upload;
  }

  @Test
  void aFileFieldsValueFindsTheUploadAttachedToThatSubmission() {
    FormUpload attached = upload(7, 42, "shot.png");
    try (MockedStatic<FormUploadRepository> repository = mockStatic(FormUploadRepository.class)) {
      repository.when(() -> FormUploadRepository.findById(7L)).thenReturn(attached);

      assertSame(attached, FormFileFieldCommand.attachedUpload(submission(42), fileField(" 7 ")));
      assertNull(FormFileFieldCommand.attachedUpload(submission(43), fileField("7")),
          "another submission's file is never reached through a value");
      assertNull(FormFileFieldCommand.attachedUpload(submission(42), fileField("8")), "gone");
    }
  }

  @Test
  void onlyAFileFieldWithANumericValueOnASavedSubmissionIsLookedUp() {
    FormField text = fileField("7");
    text.setType("text");
    try (MockedStatic<FormUploadRepository> repository = mockStatic(FormUploadRepository.class)) {
      assertNull(FormFileFieldCommand.attachedUpload(submission(42), text));
      assertNull(FormFileFieldCommand.attachedUpload(submission(42), fileField(null)));
      assertNull(FormFileFieldCommand.attachedUpload(submission(42), fileField("7; DROP TABLE")));
      assertNull(FormFileFieldCommand.attachedUpload(submission(42), fileField("99999999999999999999")));
      assertNull(FormFileFieldCommand.attachedUpload(new FormData(), fileField("7")), "not saved yet");
      assertNull(FormFileFieldCommand.attachedUpload(null, fileField("7")));

      repository.verify(() -> FormUploadRepository.findById(anyLong()), never());
    }
  }

  @Test
  void anAttachedFileIsNamedAsTheVisitorNamedIt() {
    assertEquals("shot.png", FormFileFieldCommand.attachedFilename(upload(7, 42, "shot.png")));
    assertEquals("attachment", FormFileFieldCommand.attachedFilename(upload(7, 42, " ")));
    assertNull(FormFileFieldCommand.attachedFilename(null));
  }
}
