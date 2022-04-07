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

package com.simisinc.platform.presentation.controller.mailinglists;

import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.mailinglists.MailingListMemberCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

/**
 * Manages a user's mailing list preferences
 *
 * @author matt rajkowski
 * @created 6/18/19 9:59 PM
 */
public class MailingListAjax extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public WidgetContext execute(WidgetContext context) {

    // Validate the token
    String token = context.getParameter("token");
    if (!token.equals(context.getUserSession().getFormToken())) {
      context.setJson("[]");
      return context;
    }

    // Verify user is logged in
    if (!context.getUserSession().isLoggedIn()) {
      context.setJson("[]");
      return context;
    }

    // Check for a mailing list
    long mailingListId = context.getParameterAsLong("id");
    if (mailingListId <= 0) {
      context.setJson("[]");
      return context;
    }

    MailingList mailingList = MailingListRepository.findById(mailingListId);
    if (mailingList == null || !mailingList.getShowOnline()) {
      context.setJson("[]");
      return context;
    }

    // Load the user
    User user = LoadUserCommand.loadUser(context.getUserId());
    if (user == null) {
      context.setJson("[]");
      return context;
    }

    // Execute the action
    String command = context.getParameter("command");
    try {
      if ("subscribe".equals(command)) {
        MailingListMemberCommand.subscribe(mailingList, user, context.getUserSession());
      } else if ("unsubscribe".equals(command)) {
        MailingListMemberCommand.unsubscribe(mailingList, user);
      } else {
        context.setJson("[]");
        return context;
      }
    } catch (Exception e) {
      context.setJson("{\"status\":\"1\"}");
      return context;
    }

    // Return status ok
    context.setJson("{\"status\":\"0\"}");
    return context;
  }
}
