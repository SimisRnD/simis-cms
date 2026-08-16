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

package com.simisinc.platform.presentation.widgets.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * The visual page editor renders a {@code .sc-mutate-btns} toolbar as a direct child of each
 * hovered {@code [data-editor-section]}, {@code [data-editor-column]}, and {@code
 * [data-editor-widget]}. Because CSS {@code :hover} bubbles to ancestors, hovering a widget also
 * satisfies its column's and section's {@code :hover}, so all three toolbars can render at the
 * same bottom-right corner - and when the column/widget is short and flush with its parent's edge,
 * one fully occludes another for pointer-events purposes even though its markup is well-formed.
 *
 * <p>A DOM-existence check cannot catch that: the occluded button is present and well-formed, just
 * not the one the browser hands back at that pixel. These tests render the real {@code
 * platform-editor.css} in a real Chromium page and assert {@code document.elementFromPoint} at
 * each button's own rect center resolves to that button, not an ancestor's.
 *
 * @author elizabeth houser
 */
class PlatformEditorMutateButtonsTest {

  private static final File PLATFORM_EDITOR_CSS = new File("src/main/webapp/css/platform-editor.css");

  /**
   * Mirrors the DOM shape layout-body-renderer.jspf produces for a default section (no cssClass)
   * and the button markup insertMutateButtons() in platform-editor.js appends to each level -
   * a short column (min-height: 20px, matching the real CSS) holding one short widget, flush
   * against the section's own bottom-right corner, which is the reported failure condition.
   * #col-gap sits inside the column, below the widget, so a test can hover "inside the column but
   * outside any widget" without relying on manually-computed pixel offsets.
   */
  private static final String FIXTURE_HTML =
      """
      <!DOCTYPE html>
      <html>
      <body class="page-edit-mode">
        <div class="full-container" data-editor-section="0">
          <div class="grid-container">
            <div class="grid-x grid-margin-x">
              <div id="column" class="small-12 cell" data-editor-column="0-0">
                <div id="widget" data-editor-widget="0-0-0">short widget content</div>
                <div id="col-gap" style="height: 20px;"></div>
                <div class="sc-mutate-btns" id="column-btns">
                  <button type="button" class="sc-mutate-btn-add" id="btn-column-add">+ Column</button>
                  <button type="button" class="sc-mutate-btn-remove" id="btn-column-remove">Remove Column</button>
                </div>
              </div>
            </div>
          </div>
          <div class="sc-mutate-btns" id="section-btns">
            <button type="button" class="sc-mutate-btn-add" id="btn-section-add">+ Section</button>
            <button type="button" class="sc-mutate-btn-remove" id="btn-section-remove">Remove Section</button>
          </div>
        </div>
        <script>
          // insertMutateButtons() appends the widget's own toolbar as the last child of the
          // widget element itself, after the section/column loops above have already run.
          document.getElementById('widget').insertAdjacentHTML('beforeend',
            '<div class="sc-mutate-btns" id="widget-btns">' +
            '<button type="button" class="sc-mutate-btn-remove" id="btn-widget-remove">Remove Widget</button>' +
            '</div>');
        </script>
      </body>
      </html>
      """;

  private static Playwright playwright;
  private static Browser browser;
  private Page page;

  @BeforeAll
  static void launchBrowser() throws IOException, InterruptedException {
    assertTrue(PLATFORM_EDITOR_CSS.isFile(),
        "platform-editor.css not found (run from the project root): " + PLATFORM_EDITOR_CSS.getAbsolutePath());
    String nodePath = resolveSystemNodePath();
    System.setProperty("playwright.nodejs.path", nodePath);
    installChromiumIfMissing(nodePath);
    playwright = Playwright.create();
    browser = playwright.chromium().launch();
  }

