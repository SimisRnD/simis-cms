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

package com.simisinc.platform.presentation.widgets.cms;

import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 11/18/19 4:28 PM
 */
public class GlobalMessageWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/global-message.jsp";

  public WidgetContext execute(WidgetContext context) {
    String key = context.getPreferences().getOrDefault("key", "message");
    String value = context.getSharedRequestValue(key);
    if (StringUtils.isNotBlank(value)) {
      String type = context.getPreferences().getOrDefault("type", "error");
      context.getRequest().setAttribute("messageType", type);
      context.getRequest().setAttribute("messageValue", value);
      context.setJsp(JSP);
      return context;
    }
    return null;
  }
}
