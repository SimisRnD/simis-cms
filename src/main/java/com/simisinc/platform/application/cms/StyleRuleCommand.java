/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Turns a style="" value into a stylesheet rule, so it can be served from a nonced &lt;style&gt;
 * element instead of an inline attribute (issue #1999).
 *
 * <p>The page's Content-Security-Policy needs style-src 'unsafe-inline' for as long as any element
 * carries a style attribute: a nonce covers a &lt;style&gt; element, never a style="" attribute. Some
 * values are genuinely dynamic -- a page layout's section, column or widget style, a category's header
 * colors -- so they cannot become fixed classes. Instead each element carries a
 * {@code data-sc-style="sc-..."} hook and its declarations are served as
 * {@code [data-sc-style="sc-..."]{...}} in the page head.</p>
 *
 * <p>Moving a value from an attribute into a stylesheet widens what a bad value can do. In an escaped
 * attribute, the worst a stray character does is produce a declaration the browser drops. In a
 * stylesheet, a "}" closes the rule and everything after it becomes page-wide CSS, and "&lt;/style&gt;"
 * ends the element. So every declaration is checked against a strict grammar and dropped, not escaped,
 * when it fails -- the same choice {@code LogoWidget.cssLength()} makes. A dropped declaration renders
 * as if it had not been written; nothing else on the page is affected.</p>
 *
 * <p>Every kept declaration is marked !important. An inline style outranks every normal selector, so a
 * plain rule could lose to one the attribute used to beat; !important keeps that precedence.</p>
 *
 * @author elizabeth houser
 * @created 9/10/26
 */
public class StyleRuleCommand {

  private static Log LOG = LogFactory.getLog(StyleRuleCommand.class);

  /** The attribute that ties an element to its rule. */
  public static final String HOOK_ATTRIBUTE = "data-sc-style";

  /** A property name: optionally vendor-prefixed or a custom property, then letters, digits, hyphens. */
  private static final Pattern PROPERTY = Pattern.compile("^-{0,2}[a-z][a-z0-9-]*$");

  /**
   * Anything that could end the declaration, the rule or the &lt;style&gt; element, open a comment or
   * an escape, or start an at-rule. Control characters are refused too.
   */
  private static final Pattern FORBIDDEN = Pattern.compile("[<>{}\\\\@\\x00-\\x1f\\x7f]|/\\*|\\*/");

  /** Legacy script-bearing CSS, refused wherever it appears in a value. */
  private static final Pattern SCRIPT_BEARING = Pattern.compile(
      "expression\\s*\\(|javascript:|vbscript:|-moz-binding|behavior\\s*:", Pattern.CASE_INSENSITIVE);

  /**
   * A url() with its target, quoted or not. A quoted target may hold parentheses ("photo (1).png"); an
   * unquoted one may not, as CSS requires. Anything that opens "url(" but does not match is refused.
   */
  private static final Pattern URL = Pattern.compile(
      "url\\(\\s*(?:'([^'\"]*)'|\"([^'\"]*)\"|([^'\"()\\s]*))\\s*\\)", Pattern.CASE_INSENSITIVE);

  private static final Pattern URL_OPEN = Pattern.compile("url\\(", Pattern.CASE_INSENSITIVE);

  private static final Pattern IMPORTANT = Pattern.compile("\\s*!\\s*important\\s*$", Pattern.CASE_INSENSITIVE);

  /**
   * Returns the declarations of a style value that pass validation, each marked !important and joined
   * with "; ", or null when none do.
   *
   * @param css a style attribute value, e.g. "background-color: #0b1024; padding-top: 48px"
   * @return the safe declarations, or null
   */
  public static String safeDeclarations(String css) {
    if (StringUtils.isBlank(css)) {
      return null;
    }
    List<String> kept = new ArrayList<>();
    for (String declaration : splitDeclarations(css)) {
      String trimmed = declaration.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      int colon = trimmed.indexOf(':');
      if (colon < 1) {
        LOG.warn("Dropped a style declaration with no property name");
        continue;
      }
      String property = trimmed.substring(0, colon).trim().toLowerCase(Locale.ROOT);
      String value = IMPORTANT.matcher(trimmed.substring(colon + 1).trim()).replaceFirst("").trim();
      if (!PROPERTY.matcher(property).matches()) {
        LOG.warn("Dropped a style declaration with an invalid property name");
        continue;
      }
      // Some script-bearing CSS is in the property name (-moz-binding, behavior), not the value
      if (SCRIPT_BEARING.matcher(property + ":" + value).find()) {
        LOG.warn("Dropped script-bearing CSS for style property: " + property);
        continue;
      }
      if (!isSafeValue(value)) {
        LOG.warn("Dropped an unsafe or malformed value for style property: " + property);
        continue;
      }
      kept.add(property + ": " + value + " !important");
    }
    return kept.isEmpty() ? null : String.join("; ", kept);
  }

  /**
   * The hook for a style value -- "sc-" and twelve hex digits of the SHA-256 of its safe declarations
   * -- or an empty string when nothing in it is safe. The same declarations always give the same hook,
   * so identical styles share one rule and the hook needs no per-request state.
   */
  public static String hook(String css) {
    return hookForDeclarations(safeDeclarations(css));
  }

  /**
   * The complete rule for a style value, or an empty string when nothing in it is safe. Safe to write
   * inside a &lt;style&gt; element as-is: the grammar above admits no character that can end it.
   */
  public static String rule(String css) {
    String declarations = safeDeclarations(css);
    if (declarations == null) {
      return "";
    }
    return "[" + HOOK_ATTRIBUTE + "=\"" + hookForDeclarations(declarations) + "\"]{" + declarations + "}";
  }

  static String hookForDeclarations(String declarations) {
    if (declarations == null) {
      return "";
    }
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(declarations.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder("sc-");
      for (int i = 0; i < 6; i++) {
        hex.append(String.format("%02x", digest[i]));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      // Every Java platform is required to provide SHA-256
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  static boolean isSafeValue(String value) {
    if (value.isEmpty() || FORBIDDEN.matcher(value).find() || SCRIPT_BEARING.matcher(value).find()) {
      return false;
    }
    // Quotes and parentheses must balance, or a value could run on into whatever follows it
    char quote = 0;
    int depth = 0;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (quote != 0) {
        if (c == quote) {
          quote = 0;
        }
      } else if (c == '\'' || c == '"') {
        quote = c;
      } else if (c == '(') {
        depth++;
      } else if (c == ')' && --depth < 0) {
        return false;
      }
    }
    if (quote != 0 || depth != 0) {
      return false;
    }
    // Every url( must be a well-formed url() pointing at this site or an http(s) address
    Matcher open = URL_OPEN.matcher(value);
    while (open.find()) {
      Matcher url = URL.matcher(value);
      if (!url.find(open.start()) || url.start() != open.start()) {
        return false;
      }
      String target = url.group(1) != null ? url.group(1) : url.group(2) != null ? url.group(2) : url.group(3);
      if (!isAllowedUrl(target)) {
        return false;
      }
    }
    return true;
  }

  static boolean isAllowedUrl(String target) {
    if (target == null) {
      return false;
    }
    String t = target.trim().toLowerCase(Locale.ROOT);
    return (t.startsWith("/") && !t.startsWith("//")) || t.startsWith("https://") || t.startsWith("http://");
  }

  /** Splits on semicolons that are outside quotes and parentheses. */
  static List<String> splitDeclarations(String css) {
    List<String> parts = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    char quote = 0;
    int depth = 0;
    for (int i = 0; i < css.length(); i++) {
      char c = css.charAt(i);
      if (quote != 0) {
        if (c == quote) {
          quote = 0;
        }
      } else if (c == '\'' || c == '"') {
        quote = c;
      } else if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      } else if (c == ';' && depth <= 0) {
        parts.add(current.toString());
        current.setLength(0);
        continue;
      }
      current.append(c);
    }
    parts.add(current.toString());
    return parts;
  }
}
