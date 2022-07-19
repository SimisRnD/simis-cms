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

package com.simisinc.platform.application.cms;

import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.presentation.widgets.cms.PreferenceEntriesList;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns widget preferences into Form Fields
 *
 * @author matt rajkowski
 * @created 6/1/18 11:43 AM
 */
public class FormFieldCommand {

  private static final String ALLOWED_CHARS = "abcdefghijklmnopqrstuvwyxz1234567890-";
  private static Log LOG = LogFactory.getLog(FormFieldCommand.class);

  public static List<FormField> parseFieldContent(String uniqueFormId, PreferenceEntriesList entriesList) {

    // @todo, use cache later (the uniqueFormId)

    LOG.debug("Fields found: " + entriesList.size());
    List<FormField> formFieldList = new ArrayList<>();
    List<String> nameList = new ArrayList<>();

    for (Map<String, String> valueMap : entriesList) {

      FormField formField = new FormField();

      // Label is displayed to user
      if (valueMap.containsKey("label")) {
        formField.setLabel(valueMap.get("label"));
      } else {
        formField.setLabel(valueMap.get("name"));
      }

      // Xml value is the html input type name, and database key
      if (valueMap.containsKey("value")) {
        formField.setName(valueMap.get("value"));
      } else {
        // Convert the label into a valid value
        formField.setName(generateHtmlName(formField.getLabel(), nameList));
      }

      // Use the type for formatting later
      formField.setType(valueMap.get("type"));
      formField.setPlaceholder(valueMap.get("placeholder"));
      formField.setDefaultValue(valueMap.get("defaultValue"));
      formField.setRequired("true".equals(valueMap.get("required")));
      formField.setUserValue(valueMap.get("userValue"));
      if (valueMap.containsKey("list")) {
        // Break into html values
        Map<String, String> optionsMap = new LinkedHashMap<>();
        String[] listOfOptions = valueMap.get("list").split(",");
        for (String option : listOfOptions) {
          if (option.contains("=")) {
            String[] thisOption = option.split("=");
            optionsMap.put(thisOption[0], thisOption[1]);
            continue;
          }
          optionsMap.put(generateHtmlName(option, null), option);
        }
        formField.setListOfOptions(optionsMap);
      }
      formFieldList.add(formField);
    }
    return formFieldList;
  }


  public static String generateHtmlName(String fieldName, List<String> nameList) {
    if (StringUtils.isBlank(fieldName)) {
      return null;
    }
    // Create a new one
    StringBuilder sb = new StringBuilder();
    String name = fieldName.toLowerCase();
    final int len = name.length();
    for (int i = 0; i < len; i++) {
      char c = name.charAt(i);
      if (ALLOWED_CHARS.indexOf(name.charAt(i)) > -1) {
        sb.append(c);
      } else if (c == ' ') {
        sb.append("-");
      }
    }
    String originalUniqueId = sb.toString();
    if (nameList == null) {
      return originalUniqueId;
    }

    // Find the next available unique instance
    int count = 1;
    String uniqueId = sb.toString();
    while (nameList.contains(uniqueId)) {
      ++count;
      uniqueId = originalUniqueId + "-" + count;
    }
    nameList.add(uniqueId);
    return uniqueId;
  }
}