  /**
   * The vendored driver jar (see {@link #installChromiumIfMissing}) does not search {@code PATH}
   * on its own - it requires {@code PLAYWRIGHT_NODEJS_PATH} or the {@code playwright.nodejs.path}
   * system property to already point at a Node.js binary, or it fails with "Failed to find the
   * bundled Node.js." {@code PLAYWRIGHT_NODEJS_PATH} is honored first (an explicit override), then
   * {@code PATH} is searched the same way a shell would.
   */
  private static String resolveSystemNodePath() throws IOException, InterruptedException {
    String fromEnv = System.getenv("PLAYWRIGHT_NODEJS_PATH");
    if (fromEnv != null && !fromEnv.isBlank()) {
      return fromEnv;
    }
    Process which = new ProcessBuilder("which", "node").start();
    String output = new String(which.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    if (which.waitFor() != 0 || output.isEmpty()) {
      throw new IllegalStateException(
          "No system Node.js found on PATH and PLAYWRIGHT_NODEJS_PATH is not set. "
              + "PlatformEditorMutateButtonsTest needs a Node.js install to run Playwright's driver "
              + "(the vendored jar is the small driver artifact, not driver-bundle - see CONTRIBUTING.md). "
              + "Install Node.js or set PLAYWRIGHT_NODEJS_PATH to an existing install.");
    }
    return output;
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

  @BeforeEach
  void newPage() {
    page = browser.newPage();
    page.setContent(FIXTURE_HTML);
    page.addStyleTag(new Page.AddStyleTagOptions().setPath(PLATFORM_EDITOR_CSS.toPath()));
  }

  @AfterEach
  void closePage() {
    if (page != null) {
      page.close();
    }
  }

  @Test
  void widgetButtonStaysClickableWhenHoverBubblesFromAShortFlushColumn() {
    page.locator("#widget").hover();

    assertEquals("none", computedDisplay("#section-btns"),
        "section's toolbar should be suppressed while a descendant widget is hovered");
    assertEquals("none", computedDisplay("#column-btns"),
        "column's toolbar should be suppressed while its widget is hovered");
    assertTrue(resolvesToItself("#btn-widget-remove"),
        "the widget's own button should be the element actually hit at its own position, not an ancestor's");
  }

  @Test
  void columnButtonStaysClickableWhenHoveredOutsideAnyWidget() {
    page.locator("#col-gap").hover();

    assertEquals("none", computedDisplay("#section-btns"),
        "section's toolbar should be suppressed while its column is hovered");
    assertEquals("none", computedDisplay("#widget-btns"),
        "widget's toolbar should stay hidden - it is not the hovered element");
    assertTrue(resolvesToItself("#btn-column-remove"),
        "the column's own button should be the element actually hit at its own position, not an ancestor's");
  }

  private String computedDisplay(String selector) {
    return (String) page.locator(selector).evaluate("el => getComputedStyle(el).display");
  }

  /** True only if hit-testing the button's own rendered position resolves to that exact button. */
  private boolean resolvesToItself(String selector) {
    return (boolean) page.locator(selector).evaluate(
        "el => { const r = el.getBoundingClientRect();" +
        " const hit = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);" +
        " return hit === el; }");
  }

  /**
   * Playwright's driver is a Node.js program; the vendored driver jar (not driver-bundle, which
   * embeds a Node.js runtime per platform but exceeds GitHub's 100MB file-size limit) requires a
   * system Node.js install to run it, and does not embed any browser - Chromium itself (~150MB+)
   * is downloaded on first use instead of vendored. {@code CLI.main} itself calls {@code
   * System.exit} when run in-process, so it is invoked here as a genuine child process instead of
   * calling it directly, to avoid killing the test JVM once it returns.
   */
  private static void installChromiumIfMissing(String nodePath) throws IOException, InterruptedException {
    String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    Process process = new ProcessBuilder(javaBin, "-Dplaywright.nodejs.path=" + nodePath,
        "-cp", System.getProperty("java.class.path"),
        "com.microsoft.playwright.CLI", "install", "chromium")
        .inheritIO()
        .start();
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IllegalStateException("Playwright chromium install failed with exit code " + exitCode);
    }
  }
}
