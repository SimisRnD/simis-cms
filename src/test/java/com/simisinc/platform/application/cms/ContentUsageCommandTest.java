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
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;

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
    // contentEditor -- per widget-library.xml), plus the ecommerce "cart" widget, which is not in
    // that cms-package class family but genuinely embeds up to two content blocks of its own (see
    // CartWidget#execute and the aCartWidgetsCardPreferences... tests below).
    assertEquals(Set.of("content", "contentTabs", "contentCards", "contentAccordion", "contentSlider",
        "contentReveal", "contentGallery", "contentCarousel", "contentEditor", "cart"), ContentUsageCommand.CONTENT_WIDGET_NAMES);
  }

  @Test
  void aCartWidgetsCardPreferencesAreDetectedAsUsageNotTheGenericUniqueIdTag() {
    // The real ecommerce-layout.xml /cart page: the cart widget embeds two content blocks via
    // card1uniqueId/card2uniqueId preferences, not a plain <uniqueId> child (see CartWidget#execute).
    String pageXml = "<page name=\"/cart\">"
        + "  <section><column>"
        + "    <widget name=\"cart\">"
        + "      <title>Your bag</title>"
        + "      <card1uniqueId>cart-card1</card1uniqueId>"
        + "      <card2uniqueId>cart-card2</card2uniqueId>"
        + "    </widget>"
        + "  </column></section>"
        + "</page>";
    WebPage cartPage = new WebPage();
    cartPage.setLink("/cart");
    cartPage.setPageXml(pageXml);

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      webPageRepository.when(WebPageRepository::findAll).thenReturn(List.of(cartPage));

      Map<String, List<String>> usageMap = ContentUsageCommand.findUsageMap(null);

      assertEquals(List.of("/cart"), usageMap.get("cart-card1"));
      assertEquals(List.of("/cart"), usageMap.get("cart-card2"));
    }
  }

  @Test
  void aCartWidgetWithNoCardPreferencesRegistersNoUsage() {
    // The /checkout-style "summary" and "items" cart views don't set card1uniqueId/card2uniqueId at
    // all; this must not throw or register a spurious usage entry.
    String pageXml = "<page name=\"/checkout\">"
        + "  <section><column>"
        + "    <widget name=\"cart\" class=\"callout box checkout-summary-callout\"><view>summary</view></widget>"
        + "  </column></section>"
        + "</page>";
    WebPage checkoutPage = new WebPage();
    checkoutPage.setLink("/checkout");
    checkoutPage.setPageXml(pageXml);

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      webPageRepository.when(WebPageRepository::findAll).thenReturn(List.of(checkoutPage));

      Map<String, List<String>> usageMap = ContentUsageCommand.findUsageMap(null);

      assertTrue(usageMap.isEmpty());
    }
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
  void aBlockUsedOnOnlyOnePageWithinAMultiPageTemplateFileIsNotFalselyShared() {
    // A false-positive found by review: admin-layout.xml/cms-layout.xml each bundle dozens of
    // independent <page> routes in one physical file -- a widget reference inside just one of those
    // <page> blocks must NOT be treated as inherently site-wide the way a true single-purpose
    // fragment include (footer-layout.xml, no <page> wrapper at all) correctly is. Before the fix,
    // isFilesystemTemplateLocation() only checked the file path prefix, so this reproduced exactly:
    // "login-hello" (real content block, used only on the single /login page inside cms-layout.xml)
    // showed as Shared with a "site-wide template" warning, even though editing it only affects one page.
    String cmsLayoutXml = "<pages>"
        + "  <page name=\"/login\">"
        + "    <section><column>"
        + "      <widget name=\"content\"><uniqueId>login-hello</uniqueId></widget>"
        + "    </column></section>"
        + "  </page>"
        + "  <page name=\"/register\">"
        + "    <section><column>"
        + "      <widget name=\"content\"><uniqueId>register-hello</uniqueId></widget>"
        + "    </column></section>"
        + "  </page>"
        + "</pages>";

    ServletContext servletContext = mockServletContextWithOneFile(
        "/WEB-INF/web-layouts/page/", "/WEB-INF/web-layouts/page/cms-layout.xml", cmsLayoutXml);

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      webPageRepository.when(WebPageRepository::findAll).thenReturn(List.of());

      Map<String, List<String>> usageMap = ContentUsageCommand.findUsageMap(servletContext);

      List<String> loginHelloLocations = usageMap.get("login-hello");
      assertEquals(List.of("/WEB-INF/web-layouts/page/cms-layout.xml#/login"), loginHelloLocations,
          "the location must be scoped to the specific <page> it was found in");
      assertFalse(ContentUsageCommand.isShared(loginHelloLocations),
          "a block used on exactly one <page> route inside a multi-page file must not count as Shared");
      assertNull(ContentUsageCommand.buildReusabilityWarning("login-hello", usageMap),
          "must not produce a false 'site-wide template' warning for a single-page reference");
    }
  }

  @Test
  void aBlockUsedOnTwoDifferentPagesWithinTheSameMultiPageTemplateFileIsShared() {
    // The other half of the same fix: genuine reuse across two <page> blocks in one file must still
    // be detected as Shared, same as reuse across two separate web_pages rows.
    String adminLayoutXml = "<pages>"
        + "  <page name=\"/admin\">"
        + "    <section><column>"
        + "      <widget name=\"content\"><uniqueId>admin-shared-banner</uniqueId></widget>"
        + "    </column></section>"
        + "  </page>"
        + "  <page name=\"/admin/users\">"
        + "    <section><column>"
        + "      <widget name=\"content\"><uniqueId>admin-shared-banner</uniqueId></widget>"
        + "    </column></section>"
        + "  </page>"
        + "</pages>";

    ServletContext servletContext = mockServletContextWithOneFile(
        "/WEB-INF/web-layouts/page/", "/WEB-INF/web-layouts/page/admin-layout.xml", adminLayoutXml);

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      webPageRepository.when(WebPageRepository::findAll).thenReturn(List.of());

      Map<String, List<String>> usageMap = ContentUsageCommand.findUsageMap(servletContext);

      List<String> locations = usageMap.get("admin-shared-banner");
      assertEquals(
          List.of("/WEB-INF/web-layouts/page/admin-layout.xml#/admin", "/WEB-INF/web-layouts/page/admin-layout.xml#/admin/users"),
          locations);
      assertTrue(ContentUsageCommand.isShared(locations), "genuine reuse across two <page> routes must still count as Shared");
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

  @Test
  void aTemplatedUniqueIdWithAnUnresolvedElPlaceholderIsNotRecordedAsALiteralUsageKey() {
    // The real products-layout.xml pattern (issue #499 follow-up): a per-item content block whose
    // <uniqueId> is never actually "product-details-${item.uniqueId}" at runtime -- that literal
    // string can never match a real content_unique_id, so recording it as a usage key would only
    // ever produce a permanently-dead map entry, making every real per-product content block show as
    // falsely "Orphaned" forever.
    String pageXml = "<page name=\"/show/*\">"
        + "  <section><column>"
        + "    <widget name=\"content\" hr=\"true\"><uniqueId>product-details-${item.uniqueId}</uniqueId></widget>"
        + "  </column></section>"
        + "</page>";
    WebPage productPage = new WebPage();
    productPage.setLink("/show/*");
    productPage.setPageXml(pageXml);

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      webPageRepository.when(WebPageRepository::findAll).thenReturn(List.of(productPage));

      ContentUsageCommand.UsageScan scan = ContentUsageCommand.scanUsage(null);

      assertTrue(scan.usageMap().isEmpty(), "the unresolved literal string must never be recorded as a usage key");
      assertEquals(List.of("/show/*"), scan.templatedPrefixLocations().get("product-details-"));
    }
  }

  @Test
  void aRealContentRecordMatchingATemplatedPrefixIsDistinctFromOneThatDoesNot() {
    // findUsageMap() (the literal-match convenience wrapper) must keep excluding the templated
    // prefix's literal string -- ContentListWidget is the one that cross-references
    // templatedPrefixLocations against real Content rows to decide Orphaned vs Templated, not this
    // class, so findUsageMap alone should never contain the placeholder text.
    String pageXml = "<page name=\"/show/*\">"
        + "  <section><column>"
        + "    <widget name=\"content\"><uniqueId>product-details-${item.uniqueId}</uniqueId></widget>"
        + "  </column></section>"
        + "</page>";
    WebPage productPage = new WebPage();
    productPage.setLink("/show/*");
    productPage.setPageXml(pageXml);

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      webPageRepository.when(WebPageRepository::findAll).thenReturn(List.of(productPage));

      Map<String, List<String>> usageMap = ContentUsageCommand.findUsageMap(null);

      assertFalse(usageMap.containsKey("product-details-${item.uniqueId}"));
      assertTrue(usageMap.isEmpty());
    }
  }

  @Test
  void aBarePlaceholderWithNoLiteralPrefixIsSkippedEntirely() {
    // A uniqueId that is ENTIRELY a placeholder (no literal prefix at all) must not be recorded as a
    // templated prefix either -- an empty-string prefix would match every single Content row via
    // String#startsWith, which is worse than not detecting the pattern at all.
    String pageXml = "<page name=\"/show/*\">"
        + "  <section><column>"
        + "    <widget name=\"content\"><uniqueId>${item.uniqueId}</uniqueId></widget>"
        + "  </column></section>"
        + "</page>";
    WebPage page = new WebPage();
    page.setLink("/show/*");
    page.setPageXml(pageXml);

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      webPageRepository.when(WebPageRepository::findAll).thenReturn(List.of(page));

      ContentUsageCommand.UsageScan scan = ContentUsageCommand.scanUsage(null);

      assertTrue(scan.usageMap().isEmpty());
      assertTrue(scan.templatedPrefixLocations().isEmpty(), "an empty prefix must never be recorded");
    }
  }

  @Test
  void isSharedIsTrueForMultipleLocationsRegardlessOfKind() {
    assertTrue(ContentUsageCommand.isShared(List.of("/careers", "/about-us")));
  }

  @Test
  void isSharedIsTrueForASingleFilesystemTemplateLocation() {
    // The undercounting bug (issue #499 follow-up): site-footer has exactly ONE usage-map entry
    // (footer-layout.xml), but that template is rendered on every page site-wide, so it must still
    // count as Shared/high-blast-radius, not slip through a raw ">1" count check.
    assertTrue(ContentUsageCommand.isShared(List.of("/WEB-INF/web-layouts/footer/footer-layout.xml")));
  }

  @Test
  void isSharedIsFalseForASingleOrdinaryWebPageLocation() {
    assertFalse(ContentUsageCommand.isShared(List.of("/careers")));
  }

  @Test
  void isSharedIsFalseForNullOrEmptyLocations() {
    assertFalse(ContentUsageCommand.isShared(null));
    assertFalse(ContentUsageCommand.isShared(List.of()));
  }

  @Test
  void isFilesystemTemplateLocationMatchesOnlyTheWebLayoutsPath() {
    assertTrue(ContentUsageCommand.isFilesystemTemplateLocation("/WEB-INF/web-layouts/footer/footer-layout.xml"));
    assertFalse(ContentUsageCommand.isFilesystemTemplateLocation("/careers"));
    assertFalse(ContentUsageCommand.isFilesystemTemplateLocation(null));
  }

  @Test
  void buildReusabilityWarningDescribesASingleFilesystemTemplateLocationAsSiteWide() {
    // Before this fix, a block used on exactly one location never triggered a warning at all
    // (locations.size() <= 1 short-circuited); now a single site-wide template location must still
    // warn, with wording that doesn't misleadingly say "appears on 1 pages".
    Map<String, List<String>> usageMap = Map.of(
        "site-footer", List.of("/WEB-INF/web-layouts/footer/footer-layout.xml"));

    String warning = ContentUsageCommand.buildReusabilityWarning("site-footer", usageMap);

    assertTrue(warning != null && warning.contains("site-wide template"), warning);
    assertTrue(warning.contains("/WEB-INF/web-layouts/footer/footer-layout.xml"), warning);
    assertFalse(warning.contains("1 pages"), "must not read as \"appears on 1 pages\"");
  }

  @Test
  void buildReusabilityWarningStillReturnsNullForASingleOrdinaryWebPageLocation() {
    // Unchanged behavior: a block used on exactly one ordinary page is not shared, so publishing it
    // only ever affects the page already being looked at -- no warning needed.
    Map<String, List<String>> usageMap = Map.of("solo-header", List.of("/careers"));

    assertNull(ContentUsageCommand.buildReusabilityWarning("solo-header", usageMap));
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

  // --- findOverriddenInlineDefaults (issue #1725) -------------------------------------------
  //
  // A content widget can declare inline <html> AND reference a saved record. The record always
  // wins (ContentHtmlCommand.getHtmlFromPreferences), so that inline html is inert and editing it
  // in the designer changes nothing on the page. These cover the three states that decide whether
  // a block is worth warning about.

  private static String pageXmlWith(String widgetName, String uniqueId, String inlineHtml) {
    StringBuilder sb = new StringBuilder("<page><section><column><widget name=\"")
        .append(widgetName).append("\">");
    if (uniqueId != null) {
      sb.append("<uniqueId>").append(uniqueId).append("</uniqueId>");
    }
    if (inlineHtml != null) {
      sb.append("<html><![CDATA[").append(inlineHtml).append("]]></html>");
    }
    return sb.append("</widget></column></section></page>").toString();
  }

  private static Content contentWith(String html) {
    Content content = new Content();
    content.setUniqueId("intro");
    content.setContent(html);
    return content;
  }

  @Test
  void aSavedRecordOverridingInlineHtmlIsReported() {
    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class)) {
      repository.when(() -> ContentRepository.findByUniqueId("intro"))
          .thenReturn(contentWith("<p>what visitors actually see</p>"));

      List<String> overridden = ContentUsageCommand.findOverriddenInlineDefaults(
          pageXmlWith("content", "intro", "<p>the inert default</p>"));

      assertEquals(List.of("intro"), overridden);
    }
  }

  @Test
  void inlineHtmlWithNoSavedRecordIsNotReported() {
    // Nothing is shadowing it -- the declared html is exactly what renders.
    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class)) {
      repository.when(() -> ContentRepository.findByUniqueId("intro")).thenReturn(null);

      assertTrue(ContentUsageCommand.findOverriddenInlineDefaults(
          pageXmlWith("content", "intro", "<p>the live default</p>")).isEmpty());
    }
  }

  @Test
  void aBlankRecordDoesNotCountAsOverriding() {
    // An empty record deliberately falls through to the page XML (issue #1689), so the inline html
    // IS live here. Warning about it would send someone hunting for an override that is not there.
    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class)) {
      repository.when(() -> ContentRepository.findByUniqueId("intro")).thenReturn(contentWith("   "));

      assertTrue(ContentUsageCommand.findOverriddenInlineDefaults(
          pageXmlWith("content", "intro", "<p>still live</p>")).isEmpty());
    }
  }

  @Test
  void aWidgetWithNoInlineHtmlIsNotReported() {
    // The record is the only source; there is no shadowed default to warn about.
    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class)) {
      repository.when(() -> ContentRepository.findByUniqueId("intro"))
          .thenReturn(contentWith("<p>the only source</p>"));

      assertTrue(ContentUsageCommand.findOverriddenInlineDefaults(
          pageXmlWith("content", "intro", null)).isEmpty());
    }
  }

  @Test
  void aNonContentWidgetIsIgnored() {
    // A <uniqueId> on some other widget family refers to something else entirely and must not be
    // looked up as a content block.
    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class)) {
      assertTrue(ContentUsageCommand.findOverriddenInlineDefaults(
          pageXmlWith("tableOfContents", "intro", "<p>not content</p>")).isEmpty());
      repository.verifyNoInteractions();
    }
  }

  @Test
  void unparseablePageXmlIsAdvisoryOnlyAndNeverThrows() {
    // The editor already reports malformed XML; this notice must never be the thing that stops the
    // designer rendering.
    assertTrue(ContentUsageCommand.findOverriddenInlineDefaults("<page><widget unclosed").isEmpty());
    assertTrue(ContentUsageCommand.findOverriddenInlineDefaults(null).isEmpty());
    assertTrue(ContentUsageCommand.findOverriddenInlineDefaults("   ").isEmpty());
  }
}
