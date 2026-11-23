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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;

import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.domain.model.cms.FormUpload;

/**
 * The submission notification email names each attached file and its size and sends the reader to
 * the review screen, never to the file (issue #2130).
 */
class FormUploadNotificationEmailTest {

  private static final Path TEMPLATES = Path.of("src/main/webapp/WEB-INF/email-templates");

  private static FormField field(String label, String name, String type, String value) {
    FormField field = new FormField();
    field.setLabel(label);
    field.setName(name);
    field.setType(type);
    field.setUserValue(value);
    return field;
  }

  private static String render(List<FormField> fields, Map<String, FormUploadSummary> formUploads) {
    FileTemplateResolver resolver = new FileTemplateResolver();
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setPrefix(TEMPLATES.toAbsolutePath() + "/");
    resolver.setSuffix(".html");
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);

    FormData formData = new FormData();
    formData.setId(42L);
    formData.setFormUniqueId("contact-us");
    formData.setIpAddress("203.0.113.5");
    formData.setFormFieldList(new ArrayList<>(fields));
    Context ctx = new Context();
    ctx.setVariable("site", Map.of("name", "Test Site", "url", "https://example.org"));
    ctx.setVariable("location", "Somewhere");
    ctx.setVariable("formData", formData);
    ctx.setVariable("formUploads", formUploads);
    return engine.process("cms/admin-form-submitted-notification", ctx);
  }

  private static FormUploadSummary summary() {
    FormUpload upload = new FormUpload();
    upload.setId(7L);
    upload.setFormDataId(42L);
    upload.setOriginalFilename("my <shot>.png");
    upload.setContentType("image/png");
    upload.setFileLength(1_258_291L);
    upload.setScanStatus(FormUpload.SCAN_CLEAN);
    return new FormUploadSummary(upload);
  }

  @Test
  void anAttachedFileIsNamedWithItsSizeAndLinksToTheReviewScreen() {
    String html = render(List.of(field("Name", "name", "text", "Jane"), field("Screenshot", "screenshot", "file", "7")),
        Map.of("7", summary()));

    assertTrue(html.contains("my &lt;shot&gt;.png (PNG image, 1.2 MB)"), "the visitor's name for it, escaped: " + html);
    assertTrue(html.contains("href=\"https://example.org/admin/form-data?formDataId=42\">review it in Form Data</a>"), html);
    assertTrue(html.contains(">Jane<"), "other answers are unchanged");
    assertFalse(html.contains("/assets/form-upload"), "the email never links the file itself");
    assertFalse(html.contains(">7<"), "nor shows the upload's id as if it were an answer");
  }

  @Test
  void aFileThatCanNoLongerBeFoundStillSaysOneWasAttached() {
    String html = render(List.of(field("Screenshot", "screenshot", "file", "7")), Map.of());

    assertTrue(html.contains("A file was attached"), html);
    assertTrue(html.contains("review it in Form Data"), html);
  }

  @Test
  void anOptionalFileFieldLeftEmptySaysSo() {
    String html = render(List.of(field("Screenshot", "screenshot", "file", null)), null);

    assertTrue(html.contains("No file attached"), html);
    assertFalse(html.contains("review it in Form Data"), html);
  }

  @Test
  void theWorkflowHandsTheUploadsToTheTemplate() throws IOException {
    String workflows = Files.readString(Path.of("src/main/webapp/WEB-INF/workflows/cms-workflows.yml"), StandardCharsets.UTF_8);
    assertTrue(workflows.contains("formUploads: '{{ event.formUploads }}'"));
  }
}
