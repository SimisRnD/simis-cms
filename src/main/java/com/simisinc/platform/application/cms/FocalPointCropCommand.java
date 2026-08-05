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

import java.awt.Rectangle;
import java.math.BigDecimal;

/**
 * Computes a fixed-aspect-ratio crop rectangle centered on an image's focal point (issue #411
 * PR3), so a server-generated variant keeps the subject in frame instead of a blind center crop.
 *
 * <p>
 * Pure math, no I/O: im4java's {@code crop()}/{@code gravity()} only accept absolute pixels and one
 * of 9 fixed compass strings, with no percentage/relative concept at that layer, so the conversion
 * from a stored 0-100 focal-point percentage to the pixel rectangle {@code op.crop(w, h, x, y)}
 * needs must happen here first, in plain Java, against the original image's own stored dimensions.
 * </p>
 *
 * @author SimIS Inc.
 */
public final class FocalPointCropCommand {

  private FocalPointCropCommand() {
    // Static utility, not instantiated
  }

  /**
   * Computes the largest {@code targetAspectWidth:targetAspectHeight} rectangle that fits inside
   * the original image, centered on the focal point, clamped so it never runs off-canvas.
   *
   * @param originalWidth the original image's width in pixels
   * @param originalHeight the original image's height in pixels
   * @param focalXPercent the focal point's horizontal position, 0-100
   * @param focalYPercent the focal point's vertical position, 0-100
   * @param targetAspectWidth the crop's target aspect ratio width (e.g. 1 for a square)
   * @param targetAspectHeight the crop's target aspect ratio height (e.g. 1 for a square)
   * @return the crop rectangle, always fully contained within the original image's bounds
   */
  public static Rectangle computeCropRect(int originalWidth, int originalHeight,
      BigDecimal focalXPercent, BigDecimal focalYPercent,
      int targetAspectWidth, int targetAspectHeight) {

    if (originalWidth <= 0 || originalHeight <= 0) {
      throw new IllegalArgumentException("originalWidth/originalHeight must be positive");
    }
    if (targetAspectWidth <= 0 || targetAspectHeight <= 0) {
      throw new IllegalArgumentException("targetAspectWidth/targetAspectHeight must be positive");
    }

    // Defensive clamp -- this is a pure function and must not trust its caller blindly, even
    // though the DB column is already constrained by app-layer validation on write.
    double fx = clampPercent(focalXPercent);
    double fy = clampPercent(focalYPercent);

    double originalAspect = (double) originalWidth / originalHeight;
    double targetAspect = (double) targetAspectWidth / targetAspectHeight;

    int cropWidth;
    int cropHeight;
    if (originalAspect > targetAspect) {
      // Original is relatively wider than the target -- crop the full height, narrow the width.
      cropHeight = originalHeight;
      cropWidth = (int) Math.round(originalHeight * targetAspect);
    } else {
      // Original is relatively taller than (or exactly as wide as) the target -- crop the full
      // width, shorten the height.
      cropWidth = originalWidth;
      cropHeight = (int) Math.round(originalWidth / targetAspect);
    }
    // Defensive clamp: exact arithmetic guarantees these never exceed the original's dimensions in
    // the branch that computed them, but rounding is what actually ran -- this removes any doubt
    // and guarantees the crop rectangle always fits inside the original.
    cropWidth = Math.max(1, Math.min(cropWidth, originalWidth));
    cropHeight = Math.max(1, Math.min(cropHeight, originalHeight));

    // Where the focal point lands in absolute pixel coordinates on the original.
    double focalPixelX = fx / 100.0 * originalWidth;
    double focalPixelY = fy / 100.0 * originalHeight;

    // Center the crop rectangle on the focal pixel...
    int idealX = (int) Math.round(focalPixelX - cropWidth / 2.0);
    int idealY = (int) Math.round(focalPixelY - cropHeight / 2.0);

    // ...then clamp the top-left corner so the rectangle never runs off-canvas. Because
    // cropWidth <= originalWidth and cropHeight <= originalHeight (guaranteed above),
    // [0, originalWidth - cropWidth] and [0, originalHeight - cropHeight] are always valid,
    // non-inverted ranges -- a solution always exists, no matter how close the focal point is to
    // an edge. (Near an edge, the subject ends up as close to the rectangle's center as
    // geometrically possible, not exactly centered -- there is no way to do better without padding
    // pixels that don't exist, which a plain crop can't do.)
    int x = Math.max(0, Math.min(idealX, originalWidth - cropWidth));
    int y = Math.max(0, Math.min(idealY, originalHeight - cropHeight));

    return new Rectangle(x, y, cropWidth, cropHeight);
  }

  private static double clampPercent(BigDecimal percent) {
    if (percent == null) {
      return 50.0;
    }
    return Math.max(0.0, Math.min(100.0, percent.doubleValue()));
  }
}
