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

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.mailinglists.DeleteMailingListCommand;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailSpecification;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/24/19 10:35 PM
 */
public class MailingListsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/mailing-lists.jsp";
  static String EMAIL_SEARCH_RESULTS_JSP = "/admin/email-search-results-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    String jsp = JSP;

    // Check shared request values for search criteria
    String searchEmail = context.getSharedRequestValue("searchEmail");
    if (searchEmail == null) {
      searchEmail = context.getParameter("searchEmail");
    }
    String searchName = context.getSharedRequestValue("searchName");
    if (searchName == null) {
      searchName = context.getParameter("searchName");
    }
    if (StringUtils.isNotBlank(searchName) || StringUtils.isNotBlank(searchEmail)) {

      EmailSpecification specification = new EmailSpecification();
      specification.setMatchesEmail(searchEmail);
      specification.setMatchesName(searchName);

      // Query the data
      List<Email> emailList = EmailRepository.findAll(specification, null);
      context.getRequest().setAttribute("emailList", emailList);

      context.getRequest().setAttribute("title", "Email search results");
      jsp = EMAIL_SEARCH_RESULTS_JSP;

    } else {

      // Load the mailing lists
      List<MailingList> mailingLists = MailingListRepository.findAll();
      context.getRequest().setAttribute("mailingLists", mailingLists);

      String service = LoadSitePropertyCommand.loadByName("mailing-list.service");
      if (StringUtils.isNotBlank(service)) {
        context.getRequest().setAttribute("service", service);
      }

      // Standard request items
      context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
      context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    }

    // Show the list
    context.setJsp(jsp);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {
    // Determine what's being deleted
    long mailingListId = context.getParameterAsLong("mailingListId");
    if (mailingListId > -1) {
      MailingList mailingList = MailingListRepository.findById(mailingListId);
      if (mailingList == null) {
        context.setErrorMessage("Mailing list not found");
      } else {
        if (mailingList.getMemberCount() > 10) {
          context.setWarningMessage("Mailing list cannot be deleted, there are related records");
        } else if (DeleteMailingListCommand.delete(mailingList)) {
          context.setSuccessMessage("Mailing list deleted");
        } else {
          context.setWarningMessage("Mailing list could not be deleted, there are dependencies");
        }
      }
    }
    context.setRedirect("/admin/mailing-lists");
    return context;
  }
}
