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

package com.simisinc.platform.presentation.widgets.items;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.CaptchaCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.items.*;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemCustomField;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.InvocationTargetException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/20/18 2:49 PM
 */
public class CreateAnItemWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  protected static Log LOG = LogFactory.getLog(CreateAnItemWidget.class);

  static String JSP = "/items/item-form.jsp";
  static String FULL_FORM_JSP = "/items/item-full-form.jsp";
  static String BUSINESS_FORM_JSP = "/items/item-business-form.jsp";
  static String JOB_FORM_JSP = "/items/item-job-form.jsp";
  static String NEED_PERMISSION_JSP = "/items/item-need-add-permission.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the collection
    String collectionUniqueId = context.getPreferences().get("collectionUniqueId");
    Collection collection = LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(collectionUniqueId, context.getUserId());
    if (collection == null) {
      LOG.warn("Set a collection or collectionUniqueId preference");
      return null;
    }
    context.getRequest().setAttribute("collection", collection);

    // Form Permission
    String requiresPermissionValue = context.getPreferences().getOrDefault("requiresPermission", "true");
    boolean requiresPermission = "true".equals(requiresPermissionValue);

    // Check user group permissions
    if (requiresPermission) {
      boolean canAddItem = CheckCollectionPermissionCommand.userHasAddPermission(collection.getId(), context.getUserId());
      if (!canAddItem) {
        context.setJsp(NEED_PERMISSION_JSP);
        return context;
      }
    }

    // Use a captcha when not logged in
    if (!requiresPermission && !context.getUserSession().isLoggedIn()) {
      context.getRequest().setAttribute("useCaptcha", "true");
      context.getRequest().setAttribute("googleSiteKey", LoadSitePropertyCommand.loadByName("captcha.google.sitekey"));
    }

    // Provide a category drop-down
    List<Category> categoryList = CategoryRepository.findAllByCollectionId(collection.getId());
    context.getRequest().setAttribute("categoryList", categoryList);

    // Split the list into multiple lists for the UI
    int columnSize = (int) Math.ceil((double) categoryList.size() / 2);
    if (columnSize > 0) {
      List<List<Category>> columnList = ListUtils.partition(categoryList, columnSize);
      if (columnList.size() > 0) {
        context.getRequest().setAttribute("categoryList1", columnList.get(0));
        if (columnList.size() > 1) {
          context.getRequest().setAttribute("categoryList2", columnList.get(1));
        }
      }
    }

    // Form bean
    if (context.getRequestObject() != null) {
      context.getRequest().setAttribute("item", context.getRequestObject());
    } else {
      long itemId = context.getParameterAsLong("itemId");
      Item item = ItemRepository.findById(itemId);
      context.getRequest().setAttribute("item", item);
    }

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Preferences
    context.getRequest().setAttribute("returnPage", UrlCommand.getValidReturnPage(context.getParameter("returnPage")));

    // Determine the cancel page
    String cancelUrl = context.getPreferences().get("cancelUrl");
    if (StringUtils.isBlank(cancelUrl)) {
      cancelUrl = collection.createListingsLink();
    }
    context.getRequest().setAttribute("cancelUrl", cancelUrl);

    // Show the JSP
    String form = context.getPreferences().getOrDefault("form", "default");
    if ("full".equals(form)) {
      context.setJsp(FULL_FORM_JSP);
    } else if ("business".equals(form)) {
      context.setJsp(BUSINESS_FORM_JSP);
    } else if ("job".equals(form)) {
      context.setJsp(JOB_FORM_JSP);
    } else {
      context.setJsp(JSP);
    }
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Determine the collection
    String collectionUniqueId = context.getPreferences().get("collectionUniqueId");
    Collection collection = LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(collectionUniqueId, context.getUserId());
    if (collection == null) {
      LOG.warn("Set a collection or collectionUniqueId preference");
      return null;
    }
    context.getRequest().setAttribute("collection", collection);

    // Form Permission
    String requiresPermissionValue = context.getPreferences().getOrDefault("requiresPermission", "true");
    String requiresApprovalValue = context.getPreferences().getOrDefault("requiresApproval", "false");
    boolean requiresPermission = "true".equals(requiresPermissionValue);
    boolean requiresApproval = "true".equals(requiresApprovalValue);
    if (context.hasRole("admin") || context.hasRole("data-manager")) {
      requiresApproval = false;
    }

    // Check user group permissions
    if (requiresPermission) {
      boolean canAddItem = CheckCollectionPermissionCommand.userHasAddPermission(collection.getId(), context.getUserId());
      if (!canAddItem) {
        LOG.warn("User does not have permission");
        return null;
      }
    }

    // Validate the captcha
    if (!requiresPermission && !context.getUserSession().isLoggedIn()) {
      boolean captchaSuccess = CaptchaCommand.validateRequest(context);
      if (!captchaSuccess) {
        context.setWarningMessage("The form could not be validated");
        return context;
      }
    }

    // Determine the item's categories (main must also be listed in the full set)
    long mainCategoryId = context.getParameterAsLong("categoryId", -1);
    List<Category> categoryList = CategoryRepository.findAllByCollectionId(collection.getId());
    List<Long> categoryIdList = new ArrayList<>();
    for (Category category : categoryList) {
      long categoryId = context.getParameterAsLong("categoryId" + category.getId());
      if (categoryId != -1) {
        categoryIdList.add(categoryId);
        if (mainCategoryId == -1) {
          mainCategoryId = categoryId;
        }
      }
    }
    if (mainCategoryId != -1 && !categoryIdList.contains(mainCategoryId)) {
      categoryIdList.add(mainCategoryId);
    }

    // Populate the fields
    Item itemBean = new Item();
    BeanUtils.populate(itemBean, context.getParameterMap());
    itemBean.setCreatedBy(context.getUserId());
    itemBean.setModifiedBy(context.getUserId());
    if (!context.getUserSession().isLoggedIn()) {
      itemBean.setCreatedBy(1);
      itemBean.setModifiedBy(1);
    }
    itemBean.setCollectionId(collection.getId());
    itemBean.setCategoryId(mainCategoryId);
    itemBean.setCategoryIdList(categoryIdList.toArray(new Long[0]));
    itemBean.setSource(context.getUri());
    itemBean.setIpAddress(context.getRequest().getRemoteAddr());

    // Check if the item requires permission
    if (requiresApproval) {
      itemBean.setApprovedBy(-1);
      itemBean.setApproved(null);
    } else {
      if (context.getUserSession().isLoggedIn()) {
        itemBean.setApprovedBy(context.getUserId());
      }
      itemBean.setApproved(new Timestamp(System.currentTimeMillis()));
    }

    // Check for custom fields (@todo load from the collection database)
    itemBean.addCustomField(new ItemCustomField("contactName", "Contact Name", context.getParameter("contactName")));
    itemBean.addCustomField(new ItemCustomField("contactPhoneNumber", "Phone", context.getParameter("contactPhoneNumber")));
    itemBean.addCustomField(new ItemCustomField("contactEmail", "Email", context.getParameter("contactEmail")));
    itemBean.addCustomField(new ItemCustomField("numberOfEmployees", "# of employees", context.getParameter("numberOfEmployees")));
    itemBean.addCustomField(new ItemCustomField("numberOfYearsInBusiness", "# years in business", context.getParameter("numberOfYearsInBusiness")));

    // Save the item
    Item item = null;
    try {
      item = SaveItemCommand.saveItem(itemBean);
      if (item == null) {
        throw new CategoryException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | CategoryException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(itemBean);
      if (itemBean.getId() > -1) {
        context.setWarningMessage("This name appears to be a duplicate. Please try again.");
      }
      return context;
    }

    // Send an alert based on the preferences (or transform for another system)
    // ItemCreatedEvent (use rules engine)
//    if (requiresApproval) {
    String emailAddresses = context.getPreferences().get("emailTo");
    ItemCommand.sendEmail(item, emailAddresses);
//    }

    // Determine the page to return to
    String returnPage = context.getPreferences().getOrDefault("returnPage", UrlCommand.getValidReturnPage(context.getParameter("returnPage")));
    if (StringUtils.isBlank(returnPage)) {
      returnPage = collection.createListingsLink();
    }
    if (requiresApproval) {
      context.setSuccessMessage("Thanks, the record was saved! We've notified an administrator to review your listing for approval.");
    } else {
      context.setSuccessMessage("Thanks, the record was saved!");
    }
    context.setRedirect(returnPage);
    return context;
  }
}
