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

import com.simisinc.platform.application.cms.HtmlCommand;

import static java.util.stream.Collectors.toList;

import java.lang.reflect.InvocationTargetException;
import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sanctionco.jmail.JMail;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.application.cms.CaptchaCommand;
import com.simisinc.platform.application.cms.LoadBlogCommand;
import com.simisinc.platform.application.mailinglists.SaveEmailCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
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

    // Issue #601: a blogUniqueId preference scopes this signup to that blog's associated
    // mailing list (a contextual CTA, e.g. placed on the blog's own page) instead of the
    // mailingList (name) preference. Don't render a form that can only ever fail if the blog
    // has no list configured yet -- that's a site configuration gap for an admin to fix, not
    // something a public visitor should hit.
    String blogUniqueId = context.getPreferences().get("blogUniqueId");
    if (StringUtils.isNotBlank(blogUniqueId) && resolveBlogMailingList(blogUniqueId) == null) {
      LOG.warn("emailSubscribe widget's blogUniqueId '" + blogUniqueId + "' has no associated mailing list to subscribe to");
      return null;
    }

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
      // Issue #598: let a visitor choose which public list(s) to join. Omitted entirely (no
      // checkboxes rendered) when nothing is marked show_online, so a default/fresh install's
      // single-list signup behaves exactly as it did before this feature existed.
      context.getRequest().setAttribute("onlineMailingLists", MailingListRepository.findOnlineLists());
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
    // Both render unescaped in email-subscribe-*.jsp. They are widget preferences, which come
    // from page-layout XML that content-managers can author -- the same source that made the
    // section/widget class attributes injectable. Sanitized rather than escaped because these
    // are meant to contain html.
    context.getRequest().setAttribute("introHtml",
        HtmlCommand.cleanContent(context.getPreferences().get("introHtml")));
    context.getRequest().setAttribute("footerHtml",
        HtmlCommand.cleanContent(context.getPreferences().get("footerHtml")));

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine the captcha service (enabled by default for spam prevention)
    boolean useCaptcha = "true".equals(context.getPreferences().getOrDefault("useCaptcha", "true"));
    if (useCaptcha) {
      CaptchaCommand.populateWidgetAttributes(context);
    }

    // Previous post had error
    //    Email email = (Email) context.getRequestObject();

    // Show the JSP
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Determine preferences
    String blogUniqueId = context.getPreferences().get("blogUniqueId");
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

    // Validate the captcha (enabled by default for spam prevention)
    boolean useCaptcha = "true".equals(context.getPreferences().getOrDefault("useCaptcha", "true"));
    if (useCaptcha) {
      boolean captchaSuccess = CaptchaCommand.validateRequest(context);
      if (!captchaSuccess) {
        isValid = false;
        context.setWarningMessage("Please verify you're human before subscribing");
      }
    }

    if (!isValid) {
      context.setRequestObject(emailBean);
      return context;
    }

    // Check rate limiting to prevent spam bot signup attempts
    if (!RateLimitCommand.isIpAllowedRightNow(context.getRequest().getRemoteAddr(), true)) {
      context.setErrorMessage(RateLimitCommand.INVALID_ATTEMPTS);
      return context;
    }

    // Save the record
    try {
      if (StringUtils.isNotBlank(blogUniqueId)) {
        MailingList mailingList = resolveBlogMailingList(blogUniqueId);
        if (mailingList == null) {
          // The blog's association was removed between this form rendering and being submitted
          throw new DataException("Sorry, this signup isn't available right now.");
        }
        SaveEmailCommand.saveEmail(emailBean, mailingList);
      } else {
        SaveEmailCommand.saveEmail(emailBean, mailingListName);
      }
    } catch (DataException e) {
      context.setWarningMessage(e.getMessage());
      context.setRequestObject(emailBean);
      return context;
    }

    // Redirect back so the message can be displayed
    context.addSharedRequestValue(context.getUniqueId() + "emailSubscribeWidgetSuccess", "true");
    return context;
  }

  /** The blog's associated mailing list (issue #601), or null if the blog or association doesn't exist. */
  private MailingList resolveBlogMailingList(String blogUniqueId) {
    Blog blog = LoadBlogCommand.loadBlogByUniqueId(blogUniqueId);
    if (blog == null || blog.getMailingListId() == -1) {
      return null;
    }
    return MailingListRepository.findById(blog.getMailingListId());
  }
}
