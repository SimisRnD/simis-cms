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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Structural gate: {@code web-page-list.jsp} must test its maps by key, not by substring (#2039).
 *
 * <p>{@code fn:contains} is JSTL's <b>string</b> containment function -- {@code boolean
 * contains(String, String)}. Handed a {@code Map}, EL coerces the argument with {@code toString()}
 * and matches the link as a substring of the map's printed form. That reads as correct for as long
 * as the map holds only exact, non-overlapping keys, and silently reports a page at {@code /about}
 * as present the moment the map holds {@code /about-us}.
 *
 * <p>It also hid a separate defect: {@code standardPages} was loaded from a layout filename that
 * does not exist, so the map was always empty, and an empty map prints as <code>{}</code> and
 * contains nothing. The three {@code standardPages} call sites therefore read as a clean {@code
 * false} rather than as anything broken. Fixing the loader without fixing these would have turned
 * three always-false branches into three sometimes-wrong ones, so the two belong together.
 */
class WebPageListJspMapLookupTest {

  private static final Path JSP = Paths.get("src/main/webapp/WEB-INF/jsp/admin/web-page-list.jsp");

  /** Source with {@code <%-- --%>} comments removed, so a comment can never satisfy or trip a scan. */
  private static String jspWithoutComments() throws IOException {
    assertTrue(Files.isRegularFile(JSP), "expected to run from the module root; not found: " + JSP.toAbsolutePath());
    String source = new String(Files.readAllBytes(JSP), StandardCharsets.UTF_8);
    return Pattern.compile("<%--.*?--%>", Pattern.DOTALL).matcher(source).replaceAll("");
  }

  @Test
  void neitherMapIsTestedWithTheStringContainmentFunction() throws IOException {
    Matcher matcher = Pattern.compile("fn:contains\\(\\s*(standardPages|webPageMap)\\b")
        .matcher(jspWithoutComments());
    StringBuilder found = new StringBuilder();
    while (matcher.find()) {
      found.append("\n  ").append(matcher.group());
    }
    assertEquals("", found.toString(),
        "fn:contains against a Map is a substring test on its toString(); use containsKey instead:" + found);
  }

  @Test
  void bothMapsAreTestedByKey() throws IOException {
    Matcher matcher = Pattern.compile("(standardPages|webPageMap)\\.containsKey\\(")
        .matcher(jspWithoutComments());
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    /* Anti-vacuous: the assertion above passes trivially if the call sites are simply deleted.
       Nine is what the page carries -- three against standardPages, six against webPageMap. */
    assertTrue(count >= 9, "expected at least 9 key tests against the two maps, found " + count);
  }

  @Test
  void theStringContainmentFunctionIsStillUsedWhereItBelongs() throws IOException {
    /* Control: this gate must not be satisfiable by removing fn:contains from the page wholesale.
       One genuine String use remains -- fn:contains(menuItem.link, "#") -- and should stay. */
    assertTrue(jspWithoutComments().contains("fn:contains(menuItem.link"),
        "expected the genuine String use of fn:contains to remain");
  }
}
