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

import static org.junit.jupiter.api.Assertions.assertEquals;
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

  // --- contrastingInk -------------------------------------------------------

  /** WCAG 2.2 contrast ratio, written out independently of the implementation under test. */
  private static double ratio(String first, String second) {
    double a = luminance(first);
    double b = luminance(second);
    return (Math.max(a, b) + 0.05d) / (Math.min(a, b) + 0.05d);
  }

  private static double luminance(String hex) {
    double[] c = new double[3];
    for (int i = 0; i < 3; i++) {
      double v = Integer.parseInt(hex.substring(1 + i * 2, 3 + i * 2), 16) / 255d;
      c[i] = v <= 0.03928d ? v / 12.92d : Math.pow((v + 0.055d) / 1.055d, 2.4d);
    }
    return 0.2126d * c[0] + 0.7152d * c[1] + 0.0722d * c[2];
  }

  @Test
  void theContrastFormulaMatchesKnownWcagValues() {
    // Guards the test's own yardstick, so a wrong formula cannot make the assertions below vacuous
    assertEquals(21.00d, ratio("#000000", "#ffffff"), 0.01d);
    assertEquals(4.54d, ratio("#767676", "#ffffff"), 0.01d);
    assertEquals(4.69d, ratio("#1779ba", "#ffffff"), 0.01d);
  }

  @Test
  void aLegibleConfiguredColorIsLeftAlone() {
    // The stock palette's dark fills carry white fine, so no rule should be emitted for them
    assertEquals("", ColorCommand.contrastingInk("#53575c", "#FFFFFF"));
    assertEquals("", ColorCommand.contrastingInk("#767676", "#FFFFFF"));
    assertEquals("", ColorCommand.contrastingInk("#cc4b37", "#FFFFFF"));
  }

  @Test
  void theStockSuccessAndWarningFillsGetADarkCaption() {
    // Issue 1537: white on these seeded fills is 2.86:1 and 1.86:1, base and hover alike
    assertEquals("#0a0a0a", ColorCommand.contrastingInk("#43AC6A", "#FFFFFF"));
    assertEquals("#0a0a0a", ColorCommand.contrastingInk("#3a9158", "#FFFFFF"));
    assertEquals("#0a0a0a", ColorCommand.contrastingInk("#ffae00", "#FFFFFF"));
    assertEquals("#0a0a0a", ColorCommand.contrastingInk("#cc8b00", "#FFFFFF"));
  }

  @Test
  void aDarkFillGetsALightCaptionWhenTheConfiguredOneIsUnreadable() {
    assertEquals("#fefefe", ColorCommand.contrastingInk("#101010", "#222222"));
  }

  @Test
  void unthemedOrUnparseableFillsEmitNothing() {
    // No theme fill means the stylesheet's own token already applies and must not be overridden
    assertEquals("", ColorCommand.contrastingInk(null, "#FFFFFF"));
    assertEquals("", ColorCommand.contrastingInk("", "#FFFFFF"));
    assertEquals("", ColorCommand.contrastingInk("   ", "#FFFFFF"));
    assertEquals("", ColorCommand.contrastingInk("#gggggg", "#FFFFFF"));
    assertEquals("", ColorCommand.contrastingInk("chartreuse", "#FFFFFF"));
    // Alpha is not guessed at -- the rendered color depends on what is behind it
    assertEquals("", ColorCommand.contrastingInk("rgba(255,174,0,0.5)", "#FFFFFF"));
    assertEquals("", ColorCommand.contrastingInk("rgb(300,0,0)", "#FFFFFF"));
  }

  @Test
  void anUnsetButtonTextColorStillGetsAReadableCaption() {
    // No configured ink at all: the fill still decides, rather than falling through to nothing
    assertEquals("#0a0a0a", ColorCommand.contrastingInk("#ffae00", null));
    assertEquals("#0a0a0a", ColorCommand.contrastingInk("#ffae00", ""));
  }

  @Test
  void shorthandHexAndRgbFillsAreUnderstood() {
    assertEquals("#0a0a0a", ColorCommand.contrastingInk("#fc0", "#FFFFFF"));
    assertEquals("#0a0a0a", ColorCommand.contrastingInk("rgb(255, 174, 0)", "#FFFFFF"));
    assertEquals("", ColorCommand.contrastingInk("rgb(83, 87, 92)", "#FFFFFF"));
  }

  @Test
  void everyReturnedInkActuallyMeetsTheFloorOnTheFillItWasChosenFor() {
    // The guarantee this exists to make, checked across the sRGB cube rather than on examples.
    // Includes the mid-luminance band where neither platform ink reaches 4.5:1 (worst case 4.431:1
    // at #6666ee) and the fallback to pure black/white has to carry it.
    int checked = 0;
    for (int r = 0; r < 256; r += 5) {
      for (int g = 0; g < 256; g += 5) {
        for (int b = 0; b < 256; b += 5) {
          String fill = String.format("#%02x%02x%02x", r, g, b);
          String ink = ColorCommand.contrastingInk(fill, "#FFFFFF");
          // Either the configured white was kept, or an override was chosen -- either way the
          // caption that ends up rendering must clear the floor
          String rendered = ink.isEmpty() ? "#ffffff" : ink;
          assertTrue(ratio(rendered, fill) >= 4.5d,
              "fill " + fill + " rendered caption " + rendered + " at " + ratio(rendered, fill));
          checked++;
        }
      }
    }
    assertTrue(checked > 100000, "expected a dense sweep, checked " + checked);
  }

  @Test
  void theNarrowBandWhereBothPlatformInksFailFallsBackToPureBlackOrWhite() {
    // Regression for the measured gap: #fefefe gives 4.440:1 and #0a0a0a gives 4.421:1 on this grey
    String ink = ColorCommand.contrastingInk("#777777", "#777777");
    assertEquals("#000000", ink);
    assertTrue(ratio(ink, "#777777") >= 4.5d);
  }
}
