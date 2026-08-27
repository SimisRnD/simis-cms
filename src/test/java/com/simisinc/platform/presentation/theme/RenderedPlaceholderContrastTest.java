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
 * Proof of concept for a rendered-contrast gate: measures the colours the browser actually
 * resolves, rather than the colours {@code platform-tokens.css} declares.
 *
 * <p><b>Why this exists.</b> {@code tools/check-token-contrast.py} evaluates declared token
 * pairings. It cannot see a pairing that never applies. Issue #1506 is exactly that: no
 * light-mode rule applies {@code --sc-field-placeholder} anywhere - the only
 * {@code ::placeholder} rules in first-party CSS are the two dark-scoped ones - so Foundation's
 * own {@code ::placeholder{color:var(--sc-fnd-medium-gray,#cacaca)}} wins, and the rendered light
 * placeholder is <b>1.64:1</b>, not the 2.76:1 the tokens imply. The token gate reports that
 * pairing as 2.76:1 because that is what the declarations say.
 *
 * <p><b>Why a static check cannot separate this case.</b> The obvious heuristic - "a selector
 * styled under a dark scope with no first-party light counterpart" - flags 77 of the 100
 * dark-scoped selectors in {@code platform-tokens.css}, and roughly 76 of those are correct by
 * design: {@code .card}, {@code table} and every {@code input[type=...]} are deliberately
 * dark-only because Foundation already paints them acceptably in light mode. Structurally,
 * {@code input::placeholder} is indistinguishable from them. What separates it is the value
 * Foundation resolves to (19.63:1 for {@code .card}'s ink on its surface, 1.63:1 for the
 * placeholder grey), and that value lives in vendored CSS. Resolving it means running the
 * cascade - and the cheapest correct cascade implementation is a browser.
 *
 * <p><b>Scope.</b> This covers one pairing, to prove the mechanism before committing to a
 * broader set. The contrast maths is duplicated from the Python tool here on purpose, so the
 * proof of concept stands alone; a production version should have the browser emit
 * {@code (selector, theme, colour, background)} and let the existing, already self-tested
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
   * The control: dark mode is where the field tokens are actually applied, so this proves the
   * harness reads real cascaded values rather than defaults or noise. The ratio is printed rather
   * than asserted because it is branch-dependent - on {@code main} it is 2.64:1, which is the
   * #1489 defect PR #1490 fixes; on that branch it is 5.34:1. What is asserted is the thing that
   * does not vary: that a first-party rule wins here, unlike in light mode.
   */
  @Test
  void darkModeAppliesTheFieldTokens() {
    Measured m = measurePlaceholder("dark");
    System.out.printf("  dark   placeholder %s on field %s = %.2f:1%n", m.fgHex(), m.bgHex(), m.ratio());
    assertEquals("#979ca4", m.fgHex(), "dark mode should resolve --sc-field-placeholder");
    assertNotEquals("#fefefe", m.bgHex(),
        "dark mode should resolve --sc-field-bg, not fall through to Foundation's surface");
  }

  /**
   * The finding, and it is broader than the placeholder. In light mode <em>neither</em> field
   * token reaches the field: the only first-party rules that apply {@code --sc-field-bg} and
   * {@code --sc-field-placeholder} are the two dark-scoped ones, so Foundation wins on both
   * sides of the pair. The rendered result is its {@code #cacaca} placeholder on its
   * {@code var(--sc-fnd-surface,#fefefe)} input background - <b>1.63:1</b>.
   *
   * <p>Note the declared light value of {@code --sc-field-bg} is {@code #ffffff}, which is why
   * reasoning from the tokens gives 1.64:1 against a white field. The field is not white. That
   * one hundredth is not important in itself; what it shows is that reading the declarations
   * cannot tell you either half of this pair.
   *
   * <p>Written as a characterization of the current defect rather than as a gate, because a gate
   * would be red on {@code main} until #1506 is fixed. It is strict in both directions on
   * purpose: when #1506 is fixed these assertions start failing and this should be replaced with
   * a plain {@code ratio >= TEXT_FLOOR} assertion, so it retires itself instead of entrenching
   * the bug.
   */
  @Test
  void lightModeFieldTokensNeverReachTheField() {
    Measured m = measurePlaceholder("light");
    System.out.printf("  light  placeholder %s on field %s = %.2f:1  <-- issue #1506%n",
        m.fgHex(), m.bgHex(), m.ratio());

    assertEquals("#cacaca", m.fgHex(),
        "light should still fall through to Foundation's --sc-fnd-medium-gray. If this now reads "
            + "#979ca4 or darker, #1506 is fixed - replace this test with a plain floor assertion.");
    assertEquals("#fefefe", m.bgHex(),
        "the light field background is Foundation's --sc-fnd-surface, not the declared "
            + "--sc-field-bg #ffffff - that token is unapplied in light mode too");
    assertEquals(1.63, round2(m.ratio()),
        "the rendered light placeholder ratio. The declared token pairing "
            + "(--sc-field-placeholder #979ca4 on --sc-field-bg #ffffff) computes 2.76:1, which is "
            + "what a token-pair check reports and is not what ships.");
    assertTrue(m.ratio() < TEXT_FLOOR, "this is the defect being characterized");
  }

  /**
   * States the gap in one assertion: what the declarations imply and what the browser renders are
   * different numbers, and only one of them is what a user sees. This is the whole argument for a
   * rendered check existing alongside the token check.
   */
  @Test
  void theRenderedRatioDiffersFromTheDeclaredTokenPairing() {
    double declared = contrast(parseHex("#979ca4"), parseHex("#ffffff"));  // what the tokens say
    double rendered = measurePlaceholder("light").ratio();                 // what the browser does
    System.out.printf("  declared token pairing %.2f:1 vs rendered %.2f:1%n", declared, rendered);
    assertEquals(2.76, round2(declared));
    assertEquals(1.63, round2(rendered));
    assertTrue(rendered < declared,
        "the rendered value is worse than the declared one, so the token check is optimistic here");
  }

  // ---------------------------------------------------------------- measurement

  private record Measured(int[] fg, int[] bg) {
    double ratio() {
      return contrast(fg, bg);
    }

    String fgHex() {
      return toHex(fg);
    }

    String bgHex() {
      return toHex(bg);
    }
  }

  /**
   * Renders a bare text input under the given theme and reports the colour the browser resolves
   * for its placeholder, against the field's effective background.
   *
   * <p>The background walk matters: {@code getComputedStyle} returns {@code rgba(0,0,0,0)} for a
   * transparent element rather than the colour behind it, so a naive read produces a pair that
   * looks black and measures nothing. Walking up to the first non-transparent ancestor is what
   * makes the measured pair the one a user actually sees.
   */
  private Measured measurePlaceholder(String theme) {
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
      return new Measured(parseCssColour(fg), parseCssColour(bg));
    }
  }

  // ---------------------------------------------------------------- colour maths

  /** Parses the {@code rgb(r, g, b)} / {@code rgba(r, g, b, a)} form getComputedStyle returns. */
  private static int[] parseCssColour(String value) {
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
