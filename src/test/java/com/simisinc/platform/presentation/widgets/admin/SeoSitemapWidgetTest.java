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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * @author SimIS Inc.
 */
class SeoSitemapWidgetTest extends WidgetBase {

  private static WebPage webPage(long id, String link, String title, boolean showInSitemap) {
    WebPage webPage = new WebPage();
    webPage.setId(id);
    webPage.setLink(link);
    webPage.setTitle(title);
    webPage.setShowInSitemap(showInSitemap);
    return webPage;
  }

  private static Map<String, String> siteProperties(boolean sitemapEnabled) {
    Map<String, String> properties = new HashMap<>();
    properties.put("site.sitemap.xml", String.valueOf(sitemapEnabled));
    return properties;
  }

  /** Sets both the submitted checkbox param and the render-time-snapshot hidden field the JSP emits. */
  private static void addRow(WidgetContext context, long id, boolean checked, boolean renderedAs) {
    if (checked) {
      addQueryParameter(context, "showInSitemap_" + id, "true");
    }
    addQueryParameter(context, "renderedShowInSitemap_" + id, String.valueOf(renderedAs));
  }

  @Test
  void executeListsAllWebPagesAndTheCurrentSitemapStatus() {
    List<WebPage> webPageList = new ArrayList<>();
    webPageList.add(webPage(1L, "/about", "About", true));
    preferences.put("title", "SEO Sitemap");
    preferences.put("icon", "fa-map");

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(WebPageRepository::findAll).thenReturn(webPageList);
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(siteProperties(true));

      WidgetContext result = new SeoSitemapWidget().execute(widgetContext);

      assertEquals("/admin/seo-sitemap.jsp", result.getJsp());
      assertEquals(webPageList, result.getRequest().getAttribute("webPageList"));
      assertEquals(true, result.getRequest().getAttribute("sitemapEnabled"));
      assertEquals("SEO Sitemap", result.getRequest().getAttribute("title"),
          "the page heading was configured but never rendered because execute() never set this attribute");
      assertEquals("fa-map", result.getRequest().getAttribute("icon"));
    }
  }

  @Test
  void executeReportsTheSitemapAsDisabledWhenThePropertyIsFalseOrMissing() {
    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(WebPageRepository::findAll).thenReturn(new ArrayList<>());
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(new HashMap<>());

      WidgetContext result = new SeoSitemapWidget().execute(widgetContext);

      assertEquals(false, result.getRequest().getAttribute("sitemapEnabled"));
    }
  }

  @Test
  void postTurnsOnSitemapInclusionForAPageThatWasOff() {
    setRoles(widgetContext, ADMIN);
    WebPage page = webPage(1L, "/about", "About", false);
    List<WebPage> webPageList = new ArrayList<>();
    webPageList.add(page);
    addRow(widgetContext, 1L, true, false);

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(WebPageRepository::findAll).thenReturn(webPageList);
      repository.when(() -> WebPageRepository.findById(1L)).thenReturn(page);
      repository.when(() -> WebPageRepository.save(any())).thenReturn(page);

      WidgetContext result = new SeoSitemapWidget().post(widgetContext);

      assertTrue(page.getShowInSitemap());
      repository.verify(() -> WebPageRepository.save(page), times(1));
      assertEquals("/admin/seo-sitemap", result.getRedirect());
      assertEquals("1 page updated.", result.getSuccessMessage());
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONFIGURATION), eq("sitemap.bulk_update"),
          eq(AuditEventCommand.SUCCESS), eq("web_page"), any(), any(), eq("1 page(s) updated")));
    }
  }

  @Test
  void postTurnsOffSitemapInclusionForAPageThatWasOnWhenItsCheckboxIsAbsent() {
    setRoles(widgetContext, ADMIN);
    WebPage page = webPage(1L, "/about", "About", true);
    List<WebPage> webPageList = new ArrayList<>();
    webPageList.add(page);
    // No showInSitemap_1 param at all -- matches how an unchecked checkbox submits nothing
    addRow(widgetContext, 1L, false, true);

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class)) {
      repository.when(WebPageRepository::findAll).thenReturn(webPageList);
      repository.when(() -> WebPageRepository.findById(1L)).thenReturn(page);
      repository.when(() -> WebPageRepository.save(any())).thenReturn(page);

      new SeoSitemapWidget().post(widgetContext);

      assertFalse(page.getShowInSitemap());
      repository.verify(() -> WebPageRepository.save(page), times(1));
    }
  }

  @Test
  void postDoesNotSaveAPageWhoseCheckboxStateAlreadyMatchesTheStoredValue() {
    setRoles(widgetContext, ADMIN);
    WebPage unchanged = webPage(1L, "/about", "About", true);
    List<WebPage> webPageList = new ArrayList<>();
    webPageList.add(unchanged);
    addRow(widgetContext, 1L, true, true);

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class)) {
      repository.when(WebPageRepository::findAll).thenReturn(webPageList);

      WidgetContext result = new SeoSitemapWidget().post(widgetContext);

      repository.verify(() -> WebPageRepository.findById(any(Long.class)), never());
      repository.verify(() -> WebPageRepository.save(any()), never());
      assertEquals("No changes were made.", result.getSuccessMessage());
    }
  }

  @Test
  void postSavesOnlyTheChangedPagesAmongSeveral() {
    setRoles(widgetContext, ADMIN);
    WebPage changed = webPage(1L, "/about", "About", false);
    WebPage unchanged = webPage(2L, "/contact", "Contact", true);
    List<WebPage> webPageList = new ArrayList<>();
    webPageList.add(changed);
    webPageList.add(unchanged);
    addRow(widgetContext, 1L, true, false);
    addRow(widgetContext, 2L, true, true);

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class)) {
      repository.when(WebPageRepository::findAll).thenReturn(webPageList);
      repository.when(() -> WebPageRepository.findById(1L)).thenReturn(changed);
      repository.when(() -> WebPageRepository.save(any())).thenReturn(changed);

      WidgetContext result = new SeoSitemapWidget().post(widgetContext);

      repository.verify(() -> WebPageRepository.save(changed), times(1));
      repository.verify(() -> WebPageRepository.findById(2L), never());
      repository.verify(() -> WebPageRepository.save(unchanged), never());
      assertEquals("1 page updated.", result.getSuccessMessage());
    }
  }

  @Test
  void postHandlesOneTurningOnAndOneTurningOffInTheSameRequest() {
    setRoles(widgetContext, ADMIN);
    WebPage turnedOn = webPage(1L, "/about", "About", false);
    WebPage turnedOff = webPage(2L, "/contact", "Contact", true);
    WebPage unchanged = webPage(3L, "/legal", "Legal", true);
    List<WebPage> webPageList = new ArrayList<>();
    webPageList.add(turnedOn);
    webPageList.add(turnedOff);
    webPageList.add(unchanged);
    addRow(widgetContext, 1L, true, false); // off -> on
    addRow(widgetContext, 2L, false, true); // on -> off (checkbox absent)
    addRow(widgetContext, 3L, true, true); // unchanged

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class)) {
      repository.when(WebPageRepository::findAll).thenReturn(webPageList);
      repository.when(() -> WebPageRepository.findById(1L)).thenReturn(turnedOn);
      repository.when(() -> WebPageRepository.findById(2L)).thenReturn(turnedOff);
      repository.when(() -> WebPageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      WidgetContext result = new SeoSitemapWidget().post(widgetContext);

      assertTrue(turnedOn.getShowInSitemap());
      assertFalse(turnedOff.getShowInSitemap());
      assertTrue(unchanged.getShowInSitemap(), "the unchanged row's own bean must not be mutated");
      repository.verify(() -> WebPageRepository.save(turnedOn), times(1));
      repository.verify(() -> WebPageRepository.save(turnedOff), times(1));
      repository.verify(() -> WebPageRepository.findById(3L), never());
      repository.verify(() -> WebPageRepository.save(unchanged), never());
      assertEquals("2 pages updated.", result.getSuccessMessage());
    }
  }

  @Test
  void postDoesNotRevertAPageChangedConcurrentlyThatTheSubmittingAdminNeverTouched() {
    // Simulates: admin loads the form while the page is showInSitemap=false (renderedAs=false,
    // matching what execute() would have shown then); a different concurrent edit (another admin,
    // or the single-page form) flips it to true in the DB before this POST lands; the submitting
    // admin's own browser DOM never had that checkbox checked, so no showInSitemap_1 param is sent.
    setRoles(widgetContext, ADMIN);
    WebPage concurrentlyChanged = webPage(1L, "/about", "About", true); // current DB state: true
    List<WebPage> webPageList = new ArrayList<>();
    webPageList.add(concurrentlyChanged);
    addRow(widgetContext, 1L, false, false); // rendered as false; submitted as false (untouched)

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class)) {
      repository.when(WebPageRepository::findAll).thenReturn(webPageList);

      WidgetContext result = new SeoSitemapWidget().post(widgetContext);

      repository.verify(() -> WebPageRepository.findById(any(Long.class)), never());
      repository.verify(() -> WebPageRepository.save(any()), never());
      assertTrue(concurrentlyChanged.getShowInSitemap(), "the concurrent change must survive untouched");
      assertEquals("No changes were made.", result.getSuccessMessage());
    }
  }

  @Test
  void postSkipsARowWhoseIdNoLongerResolvesOnReFetch() {
    setRoles(widgetContext, ADMIN);
    WebPage deletedInTheMeantime = webPage(1L, "/about", "About", false);
    List<WebPage> webPageList = new ArrayList<>();
    webPageList.add(deletedInTheMeantime);
    addRow(widgetContext, 1L, true, false);

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class)) {
      repository.when(WebPageRepository::findAll).thenReturn(webPageList);
      repository.when(() -> WebPageRepository.findById(1L)).thenReturn(null);

      WidgetContext result = new SeoSitemapWidget().post(widgetContext);

      repository.verify(() -> WebPageRepository.save(any()), never());
      assertEquals("No changes were made.", result.getSuccessMessage());
    }
  }

  @Test
  void postSavesTheChangeForAContentManagerRole() {
    // admin-layout.xml scopes this page to role="admin,content-manager" -- a content-manager
    // must be able to save here, same as they already can one page at a time via /admin/web-pages.
    setRoles(widgetContext, CONTENT_MANAGER);
    WebPage page = webPage(1L, "/about", "About", false);
    List<WebPage> webPageList = new ArrayList<>();
    webPageList.add(page);
    addRow(widgetContext, 1L, true, false);

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class)) {
      repository.when(WebPageRepository::findAll).thenReturn(webPageList);
      repository.when(() -> WebPageRepository.findById(1L)).thenReturn(page);
      repository.when(() -> WebPageRepository.save(any())).thenReturn(page);

      WidgetContext result = new SeoSitemapWidget().post(widgetContext);

      assertTrue(page.getShowInSitemap());
      repository.verify(() -> WebPageRepository.save(page), times(1));
      assertEquals("1 page updated.", result.getSuccessMessage());
    }
  }

  @Test
  void postDoesNothingForAUserWithoutTheRequiredRole() {
    // WidgetBase's default logged-in test user has no roles at all
    WebPage page = webPage(1L, "/about", "About", false);
    List<WebPage> webPageList = new ArrayList<>();
    webPageList.add(page);
    addRow(widgetContext, 1L, true, false);

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(WebPageRepository::findAll).thenReturn(webPageList);
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(new HashMap<>());

      new SeoSitemapWidget().post(widgetContext);

      repository.verify(() -> WebPageRepository.findById(any(Long.class)), never());
      repository.verify(() -> WebPageRepository.save(any()), never());
      assertFalse(page.getShowInSitemap(), "an unauthorized POST must not mutate the page bean at all");
    }
  }
}
