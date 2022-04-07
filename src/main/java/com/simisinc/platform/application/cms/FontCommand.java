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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Mostly to work with the different font libraries
 *
 * @author matt rajkowski
 * @created 3/26/22 9:44 AM
 */
public class FontCommand {

  private static Log LOG = LogFactory.getLog(FontCommand.class);

  private static String version = "free6";
//  private static String version = "pro6";

  public static final String REGULAR = "regular";
  public static final String LIGHT = "light";
  public static final String DUOTONE = "duotone";
  public static final String THIN = "thin";
  public static final String SOLID = "solid";
  public static final String BRANDS = "brands";

  public static String fontawesome() {
    // @todo When entry exists in DB whether true/false
//    LoadSitePropertyCommand.loadByName("cms.fontawesome.pro", "false");
    if ("pro6".equals(version)) {
      return "fontawesome-pro-6.1.1-web";
    }
    return "fontawesome-free-6.1.1-web";
  }

  private static String fa(String type) {

    // Font Awesome Pro 6
    // <i class="fa-regular fa-user"></i>
    if ("pro6".equals(version)) {
      if (REGULAR.equals(type)) {
        return "fa-regular";
      }
      if (LIGHT.equals(type)) {
        return "fa-light";
      }
      if (DUOTONE.equals(type)) {
        return "fa-duotone";
      }
      if (THIN.equals(type)) {
        return "fa-thin";
      }
      if (SOLID.equals(type)) {
        return "fa-solid";
      }
      if (BRANDS.equals(type)) {
        return "fa-brands";
      }
    }

    // Font Awesome Free 6
    // <i class="fa-regular fa-user"></i>
    if (BRANDS.equals(type)) {
      return "fa-brands";
    }
    return "fa-solid";
  }

  public static String far() {
    return fa(REGULAR);
  }

  public static String fal() {
    return fa(LIGHT);
  }

  public static String fad() {
    return fa(DUOTONE);
  }

  public static String fat() {
    return fa(THIN);
  }

  public static String fas() {
    return fa(SOLID);
  }

  public static String fab() {
    return fa(BRANDS);
  }
}
