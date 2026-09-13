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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The page designer must not fetch a stylesheet from a third-party CDN.
 *
 * <p>jquery-gridmanager ships {@code cssInclude} defaulting to a BootstrapCDN copy of Font Awesome
 * 4.1.0, and its {@code init()} appends that as a {@code <link>} to the document head. Left alone,
 * every admin opening {@code /admin/web-page-designer} fetched Font Awesome from retired
 * third-party infrastructure -- for nothing, since {@code main.jsp} already loads Font Awesome
 * locally with {@code v4-shims.min.css}, which is precisely what maps gridmanager's FA4-era class
 * names onto it.
 *
 * <p>This was invisible for a reason worth remembering: the enforced CSP has no {@code style-src}
 * entry for that host, so the request simply failed and the icons kept working from the local
 * copy. It surfaced only as a single row in the CSP violation report. If the override is ever
 * dropped, or the host reappears in another JSP, the same silence applies -- which is what these
 * assertions are for.
 *
 * @author elizabeth houser
 */
class WebPageDesignerCdnStylesheetTest {

  private static final File DESIGNER_JSP = new File("src/main/webapp/WEB-INF/jsp/cms/web-page-designer.jsp");
  private static final File JSP_ROOT = new File("src/main/webapp/WEB-INF/jsp");
  private static final File GRIDMANAGER_JS = new File(
      "src/main/webapp/javascript/jquery-gridmanager-0.3.1/jquery.gridmanager.js");

  private static String read(File file) throws IOException {
    assertTrue(file.isFile(), "not found (run from the project root): " + file.getAbsolutePath());
    return Files.readString(file.toPath(), StandardCharsets.UTF_8);
  }

  @Test
  void theDesignerSuppressesGridmanagersOwnStylesheetInclude() throws IOException {
    assertTrue(read(DESIGNER_JSP).contains("cssInclude: \"\""),
        "web-page-designer.jsp must pass an empty cssInclude, or gridmanager appends a "
            + "BootstrapCDN <link> to the head every time the designer opens");
  }

  /**
   * The override is only load-bearing while the vendored library still carries the CDN default. If
   * a gridmanager upgrade removes it, this fails so someone re-reads the situation rather than
   * leaving a line whose purpose has quietly expired.
   */
  @Test
  void theVendoredLibraryStillCarriesTheCdnDefaultTheOverrideExistsFor() throws IOException {
    assertTrue(read(GRIDMANAGER_JS).contains("maxcdn.bootstrapcdn.com"),
        "jquery.gridmanager.js no longer defaults cssInclude to BootstrapCDN -- the override in "
            + "web-page-designer.jsp may now be unnecessary; confirm and remove this test with it");
  }

  @Test
  void noJspFetchesAStylesheetFromBootstrapCdn() throws IOException {
    List<String> offenders = new ArrayList<>();
    collect(JSP_ROOT, offenders);
    assertEquals(List.of(), offenders,
        "a JSP references BootstrapCDN. The enforced CSP blocks it, so this fails silently and "
            + "nobody notices -- serve the asset locally instead");
  }

  /**
   * Comment lines are excluded deliberately: the override in web-page-designer.jsp documents the
   * exact URL it exists to suppress, and that explanation is worth more than a scan that trips
   * over its own documentation. A commented URL fetches nothing.
   */
  private static boolean referencesBootstrapCdnOutsideAComment(String source) {
    for (String line : source.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("<%--")) {
        continue;
      }
      if (trimmed.contains("bootstrapcdn.com")) {
        return true;
      }
    }
    return false;
  }

  private static void collect(File directory, List<String> offenders) throws IOException {
    File[] entries = directory.listFiles();
    if (entries == null) {
      return;
    }
    for (File entry : entries) {
      if (entry.isDirectory()) {
        collect(entry, offenders);
      } else if (entry.getName().endsWith(".jsp") || entry.getName().endsWith(".jspf")) {
        if (referencesBootstrapCdnOutsideAComment(Files.readString(entry.toPath(), StandardCharsets.UTF_8))) {
          offenders.add(entry.getName());
        }
      }
    }
  }
}
