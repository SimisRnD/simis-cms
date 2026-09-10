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

package com.simisinc.platform.presentation.widgets.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;

/**
 * Regression guard for site-stats-table.jsp's auto-refresh: it must give up after a fixed number of
 * consecutive failures and say so, instead of retrying on a timer for as long as the tab is open.
 *
 * <p><b>Context (issue #1920):</b> the widget bakes this session's form token into the poll URL at
 * render time, and several admin widgets call {@code UserSession.renewFormToken()} as they render.
 * Visiting any of those pages therefore leaves an already-open analytics tab holding a token
 * PageServlet refuses, and every subsequent poll fails permanently. The old {@code fail()} handler
 * rescheduled unconditionally, so that state produced one request every 30 seconds forever, with
 * nothing shown to the person who left the tab open -- 1,265 consecutive failures from a single
 * browser over 11 hours in the production access logs.
 *
 * <p>The widget's real script is lifted out of the JSP rather than restated here, so this fails if
 * the fix is edited away. Only the surrounding DOM is mirrored, and the ids the script reaches for
 * are asserted against the JSP source so the mirror cannot drift silently. Timers are stubbed and
 * advanced by hand -- the intervals under test are 10 and 30 seconds -- while the requests
 * themselves are real, answered by route interception, so jQuery's own failure path runs.
 *
 * @author elizabeth houser
 */
class SiteStatsTablePollingTest {

  private static final File JSP = new File("src/main/webapp/WEB-INF/jsp/admin/site-stats-table.jsp");
  private static final File JQUERY = new File("src/main/webapp/javascript/jquery-3.7.1/jquery.min.js");
  private static final File FOUNDATION_CSS = new File("src/main/webapp/css/foundation-6.8.1/foundation.min.css");

  /** Matches the production instance the access logs caught: the 9th widget on /admin/content/analytics. */
  private static final String UNIQUE_ID = "siteStats9";
  private static final String PAGE_URL = "https://site-stats.test/admin/content/analytics";
  private static final String POLL_URL_MARKER = "action=get";
  private static final int ALWAYS_FAIL = Integer.MAX_VALUE;

  private static String widgetScript;
  private static String jquery;
  private static String foundationCss;
  private static Playwright playwright;
  private static Browser browser;

  private Page page;
  private final List<String> pollRequests = Collections.synchronizedList(new ArrayList<>());

  @BeforeAll
  static void launchBrowser() throws IOException, InterruptedException {
    assertTrue(JSP.isFile(), "site-stats-table.jsp not found (run from the project root): " + JSP.getAbsolutePath());
    String jsp = Files.readString(JSP.toPath(), StandardCharsets.UTF_8);
    widgetScript = extractWidgetScript(jsp);
    jquery = Files.readString(JQUERY.toPath(), StandardCharsets.UTF_8);
    foundationCss = Files.readString(FOUNDATION_CSS.toPath(), StandardCharsets.UTF_8);
    String nodePath = resolveSystemNodePath();
    System.setProperty("playwright.nodejs.path", nodePath);
    installChromiumIfMissing(nodePath);
    playwright = Playwright.create();
    browser = playwright.chromium().launch();
  }

  /**
   * Lifts the widget's inline script out of the JSP and resolves the four expressions the container
   * would have resolved. The result is asserted to contain no leftover EL or JSTL: a new expression
   * added to that script would otherwise reach the browser verbatim and be tested as a syntax error
   * rather than as the widget.
   */
  private static String extractWidgetScript(String jsp) {
    assertTrue(jsp.contains("id=\"stopped${widgetContext.uniqueId}\""),
        "the JSP must render the callout id the script toggles");
    String open = "<script nonce=\"${cspNonce}\">";
    int start = jsp.indexOf(open);
    assertTrue(start >= 0, "the JSP must carry exactly one nonce'd script block");
    assertEquals(-1, jsp.indexOf(open, start + open.length()),
        "the JSP must carry exactly one nonce'd script block");
    int end = jsp.indexOf("</script>", start);
    String script = jsp.substring(start + open.length(), end);
    script = script.replace(
        "<c:out value=\"${empty currentValue ? optionsList.entrySet().toArray()[0].value : currentValue}\"/>", "12h");
    script = script.replace("${widgetContext.uniqueId}", UNIQUE_ID);
    script = script.replace("${widgetContext.uri}", "/admin/content/analytics");
    script = script.replace("${userSession.formToken}", "stale-token");
    assertFalse(script.contains("${"), "unresolved EL left in the extracted script: " + script);
    assertFalse(script.contains("<c:"), "unresolved JSTL left in the extracted script: " + script);
    return script;
  }

