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

import com.simisinc.platform.application.IpAddressCommand;
import com.simisinc.platform.application.cms.HtmlCommand;

import static java.util.stream.Collectors.toList;

import java.lang.reflect.InvocationTargetException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
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
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingCountryRepository;
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
          context.getPreferences().getOrDefault("successMessage",
              "Almost done! Check your email to confirm your subscription."));
      context.setJsp(SUCCESS_JSP);
      return context;
    }

    List<MailingList> onlineMailingLists = null;
    if ("inline".equals(context.getPreferences().get("view"))) {
      context.setJsp(INLINE_FORM_JSP);
      // Issue #598: let a visitor choose which public list(s) to join. Omitted entirely (no
      // checkboxes rendered) when nothing is marked show_online, so a default/fresh install's
      // single-list signup behaves exactly as it did before this feature existed.
      onlineMailingLists = MailingListRepository.findOnlineLists();
    } else if ("vertical".equals(context.getPreferences().get("view"))) {
      context.setJsp(VERTICAL_FORM_JSP);
    } else if ("true".equals(context.getPreferences().get("showName"))) {
      context.setJsp(WITH_NAME_JSP);
      // Same multi-list opt-in support as the inline form (issue #598), and a Country dropdown
      // reusing the same list ShippingAddressFormWidget already offers at checkout instead of
      // maintaining a second copy of every country name.
      onlineMailingLists = MailingListRepository.findOnlineLists();
      context.getRequest().setAttribute("countryList", ShippingCountryRepository.findAll());
    } else {
      context.setJsp(JSP);
    }
    if (onlineMailingLists != null) {
      context.getRequest().setAttribute("onlineMailingLists", onlineMailingLists);
    }

    // Issue #1724: the counterpart of the blogUniqueId check above, for the list this widget names
    // itself. A submit no longer conjures a list into existence when the preference doesn't
    // resolve, so once an admin deletes that list -- or renames one a mailingList *name*
    // preference still points at -- this form can only ever fail. Don't render it, and say so in
    // the log for whoever has to fix the preference. Only when the widget's own preference is the
    // form's sole path: a blog-scoped widget subscribes by the blog's list id and never reads
    // either preference, and the per-list checkboxes (issue #598) are a working path in their own
    // right, so neither is misconfigured just because the fallback name has drifted.
    if (StringUtils.isBlank(blogUniqueId) && (onlineMailingLists == null || onlineMailingLists.isEmpty())
        && configuredMailingList(context) == null) {
      return null;
    }

    // Preferences
    context.getRequest().setAttribute("buttonName", context.getPreferences().getOrDefault("buttonName", "Subscribe"));
    context.getRequest().setAttribute("showName", context.getPreferences().getOrDefault("showName", "false"));
    // Both render unescaped in email-subscribe-*.jsp. They are widget preferences, which come
    // from page-layout XML that content-managers can author -- the same source that made the
    // section/widget class attributes injectable. Sanitized rather than escaped because these
    // are meant to contain html.
    context.getRequest().setAttribute("introHtml",
        HtmlCommand.cleanStoredContent(context.getPreferences().get("introHtml")));
    context.getRequest().setAttribute("footerHtml",
        HtmlCommand.cleanStoredContent(context.getPreferences().get("footerHtml")));

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine the captcha service (enabled by default for spam prevention)
    boolean useCaptcha = "true".equals(context.getPreferences().getOrDefault("useCaptcha", "true"));
    if (useCaptcha) {
      CaptchaCommand.populateWidgetAttributes(context);
    }

    // Previous post had error -- redisplay what the visitor already typed (e.g. after a failed
    // captcha attempt) instead of making them retype every field, matching the pattern
    // ShippingAddressFormWidget uses for the same reason.
    Email email = context.getRequestObject() != null ? (Email) context.getRequestObject() : new Email();
    context.getRequest().setAttribute("email", email);

    // Show the JSP
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Determine preferences
    String blogUniqueId = context.getPreferences().get("blogUniqueId");
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
    // The address of the request that subscribed, not the one the session was created at
    // (issue #1782)
    emailBean.setIpAddress(IpAddressCommand.forAction(context.getRequest(),
        context.getUserSession().getIpAddress()));
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
          throw new DataException(SaveEmailCommand.LIST_UNAVAILABLE_MESSAGE);
        }
        SaveEmailCommand.saveEmailRequiringConfirmation(emailBean, mailingList);
      } else {
        // Issue #598's multi-list opt-in (the with-name form's own checkboxes, not just the
        // inline form's AJAX path): one or more mailingListId values means the visitor chose
        // from several public lists rather than this widget's single mailingList preference.
        String[] selectedListIds = context.getParameterMap().get("mailingListId");
        if (selectedListIds != null && selectedListIds.length > 0) {
          List<MailingList> selectedLists = new ArrayList<>();
          for (String listId : selectedListIds) {
            MailingList mailingList = MailingListRepository.findById(NumberUtils.toLong(listId, -1));
            if (mailingList != null) {
              selectedLists.add(mailingList);
            }
          }
          if (selectedLists.isEmpty()) {
            throw new DataException("Please choose at least one list to subscribe to");
          }
          SaveEmailCommand.saveEmailRequiringConfirmation(emailBean, selectedLists);
        } else {
          // Issue #1724: resolve this widget's own configured list -- mailingListUniqueId first,
          // then the mailingList name preference. Nothing here creates a list that doesn't exist;
          // a form whose configuration stopped resolving between rendering and submitting fails
          // the same way the blog-scoped path above does.
          MailingList mailingList = configuredMailingList(context);
          if (mailingList == null) {
            throw new DataException(SaveEmailCommand.LIST_UNAVAILABLE_MESSAGE);
          }
          SaveEmailCommand.saveEmailRequiringConfirmation(emailBean, mailingList);
        }
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

  /**
   * The list this widget's own preferences name, or null when they resolve to nothing.
   * <p>
   * mailingListUniqueId (issue #1724) points at mailing_lists.unique_id, which is assigned when a
   * list is created and never rewritten, so it survives an admin renaming the list -- the same
   * guarantee blogUniqueId already gave the blog-scoped path. mailingList is the original
   * preference and stays supported: it is what every page already published in the wild carries,
   * and it is a list *name*, which is exactly why it can drift.
   */
  private MailingList configuredMailingList(WidgetContext context) {
    return SaveEmailCommand.findMailingList(context.getPreferences().get("mailingListUniqueId"),
        context.getPreferences().get("mailingList"));
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
