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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Renders wiki mermaid diagrams with wiki-page.jsp's own script -- read from the JSP, so this tests
 * what ships -- under PageServlet's script-src and style-src, and compares them with the same page
 * rendered under no policy at all.
 *
 * <p>Mermaid styles a diagram with a style element inside the SVG and style attributes on its
 * shapes, and the site's style-src refuses both. A refusal fails nothing on its own: the browser
 * quietly drops the style, and the diagram draws in its default black, laid out from text measured
 * unstyled. That is how the diagrams shipped from #1999 until the page's script inserted them the
 * way the policy allows. So this checks the result, not just the absence of errors: every diagram
 * renders, looks and measures exactly as it does with no policy, and nothing on the page is refused.
 * It also holds the #1121 fix (a script in a label is stripped, and the bundle's DOMPurify is at
 * least 3.4.13).
 *
 * @author SimIS Inc.
 */
class MermaidStrictCspTest {

  private static final Path WEBAPP = Path.of("src/main/webapp").toAbsolutePath().normalize();
  private static final Path WIKI_JSP = WEBAPP.resolve("WEB-INF/jsp/cms/wiki-page.jsp");
  private static final String MERMAID_JS = "/javascript/mermaid-12.3.0/mermaid.min.js";
  private static final String NONCE = "mermaid-strict-csp-test";

  private static final String POLICY = "base-uri 'self'; object-src 'none'; frame-ancestors 'self'; "
      + "style-src 'self' 'nonce-" + NONCE + "' 'report-sample'; font-src 'self'; img-src 'self' data:; "
      + "script-src 'self' 'nonce-" + NONCE + "' 'report-sample'";

  private static final Map<String, String> CONTENT_TYPES = Map.of(
      ".js", "application/javascript",
      ".css", "text/css");

  /** The fences the markdown renderer emits; the last one is not valid mermaid. */
  private static final String FENCES = """
      <pre><code class="language-mermaid">graph TD
        A[Start] --> B{Is it?}
        B -->|Yes| C[OK]
        B -->|No| D[Not OK]</code></pre>
      <pre><code class="language-mermaid">sequenceDiagram
        Alice->>Bob: Hello Bob
        Bob-->>Alice: Hi Alice</code></pre>
      <pre><code class="language-mermaid">classDiagram
        Animal <|-- Duck
        Animal : +int age</code></pre>
      <pre><code class="language-mermaid">stateDiagram-v2
        [*] --> Still
        Still --> Moving
        Moving --> [*]</code></pre>
      <pre><code class="language-mermaid">graph TD
        X["&lt;img src=x onerror=window.pwned=1&gt;Payload"] --> Y[Safe]</code></pre>
      <pre><code class="language-mermaid">this is not a diagram --></code></pre>
      """;

  private static final int DIAGRAMS = 5;

  /** What a reader sees of each diagram: its size, its node boxes, and the colors and font it draws in. */
  private static final String APPEARANCE = """
      Array.from(document.querySelectorAll('.mermaid svg')).map(function (svg) {
        var box = svg.getBoundingClientRect();
        var node = svg.querySelector('.node rect, .node polygon, rect.actor, .node path');
        var label = svg.querySelector('.nodeLabel, text');
        return {
          size: Math.round(box.width) + 'x' + Math.round(box.height),
          nodes: Array.from(svg.querySelectorAll('.node, rect.actor')).map(function (n) {
            var b = n.getBBox();
            return Math.round(b.width) + 'x' + Math.round(b.height);
          }).join(' '),
          fill: node ? getComputedStyle(node).fill + ' / ' + getComputedStyle(node).stroke : '',
          font: label ? getComputedStyle(label).fontFamily + ' ' + getComputedStyle(label).fontSize : ''
        };
      })
      """;

  private static String wikiScript;
  private static HttpServer server;
  private static String origin;
  private static Playwright playwright;
  private static Browser browser;
  private final List<Page> pages = new ArrayList<>();
  private final List<String> pageErrors = new ArrayList<>();

