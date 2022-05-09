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

import com.simisinc.platform.application.cms.MakeContentUniqueIdCommand;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;

/**
 * Generates a plain text string - a uniqueId for URLs and referencing
 *
 * @author matt rajkowski
 * @created 4/19/18 2:47 PM
 */
public class GenerateItemUniqueIdCommand {

  public static String generateUniqueId(Item previousItem, Item item) {

    // Use an existing uniqueId
    if (previousItem != null && previousItem.getUniqueId() != null) {
      // See if the name changed
      if (previousItem.getName().equals(item.getName())) {
        return previousItem.getUniqueId();
      }
    }

    // Create a new one
    String value = MakeContentUniqueIdCommand.parseToValidValue(item.getName());

    // Find the next available unique instance
    int count = 1;
    String uniqueId = value;
    while (ItemRepository.findByUniqueId(uniqueId) != null) {
      ++count;
      uniqueId = value + "-" + count;
    }
    return uniqueId;
  }

}
