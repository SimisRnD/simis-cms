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

package com.simisinc.platform.application.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;

import jakarta.servlet.ServletContext;

/**
 * Tests {@link ContentUsageCommand}, focused on the two real traps found while researching issue
 * #499: (1) {@code <uniqueId>} is a generic tag name shared by unrelated widgets, so matching must
 * be scoped to the content-widget family, not any widget carrying that tag; (2) a content block
 * referenced only by a filesystem web-layout template (not any {@code web_pages} DB row) must still
 * be detected as used, not orphaned.
 *
 * @author SimIS Inc.
 */
class ContentUsageCommandTest {

  @Test
  void aNonContentWidgetSharingTheSameUniqueIdDoesNotCountAsUsage() {
    // An item/collection-style widget also uses a <uniqueId> tag -- for something unrelated to the
    // content repository entirely. A content block that happens to share that literal string must
    // NOT be reported as used by this page.
    String pageXml = "<page name=\"/careers\">"
        + "  <section><column>"
        + "    <widget name=\"items\"><uniqueId>site-footer</uniqueId></widget>"
        + "  </column></section>"
        + "</page>";
    WebPage careersPage = new WebPage();
    careersPage.setLink("/careers");
    careersPage.setPageXml(pageXml);

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      webPageRepository.when(WebPageRepository::findAll).thenReturn(List.of(careersPage));

      Map<String, List<String>> usageMap = ContentUsageCommand.findUsageMap(null);

      assertNull(usageMap.get("site-footer"),
          "an unrelated widget's same-named <uniqueId> must not register as content usage");
    }
  }

  @Test
  void aRealContentWidgetOnTheSamePageIsStillDetected() {
    // The scoping fix above must not become a blanket false negative: a genuine content-family
    // widget on the very same page, right next to the trap widget, must still be picked up.
    String pageXml = "<page name=\"/careers\">"
        + "  <section><column>"
        + "    <widget name=\"items\"><uniqueId>site-footer</uniqueId></widget>"
        + "    <widget name=\"content\"><uniqueId>careers-intro</uniqueId></widget>"
        + "  </column></section>"
        + "</page>";
    WebPage careersPage = new WebPage();
    careersPage.setLink("/careers");
    careersPage.setPageXml(pageXml);

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      webPageRepository.when(WebPageRepository::findAll).thenReturn(List.of(careersPage));

      Map<String, List<String>> usageMap = ContentUsageCommand.findUsageMap(null);

      assertEquals(List.of("/careers"), usageMap.get("careers-intro"));
      assertNull(usageMap.get("site-footer"));
    }
  }

  @Test
  void everyWidgetInTheDocumentedContentFamilyIsRecognizedByName() {
    // Regression guard for the family list itself (content, contentTabs, contentCards,
    // contentAccordion, contentSlider, contentReveal, contentGallery, contentCarousel,
    // contentEditor -- per widget-library.xml), independent of the XML-scanning mechanics above.
    assertEquals(Set.of("content", "contentTabs", "contentCards", "contentAccordion", "contentSlider",
        "contentReveal", "contentGallery", "contentCarousel", "contentEditor"), ContentUsageCommand.CONTENT_WIDGET_NAMES);
  }

  @Test
  void aContentBlockOnlyReferencedByAFilesystemTemplateIsDetectedAsUsedNotOrphaned() {
    // No web_pages DB row references it at all -- only the filesystem footer-layout.xml does,
    // exactly like the real site-footer content block. This must NOT show as orphaned.
    String footerLayoutXml = "<footers>"
        + "  <footer name=\"footer.default\">"
        + "    <section><column>"
        + "      <widget name=\"content\" class=\"margin-top-15\"><uniqueId>site-footer</uniqueId></widget>"
        + "    </column></section>"
        + "  </footer>"
        + "</footers>";

    ServletContext servletContext = mockServletContextWithOneFile(
        "/WEB-INF/web-layouts/footer/", "/WEB-INF/web-layouts/footer/footer-layout.xml", footerLayoutXml);

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      webPageRepository.when(WebPageRepository::findAll).thenReturn(List.of());

      Map<String, List<String>> usageMap = ContentUsageCommand.findUsageMap(servletContext);

      List<String> locations = usageMap.get("site-footer");
      assertTrue(locations != null && locations.contains("/WEB-INF/web-layouts/footer/footer-layout.xml"),
          "a block used only by a filesystem template must be detected as used, not orphaned; got: " + locations);
    }
  }

  @Test
  void aBlockNotReferencedAnywhereIsOrphaned() {
    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      webPageRepository.when(WebPageRepository::findAll).thenReturn(List.of());

      Map<String, List<String>> usageMap = ContentUsageCommand.findUsageMap(null);

      assertTrue(usageMap.isEmpty());
      assertFalse(usageMap.containsKey("never-used-anywhere"));
    }
  }

  @Test
  void contentTabsReferencesEachTabsContentUniqueIdAttributeNotAUniqueIdChildTag() {
    // contentTabs is the outlier in the family: each tab names its own content block via a
    // contentUniqueId attribute on a nested <tab> element, not the simple <uniqueId> child the rest
    // of the family uses (see ContentTabsWidget / PreferenceEntriesList).
    String pageXml = "<page name=\"/cmmc\">"
        + "  <section><column>"
        + "    <widget name=\"contentTabs\">"
        + "      <tabs>"
        + "        <tab contentUniqueId=\"cmmc-tab-overview\" name=\"Overview\"/>"
        + "        <tab contentUniqueId=\"cmmc-tab-details\" name=\"Details\"/>"
        + "      </tabs>"
        + "    </widget>"
        + "  </column></section>"
        + "</page>";
    WebPage cmmcPage = new WebPage();
    cmmcPage.setLink("/cmmc");
    cmmcPage.setPageXml(pageXml);

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      webPageRepository.when(WebPageRepository::findAll).thenReturn(List.of(cmmcPage));

      Map<String, List<String>> usageMap = ContentUsageCommand.findUsageMap(null);

      assertEquals(List.of("/cmmc"), usageMap.get("cmmc-tab-overview"));
      assertEquals(List.of("/cmmc"), usageMap.get("cmmc-tab-details"));
    }
  }

  @Test
  void multiplePagesReferencingTheSameBlockAreBothListedAndDeduplicated() {
    WebPage careers = new WebPage();
    careers.setLink("/careers");
    careers.setPageXml("<page><section><column><widget name=\"content\"><uniqueId>cmmc-header</uniqueId></widget></column></section></page>");
    WebPage about = new WebPage();
    about.setLink("/about-us");
    about.setPageXml("<page><section><column>"
        + "<widget name=\"content\"><uniqueId>cmmc-header</uniqueId></widget>"
        + "<widget name=\"content\"><uniqueId>cmmc-header</uniqueId></widget>" // same page, twice
        + "</column></section></page>");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      webPageRepository.when(WebPageRepository::findAll).thenReturn(List.of(careers, about));

      Map<String, List<String>> usageMap = ContentUsageCommand.findUsageMap(null);

      List<String> locations = usageMap.get("cmmc-header");
      assertEquals(List.of("/careers", "/about-us"), locations, "each distinct page listed once, even when referenced twice on the same page");
    }
  }

  @Test
  void aPageWithNoPageXmlIsSkippedWithoutError() {
    WebPage blankPage = new WebPage();
    blankPage.setLink("/not-yet-designed");
    blankPage.setPageXml(null);

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      webPageRepository.when(WebPageRepository::findAll).thenReturn(List.of(blankPage));

      Map<String, List<String>> usageMap = ContentUsageCommand.findUsageMap(null);

      assertTrue(usageMap.isEmpty());
    }
  }

  /** A ServletContext mock that resolves exactly one *.xml file under the given directory. */
  private static ServletContext mockServletContextWithOneFile(String directory, String filePath, String fileContent) {
    ServletContext servletContext = mock(ServletContext.class);
    // Any directory the recursive walk probes other than these two resolves to nothing further.
    when(servletContext.getResourcePaths(anyString())).thenAnswer(invocation -> {
      String path = invocation.getArgument(0, String.class);
      if ("/WEB-INF/web-layouts".equals(path)) {
        return Set.of(directory);
      }
      if (directory.equals(path)) {
        return Set.of(filePath);
      }
      return null;
    });
    when(servletContext.getResourceAsStream(eq(filePath))).thenAnswer(invocation ->
        new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)));
    return servletContext;
  }
}
