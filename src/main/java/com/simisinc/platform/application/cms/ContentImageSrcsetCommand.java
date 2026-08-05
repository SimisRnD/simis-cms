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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Injects a {@code srcset} attribute into {@code <img src="/assets/img/...">} tags found inside
 * rich-text HTML bodies (Content blocks, blog post bodies, wiki pages, FAQ answers -- issue #411
 * PR2's second half, distinct from the JSP-template attributes {@link ImageCommand} handles).
 *
 * <p>
 * Runs at render time, not save time: these bodies are authored over years, sanitized once at save
 * (or, for wiki, sanitized fresh on every render -- see {@code RenderWikiMarkdownCommand}) and then
 * rendered unprocessed forever after, so a save-time-only injection would leave the entire
 * historical corpus without srcset until each row happened to be re-saved. Render-time also keeps
 * this in sync with variant generation, which runs independently of any content save.
 * </p>
 *
 * <p>
 * Uses a hand-rolled, quote-aware scan rather than a full HTML parse: every existing "find/edit one
 * tag inside a larger HTML blob" operation in this codebase ({@link ReplaceImagePathCommand},
 * {@code ContentCarouselWidget}'s attribute extraction) already does this, and a full parse on every
 * render of every content block (with no caching layer here) would be real, avoidable cost. Anything
 * this scan doesn't confidently recognize is left byte-for-byte untouched -- never a best-effort
 * rewrite.
 * </p>
 *
 * @author SimIS Inc.
 */
public class ContentImageSrcsetCommand {

  private static final Log LOG = LogFactory.getLog(ContentImageSrcsetCommand.class);

  private static final String IMG_TAG_START = "<img";

  private ContentImageSrcsetCommand() {
    // Static utility, not instantiated
  }

  /**
   * @param html a rendered HTML (or, for wiki, already-sanitized Markdown-to-HTML) string that may
   *             contain zero or more {@code <img>} tags
   * @return the same content with a {@code srcset}/{@code sizes}/{@code decoding}/{@code loading}
   *         attribute spliced into any tag whose {@code src} resolves to an image with existing
   *         variants; the original string, untouched, if anything is null/blank, has no images, or
   *         a tag doesn't confidently parse
   */
  public static String injectSrcset(String html) {
    if (StringUtils.isBlank(html) || !StringUtils.containsIgnoreCase(html, IMG_TAG_START)) {
      return html;
    }
    try {
      return processTags(html);
    } catch (Exception e) {
      // Never let a markup surprise turn into a broken/blank page -- worst case, this render is
      // missing srcset, exactly like before this feature existed.
      LOG.warn("srcset injection failed; leaving content unchanged", e);
      return html;
    }
  }

  private static String processTags(String html) {
    StringBuilder output = null;
    int copiedUpTo = 0;
    int searchFrom = 0;
    while (true) {
      int tagStart = StringUtils.indexOfIgnoreCase(html, IMG_TAG_START, searchFrom);
      if (tagStart == -1) {
        break;
      }
      int tagEnd = findUnquotedGreaterThan(html, tagStart);
      if (tagEnd == -1) {
        // A truncated/malformed tag near the end of the string -- stop scanning rather than guess.
        break;
      }
      String originalTag = html.substring(tagStart, tagEnd + 1);
      String newTag = tryBuildReplacement(originalTag);
      if (newTag != null) {
        if (output == null) {
          output = new StringBuilder();
        }
        output.append(html, copiedUpTo, tagStart);
        output.append(newTag);
        copiedUpTo = tagEnd + 1;
      }
      searchFrom = tagEnd + 1;
    }
    if (output == null) {
      // Nothing qualified anywhere -- return the original object, not a byte-identical copy.
      return html;
    }
    output.append(html, copiedUpTo, html.length());
    return output.toString();
  }

  /**
   * @return a copy of originalTag with srcset/sizes/decoding/loading spliced in, or null if this
   *         tag should be left exactly as-is (no src, external/non-internal src, no variants yet,
   *         already has srcset, or the tag didn't confidently parse)
   */
  private static String tryBuildReplacement(String originalTag) {
    if (StringUtils.containsIgnoreCase(originalTag, "srcset=")) {
      return null; // idempotency guard
    }
    String src = extractQuotedAttribute(originalTag, "src");
    if (src == null) {
      return null;
    }
    String srcsetValue = ImageCommand.srcset(src);
    if (StringUtils.isBlank(srcsetValue)) {
      return null;
    }
    // TinyMCE writes an explicit width when an author resizes an image in place -- more precise
    // than the theme's grid ceiling when present. platform.css's one sitewide content-column
    // constraint (.column-container { max-width: 75rem }, i.e. 1200px) is the honest fallback
    // otherwise -- rich-text content has no fixed layout slot the way a template card grid does.
    String width = extractQuotedAttribute(originalTag, "width");
    String sizesValue = isPositiveInteger(width)
        ? "(max-width: " + width + "px) 100vw, " + width + "px"
        : "(max-width: 1200px) 100vw, 1200px";
    String insertion = " srcset=\"" + srcsetValue + "\" sizes=\"" + sizesValue + "\" decoding=\"async\" loading=\"lazy\"";
    int insertAt = originalTag.endsWith("/>") ? originalTag.length() - 2 : originalTag.length() - 1;
    return originalTag.substring(0, insertAt) + insertion + originalTag.substring(insertAt);
  }

  /**
   * Extracts a quoted attribute value from an {@code <img ...>} tag. Requires a whitespace boundary
   * immediately before the attribute name (so {@code src=} cannot match inside {@code data-src=})
   * and a matching quote character (so an unquoted value is refused rather than guessed at).
   */
  private static String extractQuotedAttribute(String tag, String attributeName) {
    String needle = attributeName + "=";
    int searchFrom = IMG_TAG_START.length();
    while (true) {
      int nameIdx = tag.indexOf(needle, searchFrom);
      if (nameIdx == -1) {
        return null;
      }
      if (!Character.isWhitespace(tag.charAt(nameIdx - 1))) {
        searchFrom = nameIdx + 1;
        continue;
      }
      int quoteIdx = nameIdx + needle.length();
      if (quoteIdx >= tag.length()) {
        return null;
      }
      char quoteChar = tag.charAt(quoteIdx);
      if (quoteChar != '"' && quoteChar != '\'') {
        return null;
      }
      int endIdx = tag.indexOf(quoteChar, quoteIdx + 1);
      if (endIdx == -1) {
        return null;
      }
      return tag.substring(quoteIdx + 1, endIdx);
    }
  }

  /** Walks forward from tagStart tracking quote state; only an unquoted '&gt;' ends the tag. */
  private static int findUnquotedGreaterThan(String html, int tagStart) {
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = tagStart; i < html.length(); i++) {
      char c = html.charAt(i);
      if (c == '"' && !inSingleQuote) {
        inDoubleQuote = !inDoubleQuote;
      } else if (c == '\'' && !inDoubleQuote) {
        inSingleQuote = !inSingleQuote;
      } else if (c == '>' && !inSingleQuote && !inDoubleQuote) {
        return i;
      }
    }
    return -1;
  }

  private static boolean isPositiveInteger(String value) {
    if (StringUtils.isBlank(value)) {
      return false;
    }
    try {
      return Integer.parseInt(value.trim()) > 0;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
