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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.items.SaveTagCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Tag;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;

/**
 * Verifies {@link CollectionTagFormWidget} (issue #632), mirroring the coverage that would apply
 * to {@code CollectionCategoryFormWidget}.
 *
 * @author SimIS Inc.
 */
class CollectionTagFormWidgetTest extends WidgetBase {

  @Test
  void postSavesANewTagAndRedirectsToTheTagList() throws Exception {
    addQueryParameter(widgetContext, "name", "Fiction");
    addQueryParameter(widgetContext, "collectionId", "5");

    try (MockedStatic<SaveTagCommand> saveTagCommand = mockStatic(SaveTagCommand.class)) {
      Tag saved = new Tag();
      saved.setId(1L);
      saved.setCollectionId(5L);
      saveTagCommand.when(() -> SaveTagCommand.saveTag(any())).thenReturn(saved);

      new CollectionTagFormWidget().post(widgetContext);

      assertEquals("/admin/collection-tags?collectionId=5", widgetContext.getRedirect());
      assertNotNull(widgetContext.getSuccessMessage());
    }
  }

  @Test
  void postOnADuplicateNameRedirectsBackToTheExistingTagWithAWarning() throws Exception {
    addQueryParameter(widgetContext, "id", "1");
    addQueryParameter(widgetContext, "name", "Fiction");
    addQueryParameter(widgetContext, "collectionId", "5");

    try (MockedStatic<SaveTagCommand> saveTagCommand = mockStatic(SaveTagCommand.class)) {
      saveTagCommand.when(() -> SaveTagCommand.saveTag(any()))
          .thenThrow(new DataException("A unique name is required"));

      new CollectionTagFormWidget().post(widgetContext);

      assertEquals("/admin/tag?tagId=1", widgetContext.getRedirect());
      assertNotNull(widgetContext.getWarningMessage());
    }
  }

  @Test
  void executeReturnsAnErrorWhenTheCollectionIsMissing() {
    addQueryParameter(widgetContext, "collectionId", "5");
    try (MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class)) {
      collectionRepository.when(() -> CollectionRepository.findById(5L)).thenReturn(null);

      new CollectionTagFormWidget().execute(widgetContext);

      assertNotNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void executeLoadsAnExistingTagByTagIdParameter() {
    addQueryParameter(widgetContext, "tagId", "1");
    Tag tag = new Tag();
    tag.setId(1L);
    tag.setCollectionId(5L);
    tag.setName("Fiction");
    Collection collection = new Collection();
    collection.setId(5L);

    try (MockedStatic<com.simisinc.platform.infrastructure.persistence.items.TagRepository> tagRepository = mockStatic(
        com.simisinc.platform.infrastructure.persistence.items.TagRepository.class);
        MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class)) {
      tagRepository.when(() -> com.simisinc.platform.infrastructure.persistence.items.TagRepository.findById(1L))
          .thenReturn(tag);
      collectionRepository.when(() -> CollectionRepository.findById(5L)).thenReturn(collection);

      new CollectionTagFormWidget().execute(widgetContext);

      assertEquals(tag, widgetContext.getRequest().getAttribute("tag"));
    }
  }
}
