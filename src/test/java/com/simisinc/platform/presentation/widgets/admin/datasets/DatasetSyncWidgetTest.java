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

package com.simisinc.platform.presentation.widgets.admin.datasets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.datasets.DatasetFileCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * A Dataset's mapped Collection can be deleted out from under it. Before this fix, execute()
 * rendered a blank "Mapped Collection" field with no explanation in that case -- the only symptom
 * appeared later, as an error, when an admin clicked Save & Sync. These tests verify execute()
 * now sets a "mappedCollectionMissing" request attribute the JSP can use to show an explicit
 * warning, and only in that specific case (not when the dataset simply hasn't been mapped yet,
 * and not when the mapped collection loads fine).
 *
 * @author elizabeth houser
 */
class DatasetSyncWidgetTest extends WidgetBase {

  private static Dataset datasetMappedTo(String collectionUniqueId) {
    Dataset dataset = new Dataset();
    dataset.setId(42L);
    dataset.setName("Test Dataset");
    dataset.setCollectionUniqueId(collectionUniqueId);
    dataset.setColumnCount(0);
    return dataset;
  }

  @Test
  void executeFlagsMappedCollectionMissingWhenTheCollectionCanNoLongerBeLoaded() throws Exception {
    Dataset dataset = datasetMappedTo("deleted-collection");
    addQueryParameter(widgetContext, "datasetId", "42");

    try (MockedStatic<DatasetRepository> datasetRepo = mockStatic(DatasetRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CategoryRepository> categoryRepo = mockStatic(CategoryRepository.class);
        MockedStatic<ItemRepository> itemRepo = mockStatic(ItemRepository.class);
        MockedStatic<DatasetFileCommand> fileCommand = mockStatic(DatasetFileCommand.class)) {
      datasetRepo.when(() -> DatasetRepository.findById(42L)).thenReturn(dataset);
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByUniqueId("deleted-collection"))
          .thenReturn(null);

      new DatasetSyncWidget().execute(widgetContext);

      assertEquals(Boolean.TRUE, widgetContext.getRequest().getAttribute("mappedCollectionMissing"));
      assertNull(widgetContext.getRequest().getAttribute("collection"));
      categoryRepo.verifyNoInteractions();
    }
  }

  @Test
  void executeDoesNotFlagMappedCollectionMissingWhenTheCollectionLoadsFine() throws Exception {
    Dataset dataset = datasetMappedTo("active-collection");
    addQueryParameter(widgetContext, "datasetId", "42");

    Collection collection = new Collection();
    collection.setId(7L);
    collection.setUniqueId("active-collection");

    try (MockedStatic<DatasetRepository> datasetRepo = mockStatic(DatasetRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CategoryRepository> categoryRepo = mockStatic(CategoryRepository.class);
        MockedStatic<ItemRepository> itemRepo = mockStatic(ItemRepository.class);
        MockedStatic<DatasetFileCommand> fileCommand = mockStatic(DatasetFileCommand.class)) {
      datasetRepo.when(() -> DatasetRepository.findById(42L)).thenReturn(dataset);
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByUniqueId("active-collection"))
          .thenReturn(collection);
      categoryRepo.when(() -> CategoryRepository.findAllByCollectionId(7L)).thenReturn(List.of());

      new DatasetSyncWidget().execute(widgetContext);

      assertNull(widgetContext.getRequest().getAttribute("mappedCollectionMissing"));
      assertEquals(collection, widgetContext.getRequest().getAttribute("collection"));
    }
  }

  @Test
  void executeDoesNotFlagMappedCollectionMissingWhenTheDatasetHasNoMappingYet() throws Exception {
    Dataset dataset = datasetMappedTo(null);
    addQueryParameter(widgetContext, "datasetId", "42");

    try (MockedStatic<DatasetRepository> datasetRepo = mockStatic(DatasetRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CategoryRepository> categoryRepo = mockStatic(CategoryRepository.class);
        MockedStatic<ItemRepository> itemRepo = mockStatic(ItemRepository.class);
        MockedStatic<DatasetFileCommand> fileCommand = mockStatic(DatasetFileCommand.class)) {
      datasetRepo.when(() -> DatasetRepository.findById(42L)).thenReturn(dataset);

      new DatasetSyncWidget().execute(widgetContext);

      assertNull(widgetContext.getRequest().getAttribute("mappedCollectionMissing"));
      assertNull(widgetContext.getRequest().getAttribute("collection"));
      // A blank/unmapped collectionUniqueId must never even reach LoadCollectionCommand
      loadCollection.verifyNoInteractions();
    }
  }
}