  @BeforeAll
  static void startServerAndBrowser() throws IOException, InterruptedException {
    // The page's own script, so a change to it is what this test runs
    Matcher script = Pattern.compile("<script nonce=\"\\$\\{cspNonce\\}\">(.*?mermaid\\.initialize.*?)</script>",
        Pattern.DOTALL).matcher(Files.readString(WIKI_JSP, StandardCharsets.UTF_8));
    assertTrue(script.find(), "no mermaid script in " + WIKI_JSP);
    wikiScript = script.group(1);

    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", MermaidStrictCspTest::serve);
    server.start();
    origin = "http://127.0.0.1:" + server.getAddress().getPort();

    String nodePath = resolveSystemNodePath();
    System.setProperty("playwright.nodejs.path", nodePath);
    installChromiumIfMissing(nodePath);
    playwright = Playwright.create();
    browser = playwright.chromium().launch();
  }

  @AfterAll
  static void stopServerAndBrowser() {
    if (browser != null) {
      browser.close();
    }
    if (playwright != null) {
      playwright.close();
    }
    if (server != null) {
      server.stop(0);
    }
  }

  @AfterEach
  void closePages() {
    pages.forEach(Page::close);
  }

  /** Opens the wiki page, under the site's policy or under none, and waits for its diagrams. */
  private Page open(boolean underPolicy) {
    Page page = browser.newPage();
    pages.add(page);
    page.onPageError(pageErrors::add);
    page.navigate(origin + (underPolicy ? "/wiki.html" : "/wiki-without-policy.html"));
    waitUntil(page, "document.querySelectorAll('.mermaid svg').length === " + DIAGRAMS);
    return page;
  }

