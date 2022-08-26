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

package com.simisinc.platform.rest.services.medicine;

import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemSpecification;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;
import com.simisinc.platform.rest.controller.ServiceResponseCommand;
import com.simisinc.platform.rest.services.items.ItemResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;

import static com.simisinc.platform.application.medicine.MedicineConstants.COLLECTION_DRUG_LIST_UNIQUE_ID;

/**
 * Returns a list of drugs based on the query
 *
 * @author matt rajkowski
 * @created 8/28/18 11:36 AM
 */
public class DrugListService {

  private static Log LOG = LogFactory.getLog(DrugListService.class);

  // endpoint: med/drugList?q={query}
  public ServiceResponse get(ServiceContext context) {

    // Check the drug list collection for access
    Collection drugListCollection = LoadCollectionCommand
        .loadCollectionByUniqueIdForAuthorizedUser(COLLECTION_DRUG_LIST_UNIQUE_ID, context.getUserId());
    if (drugListCollection == null) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Drug list was not found");
      return response;
    }

    // Query the names
    String query = context.getParameter("q");
    if (StringUtils.isBlank(query)) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Query parameter is required");
      return response;
    }

    // Determine the constraints
    int pageNumber = context.getParameterAsInt("page", 1);
    int pageSize = context.getParameterAsInt("size", 20);
    DataConstraints constraints = new DataConstraints(pageNumber, pageSize);

    // Determine the filters
    ItemSpecification specification = new ItemSpecification();
    specification.setCollectionId(drugListCollection.getId());
    specification.setForUserId(context.getUserId());
    specification.setMatchesName(query);

    // Retrieve the records
    List<Item> itemList = ItemRepository.findAll(specification, constraints);

    // Set the fields to return
    List<ItemResponse> recordList = new ArrayList<>();
    for (Item item : itemList) {
      recordList.add(new ItemResponse(item));
    }

    // Prepare the response
    ServiceResponse response = new ServiceResponse(200);
    ServiceResponseCommand.addMeta(response, "drug", recordList, null);
    response.setData(recordList);
    return response;
  }

}
