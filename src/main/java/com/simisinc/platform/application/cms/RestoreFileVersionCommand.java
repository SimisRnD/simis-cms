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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.FileVersion;

/**
 * Restores a folder file (issue #502) to a previously uploaded version.
 * <p>
 * Every upload -- the original and every later "add a file version" -- is written to its own
 * timestamped, UUID-suffixed path (see {@link FileSystemCommand#generateUniqueFilename}) and is
 * never deleted or overwritten while the file item exists; {@link DeleteFileCommand} only removes a
 * version's file when the whole file item is deleted. So the bytes for an archived
 * {@link FileVersion} are still sitting on disk at {@link FileVersion#getFileServerPath()}, and
 * restoring can point the live file record back at that existing path instead of re-uploading
 * anything. This is a genuine content restore, not a metadata-only relabeling.
 *
 * @author SimIS Inc.
 * @created 8/2/2026
 */
public class RestoreFileVersionCommand {

  private static Log LOG = LogFactory.getLog(RestoreFileVersionCommand.class);

  public static FileItem restore(FileVersion fileVersion, long userId) throws DataException {
    if (fileVersion == null) {
      throw new DataException("The selected version was not specified");
    }

    // Confirm the archived file is still physically present before promoting it to live. A missing
    // file would otherwise let this "succeed" while leaving downloads broken.
    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    File file = FileSystemCommand.resolveWithinRoot(serverRootPath, fileVersion.getFileServerPath());
    if (file == null || !file.exists()) {
      LOG.warn("Archived file version is missing from disk: " + fileVersion.getFileServerPath());
      throw new DataException("The archived file could not be found on the server and cannot be restored");
    }

    // Build the bean the same way a genuine "add a file version" upload does (see
    // SaveFilePartCommand#saveFile + FolderFilesListWidget#post), but source the file fields from
    // the archived version instead of a freshly written temp file.
    FileItem fileItemBean = new FileItem();
    fileItemBean.setId(fileVersion.getFileId());
    fileItemBean.setFolderId(fileVersion.getFolderId());
    fileItemBean.setSubFolderId(fileVersion.getSubFolderId());
    fileItemBean.setCategoryId(fileVersion.getCategoryId());
    fileItemBean.setFilename(fileVersion.getFilename());
    fileItemBean.setTitle(fileVersion.getTitle());
    fileItemBean.setVersion(fileVersion.getVersion());
    fileItemBean.setExtension(fileVersion.getExtension());
    fileItemBean.setFileServerPath(fileVersion.getFileServerPath());
    fileItemBean.setFileLength(fileVersion.getFileLength());
    fileItemBean.setFileType(fileVersion.getFileType());
    fileItemBean.setMimeType(fileVersion.getMimeType());
    fileItemBean.setFileHash(fileVersion.getFileHash());
    fileItemBean.setWidth(fileVersion.getWidth());
    fileItemBean.setHeight(fileVersion.getHeight());
    fileItemBean.setSummary(fileVersion.getSummary());
    fileItemBean.setCreatedBy(userId);
    fileItemBean.setModifiedBy(userId);

    // Re-uses the exact same path a manual "add a file version" upload takes: it snapshots the
    // (about to be replaced) current state into a new file_versions row and updates the live file
    // record -- so restoring itself becomes a new, restorable version rather than losing history.
    return SaveFileCommand.saveNewVersionOfFile(fileItemBean);
  }

}
