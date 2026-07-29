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

package com.simisinc.platform.presentation.widgets.cms;

import java.util.List;

import com.simisinc.platform.domain.model.SocialMediaLink;
import com.simisinc.platform.infrastructure.persistence.SocialMediaLinkRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/20/21 3:44 PM
 */
public class SocialMediaLinksWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/social-media-links.jsp";

  public WidgetContext execute(WidgetContext context) {
    // issue #516: an admin-editable list of (platform, url) pairs, not a fixed set of properties
    List<SocialMediaLink> socialMediaLinkList = SocialMediaLinkRepository.findAll();
    LOG.debug("socialMediaLinkList size: " + socialMediaLinkList.size());
    if (socialMediaLinkList.isEmpty()) {
      return context;
    }
    context.getRequest().setAttribute("socialMediaLinkList", socialMediaLinkList);

    // Preferences
    context.getRequest().setAttribute("iconClass", context.getPreferences().getOrDefault("iconClass", "margin-left-10"));

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }
}
