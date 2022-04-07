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

package com.simisinc.platform.presentation.controller.items;

import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemSpecification;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Performs auto-complete queries
 *
 * @author matt rajkowski
 * @created 7/27/18 2:09 PM
 */
public class LookupItemAjax extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public WidgetContext execute(WidgetContext context) {

    // Determine the query
    String query = context.getParameter("q");
    LOG.debug("q: " + query);
    if (query == null) {
      context.setJson("[]");
      return context;
    }
    if (StringUtils.isNumeric(query)) {
      context.setJson("[]");
      return context;
    }

    // Determine the collection to search within
    long collectionId = context.getParameterAsLong("cid");
    if (collectionId == -1) {
      context.setJson("[]");
      return context;
    }

    // Validate access to the collection
    Collection collection = LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(collectionId, context.getUserId());
    if (collection == null) {
      context.setJson("[]");
      return context;
    }

    // Use the item id... to exclude it from the results
    long itemId = context.getParameterAsLong("iid");

    // Determine the collection and permissions being searched
    ItemSpecification specification = new ItemSpecification();
    specification.setCollectionId(collectionId);
    specification.setForUserId(context.getUserId());
    specification.setMatchesName(query);

    // Retrieve the records
    List<Item> list = ItemRepository.findAll(specification, null);
    LOG.debug("Items found: " + list.size());

    // Determine the results to be shown
    StringBuilder sb = new StringBuilder();
    for (Item item : list) {
      if (item.getId() == itemId) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(",");
      }
      // [['Name', 'uniqueId', 'city']]...
      sb.append("[");
      sb.append("\"").append(JsonCommand.toJson(item.getName())).append("\"").append(",");
      sb.append("\"").append(item.getUniqueId()).append("\"").append(",");
      if (StringUtils.isNotBlank(item.getCity())) {
        sb.append("\"").append(JsonCommand.toJson(item.getCity())).append("\"");
      } else {
        sb.append("\"\"");
      }
      sb.append("]");
    }
    context.setJson("[" + sb.toString() + "]");
    return context;
  }
}
