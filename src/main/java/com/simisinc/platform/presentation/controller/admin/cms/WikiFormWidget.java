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

package com.simisinc.platform.presentation.controller.admin.cms;

import java.lang.reflect.InvocationTargetException;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.LoadWikiCommand;
import com.simisinc.platform.application.cms.SaveWikiCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 2/10/19 11:35 AM
 */
public class WikiFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/wiki-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // This page can return to different places
    String returnPage = context.getSharedRequestValue("returnPage");
    if (returnPage == null) {
      returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    }
    context.getRequest().setAttribute("returnPage", returnPage);

    // Form bean
    Wiki wiki = null;
    if (context.getRequestObject() != null) {
      wiki = (Wiki) context.getRequestObject();
      context.getRequest().setAttribute("wiki", wiki);
    } else {
      long wikiId = context.getParameterAsLong("wikiId");
      if (wikiId > -1) {
        wiki = LoadWikiCommand.loadWikiById(wikiId);
        context.getRequest().setAttribute("wiki", wiki);
      }
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    Wiki wikiBean = new Wiki();
    BeanUtils.populate(wikiBean, context.getParameterMap());
    wikiBean.setCreatedBy(context.getUserId());
    wikiBean.setModifiedBy(context.getUserId());

    // Determine additional settings
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));

    // Save the wiki
    Wiki wiki = null;
    try {
      wiki = SaveWikiCommand.saveWiki(wikiBean);
      if (wiki == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(wikiBean);
      context.addSharedRequestValue("returnPage", returnPage);
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Wiki was saved");
    if (StringUtils.isNotBlank(returnPage)) {
      context.setRedirect(returnPage);
    } else {
      context.setRedirect("/admin/wikis");
    }
    return context;
  }
}
