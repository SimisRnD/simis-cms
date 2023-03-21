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

package com.simisinc.platform.rest.services.items;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.items.LoadCategoryCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemSpecification;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;
import com.simisinc.platform.rest.controller.ServiceResponseCommand;

/**
 * Returns a list of items for the given collection unique id
 *
 * @author matt rajkowski
 * @created 4/27/18 10:15 AM
 */
public class ItemListService {

  private static Log LOG = LogFactory.getLog(ItemListService.class);

  // GET /items/{collectionUniqueId}?category={categoryUniqueId}&query=value
  public ServiceResponse get(ServiceContext context) {

    // Determine the collection
    String collectionUniqueId = context.getPathParam();
    Collection collection = LoadCollectionCommand.loadCollectionByUniqueId(collectionUniqueId);
    if (collection == null) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Collection was not found");
      return response;
    }

    // Validate access to the collection
    if (LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(collection.getId(), context.getUserId()) == null) {
      LOG.warn("User does not have access to this collection");
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Collection was not found");
      return response;
    }

    // Check for a specific category
    String categoryUniqueId = context.getParameter("category");
    Category category = null;
    if (!StringUtils.isBlank(categoryUniqueId)) {
      category = LoadCategoryCommand.loadCategoryByUniqueIdWithinCollection(categoryUniqueId, collection.getId());
      if (category == null) {
        ServiceResponse response = new ServiceResponse(400);
        response.getError().put("title", "Category was not found");
        return response;
      }
    }

    // Determine the constraints
    int pageNumber = context.getParameterAsInt("page", 1);
    int pageSize = context.getParameterAsInt("size", 20);
    DataConstraints constraints = new DataConstraints(pageNumber, pageSize);

    // Determine the filters
    ItemSpecification specification = new ItemSpecification();
    specification.setCollectionId(collection.getId());
    specification.setForUserId(context.getUserId());
    if (category != null) {
      specification.setCategoryId(category.getId());
    }
    
    // Retrieve the records
    List<Item> itemList = ItemRepository.findAll(specification, constraints);

    // Set the fields to return
    List<ItemResponse> recordList = new ArrayList<>();
    for (Item item : itemList) {
      recordList.add(new ItemResponse(item));
    }

    // Prepare the response
    ServiceResponse response = new ServiceResponse(200);
    ServiceResponseCommand.addMeta(response, "item", itemList, constraints);
    response.setData(recordList);
    return response;
  }

}
