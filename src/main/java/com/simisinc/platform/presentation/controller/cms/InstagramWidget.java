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

package com.simisinc.platform.presentation.controller.cms;

import com.simisinc.platform.domain.model.socialmedia.InstagramMedia;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.socialmedia.InstagramMediaRepository;
import com.simisinc.platform.infrastructure.persistence.socialmedia.InstagramMediaSpecification;
import com.simisinc.platform.presentation.controller.RequestConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 9/9/19 4:36 PM
 */
public class InstagramWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String CARD_JSP = "/cms/instagram-cards.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Common attributes
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Preferences
    context.getRequest().setAttribute("cardClass", context.getPreferences().get("cardClass"));

    // Card size preferences
    String smallCardCount = context.getPreferences().getOrDefault("smallCardCount", "6");
    context.getRequest().setAttribute("smallCardCount", smallCardCount);
    String mediumCardCount = context.getPreferences().getOrDefault("mediumCardCount", smallCardCount);
    context.getRequest().setAttribute("mediumCardCount", mediumCardCount);
    context.getRequest().setAttribute("largeCardCount", context.getPreferences().getOrDefault("largeCardCount", mediumCardCount));

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "8"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    String columnToSortBy = "id";
    String columnSortOrder = "desc";
    DataConstraints constraints = new DataConstraints(page, itemsPerPage, columnToSortBy, columnSortOrder);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Show the available media, media is retrieved by a background process
    InstagramMediaSpecification specification = new InstagramMediaSpecification();
    specification.setIsImage();
    List<InstagramMedia> instagramMediaList = InstagramMediaRepository.findAll(specification, constraints);
    if (instagramMediaList == null || instagramMediaList.isEmpty()) {
      return context;
    }

    List<String> cardList = new ArrayList<>();
    int count = 0;
    for (InstagramMedia media : instagramMediaList) {
      if (count >= limit) {
        break;
      }
      if ("IMAGE".equals(media.getMediaType())) {
        ++count;
        addCard(context, cardList, "<p><a target=\"_blank\" href=\"" + media.getPermalink() + "\"><img src=\"" + media.getMediaUrl() + "\" /></a></p>");
      }
    }
    context.getRequest().setAttribute("cardList", cardList);

    // Determine the view
    context.setJsp(CARD_JSP);
    return context;
  }

  private void addCard(WidgetContext context, List<String> cardList, String html) {
    cardList.add(html);
  }

}
