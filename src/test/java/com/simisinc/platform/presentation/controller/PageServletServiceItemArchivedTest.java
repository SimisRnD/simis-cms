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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.LoadWebPageCommand;
import com.simisinc.platform.application.cms.WebContainerLayoutCommand;
import com.simisinc.platform.application.cms.WebPageXmlLayoutCommand;
import com.simisinc.platform.application.items.LoadCategoryCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.SocialMediaLinkRepository;

/**
 * Drives {@link PageServlet#service} end-to-end -- a real routing/dispatch call, not just the
 * extracted helpers {@link PageServletTest} already covers -- through the issue #827 / PR #830
 * archived-item guard at PageServlet.java ~797-818: a page whose matched {@link Page} has no
 * role/group/capability restriction (a genuinely public item route, e.g. "/show/{uniqueId}") must
 * 404 when LoadItemCommand resolves no item for the requested uniqueId -- which is exactly what
 * passing excludeArchived=true (isPubliclyUnrestrictedPage) causes for a deactivated item -- and
 * must NOT 404 when an item is actually found.
 *
 * <p>PR #830 shipped isPubliclyUnrestrictedPage with only isolated unit tests against the helper
 * itself (see PageServletTest's isPubliclyUnrestrictedPage* tests), and its own description flagged
 * that as a gap: nothing verified the surrounding wiring in service() itself (right uniqueId
 * parsed off the request URI, right excludeArchived argument threaded through, the guard actually
 * reached and actually short-circuiting). A regression there -- e.g. an argument-order swap, or the
 * guard accidentally moved after the point where a response is already committed -- would still
 * leave every individual helper green. These two tests close that gap.
 *
 * <p>PageServlet.service() is a ~1250-line method with dozens of branches unrelated to this one
 * guard (draft blocking, redirects, layout-builder mutations, JSON-LD, header/footer rendering,
 * etc.), and there is no servlet container or database available in a unit test. Rather than making
 * all of that "really" work, everything upstream of and unrelated to the item-resolution block is
 * short-circuited to the fastest path that reaches it cleanly:
 * <ul>
 * <li>a null WebPage (LoadWebPageCommand mocked) skips draft/redirect/publish-window handling
 * entirely;</li>
 * <li>no editMode/action request parameters (Mockito's default null for an unstubbed
 * request.getParameter) skips every layout-mutation branch;</li>
 * <li>an X-Monitor request header skips page-hit tracking (SaveWebPageHitCommand /
 * DoNotTrackCommand), which is unrelated to this guard;</li>
 * <li>the matched {@link Page} is built directly via the same 3-arg constructor the XML loader
 * would otherwise produce (WebPageXmlLayoutCommand.retrievePageForRequest mocked to return it),
 * with roles/groups/capabilities left at their empty defaults so isPubliclyUnrestrictedPage is
 * true and WebComponentCommand.allowsUser (a real, pure call -- not mocked) passes for a guest,
 * matching its own open-by-default semantics for an unrestricted page.</li>
 * </ul>
 * Every other static command on this path that would otherwise hit a real database
 * (WebPageXmlLayoutCommand's page registry, LoadSitePropertyCommand, SocialMediaLinkRepository,
 * LoadItemCommand, and -- for the "item found" test, which keeps running a little further --
 * LoadCollectionCommand/LoadCategoryCommand/WebContainerLayoutCommand) is statically mocked,
 * matching PageServletTest's existing mockStatic convention for command classes.
 *
 * <p>What happens after the item-resolution block succeeds (header/footer rendering, JSP
 * forwarding) is intentionally left unmocked in the "item found" test and may throw inside
 * service()'s own top-level try/catch (e.g. WebContainerLayoutCommand.retrieveHeader returns null
 * since it isn't stubbed, and the header-rendering code a few lines later dereferences it). That's
 * fine here: every assertion this test makes is about interactions that already happened by the
 * time the item-resolution block finishes, so a later, unrelated exception -- caught and logged by
 * service() itself -- doesn't affect them.
 *
 * @author elizabeth houser
 */
class PageServletServiceItemArchivedTest {

  private static final String ITEM_UNIQUE_ID = "item123";
  private static final String REQUEST_URI = "/show/" + ITEM_UNIQUE_ID;

  /**
   * A page matched from an item detail route like "/show/{uniqueId}": itemUniqueId is a wildcard
   * pattern (contains "*", as WebPageXmlLayoutCommand's real XML-loaded pages do), and
   * roles/groups/capabilities are left at their empty defaults so isPubliclyUnrestrictedPage(this)
   * is true -- matching WebComponentCommand.allowsUser's own open-by-default semantics for an
   * unrestricted Page (any guest passes).
   */
  private Page unrestrictedShowPage() {
    return new Page("/show/*", null, "/show/*");
  }

