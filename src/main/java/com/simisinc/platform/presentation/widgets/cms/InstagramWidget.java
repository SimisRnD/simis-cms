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

import com.simisinc.platform.application.cms.NumberCommand;
import com.simisinc.platform.application.cms.UrlCommand;

import com.simisinc.platform.domain.model.socialmedia.InstagramMedia;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.socialmedia.InstagramMediaRepository;
import com.simisinc.platform.infrastructure.persistence.socialmedia.InstagramMediaSpecification;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

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
    // These are rendered into the slider's javascript config, so require plain integers
    String smallCardCount = NumberCommand.filterPositiveInteger(context.getPreferences().getOrDefault("smallCardCount", "6"), "6");
    context.getRequest().setAttribute("smallCardCount", smallCardCount);
    String mediumCardCount = NumberCommand.filterPositiveInteger(context.getPreferences().getOrDefault("mediumCardCount", smallCardCount), smallCardCount);
    context.getRequest().setAttribute("mediumCardCount", mediumCardCount);
    context.getRequest().setAttribute("largeCardCount", NumberCommand.filterPositiveInteger(context.getPreferences().getOrDefault("largeCardCount", mediumCardCount), mediumCardCount));

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
        // Sanitize the API-supplied urls at the sink; drop the card if either is unsafe
        String card = buildCardHtml(media);
        if (card == null) {
          continue;
        }
        ++count;
        addCard(context, cardList, card);
      }
    }
    context.getRequest().setAttribute("cardList", cardList);

    // Determine the view
    context.setJsp(CARD_JSP);
    return context;
  }

  /**
   * Builds the card markup for an Instagram image. The permalink and media url are supplied by the
   * Instagram Graph API (persisted by a background job) and are rendered straight into the href and
   * src attributes, so both are run through {@link UrlCommand#sanitizeUrl(String)} at this sink: a
   * value carrying a quote or an active scheme (javascript:/data:) would otherwise break out of the
   * attribute and inject script. Returns null when either url is unsafe so the caller drops the card
   * instead of emitting raw markup.
   */
  static String buildCardHtml(InstagramMedia media) {
    String permalink = UrlCommand.sanitizeUrl(media.getPermalink());
    String mediaUrl = UrlCommand.sanitizeUrl(media.getMediaUrl());
    if (permalink == null || mediaUrl == null) {
      return null;
    }
    return "<p><a target=\"_blank\" href=\"" + permalink + "\"><img src=\"" + mediaUrl + "\" /></a></p>";
  }

  private void addCard(WidgetContext context, List<String> cardList, String html) {
    cardList.add(html);
  }

}
