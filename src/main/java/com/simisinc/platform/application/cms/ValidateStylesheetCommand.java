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

import com.simisinc.platform.application.DataException;

/**
 * Validates the structure of administrator-supplied stylesheet content
 *
 * <p>
 * This replaces a parse-and-catch check that ran the CSS through a third-party minifier purely to see whether it threw.
 * The checks here are the ones that check was reaching for -- balanced braces and closed comment blocks -- reported with
 * the line number so the author can find the problem, and without handing user input to an outside parser.
 * </p>
 *
 * <p>
 * This is a structural check, not a CSS grammar validation. Declarations, selectors and property names are not
 * inspected; the browser remains the authority on whether a valid-looking rule means anything.
 * </p>
 *
 * @author Elizabeth Houser
 * @created 7/22/2026 10:30 AM
 */
public class ValidateStylesheetCommand {

  private ValidateStylesheetCommand() {
    // Static utility
  }

  /**
   * Checks that the CSS has balanced braces and no unterminated comment or string
   *
   * @param css the stylesheet content to check; blank content is treated as valid
   * @throws DataException when the content is structurally broken, with the offending line number
   */
  public static void checkCss(String css) throws DataException {
    if (StringUtils.isBlank(css)) {
      return;
    }

    int depth = 0;
    int line = 1;
    int commentStartLine = 0;
    int stringStartLine = 0;
    int unclosedBraceLine = 0;
    boolean inComment = false;
    char stringDelimiter = 0;

    for (int i = 0; i < css.length(); i++) {
      char c = css.charAt(i);
      char next = (i + 1 < css.length() ? css.charAt(i + 1) : 0);

      if (c == '\n') {
        line++;
        continue;
      }

      // Inside a /* */ comment nothing else is meaningful until it closes
      if (inComment) {
        if (c == '*' && next == '/') {
          inComment = false;
          i++;
        }
        continue;
      }

      // Inside a quoted value, braces and comment markers are literal text
      if (stringDelimiter != 0) {
        if (c == '\\') {
          i++;
        } else if (c == stringDelimiter) {
          stringDelimiter = 0;
        }
        continue;
      }

      if (c == '/' && next == '*') {
        inComment = true;
        commentStartLine = line;
        i++;
      } else if (c == '"' || c == '\'') {
        stringDelimiter = c;
        stringStartLine = line;
      } else if (c == '{') {
        if (depth == 0) {
          unclosedBraceLine = line;
        }
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth < 0) {
          throw new DataException("Unexpected closing brace '}' on line " + line);
        }
      }
    }

    if (inComment) {
      throw new DataException("Unterminated comment starting on line " + commentStartLine + " -- expected '*/'");
    }
    if (stringDelimiter != 0) {
      throw new DataException("Unterminated " + stringDelimiter + " quote starting on line " + stringStartLine);
    }
    if (depth > 0) {
      throw new DataException(
          "Unclosed brace '{' starting on line " + unclosedBraceLine + " -- " + depth + " block(s) left open");
    }
  }
}
