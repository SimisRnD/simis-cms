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

import com.simisinc.platform.application.cms.DownscaleLibraryImagesCommand;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Replaces one oversized library image with a resized copy, queued by the "Replace oversized
 * images" action on /admin/images. See {@link DownscaleLibraryImagesCommand#apply}.
 *
 * @author SimIS Inc.
 */
@NoArgsConstructor
public class DownscaleLibraryImageJob implements JobRequest {

  private static final Log LOG = LogFactory.getLog(DownscaleLibraryImageJob.class);

  @Getter
  @Setter
  private long imageId = -1;

  /** The admin who started it, recorded as the copy's creator and in the audit log */
  @Getter
  @Setter
  private long userId = -1;

  public DownscaleLibraryImageJob(long imageId, long userId) {
    this.imageId = imageId;
    this.userId = userId;
  }

  @Override
  public Class<DownscaleLibraryImageJobRequestHandler> getJobRequestHandler() {
    return DownscaleLibraryImageJobRequestHandler.class;
  }

  public static class DownscaleLibraryImageJobRequestHandler implements JobRequestHandler<DownscaleLibraryImageJob> {
    @Override
    // No retries: a run that fails after adding the copy would add a second one
    @Job(name = "Replace an oversized library image", retries = 0)
    public void run(DownscaleLibraryImageJob jobRequest) {
      LOG.info(DownscaleLibraryImagesCommand.apply(jobRequest.getImageId(), jobRequest.getUserId()));
    }
  }
}
