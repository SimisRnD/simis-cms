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
import com.simisinc.platform.infrastructure.scheduler.cms.ImageVariantJob;

/**
 * Starts the "Generate missing sizes" backfill (see {@code AdminImageBrowserWidget}) -- finds every
 * image that ought to have a given variant but does not, and queues variant generation for each.
 *
 * <p>
 * Adding a rung to the variant ladder only affects images uploaded afterwards, because variants are
 * generated once at upload. Issue #1422 added a 400px rung to close a 4x hole between the thumbnail
 * and the original; without a backfill every image already in the library keeps overshooting. This
 * is the same shape as {@link ScanForDuplicateImagesCommand#startScan()}.
 * </p>
 *
 * <p>
 * Safe to run more than once: the query only returns images still missing the variant, and
 * {@code ImageVariantRepository.save()} upserts on (image_id, variant_type), so a job that runs
 * twice replaces its own row rather than duplicating it.
 * </p>
 *
 * @author SimIS Inc.
 */
public class RegenerateImageVariantsCommand {

  private RegenerateImageVariantsCommand() {
    // Static utility, not instantiated
  }

  /**
   * @param variantType the variant to backfill, e.g. {@link GenerateImageVariantsCommand#SMALL}
   * @param maxDimension that variant's target size
   * @return how many images were queued
   */
  public static int startBackfill(String variantType, int maxDimension) {
    List<Long> imageIds = ImageRepository.findAllMissingVariant(variantType, maxDimension);
    for (Long imageId : imageIds) {
      BackgroundJobRequest.enqueue(new ImageVariantJob(imageId));
    }
    return imageIds.size();
  }
}
