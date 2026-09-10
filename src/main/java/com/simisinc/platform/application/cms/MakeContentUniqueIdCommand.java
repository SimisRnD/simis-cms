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

import org.apache.commons.text.StringEscapeUtils;

/**
 * Generates a URL compatible id
 *
 * @author matt rajkowski
 * @created 5/6/18 7:00 PM
 */
public class MakeContentUniqueIdCommand {

  private static final String ALLOWED_CHARS = "1234567890abcdefghijklmnopqrstuvwxyz";

  public static String parseToValidValue(String originalName) {

    // Decode HTML entities before anything else. Titles reach this method verbatim -- nothing on
    // the save path decodes them -- so a title carrying "&rsquo;" (pasted from an HTML source, or
    // submitted as a character reference) used to be slugified character by character: the "&"
    // became "and", the ";" was dropped, and the entity name survived as literal text, turning
    // "What&rsquo;s in America&rsquo;s Code" into "whatandrsquos-in-americaandrsquos-code".
    // Decoding first means the entity becomes the character it stands for, and that character is
    // then handled by the rules below -- so a smart quote is dropped, "&amp;" becomes "and" just
    // as a typed "&" already does, and no entity name can leak into a URL.
    //
    // This is deliberately not done earlier in the pipeline: the stored title is the site owner's
    // content and must not be rewritten as a side effect of saving. Decoding here also covers
    // every slug generator at once -- all of them call this one method.
    String name = StringEscapeUtils.unescapeHtml4(originalName).toLowerCase();

    // Create a new one
    StringBuilder sb = new StringBuilder();
    final int len = name.length();
    char lastChar = '_';
    for (int i = 0; i < len; i++) {
      char c = name.charAt(i);
      if (ALLOWED_CHARS.indexOf(name.charAt(i)) > -1) {
        sb.append(c);
        lastChar = c;
      } else if (c == '&') {
        sb.append("and");
        lastChar = '&';
      } else if (c == ' ' || c == '-' || c == '/' || isDecodedSeparator(c)) {
        if (lastChar != '-') {
          sb.append("-");
        }
        lastChar = '-';
      }
    }
    String value = sb.toString();

    // Don't end with a -
    while (value.endsWith("-")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }

  /**
   * Typographic characters that separate words and so must behave like a space rather than being
   * dropped. Decoding entities is what makes these reachable in quantity -- "&mdash;" and
   * "&nbsp;" previously arrived here as entity text -- and dropping them would run the words on
   * either side together, so "Design&mdash;Build" would slugify to "designbuild".
   *
   * @param c the character to test
   * @return true when the character should be treated as a word separator
   */
  private static boolean isDecodedSeparator(char c) {
    return c == '\u2013' // en dash
        || c == '\u2014' // em dash
        || c == '\u00a0'; // non-breaking space
  }

}
