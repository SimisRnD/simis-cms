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

import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileSpecification;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Loads a file item object based on user
 *
 * @author matt rajkowski
 * @created 12/13/18 4:29 PM
 */
public class LoadFileCommand {

  private static Log LOG = LogFactory.getLog(LoadFileCommand.class);

  public static FileItem loadItemById(long itemId) {
    // @todo use a cache
    return FileItemRepository.findById(itemId);
  }

  public static FileItem loadFileByIdForAuthorizedUser(long fileId, long userId) {
    if (fileId == -1 || userId == -1) {
      return null;
    }
    FileSpecification specification = new FileSpecification();
    specification.setId(fileId);
    specification.setForUserId(userId);
    List<FileItem> itemList = FileItemRepository.findAll(specification, null);
    if (itemList.size() == 1) {
      return itemList.get(0);
    }
    return null;
  }
}
