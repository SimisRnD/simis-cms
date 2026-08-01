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

package com.simisinc.platform.presentation.widgets.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * @author SimIS Inc.
 */
class ItemsListWidgetTest extends WidgetBase {

  private static Collection collection(long id, String uniqueId) {
    Collection collection = new Collection();
    collection.setId(id);
    collection.setUniqueId(uniqueId);
    return collection;
  }

  private WidgetContext executeWithCollection() {
    try (MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class);
        MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class)) {
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(any(), anyLong()))
          .thenReturn(collection(1L, "widgets"));
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      return new ItemsListWidget().execute(widgetContext);
    }
  }

  @Test
  void executeIgnoresTheRawEditModeRequestParameterWithNoPageEditModeAttribute() {
    // Before the fix, a bare `?editMode=true` on the query string -- with no session-backed
    // pageEditMode attribute at all -- was enough on its own to flip isEditMode true for any
    // logged-in visitor, since the vulnerable code OR'd the raw request parameter in directly.
    widgetContext.getRequest().setAttribute("editMode", "true");

    WidgetContext result = executeWithCollection();

    assertEquals("false", result.getRequest().getAttribute("isEditMode"));
  }

  @Test
  void executeIgnoresTheRawEditModeRequestParameterWhenLoggedOut() {
    // Same exploit as above, but for the anonymous case the bug report called out explicitly.
    logout(widgetContext);
    widgetContext.getRequest().setAttribute("editMode", "true");

    WidgetContext result = executeWithCollection();

    assertEquals("false", result.getRequest().getAttribute("isEditMode"));
  }

  @Test
  void executeSetsEditModeWhenPageServletHasPublishedTheSessionBackedPageEditModeAttribute() {
    // PageServlet only ever publishes "pageEditMode"="true" after EditorPermissionCommand
    // .canEditContent has passed for the current request (see PageServlet.java) -- this is the
    // legitimate authenticated-editor path this widget must still honor.
    widgetContext.getRequest().setAttribute("pageEditMode", "true");

    WidgetContext result = executeWithCollection();

    assertEquals("true", result.getRequest().getAttribute("isEditMode"));
  }

  @Test
  void executeIgnoresTheRawEditModeParamEvenWhenPageEditModeIsExplicitlyFalse() {
    // PageServlet publishes pageEditMode unconditionally on every request (including "false"), so
    // this is the realistic shape of an unauthorized request on a page with an active editor
    // session belonging to someone else -- the raw param must still not leak through.
    widgetContext.getRequest().setAttribute("pageEditMode", "false");
    widgetContext.getRequest().setAttribute("editMode", "true");

    WidgetContext result = executeWithCollection();

    assertEquals("false", result.getRequest().getAttribute("isEditMode"));
  }

  @Test
  void executeDefaultsEditModeToFalseWhenNeitherAttributeNorParamIsPresent() {
    WidgetContext result = executeWithCollection();

    assertEquals("false", result.getRequest().getAttribute("isEditMode"));
  }

  // ── No-collection placeholder (issue #817) ────────────────────────────────
  //
  // Before the fix, a widget with no collectionUniqueId preference (the state of every
  // freshly-added itemsList widget -- there is no default and no picker) rendered nothing at all:
  // execute() returned null with zero markup, so there was nothing in the composition canvas to
  // hover to reach the per-widget "Prefs" affordance that would let an editor fix it. These tests
  // cover the fix: an edit-mode, layout-builder-permitted placeholder that points at
  // /admin/collections, with the real-visitor/no-permission/valid-collection paths left unchanged.

  @Test
  void executeRendersAnEditorPlaceholderWhenNoCollectionUniqueIdIsSetAndUserCanBuildLayout() {
    // No "collectionUniqueId" preference at all -- the exact state of a widget just added via the
    // canvas's "+Widget" picker with no prefsJson.
    setRoles(widgetContext, CONTENT_MANAGER);
    request.setAttribute("pageEditMode", "true");

    WidgetContext result = new ItemsListWidget().execute(widgetContext);

    assertNotNull(result, "must render a placeholder, not silently return null");
    assertEquals(ItemsListWidget.NO_COLLECTION_JSP, result.getJsp());
  }

  @Test
  void executeRendersAnEditorPlaceholderWhenCollectionUniqueIdDoesNotResolve() {
    // A collectionUniqueId preference IS set, but points at a collection that no longer exists (or
    // that the current user cannot access) -- LoadCollectionCommand still returns null, same as the
    // unset case, and the issue calls out both as needing the same placeholder.
    preferences.put("collectionUniqueId", "does-not-exist");
    setRoles(widgetContext, ADMIN);
    request.setAttribute("pageEditMode", "true");

    try (MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class)) {
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(any(), anyLong()))
          .thenReturn(null);

      WidgetContext result = new ItemsListWidget().execute(widgetContext);

      assertNotNull(result, "must render a placeholder, not silently return null");
      assertEquals(ItemsListWidget.NO_COLLECTION_JSP, result.getJsp());
    }
  }

  @Test
  void executeReturnsNullForAnAnonymousVisitorEvenWithNoCollectionConfigured() {
    // The regression that matters most: this placeholder is an editor-only affordance and must
    // never leak to a real site visitor, regardless of what pageEditMode happens to be.
    logout(widgetContext);
    request.setAttribute("pageEditMode", "true");

    WidgetContext result = new ItemsListWidget().execute(widgetContext);

    assertNull(result, "an anonymous visitor must still see nothing, not the configure placeholder");
  }

  @Test
  void executeReturnsNullWhenPageEditModeIsFalseAndNoCollectionIsConfigured() {
    // The ordinary, out-of-canvas page render: no visual editor session at all.
    setRoles(widgetContext, CONTENT_MANAGER);

    WidgetContext result = new ItemsListWidget().execute(widgetContext);

    assertNull(result, "outside edit mode, a misconfigured widget must still render nothing");
  }

  @Test
  void executeReturnsNullWhenPageEditModeTrueButUserCannotBuildLayout() {
    // content-editor holds EditorPermissionCommand.canEditContent() (so pageEditMode can be "true"
    // for them) but is deliberately excluded from canBuildLayout() -- the "+Widget"/"Prefs" canvas
    // controls this placeholder points an editor toward are layout-builder-tier, matching
    // TableWidget's precedent for the same pageEditMode-is-not-enough-on-its-own decision.
    setRoles(widgetContext, CONTENT_EDITOR);
    request.setAttribute("pageEditMode", "true");

    WidgetContext result = new ItemsListWidget().execute(widgetContext);

    assertNull(result, "a content-editor without layout-build rights must still see nothing");
  }

  @Test
  void executeReturnsNullWhenPageEditModeTrueButUserHasNoRolesAtAll() {
    // The default logged-in test user (see WidgetBase.login) has no roles -- pageEditMode alone
    // must not be sufficient.
    request.setAttribute("pageEditMode", "true");

    WidgetContext result = new ItemsListWidget().execute(widgetContext);

    assertNull(result);
  }

  @Test
  void executeWithAValidAccessibleCollectionRendersNormallyEvenInEditModeWithPermission() {
    // The fix must not change anything about the already-working path: a real, accessible
    // collection renders its normal list JSP, never the placeholder, regardless of edit mode.
    // showWhenEmpty=true so execute() runs all the way through to setJsp() instead of taking its
    // (pre-existing, unrelated) early "no items found" return -- see the plain
    // executeWithCollection() tests below for that default-empty-list path.
    setRoles(widgetContext, CONTENT_MANAGER);
    request.setAttribute("pageEditMode", "true");
    preferences.put("showWhenEmpty", "true");

    WidgetContext result = executeWithCollection();

    assertEquals(ItemsListWidget.JSP, result.getJsp());
    assertNotNull(result.getRequest().getAttribute("collection"));
  }

  @Test
  void executeWithAValidAccessibleCollectionRendersNormallyOutsideEditMode() {
    preferences.put("showWhenEmpty", "true");

    WidgetContext result = executeWithCollection();

    assertEquals(ItemsListWidget.JSP, result.getJsp());
    assertNotNull(result.getRequest().getAttribute("collection"));
  }

  @Test
  void executeWithAValidAccessibleCollectionButNoItemsStillReturnsContextNotThePlaceholder() {
    // Pre-existing behavior for a real, empty collection (showWhenEmpty defaults to false): execute()
    // returns the context without ever calling setJsp(). The fix must not divert this path to the
    // no-collection placeholder -- a collection that resolved successfully is not a "no collection"
    // case, even when it happens to have zero items.
    WidgetContext result = executeWithCollection();

    assertNotNull(result);
    assertNull(result.getJsp());
    assertNotNull(result.getRequest().getAttribute("collection"));
  }
}
