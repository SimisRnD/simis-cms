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

import com.simisinc.platform.application.cms.FormatDateCommand;
import com.simisinc.platform.application.items.ActivityListCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.items.Activity;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.items.ActivityRepository;
import com.simisinc.platform.infrastructure.persistence.items.ActivitySpecification;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

import java.util.Collections;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/30/18 5:03 PM
 */
public class ActivityListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/items/activity-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Load the authorized item
    String itemUniqueId = context.getPreferences().getOrDefault("uniqueId", context.getCoreData().get("itemUniqueId"));
    Item item = LoadItemCommand.loadItemByUniqueId(itemUniqueId);
    if (item == null) {
      return null;
    }
    context.getRequest().setAttribute("item", item);

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "20"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage, "created", "desc");
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Load the latest entries
    long maxTimestamp = System.currentTimeMillis();
    context.getRequest().setAttribute("minTimestamp", maxTimestamp + 1);

    ActivitySpecification specification = new ActivitySpecification();
    specification.setItemId(item.getId());
    specification.setMaxTimestamp(maxTimestamp);
    //String[] activityTypes = {ActivityType.CHAT, ActivityType.ITEM_CREATED, ActivityType.RELATED_ITEM_ADDED};
    List<Activity> activityList = ActivityRepository.findAll(specification, constraints);
    String activityDate = "---";
    if (activityList != null && !activityList.isEmpty()) {
      activityDate = FormatDateCommand.formatMonthDayYear(activityList.get(0).getCreated());
      ActivityListCommand.createMessageHtml(activityList);
      Collections.reverse(activityList);
    }
    context.getRequest().setAttribute("activityList", activityList);
    context.getRequest().setAttribute("activityDate", activityDate);

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

}
