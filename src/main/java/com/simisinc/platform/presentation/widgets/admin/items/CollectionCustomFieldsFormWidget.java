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

package com.simisinc.platform.presentation.widgets.admin.items;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.CustomFieldListJSONCommand;
import com.simisinc.platform.domain.model.CustomField;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Form for collection custom fields
 *
 * @author matt rajkowski
 * @created 7/20/22 9:26 PM
 */
public class CollectionCustomFieldsFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/custom-fields-form-json.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine the collection
    long collectionId = context.getParameterAsLong("collectionId");
    Collection collection = CollectionRepository.findById(collectionId);
    if (collection == null) {
      context.setErrorMessage("Error. Collection was not found.");
      return context;
    }
    context.getRequest().setAttribute("collection", collection);

    // Determine the form bean (new request or an error occurred)
    String json = null;
    if (context.getRequestObject() != null) {
      json = (String) context.getRequestObject();
    } else {
      json = CustomFieldListJSONCommand.createJSONString(collection.getCustomFieldList());
    }
    try {
      JsonNode root = JsonLoader.fromString(json);
      json = root.toPrettyString();
    } catch (Exception e) {
      context.setErrorMessage(e.getMessage());
    }
    if (StringUtils.isNotBlank(json)) {
      context.getRequest().setAttribute("json", json);
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Check the collection
    long collectionId = context.getParameterAsLong("collectionId");
    Collection collection = CollectionRepository.findById(collectionId);
    if (collection == null) {
      context.setErrorMessage("Collection was not found");
      return context;
    }

    // Determine form request parameters
    String jsonValue = context.getParameter("json");

    // Process the request
    try {
      // Validate the JSON
      if (!StringUtils.isBlank(jsonValue)) {
        Map<String, CustomField> customFieldList = CustomFieldListJSONCommand.populateFromJSONString(jsonValue);
        collection.setCustomFieldList(customFieldList);
      } else {
        collection.setCustomFieldList(null);
      }
      // Update the repository
      CollectionRepository.updateCustomFields(collection);
      context.setSuccessMessage("The form was saved");
    } catch (Exception e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(jsonValue);
    }

    // Determine the page to return to
    context.setRedirect("/admin/collection-custom-fields?collectionId=" + collection.getId());
    return context;
  }
}
