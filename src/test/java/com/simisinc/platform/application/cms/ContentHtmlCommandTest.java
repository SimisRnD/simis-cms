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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Tests the format-aware content render dispatch: legacy HTML passes through, visual-editor Delta is
 * rendered server-side through {@link DeltaContentCommand}.
 *
 * @author elizabeth houser
 */
class ContentHtmlCommandTest {

  @Test
  void legacyFormatPassesHtmlThroughUnchanged() {
    // Existing content is stored HTML (cleaned on save); rendering must not alter it.
    String html = "<p>Existing <strong>html</strong> with <a href=\"/x\">a link</a></p>";
    assertEquals(html, ContentHtmlCommand.toHtml(html, DeltaContentCommand.LEGACY_HTML_FORMAT));
  }

  @Test
  void unrecognizedFormatFallsBackToPassthrough() {
    // A format this build does not know is treated as legacy passthrough -- backward compatible, since
    // format 0 dominates and a newer format only appears once a newer build wrote it.
    String html = "<p>content</p>";
    assertEquals(html, ContentHtmlCommand.toHtml(html, 99));
  }

  @Test
  void deltaFormatIsRenderedServerSide() {
    String delta = "{\"ops\":[{\"insert\":\"hi\",\"attributes\":{\"bold\":true}},{\"insert\":\"\\n\"}]}";
    assertEquals("<p><strong>hi</strong></p>",
        ContentHtmlCommand.toHtml(delta, DeltaContentCommand.DELTA_FORMAT_VERSION));
  }

  @Test
  void deltaFormatRoutesThroughTheSanitizingRenderer() {
    // Proves Delta content dispatches to the renderer (which escapes), not to passthrough (which would
    // emit the raw string). Passthrough of this value would be a stored-XSS; the dispatch prevents it.
    String delta = "{\"ops\":[{\"insert\":\"<script>alert(1)</script>\\n\"}]}";
    String html = ContentHtmlCommand.toHtml(delta, DeltaContentCommand.DELTA_FORMAT_VERSION);
    assertFalse(html.contains("<script"), html);
  }

  @Test
  void nullContentStaysNull() {
    // Callers treat null as "no content" (the add-content button path), so the contract is preserved.
    assertNull(ContentHtmlCommand.toHtml(null, DeltaContentCommand.DELTA_FORMAT_VERSION));
    assertNull(ContentHtmlCommand.toHtml(null, DeltaContentCommand.LEGACY_HTML_FORMAT));
  }
}
