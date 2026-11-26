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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.domain.model.cms.FormUpload;
import com.simisinc.platform.infrastructure.persistence.cms.FormUploadRepository;

/**
 * Verifies what an admin is shown about an attached file, and that a submission only ever shows its
 * own (issue #2130).
 */
class FormUploadSummaryTest {

  private static FormUpload upload(long id, long formDataId, String status) {
    FormUpload upload = new FormUpload();
    upload.setId(id);
    upload.setFormDataId(formDataId);
    upload.setFieldName("screenshot");
    upload.setOriginalFilename("shot.png");
    upload.setFilePath("forms/contact-us/abc.png");
    upload.setContentType("image/png");
    upload.setFileLength(1_992_294L);
    upload.setWidth(640);
    upload.setHeight(320);
    upload.setThumbnailPath("forms/contact-us/abc-thumbnail.png");
    upload.setScanStatus(status);
    return upload;
  }

  private static FormData submission(long id, String type, String value) {
    FormField field = new FormField();
    field.setName("screenshot");
    field.setType(type);
    field.setUserValue(value);
    FormData formData = new FormData();
    formData.setId(id);
    formData.setFormFieldList(new ArrayList<>(List.of(field)));
    return formData;
  }

  @Test
  void aCleanUploadIsDescribedAndLinkedThroughTheGatedDownload() {
    FormUploadSummary summary = new FormUploadSummary(upload(7, 1, FormUpload.SCAN_CLEAN));

    assertEquals("shot.png", summary.getFilename());
    assertEquals("PNG image", summary.getType());
    assertEquals("1.9 MB", summary.getSize());
    assertEquals("640 × 320 px", summary.getDimensions());
    assertTrue(summary.isClean());
    assertEquals("/assets/form-upload?id=7", summary.getDownloadUrl());
    assertEquals("/assets/form-upload?id=7&thumbnail=true", summary.getThumbnailUrl());
  }

  @Test
  void anImageStoredWithoutAScanIsLinkedButNotCalledClean() {
    FormUploadSummary summary = new FormUploadSummary(upload(7, 1, FormUpload.SCAN_NOT_REQUIRED));

    assertFalse(summary.isClean());
    assertTrue(summary.isScanNotRequired());
    assertTrue(summary.isDownloadable());
    assertEquals("/assets/form-upload?id=7", summary.getDownloadUrl());
    assertEquals("/assets/form-upload?id=7&thumbnail=true", summary.getThumbnailUrl());
  }

  @Test
  void anUploadThatDidNotPassItsScanHasNoLinks() {
    for (String status : new String[] { FormUpload.SCAN_PENDING, FormUpload.SCAN_REJECTED, null }) {
      FormUploadSummary summary = new FormUploadSummary(upload(7, 1, status));
      assertFalse(summary.isClean(), String.valueOf(status));
      assertNull(summary.getDownloadUrl(), String.valueOf(status));
      assertNull(summary.getThumbnailUrl(), String.valueOf(status));
    }
  }

  @Test
  void missingDetailsFallBackRatherThanShowingBlanks() {
    FormUpload upload = upload(7, 1, FormUpload.SCAN_CLEAN);
    upload.setOriginalFilename(" ");
    upload.setContentType("application/octet-stream");
    upload.setWidth(-1);
    upload.setThumbnailPath(null);
    FormUploadSummary summary = new FormUploadSummary(upload);

    assertEquals("attachment", summary.getFilename());
    assertEquals("application/octet-stream", summary.getType());
    assertNull(summary.getDimensions());
    assertNull(summary.getThumbnailUrl(), "no thumbnail to show");
    assertEquals("/assets/form-upload?id=7", summary.getDownloadUrl());
  }

  @Test
  void sizesReadNaturally() {
    assertEquals("0 bytes", FormUploadSummary.formatSize(0));
    assertEquals("640 bytes", FormUploadSummary.formatSize(640));
    assertEquals("1 KB", FormUploadSummary.formatSize(1024));
    assertEquals("88 KB", FormUploadSummary.formatSize(90_000));
    assertEquals("1.0 MB", FormUploadSummary.formatSize(1_048_576));
    assertEquals("9.5 MB", FormUploadSummary.formatSize(9_961_472));
  }

  @Test
  void aPageOfSubmissionsGetsEachOnesOwnUploads() {
    List<FormData> page = List.of(submission(1, "file", "7"), submission(2, "file", "8"), submission(3, "text", "hello"));
    try (MockedStatic<FormUploadRepository> repository = mockStatic(FormUploadRepository.class)) {
      repository.when(() -> FormUploadRepository.findAllByFormDataIds(List.of(1L, 2L)))
          .thenReturn(List.of(upload(7, 1, FormUpload.SCAN_CLEAN), upload(8, 2, FormUpload.SCAN_CLEAN)));

      Map<Long, Map<String, FormUploadSummary>> summaries = FormUploadSummary.forSubmissions(page);

      assertEquals(7, summaries.get(1L).get("7").getId());
      assertEquals(8, summaries.get(2L).get("8").getId());
      assertNull(summaries.get(1L).get("8"), "submission 1 cannot show submission 2's file");
      assertNull(summaries.get(3L), "only submissions with a file were looked up");
    }
  }

  @Test
  void aPageWithoutAttachedFilesRunsNoQuery() {
    List<FormData> page = List.of(submission(1, "text", "hello"), submission(2, "file", ""), submission(3, "file", null));
    try (MockedStatic<FormUploadRepository> repository = mockStatic(FormUploadRepository.class)) {
      assertTrue(FormUploadSummary.forSubmissions(page).isEmpty());
      assertTrue(FormUploadSummary.forSubmissions(null).isEmpty());

      repository.verify(() -> FormUploadRepository.findAllByFormDataIds(any()), never());
    }
  }

  @Test
  void oneSubmissionsUploadsAreKeyedByTheFileFieldsValue() {
    try (MockedStatic<FormUploadRepository> repository = mockStatic(FormUploadRepository.class)) {
      repository.when(() -> FormUploadRepository.findAllByFormDataId(1L))
          .thenReturn(List.of(upload(7, 1, FormUpload.SCAN_CLEAN)));

      Map<String, FormUploadSummary> summaries = FormUploadSummary.forSubmission(1L);

      assertEquals(Map.of("7", summaries.get("7")), summaries);
      assertEquals("shot.png", summaries.get("7").getFilename());
    }
  }

}
