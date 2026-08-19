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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * Regression guard for the contentSlider widget's loop=true rendering (content-card-slider.jsp,
 * backed by vendored Swiper 12.1.2): the active slide must always fall within the visible
 * container, never translated a slide-width off to the side.
 *
 * <p><b>Context (see issue and PR description for the full writeup):</b> a reported production
 * bug had loop=true stranding the active slide outside the viewport on a live page with 6 cards,
 * fixed on that page by turning loop off. Tracing Swiper's source turned up a real, documented
 * gap: {@code loopCreate}/{@code loopFix} compute the loop position exactly once, synchronously,
 * inside {@code new Swiper(...)}, from whatever width the container measures at that instant, and
 * Swiper never re-validates that math itself afterward -- only its ResizeObserver-driven {@code
 * onResize()} path does, and critically it's the only place that calls the loop-aware {@code
 * slideToLoop()} (the generic {@code update()} does not special-case loop mode the same way).
 * That divergence is real, but multiple attempts to force it locally (container hidden at init,
 * a breakpoint-crossing viewport resize, slidesPerView equal to the total slide count) all
 * self-healed correctly via Swiper's own onResize -- so the exact trigger on the live page was
 * not pinned down. The fix applied is the defensive, low-risk hardening this tracing points to:
 * force the same correction {@code onResize()} performs (update() + slideToLoop(realIndex, 0,
 * false)) once on window load, so a stale initial measurement -- whatever causes it -- can't
 * strand the active slide indefinitely. This test is a baseline invariant guard for that
 * behavior, not a reproduction of the original failure.
 *
 * @author elizabeth houser
 */
class ContentSliderSwiperLoopTest {

  private static final File SWIPER_JS = new File("src/main/webapp/javascript/swiper-12.1.2/swiper-bundle.js");
  private static final File SWIPER_CSS = new File("src/main/webapp/javascript/swiper-12.1.2/swiper-bundle.css");

  private static String swiperJs;
  private static String swiperCss;
  private static Playwright playwright;
  private static Browser browser;
  private Page page;

  @BeforeAll
  static void launchBrowser() throws IOException, InterruptedException {
    assertTrue(SWIPER_JS.isFile(), "swiper-bundle.js not found (run from the project root): " + SWIPER_JS.getAbsolutePath());
    swiperJs = Files.readString(SWIPER_JS.toPath(), StandardCharsets.UTF_8);
    swiperCss = Files.readString(SWIPER_CSS.toPath(), StandardCharsets.UTF_8);
    String nodePath = resolveSystemNodePath();
    System.setProperty("playwright.nodejs.path", nodePath);
    installChromiumIfMissing(nodePath);
    playwright = Playwright.create();
    browser = playwright.chromium().launch();
  }

  /**
   * See {@link PlatformEditorMutateButtonsTest#resolveSystemNodePath()} -- same requirement,
   * duplicated here rather than shared because that class keeps it private.
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
              + "ContentSliderSwiperLoopTest needs a Node.js install to run Playwright's driver "
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
  }

  @AfterEach
  void closePage() {
    if (page != null) {
      page.close();
    }
  }

  /**
   * Mirrors content-card-slider.jsp's rendered DOM and inline-script shape for a loop=true
   * instance with 6 cards -- the same preferences as the reported production page (smallCardCount
   * set, mediumCardCount/largeCardCount cascading to the same value, loop=true) -- including the
   * fix's window-load correction block. The real vendored Swiper JS/CSS are inlined directly so
   * nothing depends on load ordering or file/network access from within the page.
   */
  @Test
  void activeSlideStaysWithinTheVisibleContainerAfterLoad() {
    StringBuilder slides = new StringBuilder();
    for (int i = 1; i <= 6; i++) {
      slides.append("<div class=\"swiper-slide\"><div>Slide ").append(i).append("</div></div>\n");
    }
    String html = "<!DOCTYPE html>\n"
        + "<html>\n"
        + "<head><style>" + swiperCss + "</style></head>\n"
        + "<body style=\"margin:0\">\n"
        + "  <div class=\"swiper-outer-container\">\n"
        + "    <div id=\"swiper-test\" class=\"swiper\">\n"
        + "      <div class=\"swiper-wrapper\">\n"
        + slides
        + "      </div>\n"
        + "    </div>\n"
        + "    <div id=\"swiper-pagination-test\" class=\"swiper-pagination\"></div>\n"
        + "  </div>\n"
        + "  <script>" + swiperJs + "</script>\n"
        + "  <script>\n"
        + "    var sw = new Swiper(\"#swiper-test\", {\n"
        + "      slidesPerView: 1,\n"
        + "      centerInsufficientSlides: true,\n"
        + "      spaceBetween: 15,\n"
        + "      breakpoints: {\n"
        + "        640: { slidesPerView: 1, spaceBetween: 15 },\n"
        + "        1024: { slidesPerView: 1, spaceBetween: 15 }\n"
        + "      },\n"
        + "      loop: true,\n"
        + "      pagination: { el: '#swiper-pagination-test' }\n"
        + "    });\n"
        + "    window.__sw = sw;\n"
        // Mirrors the window-load correction content-card-slider.jsp adds when loop=true.
        + "    window.addEventListener('load', function () {\n"
        + "      var s = window.__sw;\n"
        + "      if (!s || s.destroyed) { return; }\n"
        + "      s.update();\n"
        + "      s.slideToLoop(s.realIndex, 0, false);\n"
        + "    });\n"
        + "  </script>\n"
        + "</body>\n"
        + "</html>\n";

    page.setContent(html);
    page.evaluate("window.dispatchEvent(new Event('load'))");

    assertTrue(activeSlideIsVisibleWithinContainer(), "the active slide should always render within the "
        + "visible swiper container, never translated a slide-width off to the side");
  }

  /** True only if the active slide's rendered rect actually falls within the container's visible bounds. */
  private boolean activeSlideIsVisibleWithinContainer() {
    return (boolean) page.evaluate("""
        () => {
          var sw = window.__sw;
          var active = sw.slides[sw.activeIndex];
          var slideRect = active.getBoundingClientRect();
          var containerRect = document.getElementById('swiper-test').getBoundingClientRect();
          return slideRect.left < containerRect.right && slideRect.right > containerRect.left;
        }
        """);
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
