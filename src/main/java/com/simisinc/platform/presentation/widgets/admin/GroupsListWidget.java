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

package com.simisinc.platform.presentation.widgets.admin;

import java.util.List;

import com.simisinc.platform.application.DeleteGroupCommand;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/24/18 8:39 AM
 */
public class GroupsListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/groups-list.jsp";

  public WidgetContext execute(WidgetContext context) {
    // Load the collections
    List<Group> groupList = GroupRepository.findAll();
    context.getRequest().setAttribute("groupList", groupList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {

    // Determine what's being deleted
    long groupId = context.getParameterAsLong("groupId");
    if (groupId > -1) {
      Group group = GroupRepository.findById(groupId);
      try {
        DeleteGroupCommand.deleteGroup(group);
        context.setSuccessMessage("Group deleted");
        context.setRedirect("/admin/groups");
        return context;
      } catch (Exception e) {
        context.setErrorMessage("Error. Group could not be deleted.");
        context.setRedirect("/admin/groups");
        return context;
      }
    }

    return context;
  }
}
