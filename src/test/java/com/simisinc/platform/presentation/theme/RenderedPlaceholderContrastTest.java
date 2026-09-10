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

package com.simisinc.platform.presentation.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * Proof of concept for a rendered-contrast gate: measures the colors the browser actually
 * resolves, rather than the colors {@code platform-tokens.css} declares.
 *
 * <p><b>Why this exists.</b> {@code tools/check-token-contrast.py} evaluates declared token
 * pairings. It cannot see a pairing that never applies, and it cannot see what wins when no
 * first-party rule applies at all. Issue #1506 was exactly that: no light-mode rule applied
 * {@code --sc-field-placeholder}, Foundation's own
 * {@code ::placeholder{color:var(--sc-fnd-medium-gray,#cdc9c3)}} was the only declaration
 * standing, and the rendered light placeholder was 1.64:1 while the token gate reported the
 * pairing as 2.76:1. That defect is fixed -- an unscoped {@code input::placeholder} rule now
 * applies the token in both themes -- so this no longer characterizes a bug. It measures a
 * healthy pairing and would go red if one regressed.
 *
 * <p><b>The gap it demonstrates is still open.</b> Even with the placeholder fixed, light mode
 * renders the placeholder on Foundation's {@code --sc-fnd-surface} rather than on the declared
 * {@code --sc-field-bg}: the only rules applying that token are dark-scoped. So the declared
 * pairing and the rendered pairing are still two different numbers, and the declared one is
 * still the more flattering of the two. Today both clear the floor and the difference is small;
 * the point is that a token nudged one step lighter would pass the declared check while failing
 * on screen.
 *
 * <p><b>Why a static check cannot separate this case.</b> The obvious heuristic - "a selector
 * styled under a dark scope with no first-party light counterpart" - flags most of the
 * dark-scoped selectors in {@code platform-tokens.css}, and nearly all of those are correct by
 * design: {@code .card}, {@code table} and every {@code input[type=...]} are deliberately
 * dark-only because Foundation already paints them acceptably in light mode. Structurally,
 * {@code input::placeholder} was indistinguishable from them. What separated it was the value
 * Foundation resolved to, and that value lives in vendored CSS. Resolving it means running the
 * cascade - and the cheapest correct cascade implementation is a browser.
 *
 * <p><b>What is asserted, and what is only printed.</b> Nothing here pins a palette value. The
 * first version of this test did, and it went red the moment the palette moved to the warm
 * ramp - reporting a regression that had not happened while proving nothing about contrast.
 * Every assertion below is either a WCAG floor or a relationship between two measurements
 * ("rendered is no better than declared", "a first-party rule wins here"), both of which survive
 * a repaint and both of which break for a real regression. Concrete colors are printed so a
 * failure can be read, never asserted.
 *
 * <p><b>Scope.</b> This covers one pairing, to prove the mechanism before committing to a
 * broader set. The contrast math is duplicated from the Python tool here on purpose, so the
 * proof of concept stands alone; a production version should have the browser emit
 * {@code (selector, theme, color, background)} and let the existing, already self-tested
 * calculator in {@code tools/check-token-contrast.py} apply the floors, rather than maintaining
 * two implementations of the thing that has to be right.
 *
 * @author elizabeth houser
 */
class RenderedPlaceholderContrastTest {

  /** Load order taken from main.jsp: Foundation, then platform.css, then the token layer. */
  private static final File[] STYLESHEETS = {
      new File("src/main/webapp/css/foundation-6.8.1/foundation.tokens.min.css"),
      new File("src/main/webapp/css/platform.css"),
      new File("src/main/webapp/css/platform-tokens.css"),
  };

  /** SC 1.4.3 Contrast (Minimum) for normal-size text. */
  private static final double TEXT_FLOOR = 4.5;

  private static Playwright playwright;
  private static Browser browser;

  @BeforeAll
  static void launchBrowser() throws IOException, InterruptedException {
    for (File css : STYLESHEETS) {
      assertTrue(css.isFile(), "stylesheet not found (run from the project root): " + css.getAbsolutePath());
    }
    String nodePath = resolveSystemNodePath();
    System.setProperty("playwright.nodejs.path", nodePath);
    installChromiumIfMissing(nodePath);
    playwright = Playwright.create();
    browser = playwright.chromium().launch();
  }

  @AfterAll
  static void closeBrowser() {
    if (browser != null) {
      browser.close();
    }
    if (playwright != null) {
      playwright.close();
    }
  }

  // ---------------------------------------------------------------- the checks

  /**
   * The calculator has to be right before any number it produces means anything. Black on white
   * is exactly 21:1; {@code #767676} on white is the canonical AA pass at 4.54:1 and
   * {@code #777777}, one step lighter, is the fail at 4.48:1 - that last pair is what proves the
   * implementation resolves the pass/fail boundary rather than rounding past it.
   */
  @Test
  void theContrastCalculatorResolvesThePassFailBoundary() {
    assertEquals(21.00, round2(contrast(new int[] {0, 0, 0}, new int[] {255, 255, 255})));
    assertEquals(4.54, round2(contrast(new int[] {0x76, 0x76, 0x76}, new int[] {255, 255, 255})));
    assertEquals(4.48, round2(contrast(new int[] {0x77, 0x77, 0x77}, new int[] {255, 255, 255})));
  }

  /**
   * The control: dark mode is where both field tokens are applied by first-party rules, so this
   * proves the harness reads real cascaded values rather than defaults or noise. Asserted against
   * the token the stylesheet declares rather than a literal, so a repaint moves both sides
   * together and only a broken cascade separates them.
   */
  @Test
  void darkModeAppliesTheFieldTokens() {
    Probe p = probe("dark");
    System.out.printf("  dark   placeholder %s on field %s = %.2f:1  (declared %s on %s)%n",
        p.fgHex(), p.bgHex(), p.ratio(), p.declaredPlaceholder(), p.declaredFieldBg());
    assertEquals(p.declaredPlaceholder(), p.fgHex(),
        "dark mode should render the declared --sc-field-placeholder");
    assertEquals(p.declaredFieldBg(), p.bgHex(),
        "dark mode should render the declared --sc-field-bg, not fall through to Foundation");
    assertTrue(p.ratio() >= TEXT_FLOOR,
        "dark placeholder is below SC 1.4.3: " + p.describe());
  }

  /**
   * Light mode after #1506. The placeholder token now reaches the field, so the rendered ratio
   * clears the text floor -- this is the assertion the original version of this test asked for
   * once the defect was fixed.
   *
   * <p>The other half of the original finding survives the fix and is asserted here: the field
   * <em>background</em> token is still unapplied in light mode. The only rules setting
   * {@code --sc-field-bg} are dark-scoped, so the light field is painted with Foundation's
   * surface. It is close enough to the declared white that it costs only a few hundredths today,
   * which is precisely why reading the declarations cannot tell you it happened.
   */
  @Test
  void lightModePlaceholderClearsTheTextFloor() {
    Probe p = probe("light");
    System.out.printf("  light  placeholder %s on field %s = %.2f:1  (declared %s on %s)%n",
        p.fgHex(), p.bgHex(), p.ratio(), p.declaredPlaceholder(), p.declaredFieldBg());
    assertEquals(p.declaredPlaceholder(), p.fgHex(),
        "light mode should render the declared --sc-field-placeholder (issue #1506)");
    assertTrue(p.ratio() >= TEXT_FLOOR,
        "light placeholder is below SC 1.4.3: " + p.describe());
    assertNotEquals(p.declaredFieldBg(), p.bgHex(),
        "if the light field now renders its declared --sc-field-bg, the dark-only scoping of that "
            + "token has been fixed too -- drop this assertion and the paragraph above it");
  }

  /**
   * States the gap in one assertion: what the declarations imply and what the browser renders are
   * different numbers, and only one of them is what a user sees. This is the whole argument for a
   * rendered check existing alongside the token check, and it holds even now that both numbers
   * pass -- the declared pairing is the optimistic one, so it is the wrong number to gate on.
   */
  @Test
  void theRenderedRatioIsNoBetterThanTheDeclaredTokenPairing() {
    Probe p = probe("light");
    double declared = contrast(parseHex(p.declaredPlaceholder()), parseHex(p.declaredFieldBg()));
    System.out.printf("  declared token pairing %.2f:1 vs rendered %.2f:1%n", declared, p.ratio());
    assertTrue(p.ratio() <= declared,
        "the token check is only safe to gate on while it is the pessimistic of the two. It reads "
            + String.format(Locale.ROOT, "%.2f:1 and the browser renders %.2f:1", declared, p.ratio()));
    assertTrue(p.ratio() >= TEXT_FLOOR,
        "the rendered pairing is what ships and it is below SC 1.4.3: " + p.describe());
  }

  // ---------------------------------------------------------------- measurement

  /**
   * One theme's worth of measurement: the pair the browser paints, alongside the pair the
   * stylesheet declares for the same two tokens. Holding both is what lets every assertion be a
   * floor or a relationship rather than a pinned palette value.
   */
  private record Probe(int[] fg, int[] bg, String declaredPlaceholder, String declaredFieldBg) {
    double ratio() {
      return contrast(fg, bg);
    }

    String fgHex() {
      return toHex(fg);
    }

    String bgHex() {
      return toHex(bg);
    }

    String describe() {
      return String.format(Locale.ROOT, "%s on %s = %.2f:1", fgHex(), bgHex(), ratio());
    }
  }

  /**
   * Renders a bare text input under the given theme and reports the color the browser resolves
   * for its placeholder, against the field's effective background, plus the two token values the
   * stylesheet declares for that theme.
   *
   * <p>The background walk matters: {@code getComputedStyle} returns {@code rgba(0,0,0,0)} for a
   * transparent element rather than the color behind it, so a naive read produces a pair that
   * looks black and measures nothing. Walking up to the first non-transparent ancestor is what
   * makes the measured pair the one a user actually sees.
   */
  private Probe probe(String theme) {
    try (Page page = browser.newPage()) {
      page.setContent("<html data-theme=\"" + theme + "\"><body class=\"platform-body\">"
          + "<form><input id=\"probe\" type=\"text\" placeholder=\"Search\"></form>"
          + "</body></html>");
      for (File css : STYLESHEETS) {
        page.addStyleTag(new Page.AddStyleTagOptions().setPath(css.toPath()));
      }
      String fg = (String) page.evaluate(
          "() => getComputedStyle(document.getElementById('probe'), '::placeholder').color");
      String bg = (String) page.evaluate(
          """
          () => {
            for (let el = document.getElementById('probe'); el; el = el.parentElement) {
              const c = getComputedStyle(el).backgroundColor;
              const m = c.match(/rgba?\\(([^)]+)\\)/);
              if (m) {
                const parts = m[1].split(/[,\\s/]+/).filter(Boolean).map(Number);
                if (parts.length < 4 || parts[3] > 0) return c;
              }
            }
            return getComputedStyle(document.documentElement).backgroundColor;
          }
          """);
      return new Probe(parseCssColor(fg), parseCssColor(bg),
          declaredToken(page, "--sc-field-placeholder"), declaredToken(page, "--sc-field-bg"));
    }
  }

  /**
   * The value the stylesheet declares for a custom property under the page's current theme.
   * Normalized to the lowercase {@code #rrggbb} form {@link #toHex} produces, so a declared value
   * and a rendered one can be compared directly.
   */
  private static String declaredToken(Page page, String name) {
    String raw = (String) page.evaluate(
        "() => getComputedStyle(document.documentElement).getPropertyValue('" + name + "').trim()");
    assertTrue(raw != null && raw.startsWith("#") && raw.length() == 7,
        name + " should be declared as a #rrggbb literal for this comparison, but reads: " + raw);
    return toHex(parseHex(raw));
  }

  // ---------------------------------------------------------------- color math

  /** Parses the {@code rgb(r, g, b)} / {@code rgba(r, g, b, a)} form getComputedStyle returns. */
  private static int[] parseCssColor(String value) {
    String inner = value.substring(value.indexOf('(') + 1, value.lastIndexOf(')'));
    String[] parts = inner.split("[,\\s/]+");
    return new int[] {
        (int) Math.round(Double.parseDouble(parts[0])),
        (int) Math.round(Double.parseDouble(parts[1])),
        (int) Math.round(Double.parseDouble(parts[2])),
    };
  }

  private static int[] parseHex(String hex) {
    String h = hex.startsWith("#") ? hex.substring(1) : hex;
    return new int[] {
        Integer.parseInt(h.substring(0, 2), 16),
        Integer.parseInt(h.substring(2, 4), 16),
        Integer.parseInt(h.substring(4, 6), 16),
    };
  }

  private static String toHex(int[] rgb) {
    return String.format(Locale.ROOT, "#%02x%02x%02x", rgb[0], rgb[1], rgb[2]);
  }

  /** WCAG relative luminance. */
  private static double luminance(int[] rgb) {
    double[] linear = new double[3];
    for (int i = 0; i < 3; i++) {
      double c = rgb[i] / 255.0;
      linear[i] = c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
  }

  private static double contrast(int[] fg, int[] bg) {
    double a = luminance(fg);
    double b = luminance(bg);
    double lighter = Math.max(a, b);
    double darker = Math.min(a, b);
    return (lighter + 0.05) / (darker + 0.05);
  }

  private static double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  // ---------------------------------------------------------------- harness plumbing
  // Mirrors PlatformEditorMutateButtonsTest; see CONTRIBUTING.md on the Node.js requirement.

  private static String resolveSystemNodePath() throws IOException, InterruptedException {
    String fromEnv = System.getenv("PLAYWRIGHT_NODEJS_PATH");
    if (fromEnv != null && !fromEnv.isBlank()) {
      return fromEnv;
    }
    Process which = new ProcessBuilder("which", "node").start();
    String output = new String(which.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    if (which.waitFor() != 0 || output.isEmpty()) {
      throw new IllegalStateException(
          "No system Node.js found on PATH and PLAYWRIGHT_NODEJS_PATH is not set - see CONTRIBUTING.md.");
    }
    return output;
  }

  private static void installChromiumIfMissing(String nodePath) throws IOException, InterruptedException {
    String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    Process process = new ProcessBuilder(javaBin, "-Dplaywright.nodejs.path=" + nodePath,
        "-cp", System.getProperty("java.class.path"),
        "com.microsoft.playwright.CLI", "install", "chromium")
        .inheritIO()
        .start();
    if (process.waitFor() != 0) {
      throw new IllegalStateException("Playwright chromium install failed");
    }
  }
}
