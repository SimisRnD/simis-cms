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

package com.simisinc.platform.infrastructure.scheduler.cms;

import java.sql.Timestamp;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

import com.simisinc.platform.application.cms.GenerateImageVariantsCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.domain.model.cms.ImageVariant;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Generates an uploaded image's resized variants in the background, off the upload request's
 * critical path (issue #411). Enqueued from {@code ImageUploadWidget} once the original has been
 * saved.
 *
 * @author SimIS Inc.
 */
@NoArgsConstructor
public class ImageVariantJob implements JobRequest {

  private static final Log LOG = LogFactory.getLog(ImageVariantJob.class);

  @Getter
  @Setter
  private long imageId = -1;

  public ImageVariantJob(long imageId) {
    this.imageId = imageId;
  }

  @Override
  public Class<ImageVariantJobRequestHandler> getJobRequestHandler() {
    return ImageVariantJobRequestHandler.class;
  }

  public static class ImageVariantJobRequestHandler implements JobRequestHandler<ImageVariantJob> {
    @Override
    @Job(name = "Generate image variants", retries = 1)
    public void run(ImageVariantJob jobRequest) {
      Image image = ImageRepository.findById(jobRequest.getImageId());
      if (image == null) {
        LOG.error("Image not found: " + jobRequest.getImageId());
        return;
      }

      List<ImageVariant> variants = GenerateImageVariantsCommand.generateVariants(image);
      LOG.info("Generated " + variants.size() + " variant(s) for image " + image.getId());

      // Marks the image as done being processed regardless of how many variants actually made
      // sense (a small original may legitimately produce zero) -- see Image.processed's existing
      // use as the "async processing finished" signal (ImageRepository.update()).
      image.setProcessed(new Timestamp(System.currentTimeMillis()));
      ImageRepository.save(image);
    }
  }
}
