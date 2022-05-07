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

package com.simisinc.platform.presentation.widgets.userProfile;

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 6/9/19 4:10 PM
 */
public class MyEmailPreferencesWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/userProfile/my-email-preferences.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("subtitle", context.getPreferences().get("subtitle"));
    boolean showWhenEmpty = "true".equals(context.getPreferences().getOrDefault("showWhenEmpty", "true"));

    // This widget is for a logged in user
    if (!context.getUserSession().isLoggedIn()) {
      return null;
    }

    // Determine the user and their email record
    User user = context.getUserSession().getUser();
    Email email = EmailRepository.findByEmailAddress(user.getEmail());

    // Retrieve the available lists and preferences
    List<MailingList> mailingList = MailingListRepository.findOnlineLists();
    context.getRequest().setAttribute("mailingList", mailingList);

    // Determine if the widget is shown
    if (!showWhenEmpty && (mailingList == null || mailingList.isEmpty())) {
      return context;
    }

    // Find this user's mailing lists
    if (email != null) {
      List<MailingList> userMailingList = MailingListRepository.findOnlineListsForEmail(email.getId());
      if (userMailingList != null) {
        List<Long> subscribedLists = new ArrayList<>();
        for (MailingList list : userMailingList) {
          subscribedLists.add(list.getId());
        }
        context.getRequest().setAttribute("subscribedLists", subscribedLists);
      }
    }

    context.setJsp(JSP);
    return context;
  }
}
