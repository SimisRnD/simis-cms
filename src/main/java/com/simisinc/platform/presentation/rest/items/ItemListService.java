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

package com.simisinc.platform.presentation.rest.items;

import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemSpecification;
import com.simisinc.platform.presentation.controller.ServiceContext;
import com.simisinc.platform.presentation.controller.ServiceResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Returns a list of items for the given collection unique id
 *
 * @author matt rajkowski
 * @created 4/27/18 10:15 AM
 */
public class ItemListService {

  private static Log LOG = LogFactory.getLog(ItemListService.class);

  // endpoint: items/{collection}
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

    // Determine the constraints
    int pageNumber = context.getParameterAsInt("page", 1);
    int pageSize = context.getParameterAsInt("size", 20);
    DataConstraints constraints = new DataConstraints(pageNumber, pageSize);

    // Determine the filters
    ItemSpecification specification = new ItemSpecification();
    specification.setCollectionId(collection.getId());
    specification.setForUserId(context.getUserId());

    // Retrieve the records
    List<Item> itemList = ItemRepository.findAll(specification, constraints);
    int maxPages = constraints.getMaxPageNumber();

    // Set the fields to return
    List<ItemHandler> recordList = new ArrayList<>();
    for (Item item : itemList) {
      recordList.add(new ItemHandler(item));
    }

    // Prepare the response
    ServiceResponse response = new ServiceResponse(200);
    response.getMeta().put("type", "item");
    response.getMeta().put("currentPage", pageNumber);
    response.getMeta().put("pages", maxPages);
    response.setData(recordList);
    return response;
  }

}
