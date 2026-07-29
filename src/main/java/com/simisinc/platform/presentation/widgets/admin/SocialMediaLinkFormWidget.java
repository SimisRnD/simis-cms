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

import java.lang.reflect.InvocationTargetException;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.domain.model.SocialMediaLink;
import com.simisinc.platform.infrastructure.persistence.SocialMediaLinkRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Adds a social media link (issue #516)
 *
 * @author SimIS Inc.
 */
public class SocialMediaLinkFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/social-media-link-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    if (context.getRequestObject() != null) {
      context.getRequest().setAttribute("socialMediaLink", context.getRequestObject());
    } else {
      context.getRequest().setAttribute("socialMediaLink", new SocialMediaLink());
    }

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    SocialMediaLink socialMediaLinkBean = new SocialMediaLink();
    BeanUtils.populate(socialMediaLinkBean, context.getParameterMap());

    if (StringUtils.isBlank(socialMediaLinkBean.getPlatformName())) {
      context.setErrorMessage("Platform name is required");
      context.setRequestObject(socialMediaLinkBean);
      return context;
    }
    // A simple, lightweight sanity check -- this is an admin-only form, not public input, so a full
    // URL parser/validator would be more than this needs.
    String url = StringUtils.trimToEmpty(socialMediaLinkBean.getUrl());
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      context.setErrorMessage("A valid URL starting with http:// or https:// is required");
      context.setRequestObject(socialMediaLinkBean);
      return context;
    }

    SocialMediaLink socialMediaLink = SocialMediaLinkRepository.save(socialMediaLinkBean);
    if (socialMediaLink == null) {
      context.setErrorMessage("Your information could not be saved due to a system error. Please try again.");
      context.setRequestObject(socialMediaLinkBean);
      return context;
    }

    AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "social_media_link.save",
        AuditEventCommand.SUCCESS, "social_media_link", String.valueOf(socialMediaLink.getId()),
        socialMediaLink.getPlatformName(), null);
    context.setSuccessMessage("Record was saved");
    return context;
  }
}
