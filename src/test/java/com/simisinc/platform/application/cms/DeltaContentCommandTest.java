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

import org.junit.jupiter.api.Test;

/**
 * Tests the server-side Delta rendering boundary for the visual editor, with emphasis on the security
 * contract: content is rendered from Delta through an allowlist, never the Quill HTML-export path.
 *
 * @author elizabeth houser
 */
class DeltaContentCommandTest {

  @Test
  void formatConstantsAreStable() {
    // The stored stamp is a contract with persisted content; a silent change would misread stored rows.
    assertEquals(0, DeltaContentCommand.LEGACY_HTML_FORMAT);
    assertEquals(2, DeltaContentCommand.DELTA_FORMAT_VERSION);
  }

  @Test
  void plainTextBecomesAnEscapedParagraph() {
    String html = DeltaContentCommand.render("{\"ops\":[{\"insert\":\"Hello & <world>\\n\"}]}");
    assertEquals("<p>Hello &amp; &lt;world&gt;</p>", html);
  }

  @Test
  void inlineAllowlistIsApplied() {
    assertEquals("<p><strong>b</strong></p>",
        DeltaContentCommand.render("{\"ops\":[{\"insert\":\"b\",\"attributes\":{\"bold\":true}},{\"insert\":\"\\n\"}]}"));
    assertEquals("<p><em>i</em></p>",
        DeltaContentCommand.render("{\"ops\":[{\"insert\":\"i\",\"attributes\":{\"italic\":true}},{\"insert\":\"\\n\"}]}"));
    assertEquals("<p><code>c</code></p>",
        DeltaContentCommand.render("{\"ops\":[{\"insert\":\"c\",\"attributes\":{\"code\":true}},{\"insert\":\"\\n\"}]}"));
  }

  @Test
  void formatsOutsideTheAllowlistAreIgnored() {
    // underline and color are real Quill formats but outside the hybrid seam; they must not render.
    String html = DeltaContentCommand.render(
        "{\"ops\":[{\"insert\":\"x\",\"attributes\":{\"underline\":true,\"color\":\"#ff0000\"}},{\"insert\":\"\\n\"}]}");
    assertEquals("<p>x</p>", html);
  }

  @Test
  void blockLevelFormatsRender() {
    assertEquals("<h2>Title</h2>",
        DeltaContentCommand.render("{\"ops\":[{\"insert\":\"Title\"},{\"insert\":\"\\n\",\"attributes\":{\"header\":2}}]}"));
    assertEquals("<blockquote>quoted</blockquote>",
        DeltaContentCommand.render("{\"ops\":[{\"insert\":\"quoted\"},{\"insert\":\"\\n\",\"attributes\":{\"blockquote\":true}}]}"));
    assertEquals("<ul><li>one</li><li>two</li></ul>",
        DeltaContentCommand.render("{\"ops\":[{\"insert\":\"one\"},{\"insert\":\"\\n\",\"attributes\":{\"list\":\"bullet\"}}"
            + ",{\"insert\":\"two\"},{\"insert\":\"\\n\",\"attributes\":{\"list\":\"bullet\"}}]}"));
    assertEquals("<ol><li>one</li></ol>",
        DeltaContentCommand.render("{\"ops\":[{\"insert\":\"one\"},{\"insert\":\"\\n\",\"attributes\":{\"list\":\"ordered\"}}]}"));
  }

  @Test
  void allowedLinkKeepsHrefAndGainsRel() {
    String html = DeltaContentCommand.render(
        "{\"ops\":[{\"insert\":\"site\",\"attributes\":{\"link\":\"https://example.org\"}},{\"insert\":\"\\n\"}]}");
    assertTrue(html.contains("href=\"https://example.org\""), html);
    // Enforced by the safelist so an editor cannot opt out of tab-nabbing protection.
    assertTrue(html.contains("rel=\"noopener noreferrer\""), html);
    assertTrue(html.contains(">site</a>"), html);
  }

  @Test
  void javascriptUrlSchemeIsStripped() {
    // The javascript: scheme is not in the safelist's protocol allowlist for href.
    String html = DeltaContentCommand.render(
        "{\"ops\":[{\"insert\":\"x\",\"attributes\":{\"link\":\"javascript:alert(1)\"}},{\"insert\":\"\\n\"}]}");
    assertFalse(html.contains("javascript"), html);
    // The visible text survives even though the dangerous link does not.
    assertTrue(html.contains("x"), html);
  }

  @Test
  void cve2025_15056PayloadIsNeutralised() {
    // The published PoC for CVE-2025-15056 (Quill HTML-export XSS). We render Delta server-side and never
    // call the export blots, so the payload is inert content: every angle bracket is escaped, so there is
    // no live <img>/<span> element and the onerror text a browser sees is plain text it will not execute.
    // This test enforces the design constraint the not_affected VEX position depends on -- the literal
    // "onerror" substring surviving as escaped text is exactly the point, not a leak.
    String payload = "</span><img src=x onerror=alert(1)>";
    String delta = "{\"ops\":[{\"insert\":" + jsonString(payload) + "},{\"insert\":\"\\n\"}]}";
    String html = DeltaContentCommand.render(delta);
    assertEquals("<p>&lt;/span&gt;&lt;img src=x onerror=alert(1)&gt;</p>", html);
    // No live markup survived: the brackets are all escaped entities, not tags.
    assertFalse(html.contains("<img"), html);
    assertFalse(html.contains("<span"), html);
  }

  @Test
  void embedInsertsAreDropped() {
    // Object inserts (image/formula/video) are outside the allowlist and must not reach the output.
    String html = DeltaContentCommand.render(
        "{\"ops\":[{\"insert\":{\"image\":\"https://x/y.png\"}},{\"insert\":\"caption\\n\"}]}");
    assertEquals("<p>caption</p>", html);
    assertFalse(html.toLowerCase().contains("img"), html);
  }

  @Test
  void isValidDeltaDistinguishesShape() {
    assertTrue(DeltaContentCommand.isValidDelta("{\"ops\":[{\"insert\":\"hi\\n\"}]}"));
    assertFalse(DeltaContentCommand.isValidDelta("{\"blocks\":[]}"));
    assertFalse(DeltaContentCommand.isValidDelta("not json"));
    assertFalse(DeltaContentCommand.isValidDelta(""));
    assertFalse(DeltaContentCommand.isValidDelta(null));
  }

  @Test
  void legacyOneXShapeIsDetectable() {
    // A 1.x embed carried an integer insert value; that shape needs migration before it can render.
    assertTrue(DeltaContentCommand.isLegacyDeltaShape("{\"ops\":[{\"insert\":1,\"attributes\":{\"image\":true}}]}"));
    assertFalse(DeltaContentCommand.isLegacyDeltaShape("{\"ops\":[{\"insert\":\"text\\n\"}]}"));
  }

  @Test
  void unparseableInputRendersEmptyNotError() {
    // A bad stored value must never break a page render.
    assertEquals("", DeltaContentCommand.render(null));
    assertEquals("", DeltaContentCommand.render(""));
    assertEquals("", DeltaContentCommand.render("garbage"));
    assertEquals("", DeltaContentCommand.render("{\"ops\":\"notarray\"}"));
  }

  private static String jsonString(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
