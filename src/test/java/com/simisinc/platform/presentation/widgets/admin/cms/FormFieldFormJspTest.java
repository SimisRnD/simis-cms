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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The field editor's file upload settings (issue #2130) post the parameters FormFieldFormWidget reads,
 * and the "File upload" type is offered only while the feature flag is on or the field already is one.
 *
 * @author SimIS Inc.
 */
class FormFieldFormJspTest {

  private static final Path EDITOR_JSP = Path.of("src/main/webapp/WEB-INF/jsp/admin/form-field-form.jsp");

  private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);

  private static final String OFFERED_WHEN = "<c:if test=\"${fileUploadsEnabled || field.type eq 'file'}\">";

  private static String editor() throws IOException {
    assertTrue(Files.isRegularFile(EDITOR_JSP), "run from the project root: " + EDITOR_JSP.toAbsolutePath());
    return JSP_COMMENT.matcher(Files.readString(EDITOR_JSP, StandardCharsets.UTF_8)).replaceAll("");
  }

  @Test
  void theFileTypeIsOfferedOnlyWhileTheFlagIsOnOrTheFieldIsAlreadyAFileField() throws IOException {
    String jsp = editor();
    int option = jsp.indexOf("<option value=\"file\"");
    assertTrue(option > -1, "the Type list has a File upload option");
    int guard = jsp.lastIndexOf(OFFERED_WHEN, option);
    assertTrue(guard > -1 && jsp.indexOf("</c:if>", guard) > option,
        "the File upload option sits inside " + OFFERED_WHEN);
  }

  @Test
  void theSettingsPostTheParameterNamesTheWidgetReads() throws IOException {
    String jsp = editor();
    // FormFieldFormWidget.post() reads the "acceptedKinds" values and the "maxMegabytes" box
    assertTrue(jsp.contains("type=\"checkbox\" name=\"acceptedKinds\""), "one acceptedKinds checkbox per kind");
    assertTrue(jsp.contains("name=\"maxMegabytes\""), "the maximum size is entered in megabytes");
  }

  @Test
  void theScriptThatHidesTheSettingsCarriesTheCspNonce() throws IOException {
    String jsp = editor();
    int script = jsp.indexOf("getElementById('fileFieldSettings')");
    assertTrue(script > -1, "a script shows the settings only for the File upload type");
    assertTrue(jsp.lastIndexOf("<script nonce=\"${cspNonce}\">", script) > -1,
        "an inline script without the nonce is blocked by the Content-Security-Policy");
  }
}
