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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * The vendored TinyMCE must load and edit under the style-src and script-src PageServlet enforces,
 * which allow no inline style attribute and no un-nonced {@code <style>} or script (issue #1999).
 * <p>The vendored copy carries one local change for this: the caret marker TinyMCE inserts to
 * scroll the selection into view gets its display through the CSSOM rather than a style attribute.
 * Upstream still writes the attribute, so every TinyMCE upgrade has to carry the change forward by
 * hand, and losing it fails nothing else: the browser refuses the style quietly, on every Enter.
 * This serves the real vendored files from {@code src/main/webapp} under that policy, drives the
 * editor through what the six editor pages use -- typing, the scroll-into-view marker, tables,
 * lists, alignment, and each plugin's dialog -- and fails on any Content-Security-Policy refusal
 * other than two known ones that 7.9.3 had as well (see {@link #RESIZE_HANDLE_REFUSAL} and
 * {@link #PREVIEW_SCRIPT_REFUSAL}).
 * <p>The configuration mirrors content-editor.jsp, the fullest of the six, plus insertdatetime and
 * fullscreen, which other editors load, so every plugin any of them uses is exercised, including
 * our own FontAwesome icon picker, whose vendored copy carries a local change of its own.
 *
 * @author SimIS Inc.
 */
class TinyMceStrictCspTest {

  private static final Path WEBAPP = Path.of("src/main/webapp").toAbsolutePath().normalize();
  private static final String TINYMCE_JS = "/javascript/tinymce-8.10.1/tinymce.min.js";
  private static final String NONCE = "tinymce-strict-csp-test";

  // PageServlet's style-src and script-src, verbatim apart from the nonce. 'report-sample' only adds
  // the refused snippet to each report, so a failure names what was refused; it allows nothing.
  private static final String POLICY = "base-uri 'self'; object-src 'none'; frame-ancestors 'self'; "
      + "style-src 'self' 'nonce-" + NONCE + "' 'report-sample'; font-src 'self'; img-src 'self' data:; "
      + "script-src 'self' 'nonce-" + NONCE + "' 'report-sample'";

  // TinyMCE's object-resize handles (ControlSelection in tinymce.js) are created with
  // style="cursor: nw-resize; margin: 0; padding: 0", which this policy refuses. The skin's content
  // CSS already gives each handle its cursor, and a div has no margin or padding, so the handles
  // look and work the same; 7.9.3 did this too. Tolerated by its exact shape only.
  private static final Pattern RESIZE_HANDLE_REFUSAL = Pattern.compile(
      "[a-z ]+: style-src-attr refused inline \\[cursor: (nw|ne|se|sw)-resize; margin: 0px; padding:.*");

  // The preview plugin writes its dialog as a sandboxed srcdoc frame ending in an inline <script>
  // that stops links in the preview from navigating, which this policy refuses; 7.9.3 did this
  // too. A sandboxed frame's events cannot be listened to, so this one is known only from the
  // console, by where it came from and what it was.
  private static final Pattern PREVIEW_SCRIPT_REFUSAL = Pattern.compile(
      "about:srcdoc:\\d+:\\d+ :: Executing inline script violates .*", Pattern.DOTALL);

  private static final Map<String, String> CONTENT_TYPES = Map.of(
      ".js", "application/javascript",
      ".css", "text/css",
      ".woff2", "font/woff2",
      ".woff", "font/woff",
      ".ttf", "font/ttf",
      ".svg", "image/svg+xml",
      ".png", "image/png",
      ".gif", "image/gif");

  private static final String EDITOR_PAGE = """
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <title>TinyMCE under the site CSP</title>
        <script src="%s"></script>
      </head>
      <body>
        <textarea id="content"><p>Existing paragraph</p></textarea>
        <script nonce="%s">
          window.cspViolations = [];
          function recordViolations(doc, where) {
            doc.addEventListener('securitypolicyviolation', function (e) {
              window.cspViolations.push(where + ': ' + e.violatedDirective + ' refused '
                  + (e.blockedURI || 'inline') + (e.sample ? ' [' + e.sample + ']' : ''));
            });
          }
          recordViolations(document, 'page');
          window.editorReady = false;
          tinymce.init({
            license_key: 'gpl',
            selector: '#content',
            branding: false,
            height: 400,
            menubar: false,
            relative_urls: false,
            convert_urls: true,
            content_css: ['/css/fontawesome-free-6.1.1-web/css/all.min.css', '/css/platform-editor-content.css'],
            noneditable_class: 'tinymce-noedit',
            plugins: 'advlist autolink lists link image charmap preview anchor searchreplace visualblocks code insertdatetime media table wordcount fullscreen fontawesome',
            toolbar: 'link image media table | undo redo | blocks | bold italic | alignleft aligncenter alignright alignjustify | bullist numlist outdent indent hr anchor | fontawesome removeformat visualblocks code fullscreen',
            formats: {
              alignleft: { selector: 'p,h1,h2,h3,h4,h5,h6,td,th,div,ul,li', classes: 'text-left' },
              aligncenter: { selector: 'p,h1,h2,h3,h4,h5,h6,td,th,div,ul,li', classes: 'text-center' },
              alignright: { selector: 'p,h1,h2,h3,h4,h5,h6,td,th,div,ul,li', classes: 'text-right' },
              alignjustify: { selector: 'p,h1,h2,h3,h4,h5,h6,td,th,div,ul,li', classes: 'text-justify' }
            },
            table_default_styles: {},
            table_sizing_mode: 'responsive',
            external_plugins: {
              fontawesome: '/javascript/tinymce-plugins/fontawesome/plugin.min.js'
            },
            extended_valid_elements: 'span[*]',
            file_picker_types: 'file image media',
            file_picker_callback: function () {},
            init_instance_callback: function (editor) {
              recordViolations(editor.getDoc(), 'editing frame');
              window.editorReady = true;
            }
          });
        </script>
      </body>
      </html>
      """.formatted(TINYMCE_JS, NONCE);

  private static HttpServer server;
  private static String origin;
  private static Playwright playwright;
  private static Browser browser;
  private Page page;
  private final List<String> consoleRefusals = new ArrayList<>();
  private final List<String> pageErrors = new ArrayList<>();

  @BeforeAll
  static void startServerAndBrowser() throws IOException, InterruptedException {
    assertTrue(WEBAPP.resolve(TINYMCE_JS.substring(1)).toFile().isFile(),
        "vendored TinyMCE not found (run from the project root): " + TINYMCE_JS);
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", TinyMceStrictCspTest::serve);
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
  void openEditor() {
    page = browser.newPage();
    // Console reports cover every frame, including ones a plugin creates and the page-level
    // listener cannot reach, such as the preview dialog's
    page.onConsoleMessage(message -> {
      if (message.text().contains("Content Security Policy")) {
        consoleRefusals.add(message.location() + " :: " + message.text());
      }
    });
    page.onPageError(pageErrors::add);
    page.navigate(origin + "/editor.html");
    waitUntil("window.editorReady === true");
  }

  /**
   * Waits for {@code expression} to be true in the page. Playwright's waitForFunction would do
   * this, but once its first check comes back false it polls by compiling the expression inside the
   * page, which this page's script-src refuses as eval -- so it passes on a fast machine and fails on
   * a slow one. page.evaluate is compiled by DevTools, which the page's policy does not govern.
   */
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
  void editingRaisesNoContentSecurityPolicyRefusal() {
    page.frameLocator(".tox-edit-area iframe").locator("body").click();
    page.keyboard().press("Control+End");
    page.keyboard().type("Typed after the existing paragraph");
    for (int i = 0; i < 3; i++) {
      page.keyboard().press("Enter");
    }
    page.keyboard().type("Typed after three Enters");

    page.evaluate("""
        () => {
          const editor = tinymce.activeEditor;
          // The marker is what the local change is about; asking for it directly makes sure this
          // test reaches it whatever TinyMCE decides about scrolling on Enter
          editor.selection.scrollIntoView();
          editor.execCommand('InsertUnorderedList');
          editor.execCommand('InsertUnorderedList');
          editor.execCommand('JustifyCenter');
          editor.execCommand('mceInsertTable', false, { rows: 2, columns: 2 });
          editor.execCommand('InsertHorizontalRule');
          editor.execCommand('mceInsertDate');
          editor.execCommand('mceVisualBlocks');
          editor.execCommand('mceVisualBlocks');
          editor.execCommand('mceFullScreen');
          editor.execCommand('mceFullScreen');
        }
        """);

    for (String dialogCommand : List.of("mceLink", "mceImage", "mceMedia", "mceAnchor", "mceShowCharmap",
        "SearchReplace", "mceCodeEditor", "mcePreview", "mceInsertTableDialog")) {
      page.evaluate("command => tinymce.activeEditor.execCommand(command)", dialogCommand);
      page.waitForSelector(".tox-dialog", new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE));
      page.evaluate("() => tinymce.activeEditor.windowManager.close()");
      page.waitForSelector(".tox-dialog", new Page.WaitForSelectorOptions().setState(WaitForSelectorState.DETACHED));
    }

    // Reports are queued as tasks; let any still pending arrive before reading them
    page.waitForTimeout(250);

    List<String> violations = ((List<?>) page.evaluate("() => window.cspViolations")).stream()
        .map(String::valueOf)
        .toList();
    List<String> unexpected = violations.stream()
        .filter(violation -> !RESIZE_HANDLE_REFUSAL.matcher(violation).matches())
        .toList();
    assertEquals(List.of(), unexpected, "Content-Security-Policy refusals inside the editor");
    // Every refusal is also logged to the console, from whichever frame it happened in. A console
    // report with no matching event came from a frame no listener watches, and is just as real.
    List<String> unexplainedConsole = consoleRefusals.stream()
        .filter(refusal -> !PREVIEW_SCRIPT_REFUSAL.matcher(refusal).matches())
        .toList();
    assertEquals(violations.size(), unexplainedConsole.size(),
        "refusals logged to the console but not caught by a listener: " + unexplainedConsole);
    assertEquals(List.of(), pageErrors, "uncaught errors on the editor page");

    String content = (String) page.evaluate("() => tinymce.activeEditor.getContent()");
    assertTrue(content.contains("Typed after three Enters"), content);
    assertTrue(content.contains("<table"), content);
    assertTrue(content.contains("text-center"), "alignment should write a class: " + content);
    assertFalse(content.contains("style="), "the editor should not write inline styles into content: " + content);
  }

  @Test
  void theScrollIntoViewMarkerIsStyledThroughTheCssom() {
    // A narrower guard on the local change itself, so a failure says which part regressed. The
    // marker exists only for the length of the scroll, so its style is read as it is inserted.
    // Under this policy a style attribute in the marker's HTML is refused and leaves display empty;
    // only a style set through the CSSOM reads back as inline-block.
    page.frameLocator(".tox-edit-area iframe").locator("body").click();
    page.keyboard().type("Marker check");
    Object markerDisplay = page.evaluate("""
        () => {
          const editor = tinymce.activeEditor;
          let display = null;
          editor.on('ScrollIntoView', (e) => {
            display = e.elm.style.display;
          });
          editor.selection.scrollIntoView();
          return display;
        }
        """);
    assertEquals("inline-block", markerDisplay,
        "the scroll-into-view marker should get display:inline-block through the CSSOM, not a style "
            + "attribute; re-apply the local change in the vendored tinymce.js and tinymce.min.js");
  }

  @Test
  void theIconPickerHidesItsNoResultsMessageUntilASearchFindsNothing() {
    // The message used to be hidden by a style attribute in the dialog's HTML, which this policy
    // refuses, so it showed whenever the picker opened. It is hidden by the plugin's stylesheet now,
    // which the plugin links on init; wait for it, or the message is briefly unstyled either way.
    page.waitForFunction("() => Array.from(document.styleSheets)"
        + ".some(sheet => (sheet.href || '').includes('/tinymce-plugins/fontawesome/css/fontawesome.css'))");
    page.click("button[aria-label='Insert an icon']");
    page.waitForSelector(".mce-fontawesome-search-container input");
    Locator noResults = page.locator(".mce-fontawesome-search-noresults");
    assertTrue(noResults.isHidden(), "the no-results message should be hidden when the picker opens");

    page.fill(".mce-fontawesome-search-container input", "no-icon-is-called-this");
    assertTrue(noResults.isVisible(), "a search that matches nothing should show the message");
    page.fill(".mce-fontawesome-search-container input", "");
    assertTrue(noResults.isHidden(), "clearing the search should hide the message again");

    page.waitForTimeout(250);
    assertEquals(List.of(), page.evaluate("() => window.cspViolations"),
        "Content-Security-Policy refusals in the icon picker");
    assertEquals(List.of(), consoleRefusals, "Content-Security-Policy refusals reported to the console");
    assertEquals(List.of(), pageErrors, "uncaught errors on the editor page");
  }

  private static void serve(HttpExchange exchange) throws IOException {
    try (exchange) {
      String path = exchange.getRequestURI().getPath();
      byte[] body;
      String contentType;
      if ("/editor.html".equals(path)) {
        body = EDITOR_PAGE.getBytes(StandardCharsets.UTF_8);
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
              + "TinyMceStrictCspTest needs a Node.js install to run Playwright's driver "
              + "(the vendored jar is the small driver artifact, not driver-bundle - see CONTRIBUTING.md). "
              + "Install Node.js or set PLAYWRIGHT_NODEJS_PATH to an existing install.");
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
