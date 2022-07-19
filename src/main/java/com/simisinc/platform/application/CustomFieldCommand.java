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

package com.simisinc.platform.application;

import com.simisinc.platform.domain.model.CustomField;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.simisinc.platform.application.cms.FormFieldCommand.generateHtmlName;

/**
 * Custom fields commands
 *
 * @author matt rajkowski
 * @created 4/28/18 2:23 PM
 */
public class CustomFieldCommand {

  private static Log LOG = LogFactory.getLog(CustomFieldCommand.class);

  public static CustomField createCustomField(Map<String, String> valueMap) {

    try {
      // Determine the name/label
      String name = valueMap.get("name");
      String label = valueMap.get("label");
      if (label == null) {
        label = name;
      }
      if (name == null) {
        name = label;
      }
      // Construct the custom field
      CustomField field = new CustomField();
      field.setLabel(label);
      field.setName(generateHtmlName(name, null));
      field.setType(valueMap.get("type"));
      field.setPlaceholder(valueMap.get("placeholder"));
      field.setProperty(valueMap.get("property"));
      field.setDefaultValue(valueMap.get("defaultValue"));
      field.setRequired("true".equals(valueMap.get("required")));
      if (valueMap.containsKey("list")) {
        // Break into html values
        Map<String, String> optionsMap = new LinkedHashMap<>();
        String[] listOfOptions = valueMap.get("list").split(",");
        for (String option : listOfOptions) {
          if (option.contains("=")) {
            String[] thisOption = option.split("=");
            optionsMap.put(generateHtmlName(thisOption[0], null), thisOption[1]);
            continue;
          }
          optionsMap.put(generateHtmlName(option, null), option);
        }
        field.setListOfOptions(optionsMap);
      }
      return field;
    } catch (Exception e) {
      LOG.error("Could not create custom field: " + e.getMessage());
    }
    return null;
  }
}