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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.cms.FormFieldCommand;
import com.simisinc.platform.domain.model.CustomField;

/**
 * Methods to combine template lists and lists already being used
 *
 * @author matt rajkowski
 * @created 7/23/22 7:30 AM
 */
public class CustomFieldListMergeCommand {

  private static Log LOG = LogFactory.getLog(CustomFieldListMergeCommand.class);

  /**
   * Merges two custom field lists while maintaining differences in options
   * @param mainList
   * @param secondaryList
   * @return
   */
  public static Map<String, CustomField> mergeCustomFieldLists(Map<String, CustomField> mainList,
      Map<String, CustomField> secondaryList) {

    if (mainList == null && secondaryList == null) {
      return null;
    }

    // Just use the second list since there's nothing in the main one
    if (mainList == null || mainList.isEmpty()) {
      return secondaryList;
    }

    if (secondaryList == null || secondaryList.isEmpty()) {
      return mainList;
    }

    // Start a new list, and default it to the main list, which will be returned so the current lists are not modified during iterator
    Map<String, CustomField> mergedList = new LinkedHashMap<>();
    mergedList.putAll(mainList);

    // Go through the secondary list, replacing the original, but retain the original's List
    // If the original list does not contain the secondary's value, then append it to the original's list
    for (Map.Entry<String, CustomField> set : secondaryList.entrySet()) {
      // Determine how the secondaryList entry and value should be merged
      String name = set.getKey();
      CustomField thisCustomField = set.getValue();
      if (!mergedList.containsKey(name)) {
        // Retain the field
        mergedList.put(name, thisCustomField);
      } else {
        // Retain the value
        CustomField existingField = mergedList.get(name);
        existingField.setValue(thisCustomField.getValue());
        if ("list".equals(existingField.getType())) {
          // Merge the list options
          LOG.debug("Existing list of options: " + existingField.getListOfOptions());
          if (StringUtils.isNotBlank(thisCustomField.getValue())) {
            if (!existingField.getListOfOptions().containsKey(thisCustomField.getValue())) {
              existingField.getListOfOptions().put(
                  FormFieldCommand.generateHtmlName(thisCustomField.getValue(), null), thisCustomField.getValue());
              LOG.debug("Missing option from item: " + thisCustomField.getValue());

            }
          }
        }
      }
    }
    return mergedList;
  }
}
