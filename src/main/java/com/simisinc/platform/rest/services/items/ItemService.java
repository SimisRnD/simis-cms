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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;
import com.simisinc.platform.rest.controller.ServiceResponseCommand;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/27/18 10:20 AM
 */
public class ItemService {

  private static Log LOG = LogFactory.getLog(ItemService.class);

  // GET /item/{uniqueId}
  public ServiceResponse get(ServiceContext context) {

    // Determine the item
    String itemUniqueId = context.getPathParam();
    Item item = LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(itemUniqueId, context.getUserId());
    if (item == null) {
      ServiceResponse response = new ServiceResponse(404);
      response.getError().put("title", "Item was not found");
      return response;
    }

    // Validate access to the collection
    if (LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(item.getCollectionId(),
        context.getUserId()) == null) {
      LOG.warn("User does not have access to this collection");
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Item was not found");
      return response;
    }

    // Set the fields to return
    ItemDetailsHandler itemDetails = new ItemDetailsHandler(item);

    // Prepare the response
    ServiceResponse response = new ServiceResponse(200);
    ServiceResponseCommand.addMeta(response, "item");
    response.setData(itemDetails);
    return response;
  }

}
