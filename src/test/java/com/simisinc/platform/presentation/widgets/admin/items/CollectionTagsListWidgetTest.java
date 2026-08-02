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
import static org.mockito.Mockito.mockStatic;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.items.DeleteTagCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Tag;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.infrastructure.persistence.items.TagRepository;

/**
 * Verifies {@link CollectionTagsListWidget} (issue #632), mirroring the coverage that would apply
 * to {@code CollectionCategoriesListWidget}.
 *
 * @author SimIS Inc.
 */
class CollectionTagsListWidgetTest extends WidgetBase {

  @Test
  void executeReturnsAnErrorWhenTheCollectionIsMissing() {
    addQueryParameter(widgetContext, "collectionId", "5");
    try (MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class)) {
      collectionRepository.when(() -> CollectionRepository.findById(5L)).thenReturn(null);

      new CollectionTagsListWidget().execute(widgetContext);

      assertNotNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void executeLoadsTheCollectionsTagList() {
    addQueryParameter(widgetContext, "collectionId", "5");
    Collection collection = new Collection();
    collection.setId(5L);
    Tag tag = new Tag();
    tag.setId(1L);
    tag.setName("Fiction");

    try (MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class);
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class)) {
      collectionRepository.when(() -> CollectionRepository.findById(5L)).thenReturn(collection);
      tagRepository.when(() -> TagRepository.findAllByCollectionId(5L)).thenReturn(List.of(tag));

      new CollectionTagsListWidget().execute(widgetContext);

      assertEquals(List.of(tag), widgetContext.getRequest().getAttribute("tagList"));
    }
  }

  @Test
  void deleteRemovesTheTagAndRedirectsToTheCollection() {
    addQueryParameter(widgetContext, "tagId", "1");
    Tag tag = new Tag();
    tag.setId(1L);
    tag.setCollectionId(5L);

    try (MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<DeleteTagCommand> deleteTagCommand = mockStatic(DeleteTagCommand.class)) {
      tagRepository.when(() -> TagRepository.findById(1L)).thenReturn(tag);

      new CollectionTagsListWidget().delete(widgetContext);

      deleteTagCommand.verify(() -> DeleteTagCommand.deleteTag(tag));
      assertEquals("/admin/collection-details?collectionId=5", widgetContext.getRedirect());
    }
  }

  @Test
  void deleteDoesNothingWhenNoTagIdWasSupplied() {
    try (MockedStatic<DeleteTagCommand> deleteTagCommand = mockStatic(DeleteTagCommand.class)) {
      new CollectionTagsListWidget().delete(widgetContext);

      deleteTagCommand.verifyNoInteractions();
      assertNull(widgetContext.getRedirect());
    }
  }
}
