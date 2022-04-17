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

package com.simisinc.platform.application.items;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.items.ItemFileItem;
import com.simisinc.platform.domain.model.items.ItemFolder;
import com.simisinc.platform.domain.model.items.ItemFolderGroup;
import com.simisinc.platform.domain.model.items.PrivacyType;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemFileItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemFolderRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates and saves an item's file item object
 *
 * @author matt rajkowski
 * @created 4/19/2021 1:00 PM
 */
public class SaveItemFileCommand {

  private static Log LOG = LogFactory.getLog(SaveItemFileCommand.class);

  private static void validateFile(ItemFileItem fileItemBean) throws DataException {
    // Validate the required fields
    if (fileItemBean.getItemId() == -1) {
      throw new DataException("An item id is required");
    }

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

  public static ItemFileItem saveFile(ItemFileItem fileItemBean) throws DataException {

    // Use a default folder
    if (fileItemBean.getFolderId() == -1) {
      setDefaultFolder(fileItemBean);
    }

    // Check the fields
    validateFile(fileItemBean);

    // Transform the fields and store...
    ItemFileItem fileItem;
    if (fileItemBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      fileItem = ItemFileItemRepository.findById(fileItemBean.getId());
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
      fileItem = new ItemFileItem();
      fileItem.setItemId(fileItemBean.getItemId());
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
    }
    fileItem.setTitle(fileItemBean.getTitle());
    fileItem.setVersion(fileItemBean.getVersion());
    fileItem.setSummary(fileItemBean.getSummary());
    fileItem.setModifiedBy(fileItemBean.getModifiedBy());
    return ItemFileItemRepository.save(fileItem);
  }

  private static synchronized void setDefaultFolder(ItemFileItem fileItemBean) throws DataException {
    // Check for a default "Documents" or create it
    String defaultName = "Documents";
    ItemFolder itemFolder = ItemFolderRepository.findByName(defaultName, fileItemBean.getItemId());
    if (itemFolder == null) {
      Group defaultGroup = GroupRepository.findByName("All Users");
      List<ItemFolderGroup> folderGroupList = new ArrayList<>();
      ItemFolderGroup folderGroup = new ItemFolderGroup();
      folderGroup.setGroupId(defaultGroup.getId());
      folderGroup.setPrivacyType(PrivacyType.getTypeIdFromString("public"));
      folderGroupList.add(folderGroup);

      itemFolder = new ItemFolder();
      itemFolder.setName("Documents");
      itemFolder.setItemId(fileItemBean.getItemId());
      itemFolder.setCreatedBy(fileItemBean.getCreatedBy());
      itemFolder.setModifiedBy(fileItemBean.getCreatedBy());
      itemFolder.setAllowsGuests(false);
      itemFolder.setFolderGroupList(folderGroupList);
      itemFolder = SaveItemFolderCommand.saveFolder(itemFolder);
    }
    fileItemBean.setFolderId(itemFolder.getId());
  }

  public static ItemFileItem saveNewVersionOfFile(ItemFileItem fileItemBean) throws DataException {
    // Check the fields
    validateFile(fileItemBean);

    // Transform the fields and store...
    ItemFileItem fileItem = ItemFileItemRepository.findById(fileItemBean.getId());
    if (fileItem == null) {
      LOG.warn("Could not find previous file");
      return null;
    }
    fileItem.setItemId(fileItemBean.getItemId());
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
    return ItemFileItemRepository.saveVersion(fileItem);
  }

}