  /**
   * Waits for {@code expression} in the page. Playwright's waitForFunction would compile it inside
   * the page when it polls, which this page's script-src refuses as eval; page.evaluate does not.
   */
  private static void waitUntil(Page page, String expression) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (!Boolean.TRUE.equals(page.evaluate(expression))) {
      assertTrue(System.nanoTime() < deadline, "timed out waiting for " + expression);
      page.waitForTimeout(50);
    }
  }

  @Test
  void everyDiagramRendersAndOneMermaidCannotParseStaysAsItsSource() {
    Page page = open(true);
    List<?> labels = (List<?>) page.evaluate(
        "Array.from(document.querySelectorAll('.mermaid svg')).map(function (svg) { return svg.textContent; })");
    String[] expected = {"Start", "Alice", "Duck", "Moving", "Safe"};
    for (int i = 0; i < expected.length; i++) {
      assertTrue(String.valueOf(labels.get(i)).contains(expected[i]), "diagram " + i + " is missing " + expected[i]);
    }
    assertEquals("this is not a diagram -->", page.evaluate(
        "Array.from(document.querySelectorAll('pre > code.language-mermaid')).map(function (c) { return c.textContent; }).join('|')"));
    assertEquals(List.of(), pageErrors);
  }

  /**
   * The point of the page's script: under the policy, a diagram looks and measures exactly as it
   * does with no policy. Before it, the same comparison gave black shapes with no outline, nodes
   * 70px tall instead of 45, and SVGs at the page's full width.
   */
  @Test
  void diagramsLookAndMeasureAsTheyDoWithNoPolicy() {
    Object underPolicy = open(true).evaluate(APPEARANCE);
    Object withoutPolicy = open(false).evaluate(APPEARANCE);
    assertEquals(withoutPolicy, underPolicy);
    Map<?, ?> flowchart = (Map<?, ?>) ((List<?>) underPolicy).get(0);
    assertNotEquals("rgb(0, 0, 0) / none", flowchart.get("fill"), "the flowchart is drawn in the default black");
  }

  /**
   * Nothing in the page is refused. Parsing mermaid's markup still reports the style attributes it
   * meets -- mermaid's own DOMPurify pass and the page's script each parse it -- but the browser
   * reports those against the detached document they were parsed in, not against anything shown.
   */
  @Test
  void nothingOnThePageIsRefused() {
    Page page = open(true);
    List<?> violations = (List<?>) page.evaluate("window.cspViolations");
    List<String> onThePage = new ArrayList<>();
    for (Object violation : violations) {
      String text = String.valueOf(violation);
      if (!text.startsWith("document ")) {
        onThePage.add(text);
      }
      assertTrue(text.contains("style-src-attr") || text.contains("style-src-elem"), "unexpected refusal: " + text);
    }
    assertEquals(List.of(), onThePage);
  }

  /** The script swaps a few DOM methods while mermaid renders; they must be the browser's again after. */
  @Test
  void theDomMethodsAreRestoredAfterRendering() {
    Page page = open(true);
    assertEquals(Boolean.TRUE, page.evaluate("[Element.prototype.setAttribute, Document.prototype.createElement,"
        + " Document.prototype.createElementNS].every(function (f) { return /\\[native code\\]/.test(String(f)); })"));
  }

  /** The strict security level runs labels through mermaid's bundled DOMPurify. */
  @Test
  void aScriptInALabelIsStrippedBeforeItReachesThePage() {
    Page page = open(true);
    assertEquals(0, ((Number) page.evaluate(
        "document.querySelectorAll('.mermaid svg [onerror], .mermaid svg script').length")).intValue());
    assertEquals(Boolean.TRUE, page.evaluate("window.pwned === undefined"));
  }

  /**
   * Issue #1121: the served bundle embeds its own DOMPurify, so package.json and the lockfile can
   * say a fixed version while browsers still get the vulnerable one -- which is how 10.9.6 shipped
   * DOMPurify 3.4.2 for months. GHSA-55q2-fjhq-7xh7 is fixed in DOMPurify 3.4.13.
   */
  @Test
  void theServedBundleCarriesAFixedDomPurify() throws IOException {
    String bundle = Files.readString(WEBAPP.resolve(MERMAID_JS.substring(1)), StandardCharsets.UTF_8);
    Matcher version = Pattern.compile("DOMPurify (\\d+)\\.(\\d+)\\.(\\d+)").matcher(bundle);
    assertTrue(version.find(), "no DOMPurify version banner in " + MERMAID_JS);
    int[] found = {Integer.parseInt(version.group(1)), Integer.parseInt(version.group(2)), Integer.parseInt(version.group(3))};
    int[] fixed = {3, 4, 13};
    assertTrue(Arrays.compare(found, fixed) >= 0, MERMAID_JS + " bundles DOMPurify " + version.group(1) + "."
        + version.group(2) + "." + version.group(3) + ", which is below 3.4.13 (GHSA-55q2-fjhq-7xh7)");
  }

  private static String wikiPage() {
    return "<!doctype html><html><head><meta charset=\"utf-8\">"
        + "<script nonce=\"" + NONCE + "\">"
        + "window.cspViolations = [];"
        + "document.addEventListener('securitypolicyviolation', function (e) {"
        + "  window.cspViolations.push((e.target === document ? 'document ' : 'element ') + e.violatedDirective"
        + "      + ' | ' + (e.sample || ''));"
        + "});"
        + "</script>"
        + "<script src=\"" + MERMAID_JS + "\"></script>"
        + "<script nonce=\"" + NONCE + "\">" + wikiScript + "</script>"
        + "</head><body>" + FENCES + "</body></html>";
  }

  private static void serve(HttpExchange exchange) throws IOException {
    try (exchange) {
      String path = exchange.getRequestURI().getPath();
      byte[] body;
      String contentType;
      if ("/wiki.html".equals(path) || "/wiki-without-policy.html".equals(path)) {
        body = wikiPage().getBytes(StandardCharsets.UTF_8);
        contentType = "text/html; charset=utf-8";
        if ("/wiki.html".equals(path)) {
          exchange.getResponseHeaders().set("Content-Security-Policy", POLICY);
        }
      } else {
        Path file = WEBAPP.resolve(path.substring(1)).normalize();
        if (!file.startsWith(WEBAPP) || !Files.isRegularFile(file)) {
          exchange.sendResponseHeaders(404, -1);
          return;
        }
        body = Files.readAllBytes(file);
        String name = file.getFileName().toString();
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
        contentType = CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
      }
      exchange.getResponseHeaders().set("Content-Type", contentType);
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    }
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
              + "MermaidStrictCspTest needs a Node.js install to run Playwright's driver.");
    }
    return output;
  }

  /**
   * See {@link ContentSliderSwiperLoopTest} -- Playwright's driver needs a system Node.js, and
   * Chromium is downloaded on first use rather than vendored. {@code CLI.main} calls
   * {@code System.exit}, so it runs as a child process.
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
