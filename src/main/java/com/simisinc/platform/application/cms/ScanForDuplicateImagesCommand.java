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

package com.simisinc.platform.application.cms;

import java.util.List;

import org.jobrunr.scheduling.BackgroundJobRequest;

import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;
import com.simisinc.platform.infrastructure.scheduler.cms.ImageHashJob;

/**
 * Starts the "Scan for Duplicates" backfill (see {@code AdminImageBrowserWidget}) -- finds every
 * image with no {@code file_hash} yet and enqueues one background job per image to compute it,
 * mirroring {@code ProcessDatasetCommand.startProcess}'s shape.
 * <p>
 * Deliberately not an automatic startup migration: hashing every existing image's bytes off disk
 * could exceed {@code DatabaseCommand}'s migration lock window on a large library, and enqueuing
 * per-image jobs (rather than one big batch job) means the admin can watch real progress in the
 * Job Queue Dashboard and a re-run only re-enqueues whatever is still un-hashed.
 *
 * @author SimIS Inc.
 */
public class ScanForDuplicateImagesCommand {

  /**
   * @return how many images were enqueued for hashing
   */
  public static int startScan() {
    List<Long> unhashedImageIds = ImageRepository.findAllUnhashed();
    for (Long imageId : unhashedImageIds) {
      BackgroundJobRequest.enqueue(new ImageHashJob(imageId));
    }
    return unhashedImageIds.size();
  }
}
