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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.servlet.ServletContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers {@link ContextListener#resolveAssetVersion}, the cache-busting token appended to
 * platform.css / platform-tokens.css (#1333).
 *
 * <p>The behavior that matters: the token must change when a stylesheet changes, because the whole
 * point is that a CDN or browser holding the previous build stops serving it after a deploy. It
 * must also never come back blank or malformed -- a broken query string on every page is a worse
 * outcome than a stale cache.
 */
class ContextListenerAssetVersionTest {

  @Test
  void usesTheNewestModificationTimeAmongTheAssets(@TempDir Path tempDir) throws IOException {
    File older = writeFile(tempDir, "platform.css", 1_000_000_000_000L);
    File newer = writeFile(tempDir, "platform-tokens.css", 2_000_000_000_000L);

    ServletContext ctx = mock(ServletContext.class);
    when(ctx.getRealPath("/css/platform.css")).thenReturn(older.getAbsolutePath());
    when(ctx.getRealPath("/css/platform-tokens.css")).thenReturn(newer.getAbsolutePath());

    assertEquals("2000000000000",
        ContextListener.resolveAssetVersion(ctx, "fallback", "/css/platform.css", "/css/platform-tokens.css"));
  }

  @Test
  void tokenChangesWhenAStylesheetChanges(@TempDir Path tempDir) throws IOException {
    File css = writeFile(tempDir, "platform.css", 1_500_000_000_000L);
    ServletContext ctx = mock(ServletContext.class);
    when(ctx.getRealPath("/css/platform.css")).thenReturn(css.getAbsolutePath());

    String before = ContextListener.resolveAssetVersion(ctx, "fallback", "/css/platform.css");

    // Simulate a deploy that ships an edited stylesheet
    assertEquals(true, css.setLastModified(1_600_000_000_000L));
    String after = ContextListener.resolveAssetVersion(ctx, "fallback", "/css/platform.css");

    assertFalse(before.equals(after), "a changed stylesheet must produce a different cache-busting token");
  }

  @Test
  void fallsBackWhenTheWarIsServedUnexpanded() {
    // getRealPath returns null when there is no filesystem path -- the token must still be usable
    ServletContext ctx = mock(ServletContext.class);
    when(ctx.getRealPath("/css/platform.css")).thenReturn(null);

    assertEquals("20260807.10002",
        ContextListener.resolveAssetVersion(ctx, "20260807.10002", "/css/platform.css"));
  }

  @Test
  void fallsBackWhenThePathResolvesButTheFileIsMissing(@TempDir Path tempDir) {
    ServletContext ctx = mock(ServletContext.class);
    when(ctx.getRealPath("/css/platform.css")).thenReturn(tempDir.resolve("absent.css").toString());

    assertEquals("fallback", ContextListener.resolveAssetVersion(ctx, "fallback", "/css/platform.css"));
  }

  @Test
  void skipsUnresolvableAssetsRatherThanFailingOutright(@TempDir Path tempDir) throws IOException {
    // One asset present, one not: the present one still supplies a real token
    File css = writeFile(tempDir, "platform.css", 1_234_000_000_000L);
    ServletContext ctx = mock(ServletContext.class);
    when(ctx.getRealPath("/css/platform.css")).thenReturn(css.getAbsolutePath());
    when(ctx.getRealPath("/css/platform-tokens.css")).thenReturn(null);

    assertEquals("1234000000000",
        ContextListener.resolveAssetVersion(ctx, "fallback", "/css/platform.css", "/css/platform-tokens.css"));
  }

  @Test
  void neverReturnsABlankToken(@TempDir Path tempDir) {
    // A blank token would emit href="...css?v=" on every page render
    ServletContext ctx = mock(ServletContext.class);
    when(ctx.getRealPath("/css/platform.css")).thenReturn(null);

    String version = ContextListener.resolveAssetVersion(ctx, "20260807.10002", "/css/platform.css");
    assertNotNull(version);
    assertFalse(version.isBlank());
  }

  private static File writeFile(Path dir, String name, long lastModified) throws IOException {
    Path path = dir.resolve(name);
    Files.writeString(path, "/* test */");
    File file = path.toFile();
    file.setLastModified(lastModified);
    return file;
  }

  @Test
  void everyStampedAssetPathResolvesToARealFile() {
    // resolveAssetVersion skips a path it cannot find, so a typo in the list fails silently: the
    // entry simply stops contributing to the token and nothing reports it. With eleven hand-typed
    // paths (#1872) that is the likeliest way this breaks, and it would look exactly like working.
    List<String> missing = new ArrayList<>();
    for (String path : ContextListener.STAMPED_ASSET_PATHS) {
      if (!Files.isRegularFile(Paths.get("src/main/webapp" + path))) {
        missing.add(path);
      }
    }
    assertTrue(missing.isEmpty(), "listed for cache-busting but not present in the webapp: " + missing);
  }

  @Test
  void noJspStillStampsAnAssetWithTheHandEditedReleaseConstant() {
    // All eleven got onto ApplicationInfo.VERSION by copying a neighbouring line, so removing the
    // interpolations matters as much as fixing them -- otherwise the next asset arrives the same
    // way. VERSION remains correct for DISPLAYING the release, which is why this looks for the
    // "?v=" stamp specifically rather than any mention of the constant.
    List<String> offenders = new ArrayList<>();
    try (var paths = Files.walk(Paths.get("src/main/webapp/WEB-INF"))) {
      paths.filter(Files::isRegularFile)
          .filter(f -> f.toString().endsWith(".jsp") || f.toString().endsWith(".jspf"))
          .forEach(f -> {
            try {
              if (Files.readString(f).contains("?v=<%= VERSION %>")) {
                offenders.add(f.toString());
              }
            } catch (IOException e) {
              throw new IllegalStateException("could not read " + f, e);
            }
          });
    } catch (IOException e) {
      throw new IllegalStateException("could not walk the JSP tree", e);
    }
    assertTrue(offenders.isEmpty(),
        "these stamp an asset with the hand-edited release constant instead of assetVersion: " + offenders);
  }
}
