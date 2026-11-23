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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FormUpload;
import com.simisinc.platform.infrastructure.persistence.cms.FormUploadRepository;

/**
 * Removes files visitors attached to forms once nothing needs them (issue #2130).
 *
 * <ul>
 * <li>{@link #removeUnlinkedUploads()}: an upload whose submission is gone. Deleting a submission
 * sets its uploads' form_data_id to null (the foreign key is ON DELETE SET NULL, so the row survives
 * to say which files to delete); an upload whose submission was never saved is null from the start.
 * The record goes first, then its files.</li>
 * <li>{@link #deleteUnreferencedFiles()}: a file under {@code forms/} in the file-server root that no
 * form_uploads row names -- left by a crash between writing a file and recording it, a file delete
 * that failed, or a quarantine copy that was never cleaned up. Nothing else writes under
 * {@code forms/}.</li>
 * </ul>
 *
 * <p>Both only touch what is at least {@link #MINIMUM_AGE} old, so a submission in progress -- its
 * files written moments before its record, its record saved moments before it is linked -- is never
 * mistaken for an orphan.
 *
 * @author SimIS Inc.
 */
public class PurgeFormUploadsCommand {

  private static Log LOG = LogFactory.getLog(PurgeFormUploadsCommand.class);

  /** Far longer than any submission takes to save */
  public static final Duration MINIMUM_AGE = Duration.ofDays(1);

  private PurgeFormUploadsCommand() {
  }

  /** Removes uploads no submission points at, and their files; returns how many */
  public static int removeUnlinkedUploads() {
    return removeUnlinkedUploads(Instant.now().minus(MINIMUM_AGE));
  }

  static int removeUnlinkedUploads(Instant cutoff) {
    List<FormUpload> unlinked = FormUploadRepository.findUnlinkedCreatedBefore(Timestamp.from(cutoff));
    int removed = 0;
    String root = FileSystemCommand.getFileServerRootPath();
    for (FormUpload upload : unlinked) {
      // The record first: if its files can't be deleted, the file sweep finds them; a record whose
      // files are already gone would point at nothing
      if (!FormUploadRepository.remove(upload)) {
        LOG.warn("Could not remove an unlinked form upload record: " + upload.getId());
        continue;
      }
      ++removed;
      if (root == null) {
        continue;
      }
      for (String path : new String[] { upload.getFilePath(), upload.getThumbnailPath() }) {
        if (StringUtils.isBlank(path)) {
          continue;
        }
        File file = FileSystemCommand.resolveWithinRoot(root, path);
        if (file != null) {
          try {
            Files.deleteIfExists(file.toPath());
          } catch (IOException e) {
            LOG.error("Could not delete a form upload file: " + path + " (" + e.getMessage() + ")");
          }
        }
      }
    }
    return removed;
  }

  /** Deletes files under forms/ that no form_uploads row names; returns how many */
  public static int deleteUnreferencedFiles() {
    return deleteUnreferencedFiles(Instant.now().minus(MINIMUM_AGE));
  }

  static int deleteUnreferencedFiles(Instant cutoff) {
    String root = FileSystemCommand.getFileServerRootPath();
    if (StringUtils.isBlank(root)) {
      return 0;
    }
    Path rootPath = Paths.get(root).toAbsolutePath().normalize();
    Path formsPath = rootPath.resolve(SaveFormUploadCommand.UPLOAD_DIRECTORY).normalize();
    // Only a real directory: a symbolic link here could point anywhere
    if (!formsPath.startsWith(rootPath) || !Files.isDirectory(formsPath, LinkOption.NOFOLLOW_LINKS)) {
      return 0;
    }
    Set<String> referenced = FormUploadRepository.findAllStoredPaths();
    if (referenced == null) {
      // Not knowing which files are in use is not the same as none being in use
      LOG.warn("Skipped the form upload file sweep: the stored paths could not be read");
      return 0;
    }
    int deleted = 0;
    // Files.walk does not follow symbolic links into other directories
    try (Stream<Path> walk = Files.walk(formsPath)) {
      for (Path path : (Iterable<Path>) walk::iterator) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
          continue;
        }
        String relative = rootPath.relativize(path).toString().replace(File.separatorChar, '/');
        if (referenced.contains(relative)) {
          continue;
        }
        try {
          if (Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().isAfter(cutoff)) {
            continue;
          }
          Files.deleteIfExists(path);
          ++deleted;
        } catch (IOException e) {
          LOG.error("Could not delete an unreferenced form upload file: " + relative + " (" + e.getMessage() + ")");
        }
      }
    } catch (IOException | RuntimeException e) {
      LOG.error("The form upload file sweep stopped early: " + e.getMessage());
    }
    return deleted;
  }
}
