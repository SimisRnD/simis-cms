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

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.FontCommand;
import com.simisinc.platform.application.cms.LoadTableOfContentsCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.cms.TableOfContents;
import com.simisinc.platform.domain.model.cms.TableOfContentsLink;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.presentation.controller.RequestConstants;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Displays a standalone link with a drop-down menu
 *
 * @author matt rajkowski
 * @created 1/29/2021 12:00 PM
 */
public class MenuWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/menu.jsp";

  public WidgetContext execute(WidgetContext context) {

    context.getRequest().setAttribute("menuId", context.getPreferences().get("id"));
    context.getRequest().setAttribute("menuClass", context.getPreferences().get("class"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("useHighlight", context.getPreferences().getOrDefault("useHighlight", "false"));
    boolean showWhenEmpty = "true".equals(context.getPreferences().getOrDefault("showWhenEmpty", "false"));

    List<Map<String, String>> linkList = new ArrayList<>();
    List<String> allowedGroups = new ArrayList<>();
    List<String> failingGroups = new ArrayList<>();

    String pagePath = (String) context.getRequest().getAttribute(RequestConstants.WEB_PAGE_PATH);

    // Add links from the tocUniqueId preference
    String tableOfContentsUniqueId = context.getPreferences().get("tocUniqueId");
    if (StringUtils.isNotBlank(tableOfContentsUniqueId)) {
      LOG.debug("Checking tocUniqueId: " + tableOfContentsUniqueId);
      TableOfContents tableOfContents = LoadTableOfContentsCommand.loadByUniqueId(tableOfContentsUniqueId, false);
      if (tableOfContents != null && tableOfContents.hasEntries()) {
        for (TableOfContentsLink link : tableOfContents.getEntries()) {
          addLink(context, linkList, link.getName(), link.getLink(), null, null, null);
        }
      }
      // Configure the content editor
      if (context.hasRole("admin") || context.hasRole("content-manager")) {
        LOG.debug("Has content manager access!");
        context.getRequest().setAttribute("uniqueId", tableOfContentsUniqueId);
        context.getRequest().setAttribute("showEditor", "true");
        context.getRequest().setAttribute("returnPage", context.getRequest().getRequestURI());
        showWhenEmpty = true;
      }
    }

    // The TOC must have entries, or the links preference
    PreferenceEntriesList entriesList = context.getPreferenceAsDataList("links");
    if (linkList.isEmpty() && entriesList.isEmpty() && !showWhenEmpty) {
      LOG.debug("Links preference is empty");
      return null;
    }

    // @todo Add cart to menu
    // Track if the cart items are going to be shown
//    boolean showCartItems = false;

    // Add links from the specified entriesList
    for (Map<String, String> valueMap : entriesList) {
      try {
        // Check the link preferences, and any special settings
        String link = valueMap.get("link");
        String menuItemClass = valueMap.get("class");
        String type = valueMap.get("type");
        String group = valueMap.get("group");
        String roleValue = valueMap.get("role");
        String ruleValue = valueMap.get("rule");
        if ("admin".equals(type)) {
          link = "/admin";
          if (StringUtils.isBlank(group)) {
            group = "admin";
          }
          if (StringUtils.isBlank(roleValue)) {
            roleValue = "admin,content-manager,community-manager,data-manager,ecommerce-manager";
          }
        } else if ("cart".equals(type)) {
          // Check that the cart is enabled
          if (!"true".equals(LoadSitePropertyCommand.loadByName("site.cart"))) {
            continue;
          }
          link = "/cart";
//          showCartItems = true;
        }

        // Determine access requirements
        boolean hasAccess = true;
        if (StringUtils.isNotBlank(roleValue)) {
          hasAccess = checkRoleAccess(context, roleValue);
        }

        // Determine if this group can be shown
        if (StringUtils.isNotBlank(group)) {
          if (!allowedGroups.contains(group) && !failingGroups.contains(group)) {
            // First time this group has been seen, that determines the menu displaying
            if (hasAccess) {
              allowedGroups.add(group);
            } else {
              failingGroups.add(group);
            }
          } else if (failingGroups.contains(group)) {
            continue;
          }
        }

        // Determine if this menu item can be shown
        if (!hasAccess) {
          continue;
        }

        // See if the link has a rule
        if (LOG.isDebugEnabled() && ruleValue != null) {
          LOG.debug("Checking link rules: " + link + ": [" + ruleValue + "]");
        }
        if (!checkRules(ruleValue)) {
          continue;
        }

        // Adjust for being flexible with icons
        String icon = valueMap.get("icon");
        if (StringUtils.isNotBlank(icon) && !icon.contains(" ")) {
          icon = "fa " + icon;
        }

        // Prepare the link
        Map<String, String> properties = new HashMap();
        addProperty(context, properties, "name", valueMap.get("name"));
        addProperty(context, properties, "link", link);
        addProperty(context, properties, "class", menuItemClass);
        addProperty(context, properties, "group", group);
        addProperty(context, properties, "icon", icon);
        addProperty(context, properties, "type", type);
        addProperty(context, properties, "icon-only", valueMap.get("icon-only"));
        linkList.add(properties);

        // Determine if this is an Admin menu, and the links which are allowed
        if ("admin".equals(type)) {

          // Determine collection options
          Collection collection = null;
          Item item = null;
          boolean isCollectionPage = pagePath.startsWith("/show/");
          if (isCollectionPage) {
            String itemUniqueId = pagePath.substring("/show/".length());
            if (itemUniqueId.contains("/")) {
              itemUniqueId = itemUniqueId.substring(0, itemUniqueId.indexOf("/"));
            }
            item = LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(itemUniqueId, context.getUserId());
            if (item == null) {
              Item unallowedItem = LoadItemCommand.loadItemByUniqueId(itemUniqueId);
              if (unallowedItem != null) {
                collection = LoadCollectionCommand.loadCollectionById(unallowedItem.getCollectionId());
              }
            } else {
              collection = LoadCollectionCommand.loadCollectionById(item.getCollectionId());
            }
          }

          // Skip options that do not apply to the current page
          boolean thisPageIsSkipped = (pagePath.startsWith("/admin") || pagePath.startsWith("/content-editor") || isCollectionPage);

          // Add the additional admin items
          addLink(context, linkList, "Admin", "/admin", "fa fa-cogs", group, "admin,content-manager,community-manager,data-manager,ecommerce-manager");

          // Add Page Editing Links
          if (!thisPageIsSkipped) {
            addDivider(context, linkList, group, "admin,content-manager");
            addLink(context, linkList, "Page Info", "/admin/web-page?webPage=" + pagePath, "fa fa-info", group, "admin,content-manager");
            addLink(context, linkList, "Page Layout", "/admin/web-page-designer?webPage=" + pagePath, "fa fa-code", group, "admin,content-manager");
            addLink(context, linkList, "Page CSS", "/admin/css-editor?webPage=" + pagePath + "&returnPage=" + pagePath, "fa fa-css3", group, "admin");
          }

          // Add Collection and Item Editing Links
          if (isCollectionPage && collection != null) {
            if (item != null) {
              addDivider(context, linkList, group, "admin");
              addLink(context, linkList, "Edit Item Details", "/edit/" + item.getUniqueId() + "?returnPage=/show/" + item.getUniqueId(), "fa fa-edit", group, "admin");
              addLink(context, linkList, "Edit Item Settings", "/show/" + item.getUniqueId() + "/settings", "fa fa-edit", group, "admin");
            }
            addDivider(context, linkList, group, "admin");
            addLink(context, linkList, "Collection Setup", "/admin/collection-details?collectionId=" + collection.getId(), "fa fa-info", group, "admin");
            addLink(context, linkList, "Collection Theme", "/admin/collection-theme?collectionId=" + collection.getId(), "fa fa-swatchbook", group, "admin");
//            addLink(context, linkList, "Collection CSS", "/admin/css-editor?collection=" + collection.getId() + "&returnPage=" + pagePath, "fa fa-css3", group, "admin");
//            addLink(context, linkList, "Collection Layout", "/admin/web-page-designer?collection=" + collection.getId() + "&returnPage=" + pagePath, "fa fa-th-large", group, "admin");
          }

          addDivider(context, linkList, group, "admin");
          addLink(context, linkList, "Site Theme", "/admin/theme-properties", "fa fa-swatchbook", group, "admin");
          addLink(context, linkList, "Site CSS", "/admin/css-editor?returnPage=" + pagePath, "fa fa-css3", group, "admin");
          addLink(context, linkList, "Site Header Layout", "/admin/web-container-designer?name=header.default&returnPage=" + pagePath, FontCommand.fal() + " fa-code", group, "admin");
          addLink(context, linkList, "Site Footer Layout", "/admin/web-container-designer?name=footer.default&returnPage=" + pagePath, FontCommand.fal() + " fa-code", group, "admin");

          // Add a Logout for users
          if (linkList.size() > 1) {
            addDivider(context, linkList, group, "users");
          }
          addLink(context, linkList, "Logout", "/logout", "fa fa-sign-out-alt", group, "users");
        }
      } catch (Exception e) {
        LOG.error("Could not get property: " + e.getMessage());
      }
    }
    if (linkList.isEmpty() && !showWhenEmpty) {
      return context;
    }
    context.getRequest().setAttribute("linkList", linkList);

    /*
    // Determine if the cart items are going to be available in this menu
    if (showCartItems) {
      Cart cart = context.getUserSession().getCart();
      if (cart != null) {
        // Load the complete cart items
        List<CartItem> cartItemList = CartItemRepository.findValidItemsByCartId(cart.getId());
        context.getRequest().setAttribute("cartItemList", cartItemList);
        // Load the related products
        List<CartEntry> cartEntryList = new ArrayList<>();
        BigDecimal runningTotal = new BigDecimal(0);
        for (CartItem cartItem : cartItemList) {
          ProductSku productSku = ProductSkuRepository.findById(cartItem.getSkuId());

          // Prepare the entry for display
          CartEntry cartEntry = new CartEntry();
          cartEntry.setCartItem(cartItem);
          cartEntry.setProduct(LoadProductCommand.loadProductMetaDataById(productSku.getProductId()));
          cartEntry.setProductSku(productSku);
          cartEntryList.add(cartEntry);

          // Track the running total
          runningTotal = runningTotal.add(productSku.getPrice().multiply(cartItem.getQuantity()));
        }
        context.getRequest().setAttribute("cartEntryList", cartEntryList);
        context.getRequest().setAttribute("runningTotal", runningTotal.doubleValue());
      }
    }
    */

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  private static boolean checkRoleAccess(WidgetContext context, String roleValue) {
    List<String> roles = Stream.of(roleValue.split(","))
        .map(String::trim)
        .collect(toList());
    if (roles.isEmpty()) {
      return true;
    }
    for (String role : roles) {
      if ("guest".equals(role) && !context.getUserSession().isLoggedIn()) {
        return true;
      }
      if ("users".equals(role) && context.getUserSession().isLoggedIn()) {
        return true;
      }
      if (context.getUserSession().hasRole(role)) {
        return true;
      }
    }
    return false;
  }

  private static boolean checkRules(String ruleValue) {
    if (StringUtils.isBlank(ruleValue)) {
      return true;
    }
    List<String> rules = Stream.of(ruleValue.split(","))
        .map(String::trim)
        .collect(toList());
    for (String rule : rules) {
      // Assumes rule is just the site property that must be true
      if (!"true".equals(LoadSitePropertyCommand.loadByName(rule))) {
        return false;
      }
    }
    return true;
  }

  private static void addProperty(WidgetContext context, Map<String, String> map, String name, String value) {
    if (StringUtils.isBlank(value)) {
      return;
    }
    String pagePath = (String) context.getRequest().getAttribute("pagePath");
    value = StringUtils.replace(value, "${pagePath}", (pagePath));
    map.put(name, value);
  }

  private static void addLink(WidgetContext context, List<Map<String, String>> linkList, String name, String link, String icon, String group, String roleValue) {
    // Check for access to this menu item
    if (StringUtils.isNotBlank(roleValue) && !checkRoleAccess(context, roleValue)) {
      return;
    }

    // Prepare the link
    Map<String, String> properties = new HashMap<>();
    addProperty(context, properties, "name", name);
    addProperty(context, properties, "link", link);
    addProperty(context, properties, "group", group);
    addProperty(context, properties, "icon", icon);
    //addProperty(context, properties, "icon-only", valueMap.get("icon-only"));
    linkList.add(properties);
  }

  private static void addDivider(WidgetContext context, List<Map<String, String>> linkList, String group, String roleValue) {
    // Check for access to this menu item
    if (StringUtils.isNotBlank(roleValue) && !checkRoleAccess(context, roleValue)) {
      return;
    }

    // Prepare the divider
    Map<String, String> properties = new HashMap<>();
    properties.put("divider", "true");
    if (StringUtils.isNotBlank(group)) {
      properties.put("group", group);
    }
    linkList.add(properties);
  }
}
