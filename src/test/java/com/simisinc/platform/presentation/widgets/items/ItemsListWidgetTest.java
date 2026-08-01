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
}
