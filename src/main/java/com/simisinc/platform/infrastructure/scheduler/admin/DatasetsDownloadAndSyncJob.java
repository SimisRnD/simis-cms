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

import com.simisinc.platform.application.datasets.DatasetFileCommand;
import com.simisinc.platform.application.datasets.ProcessDatasetCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import lombok.NoArgsConstructor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;

import java.util.List;

/**
 * Downloads scheduled datasets and kicks off processing
 *
 * @author matt rajkowski
 * @created 8/9/22 4:25 PM
 */
@NoArgsConstructor
public class DatasetsDownloadAndSyncJob {

  private static Log LOG = LogFactory.getLog(DatasetsDownloadAndSyncJob.class);

  @Job(name = "Download scheduled datasets")
  public static void execute() {

    // Retrieve a list of datasets that are ready and enabled to be downloaded
    List<Dataset> datasetList = DatasetRepository.findAllScheduledForDownload();
    for (Dataset dataset : datasetList) {
      // Try to mark it as being downloaded
      if (!DatasetRepository.markAsQueuedIfAllowed(dataset)) {
        continue;
      }
      LOG.debug("Dataset to download: " + dataset.getName());
      try {
        // Do the download
        DatasetFileCommand.handleRemoteFileDownload(dataset, dataset.getModifiedBy());
        // Mark it as updated/unlocked
        DatasetRepository.markAsUnqueued(dataset);
      } catch (Exception e) {
        // Mark this for trying again later, record the error message
        DatasetRepository.markToRetryDownload(dataset, e.getMessage());
      }

      // Check if the dataset has sync'ing enabled
      if (dataset.getSyncEnabled()) {
        try {
          // Start the background job
          ProcessDatasetCommand.startProcess(dataset);
          LOG.debug("ProcessDatasetJob enqueued");
        } catch (Exception e) {
          LOG.debug("Could not start the ProcessDatasetJob due to validation? " + e.getMessage());
        }
      }
    }
  }
}
