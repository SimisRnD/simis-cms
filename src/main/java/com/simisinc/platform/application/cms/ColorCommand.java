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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Color validation functions
 *
 * @author matt rajkowski
 * @created 7/13/22 4:32 PM
 */
public class ColorCommand {

  private static Log LOG = LogFactory.getLog(ColorCommand.class);

  public static boolean isHexColor(String hexColor) {
    return (hexColor != null && hexColor.length() == 7 && hexColor.startsWith("#") && StringUtils.isAlphanumeric(hexColor.substring(1)));
  }

}
