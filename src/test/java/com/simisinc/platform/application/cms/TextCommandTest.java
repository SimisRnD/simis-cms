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
 * @author elizabeth houser
 */
class TextCommandTest {

  @Test
  void isNumericAcceptsPlainNumbers() {
    assertTrue(TextCommand.isNumeric("1234"));
    assertTrue(TextCommand.isNumeric("1234.5"));
    assertTrue(TextCommand.isNumeric("0"));
    assertTrue(TextCommand.isNumeric("-42"));
    assertTrue(TextCommand.isNumeric("  1234  "));
  }

  @Test
  void isNumericRejectsPreFormattedDisplayText() {
    // Regression test: site-stats-table.jsp used to hand these straight to fmt:formatNumber,
    // which throws NumberFormatException and takes down the entire page render (issue found on
    // the Community Analytics page's "Avg Time on Page" / traffic-engagement reports).
    assertFalse(TextCommand.isNumeric("33.8s"));
    assertFalse(TextCommand.isNumeric("185 hits, 33.8s avg"));
    assertFalse(TextCommand.isNumeric(""));
    assertFalse(TextCommand.isNumeric(null));
  }
}
