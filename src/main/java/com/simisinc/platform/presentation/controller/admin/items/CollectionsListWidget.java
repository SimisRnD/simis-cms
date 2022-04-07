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

package com.simisinc.platform.presentation.controller.admin.items;

import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/18/18 10:25 PM
 */
public class CollectionsListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/collections-list.jsp";
  static String CARDS_JSP = "/admin/collections-cards.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Load the collections
    List<Collection> collectionList = CollectionRepository.findAll();
    context.getRequest().setAttribute("collectionList", collectionList);

    // Determine the view
    String view = context.getPreferences().getOrDefault("view", null);
    if ("cards".equals(view)) {
      context.setJsp(CARDS_JSP);
    } else {
      context.setJsp(JSP);
    }
    return context;
  }
}
