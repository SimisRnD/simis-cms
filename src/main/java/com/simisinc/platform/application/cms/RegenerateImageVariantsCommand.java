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
  /**
   * Queues every image whose variants are in a format the generator no longer produces.
   *
   * <p>Variants are now encoded as WebP rather than inheriting the original's format, but that
   * governs only variants generated after it shipped. {@link #startBackfill} cannot reach the
   * existing library: it selects images *missing* a rung, and these are not missing anything --
   * their variants are present and merely stale. Without this, an established site keeps serving
   * the multi-megabyte PNG renditions it already has, forever, and the admin's existing action
   * truthfully reports that every image already has all of its sizes.
   *
   * <p>Both formats come from {@link GenerateImageVariantsCommand}'s own constants, so the
   * population selected here is exactly the population that method would transcode.
   *
   * <p>Regenerating writes the new rendition at a new path (the extension changes) and updates the
   * variant row to point at it. The superseded file is left on disk; nothing references it, and
   * deleting files is not something a format migration should be doing on its own.
   *
   * @return how many images were queued
   */
  public static int startFormatBackfill() {
    List<Long> imageIds = ImageRepository.findAllWithStaleVariantFormat(
        GenerateImageVariantsCommand.VARIANT_FILE_TYPE,
        GenerateImageVariantsCommand.VARIANT_EXEMPT_SOURCE_FILE_TYPE);
    for (Long imageId : imageIds) {
      BackgroundJobRequest.enqueue(new ImageVariantJob(imageId));
    }
    return imageIds.size();
  }

  public static int startBackfill(String variantType, int maxDimension) {
    List<Long> imageIds = ImageRepository.findAllMissingVariant(variantType, maxDimension);
    for (Long imageId : imageIds) {
      BackgroundJobRequest.enqueue(new ImageVariantJob(imageId));
    }
    return imageIds.size();
  }
}
