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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.application.cms.ContentAccessibilityCommand.Finding;

/**
 * Tests the authoring-time accessibility checks, including the cases that must NOT be reported --
 * a noisy authoring check gets ignored, and an ignored check helps nobody.
 *
 * @author elizabeth houser
 */
class ContentAccessibilityCommandTest {

  private static List<String> rules(String html) {
    return ContentAccessibilityCommand.check(html).stream().map(Finding::getRule).toList();
  }

  @Test
  void cleanContentHasNoFindings() {
    String html = "<h2>About us</h2><p>Some text with a "
        + "<a href=\"/capabilities\">description of our capabilities</a>.</p>"
        + "<img src=\"/team.jpg\" alt=\"The engineering team at work\">";
    assertTrue(ContentAccessibilityCommand.isClean(html), rules(html).toString());
  }

  @Test
  void emptyOrBlankContentIsClean() {
    assertTrue(ContentAccessibilityCommand.isClean(null));
    assertTrue(ContentAccessibilityCommand.isClean(""));
    assertTrue(ContentAccessibilityCommand.isClean("   "));
  }

  @Test
  void imageWithoutAltIsReported() {
    List<Finding> findings = ContentAccessibilityCommand.check("<img src=\"/chart.png\">");
    assertEquals(1, findings.size());
    assertEquals(ContentAccessibilityCommand.RULE_IMAGE_MISSING_ALT, findings.get(0).getRule());
    assertEquals("WCAG 1.1.1", findings.get(0).getCriterion());
    // The context names the offending element so the author can find it.
    assertTrue(findings.get(0).getContext().contains("chart.png"), findings.get(0).getContext());
  }

  @Test
  void decorativeImageWithExplicitEmptyAltIsAccepted() {
    // alt="" is the CORRECT markup for a decorative image. Reporting it would push authors into
    // writing noise for spacer images, which actively harms screen-reader users.
    assertTrue(ContentAccessibilityCommand.isClean("<img src=\"/spacer.gif\" alt=\"\">"));
  }

  @Test
  void skippedHeadingLevelIsReported() {
    List<Finding> findings = ContentAccessibilityCommand.check("<h2>Section</h2><h4>Detail</h4>");
    assertEquals(1, findings.size());
    assertEquals(ContentAccessibilityCommand.RULE_HEADING_SKIPPED, findings.get(0).getRule());
    assertTrue(findings.get(0).getMessage().contains("h2 to h4"), findings.get(0).getMessage());
  }

  @Test
  void sequentialAndReturningHeadingLevelsAreAccepted() {
    // h2 -> h3 -> h2 is a normal document shape: going back UP a level is not a skip.
    assertTrue(ContentAccessibilityCommand.isClean(
        "<h2>One</h2><h3>One point one</h3><h2>Two</h2><h3>Two point one</h3>"));
    // Content that starts at h3 is fine -- the fragment may sit under a page heading.
    assertTrue(ContentAccessibilityCommand.isClean("<h3>Starts deeper</h3><h4>Then deeper</h4>"));
  }

  @Test
  void unclearLinkTextIsReported() {
    for (String phrase : List.of("click here", "Click Here", "read more", "here.", "Learn more!")) {
      String html = "<p>For details <a href=\"/x\">" + phrase + "</a></p>";
      assertEquals(List.of(ContentAccessibilityCommand.RULE_LINK_TEXT_UNCLEAR), rules(html),
          "expected '" + phrase + "' to be reported");
    }
  }

  @Test
  void descriptiveLinkTextIsAccepted() {
    assertTrue(ContentAccessibilityCommand.isClean(
        "<a href=\"/quality\">our AS9100 quality certifications</a>"));
    // A phrase that merely CONTAINS an unclear word is fine -- only the whole text matters.
    assertTrue(ContentAccessibilityCommand.isClean("<a href=\"/x\">here is our capability statement</a>"));
  }

  @Test
  void linkWithNoTextIsReported() {
    List<Finding> findings = ContentAccessibilityCommand.check("<a href=\"/empty\"></a>");
    assertEquals(1, findings.size());
    assertEquals(ContentAccessibilityCommand.RULE_LINK_NO_TEXT, findings.get(0).getRule());
  }

  @Test
  void imageOnlyLinkIsNotDoubleReported() {
    // An image-only link is named by its alt text, so it is the img rule's business, not the link's.
    assertEquals(List.of(),
        rules("<a href=\"/home\"><img src=\"/logo.png\" alt=\"SimIS home\"></a>"));
    // Without alt, exactly one finding -- the missing alt -- not also "link without text".
    assertEquals(List.of(ContentAccessibilityCommand.RULE_IMAGE_MISSING_ALT),
        rules("<a href=\"/home\"><img src=\"/logo.png\"></a>"));
  }

  @Test
  void multipleFindingsAreAllReported() {
    String html = "<h2>Title</h2><h5>Skipped</h5>"
        + "<img src=\"/a.png\">"
        + "<a href=\"/b\">click here</a>";
    List<String> found = rules(html);
    assertEquals(3, found.size(), found.toString());
    assertTrue(found.contains(ContentAccessibilityCommand.RULE_HEADING_SKIPPED));
    assertTrue(found.contains(ContentAccessibilityCommand.RULE_IMAGE_MISSING_ALT));
    assertTrue(found.contains(ContentAccessibilityCommand.RULE_LINK_TEXT_UNCLEAR));
    assertFalse(ContentAccessibilityCommand.isClean(html));
  }
}
