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

package com.simisinc.platform.domain.model.items;

import java.util.Arrays;

/**
 * The item's visibility setting
 *
 * @author matt rajkowski
 * @created 4/18/18 9:18 PM
 */
public class PrivacyType {

  public static final int UNDEFINED = -1;
  public static final int PUBLIC = 2000;
  public static final int PUBLIC_READ_ONLY = 3000;
  public static final int PROTECTED = 4000;
  public static final int PRIVATE = 1000;

  public static boolean isValid(int id) {
    return Arrays.asList(new Integer[]{PRIVATE, PUBLIC, PUBLIC_READ_ONLY, PROTECTED}).contains(id);
  }

  public static boolean isPublic(int id) {
    return Arrays.asList(new Integer[]{PUBLIC, PUBLIC_READ_ONLY, PROTECTED}).contains(id);
  }

  public static int getTypeIdFromString(String name) {
    if ("private".equals(name)) {
      return PRIVATE;
    } else if ("public".equals(name)) {
      return PUBLIC;
    } else if ("public-read-only".equals(name)) {
      return PUBLIC_READ_ONLY;
    } else if ("protected".equals(name)) {
      return PROTECTED;
    }
    return UNDEFINED;
  }

  public static String getStringFromTypeId(int id) {
    if (id == PRIVATE) {
      return "private";
    } else if (id == PUBLIC) {
      return "public";
    } else if (id == PUBLIC_READ_ONLY) {
      return "public-read-only";
    } else if (id == PROTECTED) {
      return "protected";
    }
    return null;
  }
}
