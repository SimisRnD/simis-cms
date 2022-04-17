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

package com.simisinc.platform.presentation.controller;

import com.simisinc.platform.ApplicationInfo;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.presentation.controller.cms.*;
import com.simisinc.platform.presentation.controller.login.ControllerSession;
import com.simisinc.platform.presentation.controller.login.UserSession;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.text.StringEscapeUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.simisinc.platform.presentation.controller.RequestConstants.*;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 2/7/2021 2:40 PM
 */
public class WebContainerCommands implements Serializable {

  static final long serialVersionUID = 536435325324169646L;
  private static Log LOG = LogFactory.getLog(WebContainerCommands.class);

  private static final String REQUEST_SHARED_VALUE_MAP = "REQUEST_SHARED_VALUE_MAP";
  private static final String MESSAGE = "MESSAGE";
  private static final String SUCCESS_MESSAGE = "SUCCESS_MESSAGE";
  private static final String WARNING_MESSAGE = "WARNING_MESSAGE";
  private static final String ERROR_MESSAGE = "ERROR_MESSAGE";
  private static final String REQUEST_OBJECT = "REQUEST_OBJECT";


  public static boolean processWidgets(WebContainerContext webContainerContext, List<Section> sections,
                                    ContainerRenderInfo containerRenderInfo, Map<String, String> coreData,
                                    String contextPath, String pagePath, UserSession userSession, Map<String, String> themePropertyMap) throws Exception {

    LOG.debug("Processing container... " + containerRenderInfo.getName() + ": " + sections.size());

    HttpServletRequest request = webContainerContext.getRequest();
    HttpServletResponse response = webContainerContext.getResponse();
    ControllerSession controllerSession = webContainerContext.getControllerSession();

    // Check the controller session for shared widget data
    Map<String, String> sharedWidgetValueMap = null;
    if (controllerSession.hasWidgetData(REQUEST_SHARED_VALUE_MAP)) {
      sharedWidgetValueMap = (HashMap) controllerSession.getWidgetData(REQUEST_SHARED_VALUE_MAP);
    }

    int widgetCount = 0;
    //if (!isPost && !isDelete && !isAction) {
    // Cycle the form token
    // @todo actually, keep a list and expire old ones eventually
    //userSession.renewFormToken();
    //}

    // Process the page (read-only)
    for (Section section : sections) {

      // Check the user's role
      if (!section.allowsUser(userSession)) {
        LOG.debug("SECTION NOT ALLOWED: roles=" + section.getRoles().toString());
        continue;
      }

      // Process the section
      boolean sectionAdded = false;
      SectionRenderInfo sectionRenderInfo = new SectionRenderInfo(section);
      LOG.debug("  Columns: " + section.getColumns().size());
      for (Column column : section.getColumns()) {

        // Check the user's role
        if (!column.allowsUser(userSession)) {
          LOG.debug("COLUMN NOT ALLOWED: roles=" + column.getRoles().toString());
          continue;
        }

        // Process the column
        boolean columnAdded = false;
        ColumnRenderInfo columnRenderInfo = new ColumnRenderInfo(column);
        LOG.debug("  Widgets: " + column.getWidgets().size());
        for (Widget widget : column.getWidgets()) {

          // Reset the request attributes for each widget
          Enumeration<?> attributeNames = request.getAttributeNames();
          while (attributeNames.hasMoreElements()) {
            String name = (String) attributeNames.nextElement();
//              LOG.debug("Found attribute: " + name);
            if (!name.startsWith("controller") && !name.startsWith("master") && !name.startsWith("request")) {
              request.removeAttribute(name);
            }
          }

          // Check the user's role
          if (!widget.allowsUser(userSession)) {
            LOG.debug("WIDGET NOT ALLOWED: " + widget.getWidgetName() + " roles=" + widget.getRoles().toString());
            continue;
          }

          // Each widget has a unique id on the page for forms, Javascript, etc.
          ++widgetCount;
          String thisWidgetUniqueId = widget.getWidgetName() + widgetCount;

          // On a POST/DELETE, only execute the action widget
          if (webContainerContext.isTargeted()) {
            if (!thisWidgetUniqueId.equals(containerRenderInfo.getTargetWidget())) {
              continue;
            }
            // Validate the token and fail immediately
            String formToken = request.getParameter("token");
            if (!userSession.getFormToken().equals(formToken)) {
              controllerSession.clearAllWidgetData();
              controllerSession.addWidgetData(thisWidgetUniqueId, MESSAGE, "Your session may have expired before submitting the form, please try again");
              response.sendRedirect(contextPath + containerRenderInfo.getName());
              return true;
            }
          }

          // Setup the context for this widget processor
          WidgetContext widgetContext = new WidgetContext(request, response, thisWidgetUniqueId);
          widgetContext.setParameterMap(request.getParameterMap());
          widgetContext.setCoreData(coreData);
          widgetContext.setUserSession(userSession);
          widgetContext.setSharedRequestValueMap(sharedWidgetValueMap);

          // Allow the widget to use the properties
          request.setAttribute("themePropertyMap", themePropertyMap);

          // Get a copy of the preferences and translate any variables
          Map<String, String> preferences = new HashMap<>();

          for (String preference : widget.getPreferences().keySet()) {
            String value = widget.getPreferences().get(preference);
            // check for dynamic preferences
            value = StringUtils.replace(value, "${ctx}", contextPath);
            if (value.contains("${platform.")) {
              value = StringUtils.replace(value, "${platform.name}", StringEscapeUtils.escapeXml11(ApplicationInfo.PRODUCT_NAME));
              value = StringUtils.replace(value, "${platform.url}", ApplicationInfo.PRODUCT_URL);
              value = StringUtils.replace(value, "${platform.version}", ApplicationInfo.VERSION);
            }
            if (value.contains("${webPage.")) {
              if (webContainerContext.getWebPage() != null) {
                value = StringUtils.replace(value, "${webPage.link}", webContainerContext.getWebPage().getLink());
              } else {
                value = StringUtils.replace(value, "${webPage.link}", pagePath);
              }
              if (widgetContext.getUri().contains("/")) {
                String webPageUniqueId = widgetContext.getUri().substring(widgetContext.getUri().lastIndexOf("/") + 1);
                value = StringUtils.replace(value, "${webPage.uniqueId}", webPageUniqueId);
              }
            }
            if (value.contains("${collection.") && coreData.containsKey("collectionUniqueId")) {
              Collection collection = LoadCollectionCommand.loadCollectionByUniqueId(coreData.get("collectionUniqueId"));
              value = StringUtils.replace(value, "${collection.uniqueId}", BeanUtils.getProperty(collection, "uniqueId"));
              value = StringUtils.replace(value, "${collection.name}", BeanUtils.getProperty(collection, "name"));
              value = StringUtils.replace(value, "${collection.link}", BeanUtils.getProperty(collection, "listingsLink"));
              value = StringUtils.replace(value, "${collection.listingsLink}", BeanUtils.getProperty(collection, "listingsLink"));
              value = StringUtils.replace(value, "${collection.name:html}", StringEscapeUtils.escapeXml11(BeanUtils.getProperty(collection, "name")));
            }
            if (value.contains("${item.") && coreData.containsKey("itemUniqueId")) {
              Item item = LoadItemCommand.loadItemByUniqueId(coreData.get("itemUniqueId"));
              Collection collection = LoadCollectionCommand.loadCollectionById(item.getCollectionId());
              value = StringUtils.replace(value, "${item.uniqueId}", BeanUtils.getProperty(item, "uniqueId"));
              value = StringUtils.replace(value, "${item.name}", BeanUtils.getProperty(item, "name"));
              value = StringUtils.replace(value, "${item.name:html}", StringEscapeUtils.escapeXml11(BeanUtils.getProperty(item, "name")));
              value = StringUtils.replace(value, "${item.summary:toHtml}", HtmlCommand.textToHtml(BeanUtils.getProperty(item, "summary")));
              value = StringUtils.replace(value, "${item.summary:html}", StringEscapeUtils.escapeXml11(BeanUtils.getProperty(item, "summary")));
              value = StringUtils.replace(value, "${item.summary:html}", "");
              value = StringUtils.replace(value, "${item.collectionUniqueId}", BeanUtils.getProperty(collection, "uniqueId"));
              value = StringUtils.replace(value, "${item.collection.name}", BeanUtils.getProperty(collection, "name"));
              value = StringUtils.replace(value, "${item.collection.link}", BeanUtils.getProperty(collection, "listingsLink"));
              value = StringUtils.replace(value, "${item.collection.listingsLink}", BeanUtils.getProperty(collection, "listingsLink"));
              value = StringUtils.replace(value, "${item.latitude}", BeanUtils.getProperty(item, "latitude"));
              value = StringUtils.replace(value, "${item.longitude}", BeanUtils.getProperty(item, "longitude"));
              value = StringUtils.replace(value, "${item.city}", BeanUtils.getProperty(item, "city"));
              value = StringUtils.replace(value, "${item.state}", BeanUtils.getProperty(item, "state"));
              value = StringUtils.replace(value, "${item.postalCode}", BeanUtils.getProperty(item, "postalCode"));
            }
            if (value.contains("${user.") && userSession.isLoggedIn()) {
              // @todo make this dynamic and speedup
              User thisUser = LoadUserCommand.loadUser(userSession.getUserId());
              value = StringUtils.replace(value, "${user.id}", BeanUtils.getProperty(thisUser, "id"));
              value = StringUtils.replace(value, "${user.email}", BeanUtils.getProperty(thisUser, "email"));
              value = StringUtils.replace(value, "${user.email:html}", StringEscapeUtils.escapeXml11(BeanUtils.getProperty(thisUser, "email")));
              value = StringUtils.replace(value, "${user.firstName}", BeanUtils.getProperty(thisUser, "firstName"));
              value = StringUtils.replace(value, "${user.firstName:html}", StringEscapeUtils.escapeXml11(BeanUtils.getProperty(thisUser, "firstName")));
              value = StringUtils.replace(value, "${user.lastName}", BeanUtils.getProperty(thisUser, "lastName"));
              value = StringUtils.replace(value, "${user.lastName:html}", StringEscapeUtils.escapeXml11(BeanUtils.getProperty(thisUser, "lastName")));
              value = StringUtils.replace(value, "${user.fullName}", BeanUtils.getProperty(thisUser, "fullName"));
              value = StringUtils.replace(value, "${user.fullName:html}", StringEscapeUtils.escapeXml11(BeanUtils.getProperty(thisUser, "fullName")));
            }
            if (value.contains("${request.")) {
              // @todo add while loop
              int idxStart = value.indexOf("${request.") + 10;
              int idxEnd = value.indexOf("}", idxStart);
              String requestParam = value.substring(idxStart, idxEnd);
              String requestValue = widgetContext.getRequest().getParameter(requestParam);
              value = StringUtils.replace(value, "${request." + requestParam + "}", requestValue);
            }
            preferences.put(preference, value);
          }
          widgetContext.setPreferences(preferences);

          // Check the controller session for widget data
          Object requestObject = controllerSession.getWidgetData(thisWidgetUniqueId, REQUEST_OBJECT);
          if (requestObject != null) {
            LOG.debug("Found a request object: " + requestObject.getClass().getName());
            widgetContext.setRequestObject(requestObject);
            controllerSession.clearWidgetData(thisWidgetUniqueId, REQUEST_OBJECT);
          }
          // Check for display messages (from GET or POST)
          String widgetMessage = (String) controllerSession.getWidgetData(thisWidgetUniqueId, MESSAGE);
          if (widgetMessage != null) {
            widgetContext.setMessage(widgetMessage);
            request.setAttribute(MESSAGE_TEXT, widgetMessage);
            controllerSession.clearWidgetData(thisWidgetUniqueId, MESSAGE);
          }
          String widgetSuccessMessage = (String) controllerSession.getWidgetData(thisWidgetUniqueId, SUCCESS_MESSAGE);
          if (widgetSuccessMessage != null) {
            widgetContext.setSuccessMessage(widgetSuccessMessage);
            request.setAttribute(SUCCESS_MESSAGE_TEXT, widgetSuccessMessage);
            controllerSession.clearWidgetData(thisWidgetUniqueId, SUCCESS_MESSAGE);
          }
          String widgetWarningMessage = (String) controllerSession.getWidgetData(thisWidgetUniqueId, WARNING_MESSAGE);
          if (widgetWarningMessage != null) {
            widgetContext.setWarningMessage(widgetWarningMessage);
            request.setAttribute(WARNING_MESSAGE_TEXT, widgetWarningMessage);
            controllerSession.clearWidgetData(thisWidgetUniqueId, WARNING_MESSAGE);
          }
          String widgetErrorMessage = (String) controllerSession.getWidgetData(thisWidgetUniqueId, ERROR_MESSAGE);
          if (widgetErrorMessage != null) {
            widgetContext.setErrorMessage(widgetErrorMessage);
            request.setAttribute(ERROR_MESSAGE_TEXT, widgetErrorMessage);
            controllerSession.clearWidgetData(thisWidgetUniqueId, ERROR_MESSAGE);
          }

          // Make this widget context and resources available in the request during processing
          request.setAttribute(RequestConstants.CONTEXT_PATH, contextPath);
          request.setAttribute(RequestConstants.WIDGET_CONTEXT, widgetContext);
          request.setAttribute("webPage", webContainerContext.getWebPage());
          request.setAttribute(RequestConstants.WEB_PAGE_PATH, pagePath);

          // Get the cached class reference for processing
          Object classRef = webContainerContext.getWidgetInstances().get(widget.getWidgetName());
          if (classRef == null) {
            LOG.error("Class not found for widget: " + widget.getWidgetName());
            continue;
          }

          // Execute the widget
          WidgetContext result = null;
          try {
            LOG.debug("-----------------------------------------------------------------------");
            LOG.trace("Getting method...");
            String methodName = "execute";
            if (webContainerContext.isPost()) {
              methodName = "post";
            } else if (webContainerContext.isDelete()) {
              methodName = "delete";
            } else if (webContainerContext.isAction()) {
              methodName = "action";
            }
            LOG.debug("Executing widget: " + widget.getWidgetName() + "." + methodName + " [" + thisWidgetUniqueId + "]");
            Method method = classRef.getClass().getMethod(methodName, widgetContext.getClass());
            result = (WidgetContext) method.invoke(classRef, new Object[]{widgetContext});
          } catch (NoSuchMethodException nm) {
            LOG.error("No Such Method Exception for method execute. MESSAGE = " + nm.getMessage(), nm);
          } catch (IllegalAccessException ia) {
            LOG.error("Illegal Access Exception. MESSAGE = " + ia.getMessage(), ia);
          } catch (Exception e) {
            LOG.error("Exception. MESSAGE = " + e.getMessage(), e);
            if (webContainerContext.isPost()) {
              widgetContext.setErrorMessage("The form could not be validated, please try again");
            }
          }

          // The container may have updated the page's render info
          if (containerRenderInfo instanceof PageRenderInfo) {
            PageRenderInfo pageRenderInfo = (PageRenderInfo) containerRenderInfo;
            if (StringUtils.isNotBlank(widgetContext.getPageTitle())) {
              if (webContainerContext.getWebPage() != null) {
                pageRenderInfo.setTitle(
                    widgetContext.getPageTitle() +
                        (StringUtils.isNotBlank(webContainerContext.getWebPage().getTitle()) ? " - " + webContainerContext.getWebPage().getTitle() : ""));
              } else if (webContainerContext.getPage() != null) {
                pageRenderInfo.setTitle(
                    widgetContext.getPageTitle() +
                        (StringUtils.isNotBlank(webContainerContext.getPage().getTitle()) ? " - " + webContainerContext.getPage().getTitle() : ""));
              }
            }
            if (StringUtils.isNotBlank(widgetContext.getPageDescription())) {
              pageRenderInfo.setDescription(widgetContext.getPageDescription());
            }
            if (StringUtils.isNotBlank(widgetContext.getPageKeywords())) {
              pageRenderInfo.setKeywords(widgetContext.getPageKeywords());
            }
          }

          // Expect JSON first and return early
          if (widgetContext.hasJson()) {
            LOG.debug("Returning JSON...");
            controllerSession.clearAllWidgetData();
            response.setContentType("application/json");
            response.setContentLength(widgetContext.getJson().length());
            PrintWriter out = response.getWriter();
            out.print(widgetContext.getJson());
            out.flush();
            return true;
          }

          // A widget can handle the response, so exit
          if (widgetContext.handledResponse()) {
            LOG.debug("Widget handled response...");
            controllerSession.clearAllWidgetData();
            return true;
          }

          // See if the widget issued a redirect
          if (!webContainerContext.isTargeted()) {
            if (widgetContext.hasRedirect()) {
              controllerSession.clearAllWidgetData();
              response.sendRedirect(contextPath + widgetContext.getRedirect());
              return true;
            }
          }

          // Handle POST and DELETE response
          if (webContainerContext.isTargeted()) {
            // Determine the next page after the action
            String actionRedirect = widgetContext.getRedirect();
            if (actionRedirect != null && contextPath.length() > 0) {
              if (actionRedirect.startsWith(contextPath)) {
                actionRedirect = actionRedirect.substring(contextPath.length());
              }
            }
            if (actionRedirect == null) {
              LOG.debug("Action pagePath: " + pagePath);
              actionRedirect = containerRenderInfo.getName();
              if (webContainerContext.getPage().checkForItemUniqueId()) {
                // The XML indicates that there is an item
                if (webContainerContext.getPage().getItemUniqueId().contains("*")) {
                  // Substitute the item's unique id
                  actionRedirect = StringUtils.replace(webContainerContext.getPage().getItemUniqueId(), "*", widgetContext.getCoreData().get("itemUniqueId"));
                }
              } else if (webContainerContext.getPage().checkForCollectionUniqueId()) {
                // The XML indicates that there is a collection
                if (webContainerContext.getPage().getCollectionUniqueId().contains("*")) {
                  // Substitute the collection's uniqueId (/uri/*)
                  actionRedirect = StringUtils.replace(webContainerContext.getPage().getCollectionUniqueId(), "*", widgetContext.getCoreData().get("collectionUniqueId"));
                } else if ("?collectionId".equals(webContainerContext.getPage().getCollectionUniqueId())) {
                  // Use the collection's id (/admin/collection-form{?collectionId})
                  if (widgetContext.getCoreData().containsKey("collectionId")) {
                    actionRedirect += webContainerContext.getPage().getCollectionUniqueId() + "=" + widgetContext.getCoreData().get("collectionId");
                  }
                } else {
                  // Use the collection's uniqueId
                  if (widgetContext.getCoreData().containsKey("collectionUniqueId")) {
                    LOG.warn("This actionRedirect was called and is in use, so remove this comment");
                    actionRedirect += webContainerContext.getPage().getCollectionUniqueId() + "=" + widgetContext.getCoreData().get("collectionUniqueId");
                  }
                }
              }
            }
            LOG.debug("Sending an action redirect to: " + actionRedirect);
            if (widgetContext.getMessage() != null) {
              controllerSession.addWidgetData(widgetContext.getUniqueId(), MESSAGE, widgetContext.getMessage());
            }
            // Since this is an action, use the session to provide data for the next request
            if (widgetContext.getSuccessMessage() != null) {
              controllerSession.addWidgetData(widgetContext.getUniqueId(), SUCCESS_MESSAGE, widgetContext.getSuccessMessage());
            }
            if (widgetContext.getWarningMessage() != null) {
              controllerSession.addWidgetData(widgetContext.getUniqueId(), WARNING_MESSAGE, widgetContext.getWarningMessage());
            }
            if (widgetContext.getErrorMessage() != null) {
              controllerSession.addWidgetData(widgetContext.getUniqueId(), ERROR_MESSAGE, widgetContext.getErrorMessage());
            }
            if (widgetContext.getRequestObject() != null) {
              LOG.debug("Adding a request object: " + widgetContext.getRequestObject().getClass().getName());
              controllerSession.addWidgetData(widgetContext.getUniqueId(), REQUEST_OBJECT, widgetContext.getRequestObject());
            }
            if (widgetContext.getSharedRequestValueMap() != null) {
              sharedWidgetValueMap = widgetContext.getSharedRequestValueMap();
              controllerSession.addWidgetData(REQUEST_SHARED_VALUE_MAP, sharedWidgetValueMap);
            }
            response.sendRedirect(contextPath + actionRedirect);
            LOG.debug("-----------------------------------------------------------------------");
            return true;
          }

          // If the method was a success, check for content
          String widgetContent = null;
          if (result != null) {
            if (widgetContext.hasJsp()) {
              // @todo if the JSP does not exist, a recursive loop occurs
              LOG.debug("Including JSP: /WEB-INF/jsp" + widgetContext.getJsp());
              WidgetResponseWrapper responseWrapper = new WidgetResponseWrapper(response);
              request.getRequestDispatcher("/WEB-INF/jsp" + widgetContext.getJsp()).include(request, responseWrapper);
              widgetContent = responseWrapper.getOutputAndClose();
            } else if (widgetContext.hasHtml()) {
              widgetContent = widgetContext.getHtml();
            }
          }

          // If there's content, then turn on the output
          if (widgetContent != null && widgetContent.length() > 0) {
            // The widget asked to be included without the main css/scripts/footer
            if (widgetContext.isEmbedded()) {
              webContainerContext.setEmbedded(true);
            }
            WidgetRenderInfo widgetRenderInfo = new WidgetRenderInfo(widget, widgetContent);
            if (!sectionAdded) {
              sectionAdded = true;
              containerRenderInfo.setHasWidgets(true);
              sectionRenderInfo.setHasWidgets(true);
              containerRenderInfo.addSection(sectionRenderInfo);
            }
            if (!columnAdded) {
              columnAdded = true;
              sectionRenderInfo.addColumn(columnRenderInfo);
            }
            columnRenderInfo.addWidget(widgetRenderInfo);
          }
        }
      }
    }
    return false;
  }
}