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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The Form Data review screen shows an attached file from this submission's own uploads, through the
 * gated download only (issue #2130).
 */
class FormDataListJspFileFieldTest {

  private static final Path JSP = Path.of("src/main/webapp/WEB-INF/jsp/admin/form-data-list.jsp");

  private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);

  private static String fileBranch() throws IOException {
    assertTrue(Files.isRegularFile(JSP), "run from the project root: " + JSP.toAbsolutePath());
    String jsp = JSP_COMMENT.matcher(Files.readString(JSP, StandardCharsets.UTF_8)).replaceAll("");
    int start = jsp.indexOf("<c:when test=\"${formField.type eq 'file'}\">");
    assertTrue(start > -1, "form-data-list.jsp renders file fields");
    int end = jsp.indexOf("<c:otherwise><c:out value=\"${formField.userValue}\"/></c:otherwise>", start);
    assertTrue(end > start);
    return jsp.substring(start, end);
  }

  @Test
  void theFileIsLookedUpWithinTheSameSubmission() throws IOException {
    assertTrue(fileBranch().contains("${formUploads[formData.id][formField.userValue]}"));
  }

  @Test
  void itLinksOnlyTheSummarysUrlsWhichAreNullUnlessTheScanPassed() throws IOException {
    String branch = fileBranch();
    assertTrue(branch.contains("src=\"${ctx}<c:out value=\"${formUpload.thumbnailUrl}\"/>\""));
    assertTrue(branch.contains("href=\"${ctx}<c:out value=\"${formUpload.downloadUrl}\"/>\""));
    assertTrue(branch.contains("<c:when test=\"${formUpload.clean}\">"));
    assertFalse(branch.contains("/assets/"), "no hand-built file URL: " + branch);
  }

  @Test
  void theVisitorsFilenameIsEscaped() throws IOException {
    String branch = fileBranch();
    assertTrue(branch.contains("<c:out value=\"${formUpload.filename}\"/>"));
    assertFalse(branch.contains("${formField.userValue}\"/>"), "the raw upload id is not shown as an answer");
  }
}
