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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Covers {@link RenderWikiMarkdownCommand}, extracted from {@code WikiWidget} so the live page and
 * the editor's new preview feature share one rendering path and cannot drift.
 *
 * @author SimIS
 * @created 7/28/2026
 */
class RenderWikiMarkdownCommandTest {

  @Test
  void rendersBasicMarkdown() {
    String html = RenderWikiMarkdownCommand.toHtml("# Title\n\nSome **bold** text.", "/mywiki");

    assertTrue(html.contains("<h1"), "expected a rendered heading: " + html);
    assertTrue(html.contains("<strong>bold</strong>"), "expected rendered bold text: " + html);
  }

  @Test
  void sanitizesRawScriptTags() {
    // The whole reason this command sanitizes at render time: CommonMark passes raw HTML through
    // untouched, and wiki bodies are stored unsanitized (the markdown source can't be run through
    // an HTML cleaner without mangling it) -- so this is the only safety boundary. This is the
    // same property the preview feature depends on for its safety, since preview renders
    // never-saved, arbitrary admin-typed content through this exact path.
    String html = RenderWikiMarkdownCommand.toHtml("Hello <script>alert(1)</script> world", "/mywiki");

    assertFalse(html.toLowerCase().contains("<script"), "a raw <script> tag must not survive rendering: " + html);
  }

  @Test
  void sanitizesEventHandlerAttributes() {
    String html = RenderWikiMarkdownCommand.toHtml("<img src=x onerror=\"alert(1)\">", "/mywiki");

    assertFalse(html.toLowerCase().contains("onerror"), "an inline event handler must not survive rendering: " + html);
  }

  @Test
  void resolvesWikiLinksAgainstTheGivenPrefix() {
    String html = RenderWikiMarkdownCommand.toHtml("[[Some Page]]", "/mywiki");

    assertTrue(html.contains("/mywiki/"), "expected the wiki link to resolve under the given prefix: " + html);
  }

  @Test
  void toleratesANullBody() {
    // The preview action can be called with an empty editor buffer
    String html = RenderWikiMarkdownCommand.toHtml(null, "/mywiki");

    assertFalse(html == null);
  }
}
