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

package com.simisinc.platform.presentation.controller;

import java.util.List;

import com.simisinc.platform.application.UserCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.UserSpecification;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

import org.apache.commons.lang3.StringUtils;

/**
 * Performs auto-complete queries
 *
 * @author matt rajkowski
 * @created 8/27/18 9:54 AM
 */
public class LookupUserAjax extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public WidgetContext execute(WidgetContext context) {

    // Determine the query
    String query = context.getParameter("q");
    LOG.debug("q: " + query);
    if (query == null) {
      context.setJson("[]");
      return context;
    }
    if (StringUtils.isNumeric(query)) {
      context.setJson("[]");
      return context;
    }

    // Determine the collection and permissions being searched
    UserSpecification specification = new UserSpecification();
    specification.setMatchesName(query);
    specification.setIsEnabled(true);

    // Retrieve the records
    List<User> list = UserRepository.findAll(specification, null);
    LOG.debug("Users found: " + list.size());

    // Determine the results to be shown
    StringBuilder sb = new StringBuilder();
    for (User user : list) {
      if (sb.length() > 0) {
        sb.append(",");
      }
      // [['Name', 'uniqueId', 'city']]...
      sb.append("[");
      sb.append("\"").append(JsonCommand.toJson(UserCommand.name(user))).append("\"").append(",");
      sb.append("\"").append(user.getId()).append("\"").append(",");
      if (StringUtils.isNotBlank(user.getCity())) {
        sb.append("\"").append(JsonCommand.toJson(user.getCity())).append("\"");
      } else {
        sb.append("\"\"");
      }
      sb.append("]");
    }
    context.setJson("[" + sb.toString() + "]");
    return context;
  }
}
