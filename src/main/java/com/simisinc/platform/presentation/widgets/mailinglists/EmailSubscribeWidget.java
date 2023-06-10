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

import static java.util.stream.Collectors.toList;

import java.lang.reflect.InvocationTargetException;
import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sanctionco.jmail.JMail;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.CaptchaCommand;
import com.simisinc.platform.application.mailinglists.SaveEmailCommand;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/24/19 9:55 PM
 */
public class EmailSubscribeWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  protected static Log LOG = LogFactory.getLog(EmailSubscribeWidget.class);

  static String JSP = "/mailinglists/email-subscribe-simple-form.jsp";
  static String INLINE_FORM_JSP = "/mailinglists/email-subscribe-inline-form.jsp";
  static String VERTICAL_FORM_JSP = "/mailinglists/email-subscribe-vertical-form.jsp";
  static String WITH_NAME_JSP = "/mailinglists/email-subscribe-with-name-form.jsp";
  static String SUCCESS_JSP = "/mailinglists/email-subscribe-success.jsp";

  public WidgetContext execute(WidgetContext context) {

    String isSuccess = context.getSharedRequestValue(context.getUniqueId() + "emailSubscribeWidgetSuccess");
    if ("true".equals(isSuccess)) {
      context.getRequest().setAttribute("successTitle", context.getPreferences().get("successTitle"));
      context.getRequest().setAttribute("successMessage",
          context.getPreferences().getOrDefault("successMessage", "You are now subscribed"));
      context.setJsp(SUCCESS_JSP);
      return context;
    }

    if ("inline".equals(context.getPreferences().get("view"))) {
      context.setJsp(INLINE_FORM_JSP);
    } else if ("vertical".equals(context.getPreferences().get("view"))) {
      context.setJsp(VERTICAL_FORM_JSP);
    } else {
      if ("true".equals(context.getPreferences().get("showName"))) {
        context.setJsp(WITH_NAME_JSP);
      } else {
        context.setJsp(JSP);
      }
    }

    // Preferences
    context.getRequest().setAttribute("buttonName", context.getPreferences().getOrDefault("buttonName", "Subscribe"));
    context.getRequest().setAttribute("showName", context.getPreferences().getOrDefault("showName", "false"));
    context.getRequest().setAttribute("introHtml", context.getPreferences().get("introHtml"));
    context.getRequest().setAttribute("footerHtml", context.getPreferences().get("footerHtml"));

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine the captcha service
    boolean useCaptcha = "true".equals(context.getPreferences().getOrDefault("useCaptcha", "false"));
    if (useCaptcha) {
      context.getRequest().setAttribute("useCaptcha", "true");
      context.getRequest().setAttribute("googleSiteKey", LoadSitePropertyCommand.loadByName("captcha.google.sitekey"));
    }

    // Previous post had error
    //    Email email = (Email) context.getRequestObject();

    // Show the JSP
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Determine preferences
    String mailingListName = context.getPreferences().get("mailingList");
    String tags = context.getPreferences().get("tags");

    // Populate the fields
    Email emailBean = new Email();
    BeanUtils.populate(emailBean, context.getParameterMap());
    emailBean.setSource("Website form");
    emailBean.setSubscribed(new Timestamp(System.currentTimeMillis()));

    // Populate the tag(s)
    if (tags != null) {
      List<String> tagList = Stream.of(tags.split(",")).map(String::trim).collect(toList());
      if (!tagList.isEmpty()) {
        emailBean.setTagList(tagList);
      }
    }

    // Populate all the http and session info
    emailBean.setIpAddress(context.getUserSession().getIpAddress());
    emailBean.setSessionId(context.getUserSession().getSessionId());
    emailBean.setReferer(context.getUserSession().getReferer());
    emailBean.setUserAgent(context.getUserSession().getUserAgent());
    if (context.getUserSession().isLoggedIn()) {
      emailBean.setCreatedBy(context.getUserId());
      emailBean.setModifiedBy(context.getUserId());
    }

    // Validate the parameters
    boolean isValid = true;
    if (!JMail.isValid(emailBean.getEmail())) {
      isValid = false;
      context.setWarningMessage("Please check the email address and try again");
    }

    // Validate the captcha
    boolean useCaptcha = "true".equals(context.getPreferences().getOrDefault("useCaptcha", "false"));
    if (useCaptcha) {
      boolean captchaSuccess = CaptchaCommand.validateRequest(context);
      if (!captchaSuccess) {
        isValid = false;
        context.setWarningMessage("The form could not be validated");
      }
    }

    if (!isValid) {
      context.setRequestObject(emailBean);
      return context;
    }

    // Save the record
    try {
      SaveEmailCommand.saveEmail(emailBean, mailingListName);
    } catch (DataException e) {
      context.setWarningMessage(e.getMessage());
      context.setRequestObject(emailBean);
      return context;
    }

    // Redirect back so the message can be displayed
    context.addSharedRequestValue(context.getUniqueId() + "emailSubscribeWidgetSuccess", "true");
    return context;
  }
}
