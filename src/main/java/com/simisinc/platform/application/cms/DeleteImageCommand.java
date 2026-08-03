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

import java.io.File;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.domain.model.cms.ImageVariant;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ImageVariantRepository;

/**
 * Deletes an image record and its physical file (issue #498).
 * <p>
 * {@code ImageRepository.remove()} already existed but nothing called it, and it never touched
 * the file system. This mirrors {@link DeleteFileCommand#deleteFile}'s ordering exactly: the
 * database row is removed first, and the physical file is only removed once that succeeds --
 * never the other way around, so a failed database delete can never leave an orphaned DB row
 * pointing at a file that no longer exists.
 *
 * <p>
 * Issue #411: any resized variants are looked up before the delete (their rows cascade-delete
 * with the parent {@code images} row via the {@code image_variants} FK) and their physical files
 * are removed alongside the original's, so a variant file never outlives its source.
 * </p>
 *
 * @author SimIS Inc.
 */
public class DeleteImageCommand {

  private static Log LOG = LogFactory.getLog(DeleteImageCommand.class);

  private DeleteImageCommand() {
    // Static utility
  }

  /**
   * Deletes the given image's database row, then (only if that succeeded) its physical file.
   *
   * @param image the image to delete
   * @return true if the database row was removed; false if the image was invalid or the row
   *         could not be removed. A missing or already-deleted physical file is not a failure --
   *         real-world state can already be inconsistent, so this method never throws for that.
   */
  public static boolean deleteImage(Image image) {
    if (image == null || image.getId() == null || image.getId() == -1) {
      LOG.warn("The image was not specified");
      return false;
    }

    // Look up variants before the delete -- their rows cascade-delete with the images row, so
    // this is the last point their paths are still queryable
    List<ImageVariant> variants = ImageVariantRepository.findByImageId(image.getId());

    // Remove the database row first
    if (!ImageRepository.remove(image)) {
      LOG.warn("Image database row could not be removed: " + image.getId());
      return false;
    }

    // Only now remove the physical files -- a failure here does not undo the DB delete
    deletePhysicalFileQuietly(image.getId(), image.getFileServerPath());
    for (ImageVariant variant : variants) {
      deletePhysicalFileQuietly(image.getId(), variant.getFileServerPath());
    }
    return true;
  }

  private static void deletePhysicalFileQuietly(long imageId, String fileServerPath) {
    if (StringUtils.isBlank(fileServerPath)) {
      return;
    }
    try {
      String serverRootPath = FileSystemCommand.getFileServerRootPath();
      File file = FileSystemCommand.resolveWithinRoot(serverRootPath, fileServerPath);
      if (file == null) {
        // resolveWithinRoot already logged why (outside the root, or unresolvable)
        return;
      }
      if (!file.exists() || !file.isFile()) {
        // Real-world state can already be inconsistent (the file may have been removed by hand,
        // or never written) -- this is not an error worth failing the delete over.
        LOG.debug("Image file was already missing on disk: " + file.getPath());
        return;
      }
      if (!file.delete()) {
        LOG.warn("Image file could not be deleted: " + file.getPath());
      }
    } catch (Exception e) {
      // Never let a file system problem look like the delete failed -- the DB row is already gone.
      LOG.warn("Error deleting image file for image " + imageId + ": " + e.getMessage());
    }
  }

}