  private HttpServletRequest mockRequest(HttpSession session) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    ServletContext servletContext = mock(ServletContext.class);
    when(request.getServletContext()).thenReturn(servletContext);
    when(servletContext.getContextPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn(REQUEST_URI);
    when(request.getSession()).thenReturn(session);
    // Skips SaveWebPageHitCommand.saveHit / DoNotTrackCommand entirely -- page-hit tracking is
    // unrelated to the archived-item guard under test.
    when(request.getHeader("X-Monitor")).thenReturn("true");
    return request;
  }

  private HttpSession mockSession(UserSession userSession) {
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.CONTROLLER)).thenReturn(new ControllerSession());
    when(session.getAttribute(SessionConstants.USER)).thenReturn(userSession);
    return session;
  }

  @Test
  void serviceReturns404WhenTheMatchedItemIsArchivedOnAPubliclyUnrestrictedRoute() throws Exception {
    HttpServletRequest request = mockRequest(mockSession(new UserSession()));
    HttpServletResponse response = mock(HttpServletResponse.class);
    Page pageRef = unrestrictedShowPage();

    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<WebPageXmlLayoutCommand> webPageXmlLayout = mockStatic(WebPageXmlLayoutCommand.class);
        MockedStatic<LoadSitePropertyCommand> loadSiteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SocialMediaLinkRepository> socialLinks = mockStatic(SocialMediaLinkRepository.class);
        MockedStatic<LoadItemCommand> loadItem = mockStatic(LoadItemCommand.class)) {

      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(anyString())).thenReturn(null);
      webPageXmlLayout.when(() -> WebPageXmlLayoutCommand.retrievePageForRequest(any(), anyString())).thenReturn(pageRef);
      webPageXmlLayout.when(WebPageXmlLayoutCommand::getWidgetLibrary).thenReturn(new HashMap<>());
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadAsMap(anyString())).thenAnswer(inv -> new HashMap<String, String>());
      socialLinks.when(SocialMediaLinkRepository::findAll).thenReturn(Collections.emptyList());
      // The crux of issue #827: excludeArchived must be true here, because unrestrictedShowPage()
      // has no role/group/capability restriction -- a deactivated item must resolve to null just
      // like a non-existent one would.
      loadItem.when(() -> LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(eq(ITEM_UNIQUE_ID), anyLong(), eq(true)))
          .thenReturn(null);

      new PageServlet().service(request, response);

      loadItem.verify(() -> LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(eq(ITEM_UNIQUE_ID), anyLong(), eq(true)));
    }

    verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  void serviceDoesNotReturn404WhenTheMatchedItemIsFoundOnAPubliclyUnrestrictedRoute() throws Exception {
    HttpServletRequest request = mockRequest(mockSession(new UserSession()));
    HttpServletResponse response = mock(HttpServletResponse.class);
    Page pageRef = unrestrictedShowPage();

    Item item = new Item();
    item.setId(42L);
    item.setUniqueId(ITEM_UNIQUE_ID);
    item.setName("A Real, Active Item");
    item.setCollectionId(7L);

    Collection collection = new Collection();
    collection.setId(7L);
    collection.setUniqueId("staff");
    collection.setName("Staff");

    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<WebPageXmlLayoutCommand> webPageXmlLayout = mockStatic(WebPageXmlLayoutCommand.class);
        MockedStatic<WebContainerLayoutCommand> webContainerLayout = mockStatic(WebContainerLayoutCommand.class);
        MockedStatic<LoadSitePropertyCommand> loadSiteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SocialMediaLinkRepository> socialLinks = mockStatic(SocialMediaLinkRepository.class);
        MockedStatic<LoadItemCommand> loadItem = mockStatic(LoadItemCommand.class);
        MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class);
        MockedStatic<LoadCategoryCommand> loadCategory = mockStatic(LoadCategoryCommand.class)) {

      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(anyString())).thenReturn(null);
      webPageXmlLayout.when(() -> WebPageXmlLayoutCommand.retrievePageForRequest(any(), anyString())).thenReturn(pageRef);
      webPageXmlLayout.when(WebPageXmlLayoutCommand::getWidgetLibrary).thenReturn(new HashMap<>());
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadAsMap(anyString())).thenAnswer(inv -> new HashMap<String, String>());
      socialLinks.when(SocialMediaLinkRepository::findAll).thenReturn(Collections.emptyList());
      loadItem.when(() -> LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(eq(ITEM_UNIQUE_ID), anyLong(), eq(true)))
          .thenReturn(item);
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionById(7L)).thenReturn(collection);
      loadCategory.when(() -> LoadCategoryCommand.loadCategoryById(anyLong())).thenReturn(null);

      new PageServlet().service(request, response);

      loadItem.verify(() -> LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(eq(ITEM_UNIQUE_ID), anyLong(), eq(true)));
      // Proves execution actually continued past the archived-item guard -- this call only
      // happens once thisItem != null -- rather than "no 404" being vacuously true for some
      // unrelated reason.
      loadCollection.verify(() -> LoadCollectionCommand.loadCollectionById(7L));
    }

    verify(response, never()).sendError(HttpServletResponse.SC_NOT_FOUND);
  }
}
