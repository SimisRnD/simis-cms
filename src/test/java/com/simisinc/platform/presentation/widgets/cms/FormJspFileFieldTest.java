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

package com.simisinc.platform.presentation.widgets.cms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The public form sends multipart only when it has a file field, and renders a file field as a file
 * input that names the parameters FormWidget reads (issue #2130).
 */
class FormJspFileFieldTest {

  private static final Path FORM_JSP = Path.of("src/main/webapp/WEB-INF/jsp/cms/form.jsp");

  private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);

  private static String form() throws IOException {
    assertTrue(Files.isRegularFile(FORM_JSP), "run from the project root: " + FORM_JSP.toAbsolutePath());
    return JSP_COMMENT.matcher(Files.readString(FORM_JSP, StandardCharsets.UTF_8)).replaceAll("");
  }

  @Test
  void theFormIsMultipartOnlyWhenItHasAFileField() throws IOException {
    // Every other form must be sent exactly as before
    assertTrue(form().contains("method=\"post\"<c:if test=\"${hasFileField}\"> enctype=\"multipart/form-data\"</c:if>>"));
  }

  @Test
  void aFileFieldIsAFileInputNamedTheWayFormWidgetReadsIt() throws IOException {
    String jsp = form();
    int branch = jsp.indexOf("<c:when test=\"${formField.type eq 'file'}\">");
    assertTrue(branch > -1, "form.jsp renders file fields");
    String input = jsp.substring(branch, jsp.indexOf("</c:when>", branch));
    assertTrue(input.contains("<input type=\"file\""));
    // FormWidget#post reads request.getPart(widget unique id + field name)
    assertTrue(input.contains("name=\"${widgetContext.uniqueId}<c:out value=\"${formField.name}\"/>\""));
    assertTrue(input.contains("accept=\"<c:out value=\"${fileInput.accept}\"/>\""));
    assertTrue(input.contains("data-max-bytes=\"<c:out value=\"${fileInput.maxBytes}\"/>\""));
  }

  @Test
  void theBrowserSizeCheckReadsTheLimitTheInputCarries() throws IOException {
    String jsp = form();
    assertTrue(jsp.contains("fileField.files[0].size > Number(fileField.getAttribute(\"data-max-bytes\"))"));
  }
}
