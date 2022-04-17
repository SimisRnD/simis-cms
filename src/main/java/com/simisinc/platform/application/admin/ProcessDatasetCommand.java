/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.application.admin;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.SaveCollectionCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.infrastructure.scheduler.admin.ProcessDatasetJob;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.scheduling.BackgroundJobRequest;

import java.util.List;

/**
 * Handles the dataset to collection item conversion
 *
 * @author matt rajkowski
 * @created 5/17/18 3:13 PM
 */
public class ProcessDatasetCommand {

  private static Log LOG = LogFactory.getLog(ProcessDatasetCommand.class);

  public static void startProcess(Dataset dataset) throws DataException {
    if (dataset.getId() == -1) {
      throw new DataException("The developer needs to set an existing record id");
    }
    if (StringUtils.isBlank(dataset.getCollectionUniqueId())) {
      throw new DataException("Please choose a collection to save the records into");
    }

    // Prepare the collection
    Collection collection = null;
    if (dataset.getCollectionUniqueId().startsWith("NEW-")) {
      // Determine if the collection exists
      String collectionName = dataset.getCollectionUniqueId().substring(4);
      collection = CollectionRepository.findByName(collectionName);
      if (collection == null) {
        // Create a new collection
        Collection collectionBean = new Collection();
        collectionBean.setName(collectionName);
        collectionBean.setCreatedBy(dataset.getModifiedBy());
        collection = SaveCollectionCommand.saveCollection(collectionBean);
        // Associate it with the dataset
        dataset.setCollectionUniqueId(collection.getUniqueId());
        DatasetRepository.save(dataset);
      }
    } else {
      // Try to load it
      collection = LoadCollectionCommand.loadCollectionByUniqueId(dataset.getCollectionUniqueId());
    }
    if (collection == null) {
      throw new DataException("The specific collection was not found, try another?");
    }

    // Get a file handle

    // Validate that the columns and fields exist
    List<String> columnNames = dataset.getColumnNamesList();
    List<String> fieldMappings = dataset.getFieldMappingsList();
    if (columnNames.isEmpty() || fieldMappings.isEmpty() || columnNames.size() != fieldMappings.size()) {
      throw new DataException("Please make sure fields have been mapped");
    }
    if (!fieldMappings.contains("name")) {
      throw new DataException("A mapping for name must assigned");
    }

    // Validate the dataset's rows/records
    LOG.debug("Validating the dataset... " + dataset.getName());
    boolean isValid = DatasetFileCommand.validateAllRows(dataset);
    if (!isValid) {
      throw new DataException("File is not valid");
    }

    // Mark the dataset as being processed
    LOG.debug("Processing the dataset... " + dataset.getName());
    dataset.setRowsProcessed(1);
    DatasetRepository.save(dataset);

    // Start the background job
    BackgroundJobRequest.enqueue(new ProcessDatasetJob(dataset));
  }

}
