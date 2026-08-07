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

package com.simisinc.platform.application.items;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * Validates that a Collection/Category color field is a narrow, strict CSS color value. These
 * values are concatenated directly into an inline &lt;style&gt; block on public pages (see
 * main.jsp), so anything permissive here is a CSS/HTML injection vector -- a value like
 * "red;}body{display:none}/*" would let a data-manager inject arbitrary CSS/selectors into every
 * visitor's view of the collection/category.
 *
 * @author matt rajkowski
 * @created 8/6/26
 */
public class CssColorValidationCommand {

  // Hex: 3, 4, 6, or 8 digits. rgb()/rgba(): purely numeric/percentage arguments only -- no
  // nested functions, no CSS variables, no url().
  private static final Pattern HEX_COLOR_PATTERN = Pattern
      .compile("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");

  private static final Pattern RGB_COLOR_PATTERN = Pattern.compile(
      "^rgba?\\(\\s*\\d{1,3}%?\\s*,\\s*\\d{1,3}%?\\s*,\\s*\\d{1,3}%?\\s*(?:,\\s*(?:\\d{1,3}(?:\\.\\d+)?%?|\\.\\d+)\\s*)?\\)$",
      Pattern.CASE_INSENSITIVE);

  public static boolean isValid(String value) {
    if (StringUtils.isBlank(value)) {
      // Blank/unset is allowed
      return true;
    }
    String trimmedValue = value.trim();
    return HEX_COLOR_PATTERN.matcher(trimmedValue).matches() || RGB_COLOR_PATTERN.matcher(trimmedValue).matches();
  }
}
