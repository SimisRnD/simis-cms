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

package com.simisinc.platform.application.datasets;

import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemSpecification;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Timestamp;
import java.util.List;

/**
 * Deletes dataset item records
 *
 * @author matt rajkowski
 * @created 8/10/22 5:00 PM
 */
public class DeleteDatasetItemsCommand {

  private static Log LOG = LogFactory.getLog(DeleteDatasetItemsCommand.class);

  public static int deleteItemsForDataset(Dataset dataset, Timestamp optionalSyncTimestamp) throws Exception {
    // Determine the constraints for Paging
    DataConstraints constraints = new DataConstraints(1, 100, "item_id");

    // Configure the record query
    ItemSpecification specification = new ItemSpecification();
    specification.setDatasetId(dataset.getId());
    if (optionalSyncTimestamp != null) {
      specification.setDatasetSyncTimestampThreshold(optionalSyncTimestamp);
    }

    // Limit to the record count
    long maxRuns = (constraints.getTotalRecordCount() / 100);
    if (maxRuns < 10_000) {
      maxRuns = 10_000;
    }

    // Remove the records in batches
    int deleteCount = 0;
    int runCount = 0;
    while (runCount == 0 || constraints.getTotalRecordCount() > 0) {
      ++runCount;
      List<Item> itemList = ItemRepository.findAll(specification, constraints);
      for (Item item : itemList) {
        ItemRepository.remove(item);
        constraints.setTotalRecordCount(constraints.getTotalRecordCount() - 1);
        ++deleteCount;
      }
      if (runCount > maxRuns) {
        throw new Exception("An error occurred, the dataset items were not fully removed");
      }
    }
    return deleteCount;
  }
}
