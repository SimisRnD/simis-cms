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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.LoadWebPageCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.SaveItemCommand;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;

/**
 * Issue #903: PageServlet's reorderCollectionItem/deactivateCollectionItem/saveCollectionItem
 * mutations (~PageServlet.java 463-590) used to gate only on session-level pageEditMode --
 * EditorPermissionCommand.canEditContent(), a generic sitewide role check with zero collection
 * scoping -- and then act on itemId/collectionId taken straight off request parameters via raw
 * ItemRepository.findById()/CollectionRepository.findById() lookups. Any user holding the generic
 * content-editor role, once they'd toggled edit mode on any page, could POST an itemId/collectionId
 * for a private/restricted collection they were never granted access to and tamper with it, even
 * though the same widget's read path (ItemsListWidget, ~line 77) already resolves collections
 * through LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser -- proving collections do
 * have real per-user/group access restrictions that only the write path ignored.
 *
 * <p>The fix threads every one of the three mutations through
 * {@link LoadCollectionCommand#loadCollectionByIdForAuthorizedUser(long, long)} -- the exact
 * ForAuthorizedUser lookup already used a few hundred lines earlier in the same file to resolve the
 * page-level collection/item (PageServlet.java ~762/775) -- before touching the item/collection.
 * These tests drive {@link PageServlet#service} end-to-end (same technique as
 * PageServletServiceItemArchivedTest) to prove: (1) a content-editor with no access to the item's/
 * collection's ForAuthorizedUser resolution is rejected by each of the three actions and the
 * underlying mutation never runs, and (2) a content-editor who *does* have access still succeeds --
 * regression coverage for the previously-working case.
 *
 * @author elizabeth houser
 */
class PageServletCollectionItemAuthorizationTest {

  private static final long USER_ID = 99L;
  private static final long COLLECTION_ID = 7L;
  private static final long ITEM_ID = 55L;

  /** A logged-in session holding exactly the generic sitewide content-editor role. */
  private UserSession contentEditorSession() {
    List<Role> roles = new ArrayList<>();
    roles.add(new Role("content-editor", "content-editor"));
    User user = new User();
    user.setId(USER_ID);
    user.setRoleList(roles);
    user.setGroupList(new ArrayList<>());
    UserSession session = new UserSession();
    session.login(user);
    return session;
  }

