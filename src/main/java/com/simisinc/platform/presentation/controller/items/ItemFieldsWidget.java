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

import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemCustomField;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.PreferenceEntriesList;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/20/18 2:23 PM
 */
public class ItemFieldsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/items/item-fields.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Load the authorized item
    Item item = LoadItemCommand.loadItemByUniqueId(context.getCoreData().get("itemUniqueId"));
    if (item == null) {
      return null;
    }

    PreferenceEntriesList entriesList = context.getPreferenceAsDataList("fields");
    if (entriesList.isEmpty()) {
      return context;
    }
    Map<String, String> fieldList = new LinkedHashMap<>();
    for (Map<String, String> valueMap : entriesList) {
      try {
        String label = valueMap.get("name");
        String objectParameter = valueMap.get("value");
        String type = valueMap.get("type");
        String value = null;
        // Determine the values to show
        if ("categoryList".equals(objectParameter)) {
          List<Category> categoryList = CategoryRepository.findAllByItemId(item.getId());
          if (!categoryList.isEmpty()) {
            for (Category category : categoryList) {
              if (value == null) {
                value = category.getName();
              } else {
                value += "; " + category.getName();
              }
            }
          }
        } else {
          value = BeanUtils.getProperty(item, objectParameter);
        }
        if (StringUtils.isBlank(value)) {
          continue;
        }
        // Validate and format as needed
        if ("url".equals(type)) {
          // @verify the URL
          if (!value.startsWith("http")) {
            value = "http://" + value;
          }
        } else if ("number".equals(type)) {
          DecimalFormat decimalFormat = new DecimalFormat("0.#####");
          value = decimalFormat.format(Double.valueOf(value));
          if ("0".equals(value) || "0.0".equals(value) || "0.00".equals(value)) {
            continue;
          }
        } else if ("currency".equals(type)) {
          NumberFormat format = NumberFormat.getCurrencyInstance();
          value = format.format(Double.valueOf(value));
          if ("$0.00".equals(value)) {
            continue;
          }
        } else if ("date".equals(type)) {
          // 2018-08-13 11:00:00.0
          if (value.contains(" ")) {
            value = value.substring(0, value.indexOf(" "));
          }
        } else if ("dateTime".equals(type)) {
          // 2018-08-13 11:00:00.0
          if (value.contains(":00.0")) {
            value = value.substring(0, value.indexOf(":00.0"));
          }
        } else if ("imageUrl".equals(type)) {
          // @verify the URL
          if (!value.startsWith("http")) {
            value = "http://" + value;
          }
        }
        fieldList.put(label, value);
      } catch (Exception e) {
        LOG.error("Could not get property: " + e.getMessage());
      }
    }

    // Show the custom fields if requested
    boolean showAllCustomFields = "true".equals(context.getPreferences().get("showAllCustomFields"));
    if (showAllCustomFields && item.getCustomFieldList() != null && !item.getCustomFieldList().isEmpty()) {
      for (ItemCustomField customField : item.getCustomFieldList()) {
        fieldList.put(customField.getName(), customField.getValue());
      }
    }

    // Show the fields unless there are none
    if (fieldList.isEmpty()) {
      return context;
    }
    context.getRequest().setAttribute("fieldList", fieldList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }
}
