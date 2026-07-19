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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Tests numeric filtering for values rendered into page style/script contexts
 *
 * @author elizabeth houser
 */
class NumberCommandTest {

  @Test
  void filterCoordinateAcceptsNumbersRejectsInjection() {
    assertEquals("38.9072", NumberCommand.filterCoordinate("38.9072"));
    assertEquals("-77.0369", NumberCommand.filterCoordinate("-77.0369"));
    assertEquals("0", NumberCommand.filterCoordinate("0"));
    // A coordinate is rendered into setView([${latitude}, ${longitude}], ...): reject anything that
    // could break out of the javascript array
    assertNull(NumberCommand.filterCoordinate("1]);alert(document.cookie);//"));
    assertNull(NumberCommand.filterCoordinate("38.9,-77"));
    assertNull(NumberCommand.filterCoordinate("38.9'"));
    assertNull(NumberCommand.filterCoordinate(""));
    assertNull(NumberCommand.filterCoordinate(null));
  }

  @Test
  void filterPositiveIntegerAcceptsDigitsElseDefault() {
    assertEquals("290", NumberCommand.filterPositiveInteger("290", "290"));
    assertEquals("500", NumberCommand.filterPositiveInteger("500", "290"));
    // Rendered as ${mapHeight}px, so no unit or breakout may pass
    assertEquals("290", NumberCommand.filterPositiveInteger("290px", "290"));
    assertEquals("290", NumberCommand.filterPositiveInteger("300\"><script>alert(1)</script>", "290"));
    assertEquals("290", NumberCommand.filterPositiveInteger("", "290"));
    assertEquals("290", NumberCommand.filterPositiveInteger(null, "290"));
  }

  @Test
  void filterCssLengthAcceptsLengthsElseDefault() {
    assertEquals("290px", NumberCommand.filterCssLength("290px", "290px"));
    assertEquals("550", NumberCommand.filterCssLength("550", "550"));
    assertEquals("80%", NumberCommand.filterCssLength("80%", "290px"));
    assertEquals("40vh", NumberCommand.filterCssLength("40vh", "290px"));
    // Reject CSS/attribute breakout
    assertEquals("290px", NumberCommand.filterCssLength("290px;} body{display:none", "290px"));
    assertEquals("290px", NumberCommand.filterCssLength("290px\"><script>alert(1)</script>", "290px"));
    assertEquals("290px", NumberCommand.filterCssLength("expression(alert(1))", "290px"));
    assertNull(NumberCommand.filterCssLength("bad", null));
  }
}
