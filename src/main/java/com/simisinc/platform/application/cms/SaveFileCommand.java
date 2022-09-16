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

package com.simisinc.platform.application.cms;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;

/**
 * Validates and saves file item objects
 *
 * @author matt rajkowski
 * @created 12/13/18 11:49 AM
 */
public class SaveFileCommand {

  private static Log LOG = LogFactory.getLog(SaveFileCommand.class);

  private static void validateFile(FileItem fileItemBean) throws DataException {
    // Validate the required fields
    if (fileItemBean.getFolderId() == -1) {
      throw new DataException("A folder is required");
    }

    if (fileItemBean.getId() > -1) {
      if (fileItemBean.getModifiedBy() == -1) {
        throw new DataException("The user modifying this record was not set");
      }
    } else {
      if (StringUtils.isBlank(fileItemBean.getFilename())) {
        throw new DataException("A file name is required, please check the fields and try again");
      }
      if (StringUtils.isBlank(fileItemBean.getFileServerPath()) && !
          ("url".equals(fileItemBean.getExtension()) && UrlCommand.isUrlValid(fileItemBean.getFilename()))
      ) {
        LOG.error("The developer needs to set a path");
        throw new DataException("A system path error occurred");
      }
      if (fileItemBean.getCreatedBy() == -1) {
        throw new DataException("The user creating this record was not set");
      }
    }
  }

  public static FileItem saveFile(FileItem fileItemBean) throws DataException {

    // Check the fields
    validateFile(fileItemBean);

    // Transform the fields and store...
    FileItem fileItem;
    if (fileItemBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      fileItem = FileItemRepository.findById(fileItemBean.getId());
      if (fileItem == null) {
        throw new DataException("The existing record could not be found");
      }
      // Check for a folder change
      if (fileItem.getFolderId() != fileItemBean.getFolderId()) {
        // The folder changed, so the subFolder is now invalid
        fileItem.setSubFolderId(-1);
        fileItem.setCategoryId(-1);
      } else {
        // Allow the subfolder to be set
        fileItem.setSubFolderId(fileItemBean.getSubFolderId());
        fileItem.setCategoryId(fileItemBean.getCategoryId());
      }
      fileItem.setFolderId(fileItemBean.getFolderId());
      // Allow some types of files to be renamed
      if ("url".equalsIgnoreCase(fileItem.getFileType())) {
        if (UrlCommand.isUrlValid(fileItemBean.getFilename())) {
          fileItem.setFilename(fileItemBean.getFilename());
        }
      } else {
        // Allow renaming if same file extension
        if (fileItemBean.getFilename().endsWith(fileItem.getExtension())) {
          fileItem.setFilename(fileItemBean.getFilename());
        } else {
          throw new DataException("The extension cannot be changed for an existing file");
        }
      }
      LOG.debug("FolderId: " + fileItemBean.getFolderId());
    } else {
      LOG.debug("Saving a new record... ");
      fileItem = new FileItem();
      fileItem.setFilename(fileItemBean.getFilename());
      fileItem.setFileServerPath(fileItemBean.getFileServerPath());
      fileItem.setExtension(fileItemBean.getExtension());
      fileItem.setCreatedBy(fileItemBean.getCreatedBy());
      fileItem.setFileLength(fileItemBean.getFileLength());
      fileItem.setMimeType(fileItemBean.getMimeType());
      fileItem.setFileType(fileItemBean.getFileType());
      fileItem.setFileHash(fileItemBean.getFileHash());
      fileItem.setWidth(fileItemBean.getWidth());
      fileItem.setHeight(fileItemBean.getHeight());
      fileItem.setFolderId(fileItemBean.getFolderId());
      fileItem.setSubFolderId(fileItemBean.getSubFolderId());
      fileItem.setCategoryId(fileItemBean.getCategoryId());
      // Determine the web path for downloads, can randomize, etc.
      Date created = new Date(System.currentTimeMillis());
      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
      fileItem.setWebPath(sdf.format(created));
    }
    fileItem.setTitle(fileItemBean.getTitle());
    fileItem.setVersion(fileItemBean.getVersion());
    fileItem.setSummary(fileItemBean.getSummary());
    fileItem.setModifiedBy(fileItemBean.getModifiedBy());
    return FileItemRepository.save(fileItem);
  }

  public static FileItem saveNewVersionOfFile(FileItem fileItemBean) throws DataException {
    // Check the fields
    validateFile(fileItemBean);

    // Transform the fields and store...
    FileItem fileItem = FileItemRepository.findById(fileItemBean.getId());
    if (fileItem == null) {
      LOG.warn("Could not find previous file");
      return null;
    }
    fileItem.setFolderId(fileItemBean.getFolderId());
    fileItem.setSubFolderId(fileItemBean.getSubFolderId());
    fileItem.setCategoryId(fileItemBean.getCategoryId());
    fileItem.setFilename(fileItemBean.getFilename());
    fileItem.setTitle(fileItemBean.getTitle());
    fileItem.setBarcode(fileItemBean.getBarcode());
    fileItem.setVersion(fileItemBean.getVersion());
    fileItem.setExtension(fileItemBean.getExtension());
    fileItem.setFileServerPath(fileItemBean.getFileServerPath());
    fileItem.setFileLength(fileItemBean.getFileLength());
    fileItem.setFileType(fileItemBean.getFileType());
    fileItem.setMimeType(fileItemBean.getMimeType());
    fileItem.setFileHash(fileItemBean.getFileHash());
    fileItem.setWidth(fileItemBean.getWidth());
    fileItem.setHeight(fileItemBean.getHeight());
    fileItem.setSummary(fileItemBean.getSummary());
    fileItem.setCreatedBy(fileItemBean.getCreatedBy());
    fileItem.setModifiedBy(fileItemBean.getModifiedBy());
    fileItem.setProcessed(null);
    fileItem.setExpirationDate(fileItemBean.getExpirationDate());
    fileItem.setPrivacyType(fileItemBean.getPrivacyType());
    fileItem.setDefaultToken(fileItemBean.getDefaultToken());
    // Determine the web path for downloads, can randomize, etc.
    Date created = new Date(System.currentTimeMillis());
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
    fileItem.setWebPath(sdf.format(created));
    return FileItemRepository.saveVersion(fileItem);
  }

}
