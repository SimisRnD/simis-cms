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

package com.simisinc.platform.presentation.controller.ecommerce;

import com.simisinc.platform.application.ecommerce.LoadProductListCommand;
import com.simisinc.platform.domain.model.ecommerce.Product;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.PreferenceEntriesList;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * Provides product information
 *
 * @author matt rajkowski
 * @created 2/4/2021 9:34 PM
 */
public class ProductBrowserWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/ecommerce/product-browser.jsp";
  static String CARD_SLIDER_JSP = "/ecommerce/product-card-slider.jsp";
  static String UNAVAILABLE_JSP = "/ecommerce/product-browser-unavailable.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine the number of cards to use across
    String smallCardCount = context.getPreferences().get("smallCardCount");
    String mediumCardCount = context.getPreferences().get("mediumCardCount");
    String largeCardCount = context.getPreferences().get("largeCardCount");
    if (StringUtils.isBlank(smallCardCount)) {
      smallCardCount = "3";
    }
    if (StringUtils.isBlank(mediumCardCount)) {
      mediumCardCount = smallCardCount;
    }
    if (StringUtils.isBlank(largeCardCount)) {
      largeCardCount = mediumCardCount;
    }
    context.getRequest().setAttribute("smallCardCount", smallCardCount);
    context.getRequest().setAttribute("mediumCardCount", mediumCardCount);
    context.getRequest().setAttribute("largeCardCount", largeCardCount);
    context.getRequest().setAttribute("cardClass", context.getPreferences().get("cardClass"));
    context.getRequest().setAttribute("buttonLabel", context.getPreferences().getOrDefault("button", "Shop"));
    context.getRequest().setAttribute("buttonClass", context.getPreferences().getOrDefault("buttonClass", "product-button button expanded"));

    // Check for <products> preference
    // <product uniqueId="the-unique-id" />
    ArrayList<String> productUniqueIdList = new ArrayList<>();
    Map<String, String> productImageMap = new HashMap<>();
    PreferenceEntriesList productEntriesList = context.getPreferenceAsDataList("products");
    for (Map<String, String> productMap : productEntriesList) {
      // Check the preferences
      String productUniqueId = productMap.get("uniqueId");
      productUniqueIdList.add(productUniqueId);
      if (productMap.containsKey("image")) {
        String image = productMap.get("image");
        if (StringUtils.isNotBlank(image)) {
          productImageMap.put(productUniqueId, image);
        }
      }
    }

    // Load the products
    List<Product> productList = LoadProductListCommand.loadProductsForSale(productUniqueIdList);
    if (productList == null || productList.isEmpty()) {
      context.setJsp(UNAVAILABLE_JSP);
      return context;
    }

    // Sort by the preferences
    if (!productUniqueIdList.isEmpty()) {
      productList.sort(Comparator.comparing(o -> (productUniqueIdList.indexOf(o.getUniqueId()))));
    }

    context.getRequest().setAttribute("productList", productList);
    if (!productImageMap.isEmpty()) {
      context.getRequest().setAttribute("productImageMap", productImageMap);
    }

    // Show the JSP
    String view = context.getPreferences().get("view");
    if ("cardSlider".equals(view)) {

      // Check the preferences
      context.getRequest().setAttribute("showControls", context.getPreferences().getOrDefault("showControls", "true"));
      context.getRequest().setAttribute("showLeftControl", context.getPreferences().getOrDefault("showLeftControl", "true"));
      context.getRequest().setAttribute("showRightControl", context.getPreferences().getOrDefault("showRightControl", "true"));
//      context.getRequest().setAttribute("showBullets", context.getPreferences().getOrDefault("showBullets", "true"));

      context.setJsp(CARD_SLIDER_JSP);
    } else {
      context.setJsp(JSP);
    }
    return context;
  }
}
