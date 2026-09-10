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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * Catches text clipped inside a container at a 320px viewport - the half of SC 1.4.10 Reflow that
 * a page-level check cannot see.
 *
 * <p><b>Why a page-level check is not enough.</b> The obvious reflow measure is
 * {@code documentElement.scrollWidth > clientWidth}: non-zero means the page scrolls in two
 * directions, which is the hard failure. It is the right authority for that, and it is
 * <em>structurally blind</em> to the other failure mode. When an ancestor clips rather than
 * scrolls, the document never grows: issue #1976 had a tag label running <b>97px past</b> the
 * viewport with its text unreadable, while {@code scrollWidth} read exactly the viewport width.
 * A scan built on {@code scrollWidth} reports such a page perfect. Both measures are needed, and
 * this test is the second one.
 *
 * <p><b>Why the measure here is text-bearing elements, not every element.</b> A walk over every
 * element produces mostly noise: carousel slides are positioned off-screen by design, and
 * Foundation's {@code grid-margin-x} rows measure wider than their container because of the
 * framework's negative margins, with cell padding keeping content inside. Neither loses any text.
 * Restricting the walk to elements that <em>directly</em> contain a non-empty text node keeps the
 * failure mode - words a reader cannot reach - and drops both families without a filter list that
 * would need maintaining.
 *
 * <p><b>Two controls, because a green gate proves nothing on its own.</b> This test renders
 * hand-written fixtures against the real stylesheets, so it has two ways to pass while measuring
 * nothing: the detector could be broken, or the stylesheets could fail to apply and let every
 * fixture lay out at full width. {@link #theDetectorCatchesADeliberateOverflow()} and
 * {@link #allThreeStylesheetsAreActuallyApplied()} exist to fail in exactly those cases.
 *
 * <p><b>Known limitation: fixture fidelity.</b> These fixtures mirror template markup rather than
 * being rendered from it, because a JSP needs a servlet container. Two consequences, both
 * deliberate. They can drift from the templates, which
 * {@link #everyFixtureStillMatchesItsTemplate()} guards by asserting each class a fixture depends
 * on still appears in the template it claims to mirror. And each fixture is measured inside a
 * {@code .grid-container} but without the rest of a page's chrome - sidebars, callout padding, a
 * card's own inset - so the column here is wider than some real ones. This gate is therefore a
 * floor: failing it is a real defect, while passing it does not prove a page passes in situ.
 *
 * @author elizabeth houser
 */
class RenderedReflowTest {

  /** Load order taken from main.jsp: Foundation, then platform.css, then the token layer. */
  private static final File[] STYLESHEETS = {
      new File("src/main/webapp/css/foundation-6.8.1/foundation.tokens.min.css"),
      new File("src/main/webapp/css/platform.css"),
      new File("src/main/webapp/css/platform-tokens.css"),
  };

  /** SC 1.4.10 Reflow states its width threshold as the equivalent of 320 CSS pixels. */
  private static final int REFLOW_WIDTH = 320;

  /** Subpixel slack, so a rounding artifact is not reported as lost text. */
  private static final double TOLERANCE = 0.5;

  private static Playwright playwright;
  private static Browser browser;

  /**
   * A piece of template markup, the template it mirrors, and the classes that mirroring depends
   * on. The classes are what {@link #everyFixtureStillMatchesItsTemplate()} checks, so a rename in
   * the template turns this test red rather than quietly leaving it testing markup that no longer
   * ships.
   */
  private record Fixture(String name, String template, List<String> classes, String html) {
  }

  private static final List<Fixture> FIXTURES = List.of(
      new Fixture(
          "blog post byline with a long tag",
          "src/main/webapp/WEB-INF/jsp/cms/blog-post-list.jsp",
          List.of("platform-blog-list-container", "platform-blog-byline", "cell shrink", "cell auto",
              "label secondary"),
          """
          <div class="platform-blog-list-container">
            <div class="platform-blog-list-item">
              <div class="platform-blog-byline">
                <div class="grid-x grid-margin-x">
                  <div class="cell shrink"><small>September 10th, 2026</small></div>
                  <div class="cell auto"><small>
                    <span class="label secondary">Autonomous &amp; Unmanned Systems</span>
                  </small></div>
                </div>
              </div>
            </div>
          </div>
          """),
      new Fixture(
          "admin settings table: label cell beside a nowrap input cell",
          "src/main/webapp/WEB-INF/jsp/admin/collection-theme-editor.jsp",
          List.of("unstriped", "nowrap"),
          """
          <div class="admin-web-content">
          <table class="unstriped">
            <thead><tr><th width="200">Name</th><th>Value</th></tr></thead>
            <tbody>
              <tr>
                <td>Header Background Color</td>
                <td nowrap><input id="headerBgColor" type="text" name="headerBgColor" value="#123456"></td>
              </tr>
              <tr>
                <td>Menu Active Border Color</td>
                <td nowrap><input id="menuActiveBorderColor" type="text" name="menuActiveBorderColor" value="#abcdef"></td>
              </tr>
            </tbody>
          </table>
          </div>
          """),
      new Fixture(
          "upcoming event block with a long title and venue",
          "src/main/webapp/WEB-INF/jsp/calendar/upcoming-events.jsp",
          List.of("platform-calendar-list-container", "platform-calendar-event-block"),
          """
          <div class="platform-calendar-list-container">
            <div class="platform-calendar-event-block">
              <h3><a href="#">Interservice/Industry Training, Simulation and Education Conference</a></h3>
              <p>November 30, 2026 - December 3, 2026</p>
              <p>Orange County Convention Center (OCCC) - South Concourse</p>
            </div>
          </div>
          """));

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

  // ---------------------------------------------------------------- the controls

  /**
   * The detector has to be able to see an overflow before its silence means anything. A nowrap
   * span far wider than 320px is the unambiguous case: if this reports clean, every other result
   * in this class is worthless.
   */
  @Test
  void theDetectorCatchesADeliberateOverflow() {
    List<String> found = clippedText(
        "<div><span style=\"white-space:nowrap\">"
            + "control control control control control control control control control control"
            + "</span></div>");
    assertFalse(found.isEmpty(),
        "the detector reported no overflow for a deliberately overflowing span, so it cannot "
            + "detect the thing this class exists to detect");
  }

  /**
   * The other way to pass while measuring nothing: if the stylesheets do not apply, every fixture
   * lays out as unstyled markup, fits trivially, and the gate is green having measured nothing.
   *
   * <p>Each assertion below reads a value that one specific stylesheet supplies, so all three have
   * to be live for this to pass. Deliberately not a layout assertion: the first version of this
   * control asserted the byline's two cells shared a row, which the #1976 fix intentionally stops
   * being true at this very width - a control that a legitimate fix turns red is a bad control.
   */
  @Test
  void allThreeStylesheetsAreActuallyApplied() {
    try (Page page = openAtReflowWidth(
        "<div class=\"platform-calendar-event-block\"><span class=\"label secondary\">tag</span></div>")) {
      assertEquals("inline-block", page.evaluate(
          "() => getComputedStyle(document.querySelector('.label')).display"),
          "Foundation is not applying: .label should be inline-block");
      assertEquals("30px", page.evaluate(
          "() => getComputedStyle(document.querySelector('.platform-calendar-event-block')).marginLeft"),
          "platform.css is not applying: .platform-calendar-event-block should carry margin-left");
      assertEquals("700", page.evaluate(
          "() => getComputedStyle(document.documentElement).getPropertyValue('--sc-weight-bold').trim()"),
          "platform-tokens.css is not applying: --sc-weight-bold should resolve");
    }
  }

  // ---------------------------------------------------------------- the gate

  /**
   * The gate. No element that directly holds text may extend past the viewport at 320px.
   *
   * <p>This is the assertion that would have caught #1976: the tag label measured 229px against a
   * 99px cell, and Foundation's {@code .label} carries {@code white-space: nowrap}, so it could
   * not wrap and the card clipped it.
   */
  @Test
  void noTextIsClippedAtTheReflowWidth() {
    List<String> failures = new ArrayList<>();
    for (Fixture fixture : FIXTURES) {
      for (String clipped : clippedText(fixture.html())) {
        failures.add(fixture.name() + " (" + fixture.template() + "): " + clipped);
      }
    }
    assertTrue(failures.isEmpty(),
        "text extends past a " + REFLOW_WIDTH + "px viewport and is clipped:\n  "
            + String.join("\n  ", failures));
  }

  /**
   * The page-level half, kept alongside rather than replaced. A fixture that makes the document
   * itself wider than the viewport is the hard SC 1.4.10 failure, and it is worth catching in the
   * same run even though it is the measure that missed #1976.
   */
  @Test
  void noFixtureMakesTheDocumentScrollSideways() {
    for (Fixture fixture : FIXTURES) {
      try (Page page = openAtReflowWidth(fixture.html())) {
        Integer scrollWidth = (Integer) page.evaluate("() => document.documentElement.scrollWidth");
        Integer clientWidth = (Integer) page.evaluate("() => document.documentElement.clientWidth");
        assertEquals(REFLOW_WIDTH, clientWidth,
            "the viewport is not at the reflow width, so this measures nothing: " + fixture.name());
        assertTrue(scrollWidth <= clientWidth,
            String.format(Locale.ROOT, "%s scrolls sideways: scrollWidth %d vs clientWidth %d",
                fixture.name(), scrollWidth, clientWidth));
      }
    }
  }

  /**
   * Fixture drift is the standing risk of mirroring markup by hand: a template renames a class,
   * the fixture keeps testing the old shape, and the gate stays green over markup that no longer
   * ships. Checking that each class a fixture depends on is still present in the template it
   * claims to mirror does not prove the fixture is faithful, but it fails on the rename that would
   * otherwise go unnoticed.
   */
  @Test
  void everyFixtureStillMatchesItsTemplate() throws IOException {
    List<String> missing = new ArrayList<>();
    for (Fixture fixture : FIXTURES) {
      File template = new File(fixture.template());
      assertTrue(template.isFile(), "template not found: " + fixture.template());
      String source = Files.readString(template.toPath(), StandardCharsets.UTF_8);
      for (String className : fixture.classes()) {
        if (!source.contains(className)) {
          missing.add(fixture.template() + " no longer contains \"" + className + "\", so the \""
              + fixture.name() + "\" fixture is testing markup the template does not emit");
        }
      }
    }
    assertTrue(missing.isEmpty(), String.join("\n  ", missing));
  }

  // ---------------------------------------------------------------- measurement

  /**
   * Renders the markup at the reflow width and reports every element that directly holds text and
   * extends past the viewport.
   *
   * <p>"Directly holds text" is the whole filter: an ancestor whose child overflows is reported
   * through that child, and layout boxes that carry no words of their own - grid rows with
   * negative margins, off-screen carousel track - never appear. Hidden elements are skipped
   * because clipping something already invisible loses nothing.
   */
  private List<String> clippedText(String bodyHtml) {
    try (Page page = openAtReflowWidth(bodyHtml)) {
      @SuppressWarnings("unchecked")
      List<String> found = (List<String>) page.evaluate(
          """
          (tolerance) => {
            const viewport = document.documentElement.clientWidth;
            const describe = (el) => el.tagName.toLowerCase()
              + (el.className && typeof el.className === 'string' && el.className.trim()
                  ? '.' + el.className.trim().split(/\\s+/).join('.') : '');
            const out = [];
            // Content inside a horizontally scrollable ancestor is reachable, not lost, so it
            // is not this check's failure mode. The admin shell relies on this:
            // .admin-web-content is overflow-x:auto, so a wide data table scrolls instead of
            // clipping. Without this the gate reports every such table as clipped text.
            const scrollable = (el) => {
              for (let n = el.parentElement; n && n !== document.documentElement; n = n.parentElement) {
                const ox = getComputedStyle(n).overflowX;
                if (ox === 'auto' || ox === 'scroll') return true;
              }
              return false;
            };
            for (const el of document.querySelectorAll('body *')) {
              let holdsText = false;
              for (const node of el.childNodes) {
                if (node.nodeType === Node.TEXT_NODE && node.textContent.trim()) { holdsText = true; break; }
              }
              if (!holdsText) continue;
              const style = getComputedStyle(el);
              if (style.display === 'none' || style.visibility === 'hidden') continue;
              const rect = el.getBoundingClientRect();
              if (rect.width === 0 && rect.height === 0) continue;
              if (rect.right > viewport + tolerance && !scrollable(el)) {
                out.push(describe(el) + ' "' + el.textContent.trim().slice(0, 44) + '" ends at '
                  + Math.round(rect.right) + 'px, ' + Math.round(rect.right - viewport)
                  + 'px past the ' + viewport + 'px viewport');
              }
            }
            return out;
          }
          """, TOLERANCE);
      return found;
    }
  }

  /**
   * A page at exactly the reflow width, carrying the real stylesheets, with the fixture inside a
   * {@code .grid-container} because that is where {@code layout-body-renderer.jspf} puts page
   * content.
   *
   * <p>The wrapper is not cosmetic. Foundation's {@code .grid-margin-x} rows carry a -10px margin
   * on each side and rely on the container's matching 10px padding to absorb it; without the
   * wrapper a byline row measures 340px in a 320px viewport and reports a 20px overflow that does
   * not exist on a real page. That is the best-known false positive of this whole measure, and
   * reproducing the real container removes it at the source rather than filtering it afterwards.
   */
  private Page openAtReflowWidth(String bodyHtml) {
    Page page = browser.newPage();
    page.setViewportSize(REFLOW_WIDTH, 800);
    page.setContent("<html><body class=\"platform-body\" style=\"margin:0\">"
        + "<div class=\"grid-container\">" + bodyHtml + "</div></body></html>");
    for (File css : STYLESHEETS) {
      page.addStyleTag(new Page.AddStyleTagOptions().setPath(css.toPath()));
    }
    return page;
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
