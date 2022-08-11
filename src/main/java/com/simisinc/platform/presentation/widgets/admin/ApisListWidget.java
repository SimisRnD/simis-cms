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

package com.simisinc.platform.presentation.widgets.admin;

import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.rest.controller.XMLServiceLoader;

import java.util.*;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 12/21/21 2:30 PM
 */
public class ApisListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/apis-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Load the list of APIs
    XMLServiceLoader xmlServiceLoader = new XMLServiceLoader();
    xmlServiceLoader.addDirectory(context.getRequest().getServletContext(), "rest-services");
    List apiList = new ArrayList<Map<String, String>>();
    for (Map<String, String> service : xmlServiceLoader.getServiceLibrary()) {
      apiList.add(service);
    }
    apiList.sort(Comparator.comparing(o -> ((String) ((Map) o).get("endpoint"))));
    apiList.sort(Comparator.comparing(o -> ((String) ((Map) o).get("serviceClass"))));
    context.getRequest().setAttribute("apiList", apiList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the editor
    context.setJsp(JSP);
    return context;
  }
}
