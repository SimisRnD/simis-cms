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
 * Regenerates an image's focal-point-dependent square variant in the background, off the admin
 * request's critical path (issue #411 PR3). Enqueued from {@code AdminImageBrowserWidget} whenever
 * an admin sets or changes an image's focal point.
 *
 * <p>
 * Deliberately separate from {@code ImageVariantJob}: thumbnail/medium/large are aspect-preserving
 * resizes unaffected by focal point, so a focal-point-only change only needs the square variant
 * regenerated, not all four.
 * </p>
 *
 * @author SimIS Inc.
 */
@NoArgsConstructor
public class FocalPointVariantJob implements JobRequest {

  private static final Log LOG = LogFactory.getLog(FocalPointVariantJob.class);

  @Getter
  @Setter
  private long imageId = -1;

  public FocalPointVariantJob(long imageId) {
    this.imageId = imageId;
  }

  @Override
  public Class<FocalPointVariantJobRequestHandler> getJobRequestHandler() {
    return FocalPointVariantJobRequestHandler.class;
  }

  public static class FocalPointVariantJobRequestHandler implements JobRequestHandler<FocalPointVariantJob> {
    @Override
    @Job(name = "Generate focal-point image variant", retries = 1)
    public void run(FocalPointVariantJob jobRequest) {
      Image image = ImageRepository.findById(jobRequest.getImageId());
      if (image == null) {
        LOG.error("Image not found: " + jobRequest.getImageId());
        return;
      }

      ImageVariant variant = GenerateImageVariantsCommand.generateSquareVariant(image);
      LOG.info((variant != null ? "Generated" : "Skipped") + " the square variant for image " + image.getId());
      // Deliberately does not touch images.processed here -- that column's documented meaning is
      // "initial upload processing finished" (see ImageVariantJob), not "some variant was
      // regenerated." A focal-point change re-triggering this job must not resurrect that
      // unrelated signal.
    }
  }
}
