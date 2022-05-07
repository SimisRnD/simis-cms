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

package com.simisinc.platform.presentation.widgets.mailinglists;

import com.simisinc.platform.application.AppException;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.mailinglists.SaveMailingListCommand;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;

import java.lang.reflect.InvocationTargetException;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/24/19 10:44 PM
 */
public class MailingListFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/mailing-list-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Form bean
    if (context.getRequestObject() != null) {
      context.getRequest().setAttribute("mailingList", context.getRequestObject());
    } else {
      long mailingListId = context.getParameterAsLong("mailingListId");
      MailingList mailingList = MailingListRepository.findById(mailingListId);
      context.getRequest().setAttribute("mailingList", mailingList);
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    MailingList mailingListBean = new MailingList();
    BeanUtils.populate(mailingListBean, context.getParameterMap());
    mailingListBean.setCreatedBy(context.getUserId());

    // Save the record
    MailingList mailingList = null;
    try {
      mailingList = SaveMailingListCommand.saveMailingList(mailingListBean);
      if (mailingList == null) {
        throw new AppException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | AppException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(mailingListBean);
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Mailing list was saved");
    context.setRedirect("/admin/mailing-lists");
    return context;
  }
}
