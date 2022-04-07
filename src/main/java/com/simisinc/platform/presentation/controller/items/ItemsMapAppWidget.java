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

import com.simisinc.platform.application.gis.GISCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.maps.FindMapTilesCredentialsCommand;
import com.simisinc.platform.domain.model.Session;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.maps.MapCredentials;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemSpecification;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/25/20 8:45 PM
 */
public class ItemsMapAppWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String MAP_APP_JSP = "/maps/items-map-app.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine the mapping service
    MapCredentials mapCredentials = FindMapTilesCredentialsCommand.getCredentials();
    if (mapCredentials == null) {
      LOG.debug("Skipping - map service not defined");
      return context;
    }
    context.getRequest().setAttribute("mapCredentials", mapCredentials);

    // Determine the collection properties
    Collection collection = null;
    String collectionName = context.getPreferences().get("collection");
    if (StringUtils.isNotBlank(collectionName)) {
      collection = CollectionRepository.findByName(collectionName);
      if (collection == null) {
        LOG.warn("Specified collection was not found: " + collectionName);
        return null;
      }
    } else {
      String collectionUniqueId = context.getPreferences().get("collectionUniqueId");
      if (StringUtils.isNotBlank(collectionUniqueId)) {
        collection = LoadCollectionCommand.loadCollectionByUniqueId(collectionUniqueId);
        if (collection == null) {
          LOG.warn("Specified collectionUniqueId was not found: " + collectionUniqueId);
          return null;
        }
      }
    }
    if (collection == null) {
      LOG.warn("Set a collection or collectionUniqueId preference");
      return null;
    }

    // Validate access to the collection
    if (LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(collection.getId(), context.getUserId()) == null) {
      LOG.warn("User does not have access to this collection");
      return null;
    }
    context.getRequest().setAttribute("collection", collection);

    // Determine criteria
    ItemSpecification specification = new ItemSpecification();
    specification.setCollectionId(collection.getId());
    specification.setForUserId(context.getUserId());
    if (!context.hasRole("admin") && !context.hasRole("data-manager")) {
      specification.setApprovedOnly(true);
    }
    specification.setHasCoordinates(true);

//    long categoryId = context.getParameterAsLong("categoryId");
//    if (categoryId > -1) {
//      Category category = CategoryRepository.findById(categoryId);
//      if (category != null && category.getCollectionId() == collection.getId()) {
//        specification.setCategoryId(categoryId);
//        context.getRequest().setAttribute("category", category);
//      }
//    }

    // Query the data
    List<Item> itemList = ItemRepository.findAll(specification, null);
    if (itemList == null || itemList.isEmpty()) {
      if (!"true".equals(context.getPreferences().getOrDefault("showWhenEmpty", "false"))) {
        LOG.debug("Skipping, no items found for collection: " + collection.getUniqueId());
        return context;
      }
    }
    context.getRequest().setAttribute("itemList", itemList);

    // Determine the center geo point from data, or use a preset
    String latitude = context.getPreferences().get("latitude");
    String longitude = context.getPreferences().get("longitude");
    if (StringUtils.isBlank(latitude) || StringUtils.isBlank(longitude)) {
      Session center = GISCommand.centerFromItems(itemList);
      if (center != null) {
        latitude = String.valueOf(center.getLatitude());
        longitude = String.valueOf(center.getLongitude());
      }
    }
    // Validate
    if (StringUtils.isBlank(latitude) || StringUtils.isBlank(longitude) ||
        "-1".equals(latitude) || "-1".equals(longitude) ||
        "0.0".equals(latitude) || "0.0".equals(longitude) ||
        "0".equals(latitude) || "0".equals(longitude)) {
      LOG.debug("Skipping - no geo point");
      return context;
    }
    context.getRequest().setAttribute("latitude", latitude);
    context.getRequest().setAttribute("longitude", longitude);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Using center: " + latitude + " / " + longitude);
      LOG.debug("Items found: " + itemList.size());
    }

    // Determine optional map info
    String mapHeight = context.getPreferences().getOrDefault("mapHeight", "290px");
    context.getRequest().setAttribute("mapHeight", mapHeight);
    int mapZoomLevelValue = Integer.parseInt(context.getPreferences().getOrDefault("mapZoomLevel", "13"));
    context.getRequest().setAttribute("mapZoomLevel", String.valueOf(mapZoomLevelValue));

    // Show the JSP
    context.setJsp(MAP_APP_JSP);
    return context;
  }
}
