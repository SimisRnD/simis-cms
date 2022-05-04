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

package com.simisinc.platform.presentation.controller.cms;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

/**
 * Displays dynamic copyright information
 *
 * @author matt rajkowski
 * @created 2/8/21 12:00 PM
 */
public class CopyrightWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public WidgetContext execute(WidgetContext context) {
    String name = context.getPreferences().getOrDefault("name", LoadSitePropertyCommand.loadByName("site.name"));
    String tag = context.getPreferences().getOrDefault("tag", "All Rights Reserved.");
    context.setHtml(
        "&copy; " + new java.text.SimpleDateFormat("yyyy").format(new java.util.Date()) + " " +
            "<span translate=\"no\">" + name + (!name.endsWith(".") ? "." : "") + "</span>" + tag);
    return context;
  }
}
