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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the sanitizer applied to markdown-rendered HTML (wiki pages).
 *
 * <p>Wiki bodies are stored as raw markdown and rendered by flexmark, which passes raw HTML through
 * as CommonMark requires; wiki-page.jsp then writes the result out unescaped. Sanitizing the
 * rendered output is what closes that path, so these tests cover both halves of the contract:
 * executable markup must not survive, and everything the configured flexmark extensions legitimately
 * emit must.
 *
 * @author SimIS
 * @created 7/21/2026 11:00 AM
 */
class RenderedMarkdownCleanerTest {

  @Test
  void stripsScriptElements() {
    String clean = HtmlCommand.cleanRenderedMarkdown(
        "<p>before</p><script>alert(document.domain)</script><p>after</p>");
    assertFalse(clean.toLowerCase().contains("<script"), "script element survived: " + clean);
    assertFalse(clean.contains("alert(document.domain)"), "script body survived: " + clean);
    assertTrue(clean.contains("before"));
    assertTrue(clean.contains("after"));
  }

  @Test
  void stripsEventHandlerAttributes() {
    String clean = HtmlCommand.cleanRenderedMarkdown(
        "<img src=\"/a.png\" onerror=\"alert(1)\" alt=\"x\">"
            + "<p onmouseover=\"alert(2)\">text</p>"
            + "<a href=\"/x\" onclick=\"alert(3)\">link</a>");
    assertFalse(clean.toLowerCase().contains("onerror"), clean);
    assertFalse(clean.toLowerCase().contains("onmouseover"), clean);
    assertFalse(clean.toLowerCase().contains("onclick"), clean);
    assertFalse(clean.contains("alert("), clean);
    // the elements themselves are legitimate markdown output and must remain
    assertTrue(clean.contains("<img"), clean);
    assertTrue(clean.contains("text"), clean);
  }

  @Test
  void stripsJavascriptUrls() {
    String clean = HtmlCommand.cleanRenderedMarkdown(
        "<a href=\"javascript:alert(1)\">a</a>"
            + "<iframe src=\"javascript:alert(2)\"></iframe>"
            + "<img src=\"javascript:alert(3)\">");
    assertFalse(clean.toLowerCase().contains("javascript:"), clean);
  }

  @Test
  void stripsInlineStyleAndStyleElements() {
    String clean = HtmlCommand.cleanRenderedMarkdown(
        "<style>body{display:none}</style><p style=\"position:fixed;top:0\">x</p>");
    assertFalse(clean.toLowerCase().contains("<style"), clean);
    assertFalse(clean.toLowerCase().contains("style="), clean);
    assertTrue(clean.contains("x"), clean);
  }

  @Test
  void keepsOrdinaryMarkdownOutput() {
    String html = "<h2 id=\"heading\">Heading</h2>"
        + "<p>Para with <strong>bold</strong>, <em>em</em>, <del>strike</del> and <code>code</code>."
        + "<br />and a break.</p>"
        + "<blockquote><p>quoted</p></blockquote>"
        + "<ul><li>bullet</li></ul><ol><li>first</li></ol><hr />";
    String clean = HtmlCommand.cleanRenderedMarkdown(html);
    for (String expected : new String[] { "<h2", "id=\"heading\"", "<strong>", "<em>", "<del>",
        "<code>", "<br", "<blockquote>", "<ul>", "<ol>", "<li>", "<hr" }) {
      assertTrue(clean.contains(expected), "lost " + expected + " from: " + clean);
    }
  }

  @Test
  void keepsTablesWithAlignment() {
    String clean = HtmlCommand.cleanRenderedMarkdown(
        "<table><thead><tr><th>a</th><th align=\"right\">b</th></tr></thead>"
            + "<tbody><tr><td>1</td><td align=\"right\">2</td></tr></tbody></table>");
    assertTrue(clean.contains("<table>"), clean);
    assertTrue(clean.contains("align=\"right\""), clean);
  }

  @Test
  void keepsTaskListCheckboxes() {
    String clean = HtmlCommand.cleanRenderedMarkdown(
        "<ul><li class=\"task-list-item\"><input type=\"checkbox\" "
            + "class=\"task-list-item-checkbox\" disabled=\"disabled\" readonly=\"readonly\" />"
            + "&nbsp;task</li></ul>");
    assertTrue(clean.contains("<input"), "task-list checkbox was stripped: " + clean);
    assertTrue(clean.contains("task-list-item"), clean);
  }

  @Test
  void keepsFencedCodeLanguageAndMermaidDiagrams() {
    String clean = HtmlCommand.cleanRenderedMarkdown(
        "<pre><code class=\"language-java\">int x = 1;</code></pre>"
            + "<div class=\"mermaid\">graph TD; A--&gt;B;</div>");
    assertTrue(clean.contains("language-java"), "code language class lost: " + clean);
    assertTrue(clean.contains("class=\"mermaid\""), "mermaid container lost: " + clean);
  }

  @Test
  void keepsYouTubeEmbedButOnlyOverHttps() {
    String clean = HtmlCommand.cleanRenderedMarkdown(
        "<iframe src=\"https://www.youtube.com/embed/abc\" width=\"420\" height=\"315\" "
            + "class=\"youtube-embedded\" allowfullscreen=\"true\" frameborder=\"0\"></iframe>");
    assertTrue(clean.contains("<iframe"), "YouTube embed was stripped: " + clean);
    assertTrue(clean.contains("https://www.youtube.com/embed/abc"), clean);
  }

  @Test
  void keepsRelativeWikiLinksAndImages() {
    String clean = HtmlCommand.cleanRenderedMarkdown(
        "<p><a href=\"/wiki/SomePage\">WikiLink</a> "
            + "<a href=\"https://example.com\" target=\"_blank\">external</a> "
            + "<img src=\"/assets/img/x.png\" alt=\"x\" /></p>");
    assertTrue(clean.contains("/wiki/SomePage"), "relative wiki link lost: " + clean);
    assertTrue(clean.contains("/assets/img/x.png"), "relative image lost: " + clean);
    assertTrue(clean.contains("target=\"_blank\""), clean);
  }

  @Test
  void handlesNullAndBlank() {
    assertTrue(HtmlCommand.cleanRenderedMarkdown(null) == null);
    assertTrue("".equals(HtmlCommand.cleanRenderedMarkdown("")));
  }
}
