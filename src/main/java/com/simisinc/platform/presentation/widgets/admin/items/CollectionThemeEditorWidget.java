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

package com.simisinc.platform.presentation.widgets.admin.items;

import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.items.CssColorValidationCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 6/29/21 8:00 PM
 */
public class CollectionThemeEditorWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/collection-theme-editor.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the parent collection
    long collectionId = context.getParameterAsLong("collectionId");
    Collection collection = CollectionRepository.findById(collectionId);
    if (collection == null) {
      context.setErrorMessage("Error. Collection was not found.");
      return context;
    }

    // Show the current style
    context.getRequest().setAttribute("collection", collection);

    // Determine the return page
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    if (StringUtils.isBlank(returnPage)) {
      returnPage = "/admin/collection-details?collectionId=" + collection.getId();
    }
    context.getRequest().setAttribute("returnPage", returnPage);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Determine the parent collection
    long collectionId = context.getParameterAsLong("collectionId");
    Collection collection = CollectionRepository.findById(collectionId);
    if (collection == null) {
      context.setErrorMessage("Collection id must be specified.");
      return context;
    }

    // Allow setting the color properties only
    Collection collectionBean = new Collection();
    BeanUtils.populate(collectionBean, context.getParameterMap());

    // Reject the whole save if any color value isn't a narrow, valid CSS color. These values are
    // rendered unescaped into an inline <style> block on public pages, so a value like
    // "red;}body{display:none}/*" would let a data-manager inject arbitrary CSS/selectors into
    // every visitor's view of this Collection.
    String headerTextColor = StringUtils.trimToNull(collectionBean.getHeaderTextColor());
    String headerBgColor = StringUtils.trimToNull(collectionBean.getHeaderBgColor());
    String menuTextColor = StringUtils.trimToNull(collectionBean.getMenuTextColor());
    String menuBgColor = StringUtils.trimToNull(collectionBean.getMenuBgColor());
    String menuBorderColor = StringUtils.trimToNull(collectionBean.getMenuBorderColor());
    String menuActiveTextColor = StringUtils.trimToNull(collectionBean.getMenuActiveTextColor());
    String menuActiveBgColor = StringUtils.trimToNull(collectionBean.getMenuActiveBgColor());
    String menuActiveBorderColor = StringUtils.trimToNull(collectionBean.getMenuActiveBorderColor());
    String menuHoverTextColor = StringUtils.trimToNull(collectionBean.getMenuHoverTextColor());
    String menuHoverBgColor = StringUtils.trimToNull(collectionBean.getMenuHoverBgColor());
    String menuHoverBorderColor = StringUtils.trimToNull(collectionBean.getMenuHoverBorderColor());
    if (!CssColorValidationCommand.isValid(headerTextColor)
        || !CssColorValidationCommand.isValid(headerBgColor)
        || !CssColorValidationCommand.isValid(menuTextColor)
        || !CssColorValidationCommand.isValid(menuBgColor)
        || !CssColorValidationCommand.isValid(menuBorderColor)
        || !CssColorValidationCommand.isValid(menuActiveTextColor)
        || !CssColorValidationCommand.isValid(menuActiveBgColor)
        || !CssColorValidationCommand.isValid(menuActiveBorderColor)
        || !CssColorValidationCommand.isValid(menuHoverTextColor)
        || !CssColorValidationCommand.isValid(menuHoverBgColor)
        || !CssColorValidationCommand.isValid(menuHoverBorderColor)) {
      context.setErrorMessage(
          "Invalid color value. Colors must be a hex value (#fff, #ffffff, #ffffffff) or an rgb()/rgba() value.");
      return context;
    }

    // Store the same (trimmed) values that were validated above -- not the original getter
    // values -- so what's validated and what's persisted can never diverge.
    collection.setHeaderTextColor(headerTextColor);
    collection.setHeaderBgColor(headerBgColor);
    collection.setMenuTextColor(menuTextColor);
    collection.setMenuBgColor(menuBgColor);
    collection.setMenuBorderColor(menuBorderColor);
    collection.setMenuActiveTextColor(menuActiveTextColor);
    collection.setMenuActiveBgColor(menuActiveBgColor);
    collection.setMenuActiveBorderColor(menuActiveBorderColor);
    collection.setMenuHoverTextColor(menuHoverTextColor);
    collection.setMenuHoverBgColor(menuHoverBgColor);
    collection.setMenuHoverBorderColor(menuHoverBorderColor);

    // Save the style
    if (CollectionRepository.updateTheme(collection) == null) {
      LOG.warn("Colors were not saved!");
      context.setErrorMessage("An error occurred");
      return context;
    }

    // Determine the page to return to
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    if (StringUtils.isEmpty(returnPage)) {
      returnPage = "/";
    }
    context.setRedirect(returnPage);
    return context;
  }
}
