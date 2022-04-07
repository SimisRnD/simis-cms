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
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/19/18 2:47 PM
 */
public class SaveCategoryCommand {

  private static Log LOG = LogFactory.getLog(SaveCategoryCommand.class);

  public static Category saveCategory(Category categoryBean) throws DataException {

    // Validate the required fields
    if (categoryBean.getCollectionId() == -1) {
      throw new DataException("A parent collection is required");
    }
    if (StringUtils.isBlank(categoryBean.getName())) {
      throw new DataException("A name is required, please check the fields and try again");
    }
    if (categoryBean.getCreatedBy() == -1) {
      throw new DataException("The user creating this category was not set");
    }

    if (categoryBean.getId() == -1 && CategoryRepository.findByNameWithinCollection(categoryBean.getName(), categoryBean.getCollectionId()) != null) {
      throw new DataException("A unique name is required");
    }

    // Transform the fields and store...
    Category category;
    if (categoryBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      category = CategoryRepository.findById(categoryBean.getId());
      if (category == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      category = new Category();
    }
    category.setCollectionId(categoryBean.getCollectionId());
    category.setName(categoryBean.getName());
    category.setDescription(categoryBean.getDescription());
    category.setCreatedBy(categoryBean.getCreatedBy());
    return CategoryRepository.save(category);
  }

}
