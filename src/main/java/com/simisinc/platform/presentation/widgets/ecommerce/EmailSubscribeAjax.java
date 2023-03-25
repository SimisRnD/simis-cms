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

package com.simisinc.platform.presentation.widgets.ecommerce;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.mailinglists.SaveEmailCommand;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;

import java.sql.Timestamp;

/**
 * Subscribes an email address
 *
 * @author matt rajkowski
 * @created 5/29/19 7:55 AM
 */
public class EmailSubscribeAjax extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public WidgetContext execute(WidgetContext context) {

    // Check the parameters
    String token = context.getParameter("token");
    String emailValue = context.getParameter("email");
    String nameValue = context.getParameter("name");

    // Validate the token
    if (!token.equals(context.getUserSession().getFormToken())) {
      context.setJson("[]");
      return context;
    }

    // Validate the email address
    if (StringUtils.isBlank(emailValue) || "null".equalsIgnoreCase(emailValue)) {
      context.setJson("[]");
      return context;
    }
    EmailValidator emailValidator = EmailValidator.getInstance(false);
    if (!emailValidator.isValid(emailValue)) {
      context.setJson("[]");
      return context;
    }

    // Populate the fields
    Email emailBean = new Email();
    emailBean.setEmail(emailValue);
    if (StringUtils.isNotBlank(nameValue)) {
      emailBean.setFirstName(nameValue);
    }
    emailBean.setSource("Website form");
    emailBean.setSubscribed(new Timestamp(System.currentTimeMillis()));

    // Populate all the http and session info
    emailBean.setIpAddress(context.getUserSession().getIpAddress());
    emailBean.setSessionId(context.getUserSession().getSessionId());
    emailBean.setReferer(context.getUserSession().getReferer());
    emailBean.setUserAgent(context.getUserSession().getUserAgent());
    if (context.getUserSession().isLoggedIn()) {
      emailBean.setCreatedBy(context.getUserId());
      emailBean.setModifiedBy(context.getUserId());
    }

    // Save the record
    try {
      SaveEmailCommand.saveEmail(emailBean);
      // Manage the related cookie
      context.getUserSession().setShowSiteNewsletterSignup(false);
    } catch (DataException e) {
      context.setJson("[]");
      return context;
    }

    // Return status ok
    context.setJson("{\"status\":\"0\"}");
    return context;
  }
}
