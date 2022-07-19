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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Custom fields commands
 *
 * @author matt rajkowski
 * @created 4/28/18 2:23 PM
 */
public class CustomFieldFormatCommand {

  private static Log LOG = LogFactory.getLog(CustomFieldFormatCommand.class);

  public static void formatValue(CustomField customField, String value) {
    if (StringUtils.isBlank(value)) {
      LOG.debug("No value to format for field: " + customField.getName());
      return;
    }

    // Format the value
    String type = customField.getType();
    if ("url".equals(type)) {
      // @verify the URL
      if (!value.startsWith("http://") && !value.startsWith("https://")) {
        value = "http://" + value;
      }
    } else if ("number".equals(type)) {
      try {
        DecimalFormat decimalFormat = new DecimalFormat("0.#####");
        value = decimalFormat.format(Double.valueOf(value));
      } catch (Exception e) {
        // just use the value
      }
      if ("0".equals(value) || "0.0".equals(value) || "0.00".equals(value)) {
        value = null;
      }
    } else if ("currency".equals(type)) {
      try {
        NumberFormat format = NumberFormat.getCurrencyInstance();
        value = format.format(Double.valueOf(value));
      } catch (Exception e) {
        // just use the value
      }
      if ("$0.00".equals(value)) {
        value = null;
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
      if (!value.startsWith("http://") && !value.startsWith("https://")) {
        value = "http://" + value;
      }
    }
    customField.setValue(value);
  }
}
