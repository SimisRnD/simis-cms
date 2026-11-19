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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Renders wiki mermaid diagrams the way wiki-page.jsp does -- the vendored bundle, the page's own
 * nonce'd script, securityLevel 'strict' -- under PageServlet's script-src and style-src, and
 * records what the browser refuses.
 *
 * <p>Written for the 10.9.6 to 12.3.0 upgrade (issue #1121). A vendored bundle can change in ways
 * no Java test sees, and a policy refusal fails nothing on its own: the browser quietly drops what
 * it refuses. So this checks that a flowchart, sequence, class and state diagram all still render,
 * that the refusals are only the two kinds of inline style 10.9.6 already had, that a script in a
 * label is stripped, and that the bundle carries a DOMPurify with the #1121 fix.
 *
 * @author SimIS Inc.
 */
class MermaidStrictCspTest {

  private static final Path WEBAPP = Path.of("src/main/webapp").toAbsolutePath().normalize();
  private static final String MERMAID_JS = "/javascript/mermaid-12.3.0/mermaid.min.js";
  private static final String NONCE = "mermaid-strict-csp-test";

  private static final String POLICY = "base-uri 'self'; object-src 'none'; frame-ancestors 'self'; "
      + "style-src 'self' 'nonce-" + NONCE + "' 'report-sample'; font-src 'self'; img-src 'self' data:; "
      + "script-src 'self' 'nonce-" + NONCE + "' 'report-sample'";

  private static final Map<String, String> CONTENT_TYPES = Map.of(
      ".js", "application/javascript",
      ".css", "text/css");

  private static final String WIKI_PAGE = """
      <!doctype html>
      <html><head><meta charset="utf-8">
      <script nonce="%2$s">
        window.cspViolations = [];
        document.addEventListener('securitypolicyviolation', function (e) {
          window.cspViolations.push(e.violatedDirective + ' | ' + e.blockedURI + ' | ' + (e.sample || ''));
        });
      </script>
      <script src="%1$s"></script>
      <script nonce="%2$s">
        mermaid.initialize({ startOnLoad: false, securityLevel: 'strict' });
        document.addEventListener('DOMContentLoaded', function () {
          document.querySelectorAll('pre > code.language-mermaid').forEach(function (code) {
            var diagram = document.createElement('pre');
            diagram.className = 'mermaid';
            diagram.textContent = code.textContent;
            code.parentElement.replaceWith(diagram);
          });
          mermaid.run().then(function () { window.mermaidDone = 'ok'; },
              function (e) { window.mermaidDone = 'error: ' + e; });
        });
      </script>
      </head><body>
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
      </body></html>
      """.formatted(MERMAID_JS, NONCE);

  private static HttpServer server;
  private static String origin;
  private static Playwright playwright;
  private static Browser browser;
  private Page page;
  private final List<String> consoleRefusals = new ArrayList<>();
  private final List<String> pageErrors = new ArrayList<>();

  @BeforeAll
  static void startServerAndBrowser() throws IOException, InterruptedException {
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

  @BeforeEach
  void openWikiPage() {
    page = browser.newPage();
    page.onConsoleMessage(message -> {
      if (message.text().contains("Content Security Policy")) {
        consoleRefusals.add(message.text());
      }
    });
    page.onPageError(pageErrors::add);
    page.navigate(origin + "/wiki.html");
    waitUntil("window.mermaidDone !== undefined");
  }

  private void waitUntil(String expression) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (!Boolean.TRUE.equals(page.evaluate(expression))) {
      assertTrue(System.nanoTime() < deadline, "timed out waiting for " + expression);
      page.waitForTimeout(50);
    }
  }

  @AfterEach
  void closePage() {
    if (page != null) {
      page.close();
    }
  }

  @Test
  void everyDiagramRendersUnderTheSitesPolicy() {
    assertEquals("ok", page.evaluate("window.mermaidDone"));
    assertEquals(List.of(), pageErrors);
    List<?> labels = (List<?>) page.evaluate(
        "Array.from(document.querySelectorAll('pre.mermaid svg')).map(function (svg) { return svg.textContent; })");
    assertEquals(5, labels.size(), "every fence becomes an SVG");
    String[] expected = {"Start", "Alice", "Duck", "Moving", "Safe"};
    for (int i = 0; i < expected.length; i++) {
      assertTrue(String.valueOf(labels.get(i)).contains(expected[i]), "diagram " + i + " is missing " + expected[i]);
    }
  }

  /**
   * Mermaid styles its diagrams with a style element inside each SVG and with style attributes,
   * and the site's style-src refuses both, so on the live site diagrams draw with the browser's
   * default fill. 10.9.6 does exactly the same (12 style elements and 145 attributes for these
   * diagrams, against 12.3.0's 12 and 237); that is a defect of its own, not this upgrade's. This
   * test holds the upgrade to those two kinds of refusal: anything else -- a script, an eval, a
   * font or image source -- is new and fails it.
   */
  @Test
  void theOnlyRefusalsAreTheInlineStylesMermaidHasAlwaysUsed() {
    List<?> violations = (List<?>) page.evaluate("window.cspViolations");
    List<String> unexpected = new ArrayList<>();
    for (Object violation : violations) {
      String text = String.valueOf(violation);
      if (!text.startsWith("style-src-attr | inline") && !text.startsWith("style-src-elem | inline")) {
        unexpected.add(text);
      }
    }
    assertEquals(List.of(), unexpected);
    // Every refusal reached the page-level listener; none came from a frame it cannot see
    assertEquals(violations.size(), consoleRefusals.size());
  }

  /** The strict security level runs labels through mermaid's bundled DOMPurify. */
  @Test
  void aScriptInALabelIsStrippedBeforeItReachesThePage() {
    assertEquals(0, ((Number) page.evaluate(
        "document.querySelectorAll('pre.mermaid svg [onerror], pre.mermaid svg script').length")).intValue());
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

  private static void serve(HttpExchange exchange) throws IOException {
    try (exchange) {
      String path = exchange.getRequestURI().getPath();
      byte[] body;
      String contentType;
      if ("/wiki.html".equals(path)) {
        body = WIKI_PAGE.getBytes(StandardCharsets.UTF_8);
        contentType = "text/html; charset=utf-8";
        exchange.getResponseHeaders().set("Content-Security-Policy", POLICY);
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

  private static String resolveSystemNodePath() throws IOException, InterruptedException {
    String fromEnv = System.getenv("PLAYWRIGHT_NODEJS_PATH");
    if (fromEnv != null && !fromEnv.isBlank()) {
      return fromEnv;
    }
    Process which = new ProcessBuilder("which", "node").start();
    String output = new String(which.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    if (which.waitFor() != 0 || output.isEmpty()) {
      throw new IllegalStateException("No system Node.js found on PATH and PLAYWRIGHT_NODEJS_PATH is not set.");
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
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IllegalStateException("Playwright chromium install failed with exit code " + exitCode);
    }
  }
}
