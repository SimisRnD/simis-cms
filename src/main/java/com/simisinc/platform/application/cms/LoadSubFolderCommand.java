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

import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderSpecification;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/27/19 4:29 PM
 */
public class LoadSubFolderCommand {

  private static Log LOG = LogFactory.getLog(LoadSubFolderCommand.class);

  public static SubFolder loadSubFolderById(long itemId) {
    // @todo use a cache
    return SubFolderRepository.findById(itemId);
  }

  public static SubFolder loadSubFolderByIdForAuthorizedUser(long subFolderId, long userId) {
    if (subFolderId == -1 || userId == -1) {
      return null;
    }
    SubFolderSpecification specification = new SubFolderSpecification();
    specification.setId(subFolderId);
    specification.setForUserId(userId);
    List<SubFolder> itemList = SubFolderRepository.findAll(specification, null);
    if (itemList.size() == 1) {
      return itemList.get(0);
    }
    return null;
  }
}