  /**
   * See {@code ContentSliderSwiperLoopTest#resolveSystemNodePath()} -- same requirement, duplicated
   * here rather than shared because that class keeps it private.
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
              + "SiteStatsTablePollingTest needs a Node.js install to run Playwright's driver "
              + "(the vendored jar is the small driver artifact, not driver-bundle - see CONTRIBUTING.md). "
              + "Install Node.js or set PLAYWRIGHT_NODEJS_PATH to an existing install.");
    }
    return output;
  }

  /**
   * See {@code ContentSliderSwiperLoopTest#installChromiumIfMissing(String)} -- {@code CLI.main}
   * calls {@code System.exit}, so it runs as a child process rather than in the test JVM.
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
   * The DOM site-stats-table.jsp renders for a tabbed report, plus a timer stub. Real timers are
   * replaced before the widget script runs so the 10s/30s intervals can be advanced by hand: each
   * scheduled callback is queued, and {@code __tick()} runs the next one.
   */
  private String widgetPage() {
    return "<!DOCTYPE html>\n"
        + "<html>\n"
        + "<head><style>" + foundationCss + "</style></head>\n"
        + "<body>\n"
        + "  <ul class=\"tabs\" id=\"tabs" + UNIQUE_ID + "\">\n"
        + "    <li id=\"val12h-" + UNIQUE_ID + "\" class=\"tabs-title is-active\">"
        + "<a href=\"#\" class=\"js-updateStats" + UNIQUE_ID + "\" data-value=\"12h\">Today</a></li>\n"
        + "    <li id=\"val7d-" + UNIQUE_ID + "\" class=\"tabs-title\">"
        + "<a href=\"#\" class=\"js-updateStats" + UNIQUE_ID + "\" data-value=\"7d\">7 Days</a></li>\n"
        + "  </ul>\n"
        + "  <div id=\"stats" + UNIQUE_ID + "\">\n"
        + "    <table class=\"unstriped\" id=\"table" + UNIQUE_ID + "\">\n"
        + "      <thead><tr><th>Link</th><th class=\"text-center\">Hits</th></tr></thead>\n"
        + "      <tbody><tr><td>/original</td><td class=\"text-center\">1</td></tr></tbody>\n"
        + "    </table>\n"
        + "    <div class=\"callout radius warning\" id=\"stopped" + UNIQUE_ID + "\" role=\"alert\" hidden>\n"
        + "      <p class=\"text-center\">Live updates stopped.</p>\n"
        + "    </div>\n"
        + "  </div>\n"
        + "  <script>" + jquery + "</script>\n"
        + "  <script>\n"
        + "    window.__scheduled = [];\n"
        + "    window.setInterval = function (fn, ms) { window.__scheduled.push({ fn: fn, ms: ms }); "
        + "return window.__scheduled.length; };\n"
        + "    window.clearInterval = function () { window.__scheduled.length = 0; };\n"
        + "    window.__tick = function () {\n"
        + "      var next = window.__scheduled.shift();\n"
        + "      if (!next) { return false; }\n"
        + "      next.fn();\n"
        + "      return true;\n"
        + "    };\n"
        + "  </script>\n"
        + "  <script>" + widgetScript + "</script>\n"
        + "</body>\n"
        + "</html>\n";
  }

