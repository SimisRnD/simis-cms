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
import com.simisinc.platform.application.items.SaveCategoryCommand;
import com.simisinc.platform.domain.model.items.Category;

/**
 * Verifies {@link CollectionCategoryFormWidget}'s server-side CSS color validation, which guards
 * against a data-manager injecting arbitrary CSS/selectors into every public page for a
 * collection's categories via headerTextColor/headerBgColor, which are concatenated unescaped
 * into an inline &lt;style&gt; block in main.jsp -- the same vector CollectionThemeEditorWidget
 * guards against for the parent Collection's own color fields.
 *
 * @author SimIS Inc.
 */
class CollectionCategoryFormWidgetTest extends WidgetBase {

  @Test
  void postSavesLegitimateHexColorValues() throws Exception {
    addQueryParameter(widgetContext, "collectionId", "5");
    addQueryParameter(widgetContext, "headerBgColor", "#ffffff");
    addQueryParameter(widgetContext, "headerTextColor", "#000000");

    try (MockedStatic<SaveCategoryCommand> saveCategoryCommand = mockStatic(SaveCategoryCommand.class)) {
      Category savedCategory = new Category();
      savedCategory.setId(9L);
      savedCategory.setCollectionId(5L);
      saveCategoryCommand.when(() -> SaveCategoryCommand.saveCategory(any())).thenReturn(savedCategory);

      new CollectionCategoryFormWidget().post(widgetContext);

      saveCategoryCommand.verify(
          () -> SaveCategoryCommand.saveCategory(
              org.mockito.ArgumentMatchers
                  .argThat(c -> "#ffffff".equals(c.getHeaderBgColor()) && "#000000".equals(c.getHeaderTextColor()))),
          times(1));
      assertNull(widgetContext.getErrorMessage());
      assertEquals("/admin/collection-categories?collectionId=5", widgetContext.getRedirect());
    }
  }

  @Test
  void postRejectsAHeaderBgColorThatBreaksOutOfTheCssDeclaration() throws Exception {
    addQueryParameter(widgetContext, "collectionId", "5");
    addQueryParameter(widgetContext, "headerBgColor", "red;}body{display:none}/*");

    try (MockedStatic<SaveCategoryCommand> saveCategoryCommand = mockStatic(SaveCategoryCommand.class)) {
      new CollectionCategoryFormWidget().post(widgetContext);

      saveCategoryCommand.verify(() -> SaveCategoryCommand.saveCategory(any()), never());
      assertNotNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void postRejectsAHeaderTextColorThatBreaksOutOfTheCssDeclaration() throws Exception {
    addQueryParameter(widgetContext, "collectionId", "5");
    addQueryParameter(widgetContext, "headerTextColor", "red;}body{display:none}/*");

    try (MockedStatic<SaveCategoryCommand> saveCategoryCommand = mockStatic(SaveCategoryCommand.class)) {
      new CollectionCategoryFormWidget().post(widgetContext);

      saveCategoryCommand.verify(() -> SaveCategoryCommand.saveCategory(any()), never());
      assertNotNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void postTreatsBlankColorValuesAsUnsetAndSaves() throws Exception {
    addQueryParameter(widgetContext, "collectionId", "5");
    addQueryParameter(widgetContext, "headerBgColor", "");
    addQueryParameter(widgetContext, "headerTextColor", "");

    try (MockedStatic<SaveCategoryCommand> saveCategoryCommand = mockStatic(SaveCategoryCommand.class)) {
      Category savedCategory = new Category();
      savedCategory.setId(9L);
      savedCategory.setCollectionId(5L);
      saveCategoryCommand.when(() -> SaveCategoryCommand.saveCategory(any())).thenReturn(savedCategory);

      new CollectionCategoryFormWidget().post(widgetContext);

      saveCategoryCommand.verify(
          () -> SaveCategoryCommand.saveCategory(
              org.mockito.ArgumentMatchers.argThat(c -> c.getHeaderBgColor() == null && c.getHeaderTextColor() == null)),
          times(1));
      assertNull(widgetContext.getErrorMessage());
    }
  }
}
