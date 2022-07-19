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

package com.simisinc.platform.application.userProfile;

import com.simisinc.platform.application.CustomFieldCommand;
import com.simisinc.platform.application.CustomFieldFormatCommand;
import com.simisinc.platform.domain.model.CustomField;
import com.simisinc.platform.domain.model.UserProfile;
import com.simisinc.platform.presentation.widgets.cms.PreferenceEntriesList;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns widget preferences into Data Lists and Form Fields
 *
 * @author matt rajkowski
 * @created 7/17/2022 6:58 PM
 */
public class UserProfileCustomFieldCommand {

  private static Log LOG = LogFactory.getLog(UserProfileCustomFieldCommand.class);

  public static List<CustomField> renderDisplayValues(PreferenceEntriesList entriesList, UserProfile userProfile) {
    return parseCustomFields(entriesList, userProfile, true);
  }

  public static List<CustomField> prepareFormValues(PreferenceEntriesList entriesList, UserProfile userProfile) {
    return parseCustomFields(entriesList, userProfile, false);
  }

  public static List<CustomField> parseCustomFields(PreferenceEntriesList entriesList, UserProfile userProfile, boolean requireValue) {
    List<CustomField> fieldList = new ArrayList<>();
    for (Map<String, String> valueMap : entriesList) {
      try {

        // Start a custom field
        CustomField customField = CustomFieldCommand.createCustomField(valueMap);

        // Determine the field's value
        if (customField == null || StringUtils.isBlank(customField.getProperty())) {
          LOG.debug("No custom field created");
          continue;
        }

        // Determine the values to show
        String value = null;
        String objectParameter = customField.getProperty();
        if (objectParameter.startsWith("custom.")) {
          String customFieldName = objectParameter.substring("custom.".length());
          CustomField field = userProfile.getCustomField(customFieldName);
          if (field != null) {
            value = field.getValue();
          }
        } else {
          value = BeanUtils.getProperty(userProfile, objectParameter);
        }
        CustomFieldFormatCommand.formatValue(customField, value);
        if (requireValue && StringUtils.isBlank(customField.getValue())) {
          LOG.debug("No value found for: " + objectParameter);
          continue;
        }
        fieldList.add(customField);
      } catch (Exception e) {
        LOG.error("Could not parse custom field: " + e.getMessage());
      }
    }
    return fieldList;
  }
}
