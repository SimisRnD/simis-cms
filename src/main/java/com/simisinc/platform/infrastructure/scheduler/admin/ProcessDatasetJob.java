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

package com.simisinc.platform.infrastructure.scheduler.admin;

import com.simisinc.platform.application.admin.DatasetFileCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

import java.sql.Timestamp;

/**
 * Imports records as items belonging to a collection
 *
 * @author matt rajkowski
 * @created 5/21/18 12:50 PM
 */
@NoArgsConstructor
public class ProcessDatasetJob implements JobRequest {

  private static Log LOG = LogFactory.getLog(ProcessDatasetJob.class);

  @Getter
  @Setter
  private long datasetId = -1;

  @Getter
  @Setter
  private long modifiedByUserId = -1;

  @Getter
  @Setter
  private boolean skipDuplicates = false;

  public ProcessDatasetJob(Dataset dataset) {
    datasetId = dataset.getId();
    modifiedByUserId = dataset.getModifiedBy();
    skipDuplicates = dataset.isSkipDuplicateNames();
  }

  @Override
  public Class<ProcessDatasetJobRequestHandler> getJobRequestHandler() {
    return ProcessDatasetJobRequestHandler.class;
  }

  public static class ProcessDatasetJobRequestHandler implements JobRequestHandler<ProcessDatasetJob> {
    @Override
    @Job(name = "Process a dataset", retries = 1)
    public void run(ProcessDatasetJob jobRequest) {

      // Load dataset
      Dataset dataset = DatasetRepository.findById(jobRequest.getDatasetId());
      if (dataset == null) {
        LOG.error("Dataset not found: " + jobRequest.getDatasetId());
        return;
      }

      // Load collection
      Collection collection = LoadCollectionCommand.loadCollectionByUniqueId(dataset.getCollectionUniqueId());
      if (collection == null) {
        LOG.error("Collection not found: " + dataset.getCollectionUniqueId());
        return;
      }

      // Handle non-persisted values
      long modifiedByUserId = jobRequest.getModifiedByUserId();
      dataset.setModifiedBy(modifiedByUserId);
      boolean skipDuplicates = jobRequest.isSkipDuplicates();
      dataset.setSkipDuplicateNames(skipDuplicates);
      LOG.debug("Skip duplicates? " + skipDuplicates);

      // Run the conversion
      LOG.info("Processing the dataset... " + dataset.getName());
      long startProcessTime = System.currentTimeMillis();
      try {
        DatasetFileCommand.convertFileToCollection(dataset, collection);
      } catch (Exception e) {
        LOG.error("CSV Error", e);
      }

      // Finally
      long endProcessTime = System.currentTimeMillis();
      long totalTime = endProcessTime - startProcessTime;
      dataset.setProcessed(new Timestamp(endProcessTime));
      dataset.setTotalProcessTime(totalTime);
      DatasetRepository.save(dataset);
      LOG.debug("Finished " + totalTime + "ms");
    }
  }
}
