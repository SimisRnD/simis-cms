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

import com.simisinc.platform.application.UserCommand;
import com.simisinc.platform.application.cms.FormatDateCommand;
import com.simisinc.platform.application.items.ActivityListCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.items.Activity;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.items.ActivityRepository;
import com.simisinc.platform.infrastructure.persistence.items.ActivitySpecification;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Performs paging for activity list entries
 *
 * @author matt rajkowski
 * @created 8/21/18 11:19 AM
 */
public class ActivityListAjax extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public WidgetContext execute(WidgetContext context) {

    // Determine the item id
    long itemId = context.getParameterAsLong("itemId");
    if (itemId == -1) {
      LOG.error("Item Id not set");
      context.setJson("[]");
      return context;
    }

    // Load the item
    Item item = LoadItemCommand.loadItemById(itemId);
    if (item == null) {
      LOG.debug("Item Id not found: " + itemId);
      context.setJson("[]");
      return context;
    }

    // Validate access to the collection
    Collection collection = LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(item.getCollectionId(), context.getUserId());
    if (collection == null) {
      LOG.debug("No access to collection");
      context.setJson("[]");
      return context;
    }

    // Just get the new ones
    long minTimestamp = context.getParameterAsLong("minTimestamp");

    ActivitySpecification specification = new ActivitySpecification();
    specification.setItemId(item.getId());
    specification.setMinTimestamp(minTimestamp);

    DataConstraints constraints = new DataConstraints();
    constraints.setColumnToSortBy("created");

    // Retrieve the records
    List<Activity> activityList = ActivityRepository.findAll(specification, constraints);
    if (activityList == null || activityList.isEmpty()) {
      context.setJson("[]");
      return context;
    }

    // Use HTML messages
    ActivityListCommand.createMessageHtml(activityList);

    // Determine the results to be shown
    StringBuilder sb = new StringBuilder();

    for (Activity activity : activityList) {
      if (activity.getCreated().getTime() >= minTimestamp) {
        minTimestamp = activity.getCreated().getTime() + 1;
      }
      if (sb.length() > 0) {
        sb.append(",");
      }
      sb.append("{");
      if (activity.getCreatedBy() > 0) {
        sb.append("\"user\":").append("\"").append(JsonCommand.toJson(UserCommand.name(activity.getCreatedBy()))).append("\"").append(",");
      }
      if (StringUtils.isNotBlank(activity.getSource())) {
        sb.append("\"source\":").append("\"").append(JsonCommand.toJson(activity.getSource())).append("\"").append(",");
      }
      sb.append("\"date\":").append("\"").append(FormatDateCommand.formatMonthDayYear(activity.getCreated())).append("\"").append(",");
      sb.append("\"time\":").append("\"").append(FormatDateCommand.formatTime(activity.getCreated())).append("\"").append(",");
      sb.append("\"type\":").append("\"").append(JsonCommand.toJson(activity.getActivityType())).append("\"").append(",");
      sb.append("\"messageHtml\":").append("\"").append(JsonCommand.toJson(activity.getMessageHtml())).append("\"");
      sb.append("}");
    }

    String activityArray = "[" + sb.toString() + "]";

    context.setJson("{" +
        "\"minTimestamp\":" + minTimestamp + ", " +
        "\"activityList\": " + activityArray +
        "}");

    return context;
  }
}
