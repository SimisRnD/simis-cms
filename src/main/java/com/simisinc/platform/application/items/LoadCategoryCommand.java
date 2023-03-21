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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;

/**
 * Loads a category object from cache or storage
 *
 * @author matt rajkowski
 * @created 4/23/18 3:39 PM
 */
public class LoadCategoryCommand {

  private static Log LOG = LogFactory.getLog(LoadCategoryCommand.class);

  // @todo cache
  public static Category loadCategoryByUniqueIdWithinCollection(String uniqueId, long collectionId) {
    if (StringUtils.isBlank(uniqueId) || collectionId == -1) {
      return null;
    }
    return CategoryRepository.findByUniqueIdWithinCollection(uniqueId, collectionId);
  }

  public static Category loadCategoryById(long categoryId) {
    if (categoryId == -1) {
      return null;
    }
    return CategoryRepository.findById(categoryId);
  }

}
