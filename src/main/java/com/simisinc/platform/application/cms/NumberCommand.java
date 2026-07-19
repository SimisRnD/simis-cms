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

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Methods for working with numbers
 *
 * @author matt rajkowski
 * @created 7/11/18 5:18 PM
 */
public class NumberCommand {

  // A signed decimal coordinate, e.g. 38.9, -77.03
  private static final Pattern COORDINATE = Pattern.compile("^-?\\d{1,3}(\\.\\d{1,15})?$");
  // Digits only, for a pixel dimension the template renders as ${value}px
  private static final Pattern POSITIVE_INTEGER = Pattern.compile("^\\d{1,6}$");
  // A CSS length: digits, optional decimal, optional unit (px, em, rem, %, vh, vw)
  private static final Pattern CSS_LENGTH = Pattern.compile("^\\d{1,6}(\\.\\d{1,3})?(px|em|rem|%|vh|vw)?$");

  /** Returns the value when it is a valid geo coordinate, otherwise null. These values are rendered into
   * page javascript, so anything non-numeric must not reach the template. */
  public static String filterCoordinate(String value) {
    if (StringUtils.isNotBlank(value) && COORDINATE.matcher(value).matches()) {
      return value;
    }
    return null;
  }

  /** Returns the value when it is a plain positive integer, otherwise the default. */
  public static String filterPositiveInteger(String value, String defaultValue) {
    if (StringUtils.isNotBlank(value) && POSITIVE_INTEGER.matcher(value).matches()) {
      return value;
    }
    return defaultValue;
  }

  /** Returns the value when it is a valid CSS length (number + optional unit), otherwise the default. */
  public static String filterCssLength(String value, String defaultValue) {
    if (StringUtils.isNotBlank(value) && CSS_LENGTH.matcher(value).matches()) {
      return value;
    }
    return defaultValue;
  }

  /**
   * Returns a string value of a number, with a suffix
   * @param count
   * @return
   */
  public static String suffix(long count) {
    if (count < 1000) {
      return String.valueOf(count);
    }
    int exp = (int) (Math.log(count) / Math.log(1000));
    char suffix = "kMGTPE".charAt(exp - 1);
    if (suffix == 'k') {
      return String.format("%.0f%c", count / Math.pow(1000, exp), suffix);
    }
    return String.format("%.1f%c", count / Math.pow(1000, exp), suffix);
  }
}