  private HttpSession mockSession(UserSession userSession) {
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.CONTROLLER)).thenReturn(new ControllerSession());
    when(session.getAttribute(SessionConstants.USER)).thenReturn(userSession);
    // The editor has already toggled edit mode on some page earlier in the session -- this is the
    // generic, collection-agnostic flag the three mutations used to rely on exclusively.
    when(session.getAttribute(SessionConstants.PAGE_EDIT_MODE)).thenReturn("true");
    return session;
  }

  private HttpServletRequest mockRequest(HttpSession session, String action, String formToken) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    ServletContext servletContext = mock(ServletContext.class);
    when(request.getServletContext()).thenReturn(servletContext);
    when(servletContext.getContextPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn("/some-page");
    when(request.getSession()).thenReturn(session);
    when(request.getRemoteAddr()).thenReturn("203.0.113.5");
    when(request.getParameter("action")).thenReturn(action);
    when(request.getParameter("token")).thenReturn(formToken);
    return request;
  }

  private HttpServletResponse mockResponse(StringWriter body) throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(body));
    return response;
  }

  // --- reorderCollectionItem -------------------------------------------------------------------

  @Test
  void reorderCollectionItemRejectsAContentEditorWithoutAccessToTheItemsCollection() throws Exception {
    UserSession userSession = contentEditorSession();
    HttpServletRequest request = mockRequest(mockSession(userSession), "reorderCollectionItem", userSession.getFormToken());
    when(request.getParameter("itemId")).thenReturn(String.valueOf(ITEM_ID));
    when(request.getParameter("newOrder")).thenReturn("3");
    StringWriter body = new StringWriter();
    HttpServletResponse response = mockResponse(body);

    Item item = new Item();
    item.setId(ITEM_ID);
    item.setCollectionId(COLLECTION_ID);

    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<LoadSitePropertyCommand> loadSiteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ItemRepository> itemRepository = mockStatic(ItemRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class)) {

      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(anyString())).thenReturn(null);
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      itemRepository.when(() -> ItemRepository.findById(ITEM_ID)).thenReturn(item);
      // The user is a content-editor, but not authorized for this specific (e.g. private/
      // restricted) collection -- the ForAuthorizedUser lookup returns null just like it would for
      // a collection-scoped ItemsListWidget render.
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(eq(COLLECTION_ID), eq(USER_ID)))
          .thenReturn(null);

      new PageServlet().service(request, response);

      itemRepository.verify(() -> ItemRepository.reorderItem(anyLong(), anyLong(), anyInt()), never());
    }

    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
    assertTrue(body.toString().contains("\"success\":false"), "expected a rejection, got: " + body);
  }

  @Test
  void reorderCollectionItemSucceedsForAContentEditorWithAccessToTheItemsCollection() throws Exception {
    UserSession userSession = contentEditorSession();
    HttpServletRequest request = mockRequest(mockSession(userSession), "reorderCollectionItem", userSession.getFormToken());
    when(request.getParameter("itemId")).thenReturn(String.valueOf(ITEM_ID));
    when(request.getParameter("newOrder")).thenReturn("3");
    StringWriter body = new StringWriter();
    HttpServletResponse response = mockResponse(body);

    Item item = new Item();
    item.setId(ITEM_ID);
    item.setCollectionId(COLLECTION_ID);
    Collection collection = new Collection();
    collection.setId(COLLECTION_ID);

    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<LoadSitePropertyCommand> loadSiteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ItemRepository> itemRepository = mockStatic(ItemRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class)) {

      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(anyString())).thenReturn(null);
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      itemRepository.when(() -> ItemRepository.findById(ITEM_ID)).thenReturn(item);
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(eq(COLLECTION_ID), eq(USER_ID)))
          .thenReturn(collection);
      itemRepository.when(() -> ItemRepository.reorderItem(COLLECTION_ID, ITEM_ID, 3)).thenReturn(true);

      new PageServlet().service(request, response);

      itemRepository.verify(() -> ItemRepository.reorderItem(COLLECTION_ID, ITEM_ID, 3), times(1));
    }

    verify(response, never()).setStatus(HttpServletResponse.SC_NOT_FOUND);
    assertTrue(body.toString().contains("\"success\":true"), "expected success, got: " + body);
  }

  // --- deactivateCollectionItem ------------------------------------------------------------------

  @Test
  void deactivateCollectionItemRejectsAContentEditorWithoutAccessToTheItemsCollection() throws Exception {
    UserSession userSession = contentEditorSession();
    HttpServletRequest request = mockRequest(mockSession(userSession), "deactivateCollectionItem", userSession.getFormToken());
    when(request.getParameter("itemId")).thenReturn(String.valueOf(ITEM_ID));
    StringWriter body = new StringWriter();
    HttpServletResponse response = mockResponse(body);

    Item item = new Item();
    item.setId(ITEM_ID);
    item.setCollectionId(COLLECTION_ID);

    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<LoadSitePropertyCommand> loadSiteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ItemRepository> itemRepository = mockStatic(ItemRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class)) {

      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(anyString())).thenReturn(null);
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      itemRepository.when(() -> ItemRepository.findById(ITEM_ID)).thenReturn(item);
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(eq(COLLECTION_ID), eq(USER_ID)))
          .thenReturn(null);

      new PageServlet().service(request, response);

      itemRepository.verify(() -> ItemRepository.save(any(Item.class)), never());
    }

    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
    assertTrue(body.toString().contains("\"success\":false"), "expected a rejection, got: " + body);
  }

  @Test
  void deactivateCollectionItemSucceedsForAContentEditorWithAccessToTheItemsCollection() throws Exception {
    UserSession userSession = contentEditorSession();
    HttpServletRequest request = mockRequest(mockSession(userSession), "deactivateCollectionItem", userSession.getFormToken());
    when(request.getParameter("itemId")).thenReturn(String.valueOf(ITEM_ID));
    StringWriter body = new StringWriter();
    HttpServletResponse response = mockResponse(body);

    Item item = new Item();
    item.setId(ITEM_ID);
    item.setCollectionId(COLLECTION_ID);
    Collection collection = new Collection();
    collection.setId(COLLECTION_ID);

    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<LoadSitePropertyCommand> loadSiteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ItemRepository> itemRepository = mockStatic(ItemRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class)) {

      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(anyString())).thenReturn(null);
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      itemRepository.when(() -> ItemRepository.findById(ITEM_ID)).thenReturn(item);
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(eq(COLLECTION_ID), eq(USER_ID)))
          .thenReturn(collection);
      itemRepository.when(() -> ItemRepository.save(any(Item.class))).thenReturn(item);

      new PageServlet().service(request, response);

      itemRepository.verify(() -> ItemRepository.save(item), times(1));
    }

    verify(response, never()).setStatus(HttpServletResponse.SC_NOT_FOUND);
    assertTrue(body.toString().contains("\"success\":true"), "expected success, got: " + body);
  }

  // --- saveCollectionItem ---------------------------------------------------------------------

  @Test
  void saveCollectionItemRejectsAContentEditorWithoutAccessToTheCollection() throws Exception {
    UserSession userSession = contentEditorSession();
    HttpServletRequest request = mockRequest(mockSession(userSession), "saveCollectionItem", userSession.getFormToken());
    when(request.getParameter("collectionId")).thenReturn(String.valueOf(COLLECTION_ID));
    when(request.getParameter("itemName")).thenReturn("Injected Item");
    when(request.getParameter("itemSummary")).thenReturn("Attacker-supplied summary");
    StringWriter body = new StringWriter();
    HttpServletResponse response = mockResponse(body);

    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<LoadSitePropertyCommand> loadSiteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class);
        MockedStatic<SaveItemCommand> saveItemCommand = mockStatic(SaveItemCommand.class)) {

      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(anyString())).thenReturn(null);
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      // The content-editor is not authorized for this (e.g. private/restricted) collection.
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(eq(COLLECTION_ID), eq(USER_ID)))
          .thenReturn(null);

      new PageServlet().service(request, response);

      saveItemCommand.verify(() -> SaveItemCommand.saveItem(any(Item.class)), never());
    }

    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
    assertTrue(body.toString().contains("\"success\":false"), "expected a rejection, got: " + body);
  }

  @Test
  void saveCollectionItemSucceedsForAContentEditorWithAccessToTheCollection() throws Exception {
    UserSession userSession = contentEditorSession();
    HttpServletRequest request = mockRequest(mockSession(userSession), "saveCollectionItem", userSession.getFormToken());
    when(request.getParameter("collectionId")).thenReturn(String.valueOf(COLLECTION_ID));
    when(request.getParameter("itemName")).thenReturn("A New Staff Member");
    when(request.getParameter("itemSummary")).thenReturn("Joined this week");
    StringWriter body = new StringWriter();
    HttpServletResponse response = mockResponse(body);

    Collection collection = new Collection();
    collection.setId(COLLECTION_ID);

    Item savedItem = new Item();
    savedItem.setId(1234L);
    savedItem.setCollectionId(COLLECTION_ID);

    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<LoadSitePropertyCommand> loadSiteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class);
        MockedStatic<ItemRepository> itemRepository = mockStatic(ItemRepository.class);
        MockedStatic<SaveItemCommand> saveItemCommand = mockStatic(SaveItemCommand.class)) {

      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(anyString())).thenReturn(null);
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(eq(COLLECTION_ID), eq(USER_ID)))
          .thenReturn(collection);
      itemRepository.when(() -> ItemRepository.getNextItemOrder(COLLECTION_ID)).thenReturn(1);
      saveItemCommand.when(() -> SaveItemCommand.saveItem(any(Item.class))).thenReturn(savedItem);

      new PageServlet().service(request, response);

      saveItemCommand.verify(() -> SaveItemCommand.saveItem(any(Item.class)), times(1));
    }

    verify(response, never()).setStatus(HttpServletResponse.SC_NOT_FOUND);
    assertTrue(body.toString().contains("\"success\":true"), "expected success, got: " + body);
  }
}
