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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;

/**
 * Validates and saves a category object
 *
 * @author matt rajkowski
 * @created 4/19/18 2:47 PM
 */
public class SaveCategoryCommand {

  private static Log LOG = LogFactory.getLog(SaveCategoryCommand.class);

  private static final String allowedChars = "abcdefghijklmnopqrstuvwyxz0123456789";
  private static final String allowedFinalChars = "abcdefghijklmnopqrstuvwyxz0123456789-";

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

    if (categoryBean.getId() == -1 && CategoryRepository.findByNameWithinCollection(categoryBean.getName(),
        categoryBean.getCollectionId()) != null) {
      throw new DataException("A unique name is required");
    }

    // Validate the unique id
    if (StringUtils.isNotBlank(categoryBean.getUniqueId())) {
      if (!StringUtils.containsOnly(categoryBean.getUniqueId(), allowedFinalChars)) {
        throw new DataException("The uniqueId contains invalid characters");
      }
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
    // @note set the uniqueId before setting the name
    category.setUniqueId(GenerateCategoryUniqueIdCommand.generateUniqueId(category, categoryBean));
    category.setName(categoryBean.getName());
    category.setDescription(categoryBean.getDescription());
    category.setIcon(categoryBean.getIcon());
    category.setHeaderTextColor(categoryBean.getHeaderTextColor());
    category.setHeaderBgColor(categoryBean.getHeaderBgColor());
    category.setItemUrlText(categoryBean.getItemUrlText());
    category.setCreatedBy(categoryBean.getCreatedBy());
    return CategoryRepository.save(category);
  }

}
