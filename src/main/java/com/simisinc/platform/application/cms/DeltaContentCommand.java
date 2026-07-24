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
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The server-side boundary for the visual editor's rich-text content.
 *
 * <p>The visual editor (Project #6) stores rich text as <b>Quill 2.x Delta JSON</b> and renders it
 * <b>here, on the server</b>, into a small allowlisted subset of HTML. It deliberately does NOT use
 * the editor's own {@code getSemanticHTML()} / {@code getHTML()} export path.
 *
 * <p><b>Why that matters (security).</b> CVE-2025-15056 is an XSS in Quill's HTML-<i>export</i> blots
 * (formula/video interpolate unescaped user values in {@code html()}); it is reachable ONLY via the
 * export API and does not touch Delta storage or server rendering. By storing Delta and rendering it
 * ourselves through {@link #INLINE_FORMATS} — which does not include formula or video — the vulnerable
 * code is never in the execute path. This is the same VEX {@code not_affected /
 * vulnerable_code_not_in_execute_path} pattern used for the database image, and it is a
 * <b>design constraint, not an assumption</b>: introducing an export-API call, or widening the format
 * allowlist to a raw-HTML-bearing format, voids it. See the build-vs-adopt runbook, third pass.
 *
 * <p><b>Format versioning.</b> Stored content carries a stamped {@link #DELTA_FORMAT_VERSION} so the
 * renderer can tell Delta from legacy HTML and so a future Quill Delta shape change (1.x embeds/list
 * keys were removed in 2.0) is a migration rather than silent corruption.
 *
 * @author elizabeth houser
 */
public class DeltaContentCommand {

  private static final Log LOG = LogFactory.getLog(DeltaContentCommand.class);

  /** Content predating the visual editor: an HTML string, rendered via {@link HtmlCommand}. */
  public static final int LEGACY_HTML_FORMAT = 0;

  /**
   * Quill 2.x Delta JSON, rendered by this class. There is no format {@code 1}: Quill's own 1.x Delta
   * shape (integer embed values, different list attribute keys) was removed in Quill 2.0, so the number
   * is reserved for that legacy shape should a migration ever need to name what it upgrades <i>from</i>.
   */
  public static final int DELTA_FORMAT_VERSION = 2;

  /**
   * The inline formats the hybrid seam supports. Anything outside this set is ignored on render. This is
   * the allowlist the CVE-2025-15056 analysis above depends on — notably it excludes {@code formula} and
   * {@code video}. Do not add a format that carries raw HTML without revisiting that analysis.
   */
  public static final List<String> INLINE_FORMATS = List.of("bold", "italic", "code", "link");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  // The safelist enumerates exactly the tags/attributes render() emits, and nothing else. It is
  // defense-in-depth: even a bug in the block/inline assembly below cannot emit script, an event
  // handler, or a disallowed URL scheme, because the output is re-cleaned against this before return.
  private static final Safelist RENDER_SAFELIST = new Safelist()
      .addTags("p", "br", "strong", "em", "code", "pre", "blockquote",
          "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "a")
      .addAttributes("a", "href", "rel", "target")
      .addProtocols("a", "href", "http", "https", "mailto")
      .addEnforcedAttribute("a", "rel", "noopener noreferrer");

  private DeltaContentCommand() {
    // Static command
  }

  /**
   * @return true if the value parses as a Quill Delta document ({@code {"ops":[...]}}).
   */
  public static boolean isValidDelta(String deltaJson) {
    return parseOps(deltaJson) != null;
  }

  /**
   * Detects the removed Quill 1.x Delta shape (embed inserts with integer values), which needs migration
   * before it can be rendered. Distinct from an invalid document, so a migration can find these on sight.
   */
  public static boolean isLegacyDeltaShape(String deltaJson) {
    JsonNode ops = parseOps(deltaJson);
    if (ops == null) {
      return false;
    }
    for (JsonNode op : ops) {
      JsonNode insert = op.get("insert");
      if (insert != null && insert.isNumber()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Renders a Quill Delta document to the allowlisted HTML subset. Returns an empty string for null,
   * blank, or unparseable input rather than throwing — a bad stored value must never break a page render.
   */
  public static String render(String deltaJson) {
    JsonNode ops = parseOps(deltaJson);
    if (ops == null) {
      return "";
    }

    List<String> blocks = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    List<String> pendingListItems = new ArrayList<>();
    String pendingListType = null;

    for (JsonNode op : ops) {
      JsonNode insert = op.get("insert");
      if (insert == null || !insert.isTextual()) {
        // Embeds (image/formula/video) are objects, not text, and are outside the allowlist: skip them.
        continue;
      }
      JsonNode attributes = op.get("attributes");
      String text = insert.textValue();

      // A text insert may span multiple lines; each '\n' closes the current line as a block.
      int start = 0;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) != '\n') {
          continue;
        }
        // Inline text before this newline belongs to the current line, formatted by THIS op's attributes.
        line.append(applyInlineFormats(text.substring(start, i), attributes));
        // The newline itself carries the block-level attributes for the line it closes.
        String listType = blockListType(attributes);
        if (listType != null) {
          if (pendingListType != null && !pendingListType.equals(listType)) {
            blocks.add(wrapList(pendingListType, pendingListItems));
            pendingListItems = new ArrayList<>();
          }
          pendingListType = listType;
          pendingListItems.add("<li>" + line + "</li>");
        } else {
          if (pendingListType != null) {
            blocks.add(wrapList(pendingListType, pendingListItems));
            pendingListItems = new ArrayList<>();
            pendingListType = null;
          }
          String block = wrapBlock(line.toString(), attributes);
          if (block != null) {
            blocks.add(block);
          }
        }
        line.setLength(0);
        start = i + 1;
      }
      // Trailing inline text with no newline yet stays buffered for the next line.
      if (start < text.length()) {
        line.append(applyInlineFormats(text.substring(start), attributes));
      }
    }
    if (pendingListType != null) {
      blocks.add(wrapList(pendingListType, pendingListItems));
    }
    // Any dangling inline content with no closing newline becomes a final paragraph.
    if (line.length() > 0) {
      blocks.add("<p>" + line + "</p>");
    }

    return sanitize(String.join("", blocks));
  }

  // --- internals -----------------------------------------------------------------------------------

  private static JsonNode parseOps(String deltaJson) {
    if (deltaJson == null || deltaJson.isBlank()) {
      return null;
    }
    try {
      JsonNode root = MAPPER.readTree(deltaJson);
      JsonNode ops = root.get("ops");
      if (ops == null || !ops.isArray()) {
        return null;
      }
      return ops;
    } catch (Exception e) {
      LOG.debug("Not a parseable Delta document: " + e.getMessage());
      return null;
    }
  }

  private static String applyInlineFormats(String rawText, JsonNode attributes) {
    if (rawText.isEmpty()) {
      return "";
    }
    // Escape first: the text is always literal content, never markup, regardless of what it contains.
    String html = Entities.escape(rawText);
    if (attributes == null) {
      return html;
    }
    if (attributes.path("code").asBoolean(false)) {
      html = "<code>" + html + "</code>";
    }
    if (attributes.path("bold").asBoolean(false)) {
      html = "<strong>" + html + "</strong>";
    }
    if (attributes.path("italic").asBoolean(false)) {
      html = "<em>" + html + "</em>";
    }
    JsonNode link = attributes.get("link");
    if (link != null && link.isTextual()) {
      // The href is passed through as-is here; RENDER_SAFELIST enforces the http/https/mailto scheme
      // allowlist and drops the anchor entirely if the scheme is not permitted (e.g. javascript:).
      html = "<a href=\"" + Entities.escape(link.textValue()) + "\">" + html + "</a>";
    }
    return html;
  }

  private static String blockListType(JsonNode attributes) {
    if (attributes == null) {
      return null;
    }
    JsonNode list = attributes.get("list");
    if (list == null || !list.isTextual()) {
      return null;
    }
    return "ordered".equals(list.textValue()) ? "ol" : "ul";
  }

  private static String wrapBlock(String inner, JsonNode attributes) {
    if (attributes != null) {
      int header = attributes.path("header").asInt(0);
      if (header >= 1 && header <= 6) {
        return "<h" + header + ">" + inner + "</h" + header + ">";
      }
      if (attributes.path("blockquote").asBoolean(false)) {
        return "<blockquote>" + inner + "</blockquote>";
      }
      if (attributes.path("code-block").asBoolean(false)) {
        return "<pre><code>" + inner + "</code></pre>";
      }
    }
    // Skip genuinely empty paragraphs (consecutive newlines) rather than emitting <p></p> noise.
    if (inner.isEmpty()) {
      return null;
    }
    return "<p>" + inner + "</p>";
  }

  private static String wrapList(String listTag, List<String> items) {
    return "<" + listTag + ">" + String.join("", items) + "</" + listTag + ">";
  }

  private static String sanitize(String html) {
    Document dirty = Jsoup.parseBodyFragment(html);
    Document clean = new Cleaner(RENDER_SAFELIST).clean(dirty);
    clean.outputSettings().prettyPrint(false);
    return clean.body().html();
  }
}
