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

package com.simisinc.platform.presentation.widgets.userProfile;

import com.simisinc.platform.application.userProfile.UserProfileCustomFieldCommand;
import com.simisinc.platform.domain.model.CustomField;
import com.simisinc.platform.domain.model.UserProfile;
import com.simisinc.platform.infrastructure.persistence.UserProfileRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.widgets.cms.PreferenceEntriesList;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/17/2022 8:34 AM
 */
public class MyProfileInfoWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/portal/custom-fields.jsp";
  static String TABLE_JSP = "/portal/custom-fields-table.jsp";

  public WidgetContext execute(WidgetContext context) {

    if (!context.getUserSession().isLoggedIn()) {
      LOG.warn("User is not logged in");
      return context;
    }

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Preferences
    String view = context.getPreferences().get("view");

    // Object to base the custom fields on
    UserProfile userProfile = UserProfileRepository.findByUserId(context.getUserId());
    if (userProfile == null) {
      LOG.error("Could not find current user record");
      return context;
    }

    // Use the fields preference to determine the object properties to be shown
    PreferenceEntriesList entriesList = context.getPreferenceAsDataList("fields");
    if (entriesList.isEmpty()) {
      return context;
    }

    // Render displayable values
    List<CustomField> fieldList = UserProfileCustomFieldCommand.renderDisplayValues(entriesList, userProfile);

    // Show the fields unless there are none
    if (fieldList.isEmpty()) {
      return context;
    }
    context.getRequest().setAttribute("fieldList", fieldList);

    // Determine the view
    if ("table".equals(view)) {
      context.setJsp(TABLE_JSP);
    } else {
      context.setJsp(JSP);
    }
    return context;
  }
}
