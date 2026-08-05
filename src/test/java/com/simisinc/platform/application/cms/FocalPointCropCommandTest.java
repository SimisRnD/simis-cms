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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Rectangle;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class FocalPointCropCommandTest {

  private static final BigDecimal CENTER = new BigDecimal("50");

  @Test
  void squareCropOnAWideOriginalCenteredOnAFocalPointInTheMiddleIsHorizontallyCentered() {
    Rectangle rect = FocalPointCropCommand.computeCropRect(1000, 500, CENTER, CENTER, 1, 1);
    assertEquals(new Rectangle(250, 0, 500, 500), rect);
  }

  @Test
  void squareCropOnAWideOriginalClampsToTheLeftEdgeWhenTheFocalPointIsNearIt() {
    Rectangle rect = FocalPointCropCommand.computeCropRect(1000, 500, new BigDecimal("5"), CENTER, 1, 1);
    assertEquals(new Rectangle(0, 0, 500, 500), rect);
  }

  @Test
  void squareCropOnAWideOriginalClampsToTheRightEdgeWhenTheFocalPointIsNearIt() {
    Rectangle rect = FocalPointCropCommand.computeCropRect(1000, 500, new BigDecimal("95"), CENTER, 1, 1);
    assertEquals(new Rectangle(500, 0, 500, 500), rect);
  }

  @Test
  void squareCropOnATallOriginalCenteredOnAFocalPointInTheMiddleIsVerticallyCentered() {
    Rectangle rect = FocalPointCropCommand.computeCropRect(500, 1000, CENTER, CENTER, 1, 1);
    assertEquals(new Rectangle(0, 250, 500, 500), rect);
  }

  @Test
  void squareCropOnATallOriginalClampsToTheTopEdgeWhenTheFocalPointIsNearIt() {
    Rectangle rect = FocalPointCropCommand.computeCropRect(500, 1000, CENTER, new BigDecimal("5"), 1, 1);
    assertEquals(new Rectangle(0, 0, 500, 500), rect);
  }

  @Test
  void squareCropOnATallOriginalClampsToTheBottomEdgeWhenTheFocalPointIsNearIt() {
    Rectangle rect = FocalPointCropCommand.computeCropRect(500, 1000, CENTER, new BigDecimal("95"), 1, 1);
    assertEquals(new Rectangle(0, 500, 500, 500), rect);
  }

  @Test
  void squareCropOnAnAlreadySquareOriginalIsTheFullImageRegardlessOfFocalPoint() {
    // cropWidth == cropHeight == originalWidth == originalHeight here, so the valid clamp range
    // collapses to a single point (0,0) no matter where the focal point sits.
    Rectangle centered = FocalPointCropCommand.computeCropRect(600, 600, CENTER, CENTER, 1, 1);
    Rectangle cornered = FocalPointCropCommand.computeCropRect(600, 600, new BigDecimal("2"), new BigDecimal("98"), 1, 1);

    assertEquals(new Rectangle(0, 0, 600, 600), centered);
    assertEquals(new Rectangle(0, 0, 600, 600), cornered);
  }

  @Test
  void nonSquareTargetAspectRatioIsSizedAndCenteredCorrectly() {
    // A 16:9 crop out of a very tall, narrow original: the full width is used (400), height is
    // 400 * 9/16 = 225 exactly. Centering on the vertical midpoint (focal y = 500 of 1000) puts the
    // crop's top at 500 - 225/2 = 387.5, which rounds up to 388.
    Rectangle rect = FocalPointCropCommand.computeCropRect(400, 1000, CENTER, CENTER, 16, 9);
    assertEquals(new Rectangle(0, 388, 400, 225), rect);
  }

  @Test
  void aNullFocalPointDefaultsToDeadCenter() {
    Rectangle withNull = FocalPointCropCommand.computeCropRect(1000, 500, null, null, 1, 1);
    Rectangle withExplicitCenter = FocalPointCropCommand.computeCropRect(1000, 500, CENTER, CENTER, 1, 1);
    assertEquals(withExplicitCenter, withNull);
  }

  @Test
  void anOutOfRangeFocalPointIsClampedDefensivelyRatherThanTrustedBlindly() {
    // computeCropRect is a pure function that must not assume its caller already validated -- even
    // though the DB column and the admin action both already constrain focal_x/focal_y to [0, 100].
    Rectangle negative = FocalPointCropCommand.computeCropRect(1000, 500, new BigDecimal("-10"), CENTER, 1, 1);
    Rectangle atZero = FocalPointCropCommand.computeCropRect(1000, 500, BigDecimal.ZERO, CENTER, 1, 1);
    Rectangle over100 = FocalPointCropCommand.computeCropRect(1000, 500, new BigDecimal("150"), CENTER, 1, 1);
    Rectangle at100 = FocalPointCropCommand.computeCropRect(1000, 500, new BigDecimal("100"), CENTER, 1, 1);

    assertEquals(atZero, negative);
    assertEquals(at100, over100);
  }

  @Test
  void rejectsNonPositiveOriginalDimensions() {
    assertThrows(IllegalArgumentException.class,
        () -> FocalPointCropCommand.computeCropRect(0, 500, CENTER, CENTER, 1, 1));
    assertThrows(IllegalArgumentException.class,
        () -> FocalPointCropCommand.computeCropRect(500, -1, CENTER, CENTER, 1, 1));
  }

  @Test
  void rejectsNonPositiveTargetAspectRatio() {
    assertThrows(IllegalArgumentException.class,
        () -> FocalPointCropCommand.computeCropRect(500, 500, CENTER, CENTER, 0, 1));
    assertThrows(IllegalArgumentException.class,
        () -> FocalPointCropCommand.computeCropRect(500, 500, CENTER, CENTER, 1, -1));
  }
}
