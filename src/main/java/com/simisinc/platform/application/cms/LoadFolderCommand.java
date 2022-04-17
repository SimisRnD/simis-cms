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

import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FolderSpecification;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Loads a folder object based on user
 *
 * @author matt rajkowski
 * @created 12/12/18 3:38 PM
 */
public class LoadFolderCommand {

  private static Log LOG = LogFactory.getLog(LoadFolderCommand.class);

  public static Folder loadFolderByUniqueId(String uniqueId) {
//    return (Collection) CacheManager.getLoadingCache(CacheManager.COLLECTION_UNIQUE_ID_CACHE).get(uniqueId);
    return FolderRepository.findByUniqueId(uniqueId);
  }

  public static Folder loadFolderById(long folderId) {
    return FolderRepository.findById(folderId);
  }

  public static Folder loadFolderByIdForAuthorizedUser(long folderId, long userId) {
    if (folderId == -1 || userId == -1) {
      return null;
    }
    FolderSpecification specification = new FolderSpecification();
    specification.setId(folderId);
    specification.setForUserId(userId);
    List<Folder> folderList = FolderRepository.findAll(specification, null);
    if (folderList.size() == 1) {
      return folderList.get(0);
    }
    return null;
  }

  public static Folder loadFolderByUniqueIdForAuthorizedUser(String uniqueId, long userId) {
    if (StringUtils.isBlank(uniqueId) || userId == -1) {
      return null;
    }
    FolderSpecification specification = new FolderSpecification();
    specification.setUniqueId(uniqueId);
    specification.setForUserId(userId);
    List<Folder> folderList = FolderRepository.findAll(specification, null);
    if (folderList.size() == 1) {
      return folderList.get(0);
    }
    return null;
  }

  public static List<Folder> findAllAuthorizedForUser(long userId) {
    if (userId == -1) {
      return null;
    }
    // @todo determine records
    // Some users are admins
    // Some collections map to a user group
    FolderSpecification specification = new FolderSpecification();
    // if an admin, then skip this...
    specification.setForUserId(userId);
    return FolderRepository.findAll(specification, null);
  }

}
