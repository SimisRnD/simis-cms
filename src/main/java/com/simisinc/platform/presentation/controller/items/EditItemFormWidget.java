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

package com.simisinc.platform.presentation.controller.items;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.items.*;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemCustomField;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/15/18 8:53 AM
 */
public class EditItemFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  protected static Log LOG = LogFactory.getLog(EditItemFormWidget.class);

  static String FULL_FORM_JSP = "/items/item-full-form.jsp";
  static String BUSINESS_FORM_JSP = "/items/item-business-form.jsp";
  static String NEED_PERMISSION_JSP = "/items/item-need-edit-permission.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the item and verify access
    long userId = context.getUserId();
    String itemUniqueId = context.getPreferences().get("uniqueId");
    if (itemUniqueId == null) {
      return null;
    }
    Item item = LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(itemUniqueId, userId);
    if (item == null) {
      return null;
    }
    Collection collection = LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(item.getCollectionId(), userId);
    if (collection == null) {
      return null;
    }

    // See if the user group can edit any item in this collection
    boolean canEditItem = CheckCollectionPermissionCommand.userHasEditPermission(collection.getId(), userId);
    if (!canEditItem) {
      context.setJsp(NEED_PERMISSION_JSP);
      return context;
    }

    // Provide a category drop-down
    List<Category> categoryList = CategoryRepository.findAllByCollectionId(collection.getId());
    context.getRequest().setAttribute("categoryList", categoryList);

    // Split the list into multiple lists for the UI
    int columnSize = (int) Math.ceil((double) categoryList.size() / 2);
    if (columnSize > 0) {
      List<List<Category>> columnList = ListUtils.partition(categoryList, columnSize);
      context.getRequest().setAttribute("categoryList1", columnList.get(0));
      context.getRequest().setAttribute("categoryList2", columnList.get(1));
    }

    // Form bean
    if (context.getRequestObject() != null) {
      context.getRequest().setAttribute("item", context.getRequestObject());
    } else {
      context.getRequest().setAttribute("item", item);
    }

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("returnPage", context.getPreferences().getOrDefault("returnPage", UrlCommand.getValidReturnPage(context.getParameter("returnPage"))));

    // Show the JSP
    context.setJsp(FULL_FORM_JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Determine the item and verify access
    long userId = context.getUserId();
    String itemUniqueId = context.getPreferences().get("uniqueId");
    if (itemUniqueId == null) {
      return null;
    }
    Item itemBean = LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(itemUniqueId, userId);
    if (itemBean == null) {
      return null;
    }

    // See if the user group can edit any item in this collection
    boolean canEditItem = CheckCollectionPermissionCommand.userHasEditPermission(itemBean.getCollectionId(), userId);
    if (!canEditItem) {
      context.setJsp(NEED_PERMISSION_JSP);
      return context;
    }

    // Populate the fields
    BeanUtils.populate(itemBean, context.getParameterMap());
    itemBean.setModifiedBy(context.getUserId());
    itemBean.setIpAddress(context.getRequest().getRemoteAddr());

    // Handle the categories
    long mainCategoryId = itemBean.getCategoryId();
    if (mainCategoryId == 0) {
      mainCategoryId = -1;
    }
    List<Category> categoryList = CategoryRepository.findAllByCollectionId(itemBean.getCollectionId());
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
    itemBean.setCategoryId(mainCategoryId);
    itemBean.setCategoryIdList(categoryIdList.toArray(new Long[0]));

    // Check for custom fields (@todo load from the collection database)


    itemBean.addCustomField(new ItemCustomField("contactName", "Contact Name", context.getParameter(context.getUniqueId() + "contactName")));
    itemBean.addCustomField(new ItemCustomField("contactPhoneNumber", "Phone", context.getParameter(context.getUniqueId() + "contactPhoneNumber")));
    itemBean.addCustomField(new ItemCustomField("contactEmail", "Email", context.getParameter(context.getUniqueId() + "contactEmail")));
    itemBean.addCustomField(new ItemCustomField("numberOfEmployees", "# of employees", context.getParameter(context.getUniqueId() + "numberOfEmployees")));
    itemBean.addCustomField(new ItemCustomField("numberOfYearsInBusiness", "# years in business", context.getParameter(context.getUniqueId() + "numberOfYearsInBusiness")));

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
      return context;
    }

    // Determine the page to return to
    String returnPage = context.getPreferences().getOrDefault("returnPage", UrlCommand.getValidReturnPage(context.getParameter("returnPage")));
    if (StringUtils.isNotBlank(returnPage)) {
      // Go to the item (could be renamed)
      if (returnPage.startsWith("/show/")) {
        returnPage = "/show/" + item.getUniqueId();
      }
    } else {
      // Go to the overview page
      Collection collection = LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(item.getCollectionId(), userId);
      if (StringUtils.isNotBlank(collection.getListingsLink())) {
        returnPage = collection.getListingsLink();
      } else {
        returnPage = "/directory/" + collection.getUniqueId();
      }
    }
    context.setSuccessMessage("Thanks, the record was saved!");
    context.setRedirect(returnPage);
    return context;
  }
}
