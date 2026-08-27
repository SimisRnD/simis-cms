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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Color validation functions
 *
 * @author matt rajkowski
 * @created 7/13/22 4:32 PM
 */
public class ColorCommand {

  private static Log LOG = LogFactory.getLog(ColorCommand.class);

  public static boolean isHexColor(String hexColor) {
    // Alphanumeric alone let non-hex letters like #gggggg through -- only 0-9/a-f are valid hex digits
    return hexColor != null && hexColor.matches("^#[0-9a-fA-F]{6}$");
  }

  /**
   * The caption colors a themed button can use, in preference order.
   *
   * <p>The first two are the platform's own inks, so a derived caption is indistinguishable from one
   * Foundation drew itself -- they are the values behind --sc-fnd-on-accent and --sc-fnd-ink-on-accent.
   * They do not cover every fill: measured across the sRGB cube, there is a narrow mid-luminance band
   * where neither reaches 4.5:1, worst case 4.431:1 at #6666ee and 4.440:1 at the grey #777777. Pure
   * black and white have no such gap -- their worst case over the same sweep is 4.583:1 -- so they are
   * the fallback, and the ladder as a whole can never fail to find a passing ink.
   */
  private static final String[] INK_PREFERENCE = { "#fefefe", "#0a0a0a", "#ffffff", "#000000" };

  /** WCAG 2.2 SC 1.4.3 normal-text minimum. */
  private static final double MINIMUM_CONTRAST_RATIO = 4.5d;

  /**
   * Returns a caption color guaranteed to meet SC 1.4.3 on the given button fill, or an empty string
   * when the theme's own button text color already does.
   *
   * <p>An empty return means "emit no rule": the configured color is legible on this fill and stays in
   * force, so a site's chosen button text color is honored wherever it works and corrected only where it
   * would not be readable. A blank or unparseable fill also returns empty, because then the button is
   * not painted by the theme at all and the stylesheet's own token is already correct for it.
   *
   * <p>The returned value is always one of {@link #INK_PREFERENCE} -- never anything derived from the
   * arguments -- so a theme property can never reach the rendered stylesheet through this method.
   *
   * @param backgroundColor the fill the caption will sit on, as configured for the theme
   * @param preferredInk the theme's configured button text color
   * @return a hex color to override with, or "" to leave the configured color in place
   */
  public static String contrastingInk(String backgroundColor, String preferredInk) {
    double[] background = toLinearRgb(backgroundColor);
    if (background == null) {
      return "";
    }
    double backgroundLuminance = relativeLuminance(background);
    double[] preferred = toLinearRgb(preferredInk);
    if (preferred != null && contrastRatio(relativeLuminance(preferred), backgroundLuminance) >= MINIMUM_CONTRAST_RATIO) {
      return "";
    }
    String best = INK_PREFERENCE[0];
    double bestRatio = -1d;
    for (String ink : INK_PREFERENCE) {
      double ratio = contrastRatio(relativeLuminance(toLinearRgb(ink)), backgroundLuminance);
      if (ratio >= MINIMUM_CONTRAST_RATIO) {
        return ink;
      }
      if (ratio > bestRatio) {
        bestRatio = ratio;
        best = ink;
      }
    }
    // Unreachable for any sRGB color -- see INK_PREFERENCE. Kept so a future palette edit degrades to
    // the most readable option rather than to whatever happens to be first in the list.
    LOG.warn("No ink in the preference list reaches " + MINIMUM_CONTRAST_RATIO + ":1 on a button fill");
    return best;
  }

  /** WCAG 2.2 contrast ratio between two relative luminances. */
  private static double contrastRatio(double first, double second) {
    double lighter = Math.max(first, second);
    double darker = Math.min(first, second);
    return (lighter + 0.05d) / (darker + 0.05d);
  }

  /** WCAG 2.2 relative luminance from already-linearized channels. */
  private static double relativeLuminance(double[] linearRgb) {
    return 0.2126d * linearRgb[0] + 0.7152d * linearRgb[1] + 0.0722d * linearRgb[2];
  }

  /**
   * Parses a CSS color into linearized sRGB channels, or null if it is blank or not a form this
   * understands. Only the opaque forms an administrator can produce are handled -- 3- and 6-digit hex,
   * and rgb() with integer channels. Anything carrying alpha is treated as unparseable rather than
   * guessed at, since its rendered color depends on what is behind it.
   */
  private static double[] toLinearRgb(String color) {
    if (color == null) {
      return null;
    }
    String value = color.trim();
    if (value.isEmpty()) {
      return null;
    }
    int[] channels = null;
    if (value.matches("^#[0-9a-fA-F]{3}$")) {
      channels = new int[3];
      for (int i = 0; i < 3; i++) {
        int digit = Integer.parseInt(value.substring(i + 1, i + 2), 16);
        channels[i] = digit * 16 + digit;
      }
    } else if (isHexColor(value)) {
      channels = new int[3];
      for (int i = 0; i < 3; i++) {
        channels[i] = Integer.parseInt(value.substring(1 + i * 2, 3 + i * 2), 16);
      }
    } else if (value.matches("^(?i)rgb\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*\\)$")) {
      String[] parts = value.substring(value.indexOf('(') + 1, value.lastIndexOf(')')).split(",");
      channels = new int[3];
      for (int i = 0; i < 3; i++) {
        channels[i] = Integer.parseInt(parts[i].trim());
        if (channels[i] > 255) {
          return null;
        }
      }
    }
    if (channels == null) {
      return null;
    }
    double[] linear = new double[3];
    for (int i = 0; i < 3; i++) {
      double channel = channels[i] / 255d;
      linear[i] = channel <= 0.03928d ? channel / 12.92d : Math.pow((channel + 0.055d) / 1.055d, 2.4d);
    }
    return linear;
  }

}
