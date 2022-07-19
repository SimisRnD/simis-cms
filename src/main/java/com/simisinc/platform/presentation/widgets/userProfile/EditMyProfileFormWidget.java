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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.userProfile.SaveUserProfileCommand;
import com.simisinc.platform.application.userProfile.UserProfileCustomFieldCommand;
import com.simisinc.platform.domain.model.CustomField;
import com.simisinc.platform.domain.model.UserProfile;
import com.simisinc.platform.infrastructure.persistence.UserProfileRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.widgets.cms.PreferenceEntriesList;
import org.apache.commons.beanutils.BeanUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/17/2022 8:34 AM
 */
public class EditMyProfileFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/userProfile/my-profile-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Require a logged in user
    if (!context.getUserSession().isLoggedIn()) {
      LOG.warn("User is not logged in");
      return context;
    }

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("subtitle", context.getPreferences().get("subtitle"));

    // Use the fields preference to determine the object properties to be shown
    PreferenceEntriesList entriesList = context.getPreferenceAsDataList("fields");
    if (entriesList.isEmpty()) {
      return context;
    }

    // Object to base the custom fields on
    UserProfile userProfile = UserProfileRepository.findByUserId(context.getUserId());
    if (userProfile == null) {
      LOG.error("Could not find current user record");
      return context;
    }

    // Prepare form fields
    List<CustomField> fieldList = UserProfileCustomFieldCommand.prepareFormValues(entriesList, userProfile);

    // Show the fields unless there are none
    if (fieldList.isEmpty()) {
      return context;
    }
    context.getRequest().setAttribute("fieldList", fieldList);

    // Determine the cancel page
    String cancelUrl = context.getPreferences().get("cancelUrl");
    context.getRequest().setAttribute("cancelUrl", UrlCommand.getValidReturnPage(cancelUrl));

    // Determine the view
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {
    // Require a logged in user
    if (!context.getUserSession().isLoggedIn()) {
      LOG.warn("User is not logged in");
      return context;
    }

    // Use the fields preference to determine the object properties to update
    PreferenceEntriesList entriesList = context.getPreferenceAsDataList("fields");
    if (entriesList.isEmpty()) {
      LOG.warn("No properties are specified in the widget to edit");
      return context;
    }

    UserProfile userProfile = UserProfileRepository.findByUserId(context.getUserId());
    if (userProfile == null) {
      LOG.warn("UserProfile record not found");
      return context;
    }

    // Determine the fields and values to update on the object
    List<CustomField> fieldList = UserProfileCustomFieldCommand.prepareFormValues(entriesList, userProfile);
    for (CustomField field : fieldList) {
      String parameterName = context.getUniqueId() + field.getName();
      String parameterValue = context.getParameter(parameterName);
      if (field.getProperty().startsWith(("custom."))) {
        if ("list".equals(field.getType()) && field.getListOfOptions() != null) {
          field.setValue(field.getListOfOptions().get(parameterValue));
        } else {
          field.setValue(parameterValue);
        }
        userProfile.addCustomField(field);
      } else {
        BeanUtils.setProperty(userProfile, field.getProperty(), parameterValue);
      }
    }
    userProfile.setModifiedBy(context.getUserId());

    // Save the item
    try {
      userProfile = SaveUserProfileCommand.saveUserProfile(userProfile);
      if (userProfile == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(userProfile);
      return context;
    }

    // Determine the page to return to
    String returnPage = context.getPreferences().getOrDefault("returnPage", UrlCommand.getValidReturnPage(context.getParameter("returnPage")));
    context.setSuccessMessage("Thanks, your record was saved!");
    context.setRedirect(returnPage);
    return context;
  }
}
