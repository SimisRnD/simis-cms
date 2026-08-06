/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

import com.simisinc.platform.application.AppException;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.SaveBotUserAgentCommand;
import com.simisinc.platform.domain.model.BotUserAgent;
import com.simisinc.platform.infrastructure.persistence.BotUserAgentRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;

import java.lang.reflect.InvocationTargetException;

/**
 * The "Add to List" sidebar form for the bot user-agent list
 *
 * @author elizabeth houser
 */
public class BotUserAgentFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908895L;

  static String JSP = "/admin/bot-list-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Form bean
    if (context.getRequestObject() != null) {
      context.getRequest().setAttribute("botList", context.getRequestObject());
    } else {
      int botListId = context.getParameterAsInt("botListId");
      BotUserAgent botUserAgent = BotUserAgentRepository.findById(botListId);
      context.getRequest().setAttribute("botList", botUserAgent);
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    BotUserAgent botUserAgentBean = new BotUserAgent();
    BeanUtils.populate(botUserAgentBean, context.getParameterMap());

    // Skip duplicates
    if (BotUserAgentRepository.findByUserAgent(botUserAgentBean.getUserAgent()) != null) {
      context.setWarningMessage("This user agent value already exists");
      return context;
    }

    // Save the record
    BotUserAgent botUserAgent = null;
    try {
      botUserAgent = SaveBotUserAgentCommand.save(botUserAgentBean);
      if (botUserAgent == null) {
        throw new AppException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | AppException e) {
      AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "bot_user_agent.add", AuditEventCommand.FAILURE,
          "bot_user_agent", null, botUserAgentBean.getUserAgent(), e.getMessage());
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(botUserAgentBean);
      return context;
    }
    AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "bot_user_agent.add", AuditEventCommand.SUCCESS,
        "bot_user_agent", String.valueOf(botUserAgent.getId()), botUserAgent.getUserAgent(), botUserAgent.getLabel());

    // Determine the page to return to
    context.setSuccessMessage("Record was saved");
    return context;
  }
}
