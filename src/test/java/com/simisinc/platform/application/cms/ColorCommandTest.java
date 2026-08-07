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

class ColorCommandTest {

  @Test
  void aValid6DigitHexColorIsAccepted() {
    assertTrue(ColorCommand.isHexColor("#000000"));
    assertTrue(ColorCommand.isHexColor("#FFFFFF"));
    assertTrue(ColorCommand.isHexColor("#1a2B3c"));
  }

  @Test
  void nonHexLettersAreRejectedEvenThoughTheyAreAlphanumeric() {
    // Regression: isAlphanumeric("gggggg") is true (they're letters), but they aren't hex digits --
    // this used to save successfully and silently degrade to browser-default styling.
    assertFalse(ColorCommand.isHexColor("#gggggg"));
    assertFalse(ColorCommand.isHexColor("#zzzzzz"));
  }

  @Test
  void malformedValuesAreRejected() {
    assertFalse(ColorCommand.isHexColor(null));
    assertFalse(ColorCommand.isHexColor(""));
    assertFalse(ColorCommand.isHexColor("#fff"));
    assertFalse(ColorCommand.isHexColor("000000"));
    assertFalse(ColorCommand.isHexColor("#0000000"));
  }
}