  /**
   * Serves the page and answers the widget's polls. {@code failuresBeforeSuccess} polls get the 404
   * PageServlet returns for a token it will not accept; anything after that gets real data.
   */
  private void serve(int failuresBeforeSuccess) {
    String html = widgetPage();
    page.route("**/*", route -> {
      String url = route.request().url();
      if (url.contains(POLL_URL_MARKER)) {
        pollRequests.add(url);
        if (pollRequests.size() <= failuresBeforeSuccess) {
          route.fulfill(new Route.FulfillOptions().setStatus(404).setContentType("text/html").setBody("Not Found"));
        } else {
          route.fulfill(new Route.FulfillOptions().setStatus(200).setContentType("application/json")
              .setBody("[{\"label\":\"/refreshed\",\"value\":\"7\"}]"));
        }
        return;
      }
      route.fulfill(new Route.FulfillOptions().setContentType("text/html").setBody(html));
    });
    page.navigate(PAGE_URL);
  }

  /** Waits for the widget's next refresh to be queued, i.e. for the poll before it to have settled. */
  private void awaitScheduledRefresh() {
    page.waitForFunction("() => window.__scheduled.length === 1");
  }

  /** Runs the queued refresh callback, standing in for the interval firing. Returns false if none is queued. */
  private boolean tick() {
    return (boolean) page.evaluate("() => window.__tick()");
  }

  /**
   * Asks the browser, not the markup: {@code hidden} only hides the callout because Foundation's
   * bundled normalize declares {@code [hidden]{display:none}} and nothing in .callout overrides it.
   */
  private static String CALLOUT_IS_VISIBLE = "() => getComputedStyle(document.getElementById('stopped%s'))"
      + ".display !== 'none'";

  private void awaitStoppedCallout() {
    page.waitForFunction(String.format(CALLOUT_IS_VISIBLE, UNIQUE_ID));
  }

  private boolean stoppedCalloutIsVisible() {
    return (boolean) page.evaluate(String.format(CALLOUT_IS_VISIBLE, UNIQUE_ID));
  }

  private int scheduledTimers() {
    return (int) page.evaluate("() => window.__scheduled.length");
  }

  @Test
  void pollingStopsAfterThreeConsecutiveFailures() {
    serve(ALWAYS_FAIL);

    assertFalse(stoppedCalloutIsVisible(), "nothing should be reported before the first poll runs");

    // The first poll is the one a range tab triggers; every later one is the interval firing
    page.evaluate("() => query" + UNIQUE_ID + "('12h')");
    awaitScheduledRefresh();
    assertTrue(tick(), "a retry should be scheduled after 1 failure");
    awaitScheduledRefresh();
    assertTrue(tick(), "a retry should be scheduled after 2 failures");
    awaitStoppedCallout();

    assertEquals(0, scheduledTimers(), "the third consecutive failure must not schedule another poll");
    assertFalse(tick(), "no timer should remain to fire");
    assertEquals(3, pollRequests.size(), "polling must stop at the failure budget, not keep retrying");
  }

  @Test
  void aRecoveredPollRestoresTheFullBudget() {
    serve(2);

    page.evaluate("() => query" + UNIQUE_ID + "('12h')");
    awaitScheduledRefresh();
    tick();
    awaitScheduledRefresh();
    tick();
    // The third poll succeeds, so nothing is reported and the widget keeps refreshing
    page.waitForFunction("() => document.querySelector('#table" + UNIQUE_ID + "').textContent.includes('/refreshed')");

    assertEquals(3, pollRequests.size(), "the widget should have polled three times");
    assertFalse(stoppedCalloutIsVisible(), "a recovered widget must not claim updates have stopped");
    assertEquals(1, scheduledTimers(), "a successful poll must schedule the next refresh");
  }

  @Test
  void choosingARangeRestartsAWidgetThatGaveUp() {
    serve(3);

    page.evaluate("() => query" + UNIQUE_ID + "('12h')");
    awaitScheduledRefresh();
    tick();
    awaitScheduledRefresh();
    tick();
    awaitStoppedCallout();

    // A deliberate retry: the 4th poll succeeds, so the callout clears and refreshing resumes
    page.click(".js-updateStats" + UNIQUE_ID + "[data-value='7d']");
    awaitScheduledRefresh();

    assertEquals(4, pollRequests.size(), "the tab click should have polled once more");
    assertFalse(stoppedCalloutIsVisible(), "picking a range must clear the stopped state");
  }
}
