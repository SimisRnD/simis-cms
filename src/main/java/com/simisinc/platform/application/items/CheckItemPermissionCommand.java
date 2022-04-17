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

package com.simisinc.platform.application.items;

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.items.Item;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Methods to check item object permissions
 *
 * @author matt rajkowski
 * @created 10/1/18 8:15 AM
 */
public class CheckItemPermissionCommand {

  private static Log LOG = LogFactory.getLog(CheckItemPermissionCommand.class);

  public static boolean userHasViewPermission(Item item, User user) {
    Item authorizedItem = LoadItemCommand.loadItemForAuthorizedUser(item, user);
    if (authorizedItem == null) {
      return false;
    }
    return true;
  }
}
