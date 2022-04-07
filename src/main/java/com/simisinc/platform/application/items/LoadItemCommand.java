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

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemSpecification;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/23/18 3:39 PM
 */
public class LoadItemCommand {

  private static Log LOG = LogFactory.getLog(LoadItemCommand.class);

  public static Item loadItemByUniqueId(String uniqueId) {
    // @todo use a cache
    return ItemRepository.findByUniqueId(uniqueId);
  }

  public static Item loadItemById(long itemId) {
    // @todo use a cache
    return ItemRepository.findById(itemId);
  }

  public static Item loadItemByIdWithinCollection(long itemId, Collection collection) {
    return ItemRepository.findByIdWithinCollection(itemId, collection.getId());
  }

  public static Item loadItemByUniqueIdWithinCollection(String uniqueId, Collection collection) {
    return ItemRepository.findByUniqueIdWithinCollection(uniqueId, collection.getId());
  }

  public static Item loadItemByUniqueIdForAuthorizedUser(String uniqueId, long userId) {
    if (StringUtils.isBlank(uniqueId) || userId == -1) {
      return null;
    }
    ItemSpecification specification = new ItemSpecification();
    specification.setUniqueId(uniqueId);
    specification.setForUserId(userId);
    List<Item> itemList = ItemRepository.findAll(specification, null);
    if (itemList.size() == 1) {
      return itemList.get(0);
    }
    return null;
  }

  public static Item loadItemForAuthorizedUser(Item item, User user) {
    ItemSpecification specification = new ItemSpecification();
    specification.setId(item.getId());
    specification.setForUserId(user.getId());
    List<Item> itemList = ItemRepository.findAll(specification, null);
    if (itemList.size() == 1) {
      return itemList.get(0);
    }
    return null;
  }

}
