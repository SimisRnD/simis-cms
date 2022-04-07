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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.MenuItem;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.infrastructure.persistence.cms.MenuItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.MenuTabRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Deletes menu tabs
 *
 * @author matt rajkowski
 * @created 5/1/18 9:05 AM
 */
public class DeleteMenuTabCommand {

  private static Log LOG = LogFactory.getLog(DeleteMenuTabCommand.class);

  public static boolean deleteMenuTab(MenuTab menuTabBean) throws DataException {

    // Verify the object
    if (menuTabBean == null || menuTabBean.getId() == -1) {
      throw new DataException("The menu tab was not specified");
    }

    if ("/".equals(menuTabBean.getLink()) && menuTabBean.getId() == -1) {
      throw new DataException("The Home menu tab cannot be deleted");
    }

    if (!MenuTabRepository.remove(menuTabBean)) {
      throw new DataException("The tab could not be deleted");
    }
    return true;
  }

  public static boolean deleteMenuItem(MenuItem menuItemBean) throws DataException {

    // Verify the object
    if (menuItemBean == null || menuItemBean.getId() == -1) {
      throw new DataException("The menu item was not specified");
    }

    return MenuItemRepository.remove(menuItemBean);
  }
}
