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

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.text.StringEscapeUtils;

import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.domain.model.cms.ImageVariant;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ImageVariantRepository;

/**
 * Fills in {@code <img src="/assets/img/...">} tags found inside rich-text HTML bodies (Content
 * blocks, blog post bodies, wiki pages, FAQ answers) from what the media library already knows
 * about the image: a {@code srcset} (issue #411 PR2's second half, distinct from the JSP-template
 * attributes {@link ImageCommand} handles) and the author's alt text (issue #1373).
 *
 * <p>
 * The alt half exists because the two halves of that job were never connected. An author can enter
 * alt text against an image in {@code /admin/images}, and it is stored on the {@code Image} record
 * -- but the editor's image picker never carried the value, so inserting that image into an article
 * produced {@code alt=""}, and no renderer but {@code ImageWidget} ever read it back. Measured on a
 * live site before this change: 61 of 94 library images had author-written alt text, and 31 of 34
 * images inside post bodies rendered {@code alt=""}.
 * </p>
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
   * @return the same content with {@code srcset}/{@code sizes}/{@code decoding}/{@code loading}
   *         spliced into any tag whose {@code src} resolves to an image with existing variants, and
   *         {@code alt} filled from the image's stored alt text where the tag has none of its own;
   *         the original string, untouched, if anything is null/blank, has no images, or a tag
   *         doesn't confidently parse
   */
  public static String enhanceImageTags(String html) {
    if (StringUtils.isBlank(html) || !StringUtils.containsIgnoreCase(html, IMG_TAG_START)) {
      return html;
    }
    try {
      return processTags(html);
    } catch (Exception e) {
      // Never let a markup surprise turn into a broken/blank page -- worst case, this render is
      // missing srcset, exactly like before this feature existed.
      LOG.warn("Image tag enhancement failed; leaving content unchanged", e);
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
   * @return a copy of originalTag with alt and/or srcset/sizes/decoding/loading spliced in, or null
   *         if this tag should be left exactly as-is (no src, external/non-internal src, nothing
   *         the library can add, or the tag didn't confidently parse)
   */
  private static String tryBuildReplacement(String originalTag) {
    String src = extractQuotedAttribute(originalTag, "src");
    if (src == null) {
      return null;
    }
    Long imageId = ImageCommand.parseImageId(src);
    if (imageId == null) {
      return null;
    }
    // One record load serves both halves: the original's width, so it can be offered as a srcset
    // candidate (issue #1370), and the author's alt text (issue #1373).
    Image image = lookupImage(imageId);
    String result = applySrcset(applyStoredAltText(originalTag, image), src, imageId, image);
    return result.equals(originalTag) ? null : result;
  }

  /** Defensive: a missing record must cost this tag its enhancement, never the whole page. */
  private static Image lookupImage(Long imageId) {
    try {
      return ImageRepository.findById(imageId);
    } catch (Exception e) {
      LOG.debug("Could not load the image record for a content <img>: " + imageId, e);
      return null;
    }
  }

  /**
   * Fills alt from the image's stored alt text when the tag has none of its own.
   *
   * <p>
   * An alt the author actually wrote always wins -- this only fills a missing attribute or an empty
   * one. Overwriting {@code alt=""} is deliberate and is the whole point: the editor emits it on
   * every insert whether or not anyone decided anything, so on its own it cannot be read as "I
   * considered this image and it is decorative." The distinguishing signal is the library record --
   * a genuine spacer has no alt text stored against it either, so it is left alone here. The
   * residual case, an image with stored alt text that some page uses purely decoratively, gets a
   * description it does not need; that is a far smaller harm than the 31 photographs currently
   * announcing nothing.
   * </p>
   *
   * @return originalTag when there is nothing to add or the alt attribute doesn't confidently parse
   */
  private static String applyStoredAltText(String originalTag, Image image) {
    if (image == null) {
      return originalTag;
    }
    String storedAltText = StringUtils.trimToNull(image.getAltText());
    if (storedAltText == null) {
      return originalTag;
    }
    if (StringUtils.isNotBlank(extractQuotedAttribute(originalTag, "alt"))) {
      return originalTag;
    }
    String value = StringEscapeUtils.escapeXml11(storedAltText);
    int nameIdx = indexOfAttributeName(originalTag, "alt");
    if (nameIdx == -1) {
      return spliceIntoTag(originalTag, " alt=\"" + value + "\"");
    }
    // Replace the existing (empty) value in place rather than appending a second alt attribute,
    // which would be invalid and which browsers resolve by keeping the first -- the empty one.
    int quoteIdx = nameIdx + "alt=".length();
    if (quoteIdx >= originalTag.length()) {
      return originalTag;
    }
    char quoteChar = originalTag.charAt(quoteIdx);
    if (quoteChar != '"' && quoteChar != '\'') {
      return originalTag;
    }
    int endIdx = originalTag.indexOf(quoteChar, quoteIdx + 1);
    if (endIdx == -1) {
      return originalTag;
    }
    return originalTag.substring(0, quoteIdx + 1) + value + originalTag.substring(endIdx);
  }

  /**
   * @return originalTag when srcset is already declared, when the variants can't be read, or when
   *         there is no candidate to offer
   */
  private static String applySrcset(String originalTag, String src, Long imageId, Image image) {
    if (StringUtils.containsIgnoreCase(originalTag, "srcset=")) {
      return originalTag; // idempotency guard
    }
    List<ImageVariant> variants;
    try {
      variants = ImageVariantRepository.findByImageId(imageId);
    } catch (Exception e) {
      // Defensive for the same reason as lookupImage, and specifically so a variants failure can
      // no longer cost this tag its alt text, which has already been applied by this point.
      LOG.debug("Could not load image variants for a content <img>: " + imageId, e);
      return originalTag;
    }
    String srcsetValue = ImageCommand.buildSrcset(src, variants, image != null ? image.getWidth() : 0);
    if (StringUtils.isBlank(srcsetValue)) {
      return originalTag;
    }
    // TinyMCE writes an explicit width when an author resizes an image in place -- more precise
    // than the theme's grid ceiling when present. platform.css's one sitewide content-column
    // constraint (.column-container { max-width: 75rem }, i.e. 1200px) is the honest fallback
    // otherwise -- rich-text content has no fixed layout slot the way a template card grid does.
    String width = extractQuotedAttribute(originalTag, "width");
    String staticSizes = isPositiveInteger(width)
        ? "(max-width: " + width + "px) 100vw, " + width + "px"
        : "(max-width: 1200px) 100vw, 1200px";
    // That ceiling is still only a guess, though, and guessing high is not free: the browser
    // resolves sizes= before layout and then picks the smallest candidate that covers it, so a
    // 1200px claim on an award badge the theme paints at ~104px selects the 800w (or 1600w)
    // rendition over the 200w one -- the srcset spending bytes instead of saving them, on exactly
    // the images it was added to help. sizes="auto" hands that decision to the element's real
    // layout width, which is the case here: a rich-text image's displayed size is set by the
    // theme's CSS and is genuinely unknowable at render time.
    //
    // It prefixes the previous value rather than replacing it. sizes="auto" is only valid on a
    // lazily-loaded image, and two live paths reach this markup without lazy loading: a content
    // author's own loading="eager" paste, and ContentCarouselWidget, which strips the loading=
    // this command adds and lets content-carousel.jsp mark the first slide eager (issue #413/#975).
    // Both -- along with any browser that predates sizes="auto" -- skip the auto entry and read on,
    // landing on precisely the behavior they have today instead of the 100vw default.
    String sizesValue = willLoadLazily(originalTag) ? "auto, " + staticSizes : staticSizes;
    StringBuilder insertion = new StringBuilder(" srcset=\"").append(srcsetValue).append("\" sizes=\"").append(sizesValue)
        .append("\"");
    // Don't add loading=/decoding= on top of an already-declared value -- a content author can
    // paste raw HTML that already sets one, and (issue #413/#975) a JSP consumer of extracted
    // attributes (ContentCarouselWidget) may add its own position-aware loading logic downstream
    // of this injection; either way, an existing declaration wins over this generic one.
    if (!StringUtils.containsIgnoreCase(originalTag, "decoding=")) {
      insertion.append(" decoding=\"async\"");
    }
    if (!StringUtils.containsIgnoreCase(originalTag, "loading=")) {
      insertion.append(" loading=\"lazy\"");
    }
    return spliceIntoTag(originalTag, insertion.toString());
  }

  /** Inserts attribute text immediately before the tag's closing {@code >} or {@code />}. */
  private static String spliceIntoTag(String tag, String insertion) {
    int insertAt = tag.endsWith("/>") ? tag.length() - 2 : tag.length() - 1;
    return tag.substring(0, insertAt) + insertion + tag.substring(insertAt);
  }

  /**
   * Extracts a quoted attribute value from an {@code <img ...>} tag. Requires a whitespace boundary
   * immediately before the attribute name (so {@code src=} cannot match inside {@code data-src=})
   * and a matching quote character (so an unquoted value is refused rather than guessed at).
   */
  private static String extractQuotedAttribute(String tag, String attributeName) {
    int nameIdx = indexOfAttributeName(tag, attributeName);
    if (nameIdx == -1) {
      return null;
    }
    {
      int quoteIdx = nameIdx + attributeName.length() + 1;
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

  /**
   * @return the index of {@code attributeName} within the tag, requiring a whitespace boundary
   *         immediately before it so {@code src=} cannot match inside {@code data-src=}; -1 if the
   *         attribute is not present at all
   */
  private static int indexOfAttributeName(String tag, String attributeName) {
    String needle = attributeName + "=";
    int searchFrom = IMG_TAG_START.length();
    while (true) {
      int nameIdx = tag.indexOf(needle, searchFrom);
      if (nameIdx == -1) {
        return -1;
      }
      if (Character.isWhitespace(tag.charAt(nameIdx - 1))) {
        return nameIdx;
      }
      searchFrom = nameIdx + 1;
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

  /**
   * {@code sizes="auto"} is valid only on a lazily-loaded image -- on any other image the browser
   * discards it and falls back to the 100vw default, the worst possible guess for a small one.
   *
   * @return true when the rendered tag will carry {@code loading="lazy"}: either it already
   *         declares it, or it declares no loading= at all and this command is about to add it
   */
  private static boolean willLoadLazily(String tag) {
    if (!StringUtils.containsIgnoreCase(tag, "loading=")) {
      return true;
    }
    return "lazy".equalsIgnoreCase(StringUtils.trimToNull(extractQuotedAttribute(tag, "loading")));
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
