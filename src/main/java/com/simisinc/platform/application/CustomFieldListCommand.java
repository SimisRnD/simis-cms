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
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom fields commands
 *
 * @author matt rajkowski
 * @created 8/9/18 3:53 PM
 */
public class CustomFieldListCommand {

  private static Log LOG = LogFactory.getLog(CustomFieldListCommand.class);

  public static void addCustomField(List<CustomField> customFieldList, CustomField customField) {
    if (StringUtils.isBlank(customField.getValue())) {
      return;
    }
    boolean isNew = true;
    List<CustomField> duplicateList = new ArrayList<>();
    for (CustomField existingField : customFieldList) {
      if (existingField.getName().equalsIgnoreCase(customField.getName())) {
        if (!isNew) {
          duplicateList.add(existingField);
        } else {
          try {
            // Copy the properties over
            BeanUtils.copyProperties(existingField, customField);
            LOG.debug("Updated existing");
          } catch (Exception e) {
            LOG.error("Tried to copy properties from new field", e);
          }
          isNew = false;
        }
      }
    }
    if (isNew) {
      LOG.debug("Added new");
      customFieldList.add(customField);
    }
    if (!duplicateList.isEmpty()) {
      for (CustomField duplicate : duplicateList) {
        LOG.debug("Removed duplicate");
        customFieldList.remove(duplicate);
      }
    }
  }
}
