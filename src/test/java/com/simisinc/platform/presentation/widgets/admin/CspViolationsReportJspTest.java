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

package com.simisinc.platform.presentation.widgets.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.domain.model.cms.CspViolation;

/**
 * The CSP violations report shows a row's page as where it was first seen, not as an example of where
 * it happens.
 *
 * <p>Each row counts reports for one directive and host from every page, but
 * CspViolationRepository.save() keeps the page from the row's first report only. Under the heading
 * "Example page", a row of 161 blocked inline scripts listing one page read as that page being
 * behind all 161; the page loaded cleanly, and the count came from elsewhere on the site.
 *
 * @author SimIS Inc.
 */
class CspViolationsReportJspTest {

  private static final Path REPORT_JSP = Path.of("src/main/webapp/WEB-INF/jsp/admin/csp-violations.jsp");

  private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
  private static final Pattern HEADER_CELL = Pattern.compile("<th\\b[^>]*>(.*?)</th>", Pattern.DOTALL);
  private static final Pattern ROW_CELL = Pattern.compile("<td\\b[^>]*>(.*?)</td>", Pattern.DOTALL);
  private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^}]*)}");
  private static final Pattern VIOLATION_PROPERTY = Pattern.compile("\\bviolation\\.([A-Za-z_]\\w*)");

  private static String report() throws IOException {
    assertTrue(Files.isRegularFile(REPORT_JSP), "run from the project root: " + REPORT_JSP.toAbsolutePath());
    // Comments are stripped so one that quotes the old heading or markup cannot satisfy or fail a check
    return JSP_COMMENT.matcher(Files.readString(REPORT_JSP, StandardCharsets.UTF_8)).replaceAll("");
  }

  private static String between(String text, String start, String end) {
    int from = text.indexOf(start);
    assertTrue(from >= 0, "no " + start + " in the report");
    int to = text.indexOf(end, from);
    assertTrue(to > from, "no " + end + " after " + start + " in the report");
    return text.substring(from, to);
  }

  private static List<String> cells(Pattern cell, String markup) {
    List<String> found = new ArrayList<>();
    Matcher matcher = cell.matcher(markup);
    while (matcher.find()) {
      found.add(matcher.group(1).trim());
    }
    return found;
  }

  @Test
  void thePageIsShownAsWhereTheRowWasFirstSeen() throws IOException {
    String report = report();
    List<String> headings = cells(HEADER_CELL, between(report, "<thead>", "</thead>"));
    List<String> row = cells(ROW_CELL, between(report, "<tbody>", "</tbody>"));

    assertEquals(List.of("Directive", "Blocked", "Add to policy as", "Times", "First seen", "Last seen"), headings);
    assertEquals(headings.size(), row.size(), "each heading needs one cell in the row: " + row);

    String firstSeen = row.get(headings.indexOf("First seen"));
    assertTrue(firstSeen.contains("${violation.firstSeen}"), firstSeen);
    assertTrue(firstSeen.contains("violation.sampleDocumentPath"), firstSeen);

    String lastSeen = row.get(headings.indexOf("Last seen"));
    assertTrue(lastSeen.contains("${violation.lastSeen}"), lastSeen);
    assertFalse(lastSeen.contains("sampleDocumentPath"), "the page is from the first report, not the last: " + lastSeen);
  }

  /**
   * CspViolation is a JavaBean, and EL reads a JavaBean through its getters. A property with no getter
   * throws PropertyNotFoundException at render time, which PageServlet turns into an empty page.
   */
  @Test
  void everyPropertyTheReportReadsHasAGetter() throws Exception {
    Set<String> read = new LinkedHashSet<>();
    Matcher expression = EXPRESSION.matcher(report());
    while (expression.find()) {
      Matcher property = VIOLATION_PROPERTY.matcher(expression.group(1));
      while (property.find()) {
        read.add(property.group(1));
      }
    }
    assertTrue(read.contains("firstSeen") && read.contains("sampleDocumentPath"), "found " + read);

    Set<String> readable = new LinkedHashSet<>();
    for (PropertyDescriptor descriptor : Introspector.getBeanInfo(CspViolation.class).getPropertyDescriptors()) {
      if (descriptor.getReadMethod() != null) {
        readable.add(descriptor.getName());
      }
    }
    Set<String> missing = new LinkedHashSet<>(read);
    missing.removeAll(readable);
    assertEquals(Set.of(), missing, "the report reads properties CspViolation has no getter for");
  }
}
