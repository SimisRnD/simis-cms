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

package com.simisinc.platform.presentation.controller.todoList;

import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.PreferenceEntriesList;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class TodoListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/todoList/todo-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    PreferenceEntriesList entriesList = context.getPreferenceAsDataList("items");
    if (!entriesList.isEmpty()) {
      Map<String, String> todoList = new LinkedHashMap<>();
      for (Map<String, String> valueMap : entriesList) {
        String label = valueMap.get("name");
        String checked = valueMap.get("checked");
        todoList.put(label, ("true".equals(checked) ? "true" : "false"));
      }
      context.getRequest().setAttribute("todoList", todoList);
    }
    context.setJsp(JSP);
    return context;
  }


}
