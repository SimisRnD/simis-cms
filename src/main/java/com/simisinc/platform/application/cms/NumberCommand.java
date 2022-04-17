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

/**
 * Methods for working with numbers
 *
 * @author matt rajkowski
 * @created 7/11/18 5:18 PM
 */
public class NumberCommand {

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
