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

import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;
import com.simisinc.platform.rest.controller.ServiceResponseCommand;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/20/18 10:33 AM
 */
public class CollectionListService {

  private static Log LOG = LogFactory.getLog(CollectionListService.class);

  // GET /collections
  public ServiceResponse get(ServiceContext context) {

    // Load the collections
    List<Collection> collectionList = LoadCollectionCommand.findAllAuthorizedForUser(context.getUserId());
    List<CollectionHandler> recordList = new ArrayList<>();
    for (Collection collection : collectionList) {
      recordList.add(new CollectionHandler(collection));
    }

    // Prepare the response
    ServiceResponse response = new ServiceResponse(200);
    ServiceResponseCommand.addMeta(response, "collection", recordList, null);
    response.setData(recordList);
    return response;
  }
}
