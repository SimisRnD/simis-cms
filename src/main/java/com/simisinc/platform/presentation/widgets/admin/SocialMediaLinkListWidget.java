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

import java.util.List;

import com.simisinc.platform.domain.model.SocialMediaLink;
import com.simisinc.platform.infrastructure.persistence.SocialMediaLinkRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Lists and removes social media links (issue #516)
 *
 * @author SimIS Inc.
 */
public class SocialMediaLinkListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/social-media-link-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    List<SocialMediaLink> socialMediaLinkList = SocialMediaLinkRepository.findAll();
    context.getRequest().setAttribute("socialMediaLinkList", socialMediaLinkList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {
    long recordId = context.getParameterAsLong("socialMediaLinkId");
    if (recordId > -1) {
      SocialMediaLink socialMediaLink = SocialMediaLinkRepository.findById(recordId);
      String targetLabel = socialMediaLink != null ? socialMediaLink.getPlatformName() : null;
      boolean removed = socialMediaLink != null && SocialMediaLinkRepository.remove(socialMediaLink);
      AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "social_media_link.remove",
          removed ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
          "social_media_link", String.valueOf(recordId), targetLabel, null);
      if (removed) {
        context.setSuccessMessage("Record deleted");
      } else {
        context.setErrorMessage("Error. Record could not be deleted.");
      }
    }
    return context;
  }
}
