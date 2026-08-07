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

package com.simisinc.platform.presentation.widgets.admin.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;

/**
 * Verifies {@link CollectionThemeEditorWidget}'s server-side CSS color validation, which guards
 * against a data-manager injecting arbitrary CSS/selectors into every public page for a
 * Collection via the color fields (headerTextColor, headerBgColor, etc.), which are concatenated
 * unescaped into an inline &lt;style&gt; block in main.jsp.
 *
 * @author SimIS Inc.
 */
class CollectionThemeEditorWidgetTest extends WidgetBase {

  private Collection existingCollection(long id) {
    Collection collection = new Collection();
    collection.setId(id);
    return collection;
  }

  @Test
  void postSavesALegitimateHexColorValue() throws Exception {
    addQueryParameter(widgetContext, "collectionId", "5");
    addQueryParameter(widgetContext, "headerBgColor", "#ffffff");

    try (MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class)) {
      collectionRepository.when(() -> CollectionRepository.findById(5L)).thenReturn(existingCollection(5L));
      collectionRepository.when(() -> CollectionRepository.updateTheme(any())).thenReturn(existingCollection(5L));

      new CollectionThemeEditorWidget().post(widgetContext);

      collectionRepository.verify(
          () -> CollectionRepository.updateTheme(
              org.mockito.ArgumentMatchers.argThat(c -> "#ffffff".equals(c.getHeaderBgColor()))),
          times(1));
      assertNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void postRejectsAValueThatBreaksOutOfTheCssDeclarationWithSemicolonAndBrace() throws Exception {
    addQueryParameter(widgetContext, "collectionId", "5");
    addQueryParameter(widgetContext, "headerBgColor", "red;}body{display:none}/*");

    try (MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class)) {
      collectionRepository.when(() -> CollectionRepository.findById(5L)).thenReturn(existingCollection(5L));

      new CollectionThemeEditorWidget().post(widgetContext);

      collectionRepository.verify(() -> CollectionRepository.updateTheme(any()), never());
      assertNotNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void postRejectsAValueContainingACurlyBrace() throws Exception {
    addQueryParameter(widgetContext, "collectionId", "5");
    addQueryParameter(widgetContext, "menuBgColor", "#fff{}");

    try (MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class)) {
      collectionRepository.when(() -> CollectionRepository.findById(5L)).thenReturn(existingCollection(5L));

      new CollectionThemeEditorWidget().post(widgetContext);

      collectionRepository.verify(() -> CollectionRepository.updateTheme(any()), never());
      assertNotNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void postRejectsAValueContainingACssCommentOpener() throws Exception {
    addQueryParameter(widgetContext, "collectionId", "5");
    addQueryParameter(widgetContext, "menuTextColor", "#fff/*x*/");

    try (MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class)) {
      collectionRepository.when(() -> CollectionRepository.findById(5L)).thenReturn(existingCollection(5L));

      new CollectionThemeEditorWidget().post(widgetContext);

      collectionRepository.verify(() -> CollectionRepository.updateTheme(any()), never());
      assertNotNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void postSavesAValidRgbColorValue() throws Exception {
    addQueryParameter(widgetContext, "collectionId", "5");
    addQueryParameter(widgetContext, "menuActiveBgColor", "rgb(12, 200, 45)");

    try (MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class)) {
      collectionRepository.when(() -> CollectionRepository.findById(5L)).thenReturn(existingCollection(5L));
      collectionRepository.when(() -> CollectionRepository.updateTheme(any())).thenReturn(existingCollection(5L));

      new CollectionThemeEditorWidget().post(widgetContext);

      collectionRepository.verify(
          () -> CollectionRepository.updateTheme(
              org.mockito.ArgumentMatchers.argThat(c -> "rgb(12, 200, 45)".equals(c.getMenuActiveBgColor()))),
          times(1));
      assertNull(widgetContext.getErrorMessage());
      assertEquals("/", widgetContext.getRedirect());
    }
  }

  @Test
  void postRejectsAnInvalidHeaderTextColor() throws Exception {
    addQueryParameter(widgetContext, "collectionId", "5");
    addQueryParameter(widgetContext, "headerTextColor", "red;}body{display:none}/*");

    try (MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class)) {
      collectionRepository.when(() -> CollectionRepository.findById(5L)).thenReturn(existingCollection(5L));

      new CollectionThemeEditorWidget().post(widgetContext);

      collectionRepository.verify(() -> CollectionRepository.updateTheme(any()), never());
      assertNotNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void postRejectsAnInvalidMenuBorderColor() throws Exception {
    addQueryParameter(widgetContext, "collectionId", "5");
    addQueryParameter(widgetContext, "menuBorderColor", "red;}body{display:none}/*");

    try (MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class)) {
      collectionRepository.when(() -> CollectionRepository.findById(5L)).thenReturn(existingCollection(5L));

      new CollectionThemeEditorWidget().post(widgetContext);

      collectionRepository.verify(() -> CollectionRepository.updateTheme(any()), never());
      assertNotNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void postRejectsAnInvalidMenuActiveTextColor() throws Exception {
    addQueryParameter(widgetContext, "collectionId", "5");
    addQueryParameter(widgetContext, "menuActiveTextColor", "red;}body{display:none}/*");

    try (MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class)) {
      collectionRepository.when(() -> CollectionRepository.findById(5L)).thenReturn(existingCollection(5L));

      new CollectionThemeEditorWidget().post(widgetContext);

      collectionRepository.verify(() -> CollectionRepository.updateTheme(any()), never());
      assertNotNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void postRejectsAnInvalidMenuActiveBorderColor() throws Exception {
    addQueryParameter(widgetContext, "collectionId", "5");
    addQueryParameter(widgetContext, "menuActiveBorderColor", "red;}body{display:none}/*");

    try (MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class)) {
      collectionRepository.when(() -> CollectionRepository.findById(5L)).thenReturn(existingCollection(5L));

      new CollectionThemeEditorWidget().post(widgetContext);

      collectionRepository.verify(() -> CollectionRepository.updateTheme(any()), never());
      assertNotNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void postRejectsAnInvalidMenuHoverTextColor() throws Exception {
    addQueryParameter(widgetContext, "collectionId", "5");
    addQueryParameter(widgetContext, "menuHoverTextColor", "red;}body{display:none}/*");

    try (MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class)) {
      collectionRepository.when(() -> CollectionRepository.findById(5L)).thenReturn(existingCollection(5L));

      new CollectionThemeEditorWidget().post(widgetContext);

      collectionRepository.verify(() -> CollectionRepository.updateTheme(any()), never());
      assertNotNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void postRejectsAnInvalidMenuHoverBorderColor() throws Exception {
    addQueryParameter(widgetContext, "collectionId", "5");
    addQueryParameter(widgetContext, "menuHoverBorderColor", "red;}body{display:none}/*");

    try (MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class)) {
      collectionRepository.when(() -> CollectionRepository.findById(5L)).thenReturn(existingCollection(5L));

      new CollectionThemeEditorWidget().post(widgetContext);

      collectionRepository.verify(() -> CollectionRepository.updateTheme(any()), never());
      assertNotNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void postTreatsABlankColorValueAsUnsetAndSaves() throws Exception {
    addQueryParameter(widgetContext, "collectionId", "5");
    addQueryParameter(widgetContext, "menuHoverBgColor", "");

    try (MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class)) {
      collectionRepository.when(() -> CollectionRepository.findById(5L)).thenReturn(existingCollection(5L));
      collectionRepository.when(() -> CollectionRepository.updateTheme(any())).thenReturn(existingCollection(5L));

      new CollectionThemeEditorWidget().post(widgetContext);

      collectionRepository.verify(
          () -> CollectionRepository.updateTheme(
              org.mockito.ArgumentMatchers.argThat(c -> c.getMenuHoverBgColor() == null || c.getMenuHoverBgColor().isEmpty())),
          times(1));
      assertNull(widgetContext.getErrorMessage());
    }
  }
}
