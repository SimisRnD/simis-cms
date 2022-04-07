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

import com.simisinc.platform.domain.model.items.ItemFileItem;
import com.simisinc.platform.infrastructure.persistence.items.ItemFileItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemFileSpecification;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/19/2021 1:00 PM
 */
public class LoadItemFileCommand {

  private static Log LOG = LogFactory.getLog(LoadItemFileCommand.class);

  public static ItemFileItem loadItemById(long itemId) {
    // @todo use a cache
    return ItemFileItemRepository.findById(itemId);
  }

  public static ItemFileItem loadFileByIdForAuthorizedUser(long fileId, long userId, long itemId) {
    if (fileId == -1 || userId == -1) {
      return null;
    }
    ItemFileSpecification specification = new ItemFileSpecification();
    specification.setId(fileId);
    specification.setForUserId(userId);
    specification.setItemId(itemId);
    List<ItemFileItem> itemList = ItemFileItemRepository.findAll(specification, null);
    if (itemList.size() == 1) {
      return itemList.get(0);
    }
    return null;
  }
}
