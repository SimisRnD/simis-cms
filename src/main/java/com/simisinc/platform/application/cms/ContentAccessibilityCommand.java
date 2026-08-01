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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Authoring-time accessibility checks over a fragment of content HTML (Project #6, Phase 3).
 *
 * <p>Scope is deliberately small. Accessibility here is a <b>nice-to-have pursued where it is cheap
 * and right</b> — it is not the product's differentiation thesis and this never gates a release. What
 * it does buy is the one thing Section 508 provision 504 actually asks of an <i>authoring tool</i>:
 * that it prompt the author toward accessible content while they are writing it. These four checks
 * are the cheap, high-signal ones — the WCAG 2.1/2.2 AA failures that ADA Title III complaints
 * overwhelmingly cite — and each maps to a named success criterion so a finding can be explained
 * rather than merely asserted.
 *
 * <p>This reports; it never rewrites. Auto-generating alt text would produce confident-sounding
 * nonsense for a screen-reader user, which is worse than a visible gap the author can fix.
 *
 * @author elizabeth houser
 */
public class ContentAccessibilityCommand {

  /** An image carries no alternative text (WCAG 1.1.1 Non-text Content). */
  public static final String RULE_IMAGE_MISSING_ALT = "image-missing-alt";
  /** Heading levels skip a rank, e.g. h2 followed by h4 (WCAG 1.3.1 Info and Relationships). */
  public static final String RULE_HEADING_SKIPPED = "heading-order-skipped";
  /** Link text conveys nothing out of context (WCAG 2.4.4 Link Purpose). */
  public static final String RULE_LINK_TEXT_UNCLEAR = "link-text-unclear";
  /** A link has no discernible text at all (WCAG 2.4.4 / 4.1.2 Name, Role, Value). */
  public static final String RULE_LINK_NO_TEXT = "link-without-text";

  /**
   * Link phrases that carry no meaning when a screen-reader user lists the links on a page. Kept
   * short and unambiguous on purpose: a noisy authoring check gets ignored, and an ignored check
   * helps nobody.
   */
  private static final Set<String> UNCLEAR_LINK_PHRASES = Set.of(
      "click here", "here", "read more", "more", "link", "this", "this link", "learn more", "details");

  private ContentAccessibilityCommand() {
    // Static command
  }

  /**
   * @return the accessibility findings in the given content HTML, in document order; never null.
   */
  public static List<Finding> check(String html) {
    List<Finding> findings = new ArrayList<>();
    if (StringUtils.isBlank(html)) {
      return findings;
    }
    Document document = Jsoup.parseBodyFragment(html);

    for (Element image : document.select("img")) {
      // An explicitly empty alt is correct for a decorative image, so only a MISSING attribute is a
      // finding. Punishing alt="" would push authors into writing noise for spacer images.
      if (!image.hasAttr("alt")) {
        findings.add(new Finding(RULE_IMAGE_MISSING_ALT, "WCAG 1.1.1",
            "This image has no alt text. Describe it, or mark it decorative with alt=\"\".",
            describe(image, image.attr("src"))));
      }
    }

    int previousLevel = 0;
    for (Element heading : document.select("h1, h2, h3, h4, h5, h6")) {
      int level = Integer.parseInt(heading.tagName().substring(1));
      if (previousLevel > 0 && level > previousLevel + 1) {
        findings.add(new Finding(RULE_HEADING_SKIPPED, "WCAG 1.3.1",
            "Heading level jumps from h" + previousLevel + " to h" + level
                + ". Screen-reader users navigate by heading order, so do not skip a level.",
            describe(heading, heading.text())));
      }
      previousLevel = level;
    }

    for (Element link : document.select("a")) {
      String text = link.text().trim();
      if (text.isEmpty()) {
        // An image-only link is described by the image's alt text, which the img check covers.
        if (link.select("img").isEmpty()) {
          findings.add(new Finding(RULE_LINK_NO_TEXT, "WCAG 2.4.4",
              "This link has no text, so it cannot be announced.", describe(link, link.attr("href"))));
        }
        continue;
      }
      String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]+$", "").trim();
      if (UNCLEAR_LINK_PHRASES.contains(normalized)) {
        findings.add(new Finding(RULE_LINK_TEXT_UNCLEAR, "WCAG 2.4.4",
            "Link text \"" + text + "\" does not say where it goes. Screen-reader users often browse a "
                + "list of links with no surrounding sentence.",
            describe(link, text)));
      }
    }

    return findings;
  }

  /** @return true if the content has no accessibility findings. */
  public static boolean isClean(String html) {
    return check(html).isEmpty();
  }

  /**
   * Turns a list of findings into a terse, human-readable summary for a non-blocking author-facing
   * notice -- e.g. "3 accessibility issues found: missing alt text (2), skipped heading level (1)".
   * Shared by every caller that surfaces {@link #check(String)}'s results directly to an author (a
   * JSON response, a warning banner) so the phrasing stays consistent rather than being
   * reformatted ad hoc at each call site.
   *
   * @return the summary, or null if there is nothing to report.
   */
  public static String summarize(List<Finding> findings) {
    if (findings == null || findings.isEmpty()) {
      return null;
    }
    // Group by rule, preserving the order rules first appear in.
    Map<String, Long> countByRule = findings.stream()
        .collect(Collectors.groupingBy(Finding::getRule, LinkedHashMap::new, Collectors.counting()));

    String noun = findings.size() == 1 ? "issue" : "issues";
    String breakdown = countByRule.size() == 1
        ? readableRule(countByRule.keySet().iterator().next())
        : countByRule.entrySet().stream()
            .map(entry -> readableRule(entry.getKey()) + " (" + entry.getValue() + ")")
            .collect(Collectors.joining(", "));

    return findings.size() + " accessibility " + noun + " found: " + breakdown;
  }

  /** @return a short author-facing label for a rule constant, falling back to the rule name itself. */
  private static String readableRule(String rule) {
    if (RULE_IMAGE_MISSING_ALT.equals(rule)) {
      return "missing alt text";
    } else if (RULE_HEADING_SKIPPED.equals(rule)) {
      return "skipped heading level";
    } else if (RULE_LINK_TEXT_UNCLEAR.equals(rule)) {
      return "unclear link text";
    } else if (RULE_LINK_NO_TEXT.equals(rule)) {
      return "link without text";
    }
    return rule;
  }

  private static String describe(Element element, String detail) {
    String trimmed = StringUtils.abbreviate(StringUtils.trimToEmpty(detail), 60);
    return trimmed.isEmpty() ? "<" + element.tagName() + ">" : "<" + element.tagName() + "> " + trimmed;
  }

  /** One accessibility finding: which rule, which success criterion, and what the author should do. */
  public static class Finding {

    private final String rule;
    private final String criterion;
    private final String message;
    private final String context;

    Finding(String rule, String criterion, String message, String context) {
      this.rule = rule;
      this.criterion = criterion;
      this.message = message;
      this.context = context;
    }

    public String getRule() {
      return rule;
    }

    /** The WCAG success criterion, so a finding can be explained rather than merely asserted. */
    public String getCriterion() {
      return criterion;
    }

    public String getMessage() {
      return message;
    }

    /** Where in the content it was found, abbreviated for display. */
    public String getContext() {
      return context;
    }

    @Override
    public String toString() {
      return rule + " (" + criterion + "): " + message + " [" + context + "]";
    }
  }
}
