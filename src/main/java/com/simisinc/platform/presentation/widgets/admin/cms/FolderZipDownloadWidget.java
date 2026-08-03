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

package com.simisinc.platform.presentation.widgets.admin.cms;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.cms.LoadFolderCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderRepository;
import com.simisinc.platform.presentation.controller.FileDownloadCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Streams a folder's (or sub-folder's) file list as a single zip archive, written directly to the
 * response -- same "raw bytes + setHandledResponse(true)" shape as {@link com.simisinc.platform.presentation.widgets.cms.DownloadFileWidget},
 * scoped by the same folder/sub-folder authorization used by {@link FolderFilesListWidget}.
 *
 * @author SimIS Inc.
 */
public class FolderZipDownloadWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  private static Log LOG = LogFactory.getLog(FolderZipDownloadWidget.class);

  public WidgetContext execute(WidgetContext context) {

    // Determine the folder, using the same sub-folder-first resolution as FolderFilesListWidget
    Folder folder;
    SubFolder subFolder = null;
    long subFolderId = context.getParameterAsLong("subFolderId");
    if (subFolderId > -1) {
      subFolder = SubFolderRepository.findById(subFolderId);
      if (subFolder == null) {
        LOG.warn("Sub-folder was not found: " + subFolderId);
        return null;
      }
      if (context.hasRole("admin")) {
        folder = FolderRepository.findById(subFolder.getFolderId());
      } else {
        folder = LoadFolderCommand.loadFolderByIdForAuthorizedUser(subFolder.getFolderId(), context.getUserId());
      }
    } else {
      long folderId = context.getParameterAsLong("folderId");
      if (context.hasRole("admin")) {
        folder = FolderRepository.findById(folderId);
      } else {
        folder = LoadFolderCommand.loadFolderByIdForAuthorizedUser(folderId, context.getUserId());
      }
    }
    if (folder == null) {
      LOG.warn("Folder was not found or no access");
      return null;
    }

    // Determine the same file list FolderFilesListWidget would show for this folder/sub-folder
    FileSpecification specification = new FileSpecification();
    specification.setFolderId(folder.getId());
    if (subFolderId > -1) {
      specification.setSubFolderId(subFolderId);
    } else {
      specification.setInASubFolder(false);
    }
    List<FileItem> fileList = FileItemRepository.findAll(specification, null);

    // Build the response headers
    String baseName = (subFolder != null ? subFolder.getName() : folder.getName());
    if (StringUtils.isBlank(baseName)) {
      baseName = "folder";
    }
    String zipFilename = FileDownloadCommand.sanitizeFilename(baseName + ".zip");
    if (zipFilename == null) {
      zipFilename = "folder.zip";
    }
    context.getResponse().setHeader("X-Content-Type-Options", "nosniff");
    context.getResponse().setContentType("application/zip");
    context.getResponse().setHeader("Content-Disposition", "attachment; filename=\"" + zipFilename + "\"");

    // Stream each file into the zip. A missing/unreadable file is skipped (logged), not fatal --
    // the admin still gets a usable archive of everything that could be read.
    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    Set<String> usedEntryNames = new HashSet<>();
    try (ZipOutputStream zos = new ZipOutputStream(context.getResponse().getOutputStream())) {
      for (FileItem record : fileList) {
        // URL-type "files" are external links with no local bytes to zip
        if (record.getFileType() != null && "URL".equalsIgnoreCase(record.getFileType())) {
          continue;
        }
        File file = FileSystemCommand.resolveWithinRoot(serverRootPath, record.getFileServerPath());
        if (file == null || !file.isFile()) {
          LOG.warn("Skipping missing server file for zip download: " + record.getFileServerPath());
          continue;
        }

        String entryName = uniqueEntryName(record, usedEntryNames);
        zos.putNextEntry(new ZipEntry(entryName));
        try (InputStream in = new FileInputStream(file)) {
          byte[] buf = new byte[8192];
          int count;
          while ((count = in.read(buf)) >= 0) {
            zos.write(buf, 0, count);
          }
        }
        zos.closeEntry();
      }
    } catch (IOException e) {
      // The client most likely aborted the download mid-stream; nothing more to do
      LOG.debug("Zip stream error: " + e.getMessage());
    }

    context.setHandledResponse(true);
    return context;
  }

  /**
   * Determines a safe, unique zip entry name for a file record, falling back to the title and then
   * the record id when the filename is blank or a collision has already used the plain name.
   */
  private String uniqueEntryName(FileItem record, Set<String> usedEntryNames) {
    String rawName = StringUtils.isNotBlank(record.getFilename()) ? record.getFilename() : record.getTitle();
    String safeName = FileDownloadCommand.sanitizeFilename(rawName);
    if (safeName == null) {
      safeName = "file-" + record.getId();
    }
    if (usedEntryNames.add(safeName)) {
      return safeName;
    }
    // De-dupe a colliding name: file.pdf, file (2).pdf, file (3).pdf, ...
    String base = safeName;
    String extension = "";
    int dot = safeName.lastIndexOf('.');
    if (dot > 0) {
      base = safeName.substring(0, dot);
      extension = safeName.substring(dot);
    }
    int suffix = 2;
    String candidate;
    do {
      candidate = base + " (" + suffix + ")" + extension;
      suffix++;
    } while (!usedEntryNames.add(candidate));
    return candidate;
  }
}
