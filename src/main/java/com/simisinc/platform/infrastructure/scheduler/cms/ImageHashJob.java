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

import java.io.File;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Computes and saves one pre-existing image's file hash, off the request thread. Enqueued from
 * {@code ScanForDuplicateImagesCommand}'s admin-triggered "Scan for Duplicates" backfill, one job
 * per un-hashed image (mirrors {@link ImageVariantJob}'s shape exactly) -- new uploads never reach
 * this job, since {@code ValidateImageCommand.checkFile} hashes them synchronously at upload time.
 *
 * @author SimIS Inc.
 */
@NoArgsConstructor
public class ImageHashJob implements JobRequest {

  private static final Log LOG = LogFactory.getLog(ImageHashJob.class);

  @Getter
  @Setter
  private long imageId = -1;

  public ImageHashJob(long imageId) {
    this.imageId = imageId;
  }

  @Override
  public Class<ImageHashJobRequestHandler> getJobRequestHandler() {
    return ImageHashJobRequestHandler.class;
  }

  public static class ImageHashJobRequestHandler implements JobRequestHandler<ImageHashJob> {
    @Override
    @Job(name = "Compute image file hash", retries = 1)
    public void run(ImageHashJob jobRequest) {
      Image image = ImageRepository.findById(jobRequest.getImageId());
      if (image == null) {
        LOG.error("Image not found: " + jobRequest.getImageId());
        return;
      }

      String serverRootPath = FileSystemCommand.getFileServerRootPath();
      File imageFile = FileSystemCommand.resolveWithinRoot(serverRootPath, image.getFileServerPath());
      if (imageFile == null || !imageFile.exists()) {
        LOG.warn("Image file not found on disk, skipping hash: " + image.getId());
        return;
      }

      String fileHash = FileSystemCommand.getFileChecksum(imageFile);
      if (fileHash == null) {
        LOG.warn("Could not compute a file hash for image " + image.getId());
        return;
      }

      image.setFileHash(fileHash);
      ImageRepository.save(image);
    }
  }
}
