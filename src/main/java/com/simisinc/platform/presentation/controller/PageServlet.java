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

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.*;
import com.simisinc.platform.application.items.LoadCategoryCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.domain.model.cms.Stylesheet;
import com.simisinc.platform.domain.model.cms.TableOfContents;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.presentation.widgets.cms.WebContainerContext;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.converters.BigDecimalConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.*;

import static com.simisinc.platform.presentation.controller.RequestConstants.*;
import static javax.servlet.http.HttpServletResponse.SC_MOVED_PERMANENTLY;

/**
 * Handles all web browser page requests
 *
 * @author matt rajkowski
 * @created 4/6/18 9:22 AM
 */
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 2, // 2MB
    maxFileSize = 1024 * 1024 * 100,      // 100MB
    maxRequestSize = 1024 * 1024 * 100)   // 100MB
public class PageServlet extends HttpServlet {

  private static Log LOG = LogFactory.getLog(PageServlet.class);

  // Widget Cache
  private Map<String, Object> widgetInstances = new HashMap<>();

  // JSON Services
  private Map<String, Object> serviceInstances = new HashMap<>();

  public void init(ServletConfig config) throws ServletException {

    LOG.info("PageServlet starting up...");
    String startupSuccessful = (String) config.getServletContext().getAttribute("STARTUP_SUCCESSFUL");
    if (!"true".equals(startupSuccessful)) {
      throw new ServletException("Startup failed due to previous error");
    }

    // Load the web page designs
    LOG.info("Loading the web page designs...");
    Map<String, String> widgetLibrary = WebPageXmlLayoutCommand.init(config.getServletContext());

    // Load the web containers
    LOG.info("Populating the header and footer containers...");
    WebContainerLayoutCommand.populateCache(config.getServletContext(), widgetLibrary);

    // Instantiate the widgets
    LOG.info("Instantiating the widgets...");
    for (String widgetName : widgetLibrary.keySet()) {
      try {
        String widgetClass = widgetLibrary.get(widgetName);
        Object classRef = Class.forName(widgetClass).getDeclaredConstructor().newInstance();
        widgetInstances.put(widgetName, classRef);
        LOG.info("Added widget class: " + widgetName + " = " + widgetClass);
      } catch (Exception e) {
        LOG.error("Class not found for '" + widgetName + "': " + e.getMessage());
      }
    }
    LOG.info("Widgets loaded: " + widgetInstances.size());

    // Instantiate the services
    LOG.info("Instantiating the JSON services...");
    XMLJSONServiceLoader xmlJsonServiceLoader = new XMLJSONServiceLoader();
    xmlJsonServiceLoader.addDirectory(config.getServletContext(), "json-services");
    for (String endpoint : xmlJsonServiceLoader.getServiceLibrary().keySet()) {
      try {
        String serviceClass = xmlJsonServiceLoader.getServiceLibrary().get(endpoint);
        Object classRef = Class.forName(serviceClass).getDeclaredConstructor().newInstance();
        serviceInstances.put(endpoint, classRef);
        LOG.info("Added service class: " + endpoint + " = " + serviceClass);
      } catch (Exception e) {
        LOG.error("Class not found for '" + endpoint + "': " + e.getMessage());
      }
    }

    // Configure BeanUtils
    ConvertUtils.register(new BigDecimalConverter(null), BigDecimal.class);
    // Override the SQL Timestamp formatting
    String pattern = "MM-dd-yyyy HH:mm";
    Locale locale = Locale.getDefault();
    SqlTimestampConverter converter = new SqlTimestampConverter(null);
    converter.setLocale(locale);
    String timeZoneProperty = LoadSitePropertyCommand.loadByName("site.timezone");
    if (StringUtils.isNotBlank(timeZoneProperty)) {
      converter.setTimeZone(TimeZone.getTimeZone(ZoneId.of(timeZoneProperty)));
    }
    converter.setPattern(pattern);
    ConvertUtils.register(converter, Timestamp.class);
  }

  public void destroy() {

  }

  @Override
  public void service(HttpServletRequest request, HttpServletResponse response) {

    if (LOG.isDebugEnabled()) {
      // Reload the configuration for any developer changes
      WebPageXmlLayoutCommand.reloadPages(request.getServletContext());
    }

    long startRequestTime = System.currentTimeMillis();

    LOG.trace("Widget processor...");
    response.setContentType("text/html");
    try {
      response.setCharacterEncoding("UTF-8");
      request.setCharacterEncoding("UTF-8");
    } catch (Exception e) {
      LOG.warn("Unsupported encoding UTF-8: " + e.getMessage());
    }
    response.setHeader("X-Frame-Options", "SAMEORIGIN");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-XSS-Protection", "1; mode=block");

    try {
      // Determine the resource
      String scheme = request.getScheme();
      String serverName = request.getServerName();
      int port = request.getServerPort();
      String contextPath = request.getServletContext().getContextPath();
      String requestURI = request.getRequestURI();
      String pagePath = requestURI.substring(contextPath.length());
      LOG.debug("Using resource: " + pagePath);

      // Use the session data (created in WebRequestFilter)
      ControllerSession controllerSession = (ControllerSession) request.getSession().getAttribute(SessionConstants.CONTROLLER);

      // Confirm the servlet filter setup a user session
      UserSession userSession = (UserSession) request.getSession().getAttribute(SessionConstants.USER);
      if (userSession == null) {
        LOG.debug("A user session is required, and it's set by the servlet filter");
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      if (!pagePath.startsWith("/assets")) {
        response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
        response.setHeader("Pragma", "no-cache");
      }

      // Determine if this is a JSON service (shares similarities as a page)
      /*
      if (serviceInstances.containsKey(pagePath)) {
        Object classRef = serviceInstances.get(pagePath);
        WidgetContext widgetContext = createWidgetContext();
      }
      */

      // Always access the webPage record so it can be used downstream
      WebPage webPage = LoadWebPageCommand.loadByLink(pagePath);
      if (webPage != null) {
        // Determine if this is a draft page
        if (webPage.getDraft()) {
          if (!userSession.hasRole("admin") && !userSession.hasRole("content-manager")) {
            LOG.error("DRAFT FOUND, no access: " + pagePath + " " + request.getRemoteAddr());
            controllerSession.clearAllWidgetData();
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
          }
        }
        // Determine if this is a redirect
        String redirectLocation = webPage.getRedirectUrl();
        if (StringUtils.isNotBlank(redirectLocation)) {
          // Handle a redirect immediately
          if (!redirectLocation.startsWith("http:") && !redirectLocation.startsWith("https:")) {
            redirectLocation =
                scheme + "://" +
                    serverName +
                    (port != 80 ? ":" + port : "") +
                    (redirectLocation.startsWith("/") ? "" : "/") +
                    redirectLocation;
          }
          response.setHeader("Location", redirectLocation);
          response.sendError(SC_MOVED_PERMANENTLY);
          return;
        }
        request.setAttribute(MASTER_WEB_PAGE, webPage);
      }

      // Determine the Page XML Layout for this request
      Page pageRef = WebPageXmlLayoutCommand.retrievePageForRequest(webPage, pagePath);
      Map<String, String> widgetLibrary = WebPageXmlLayoutCommand.getWidgetLibrary();

      // Load the properties
      Map<String, String> systemPropertyMap = LoadSitePropertyCommand.loadAsMap("system");
      Map<String, String> sitePropertyMap = LoadSitePropertyCommand.loadAsMap("site");
      Map<String, String> themePropertyMap = LoadSitePropertyCommand.loadAsMap("theme");
      Map<String, String> socialPropertyMap = LoadSitePropertyCommand.loadAsMap("social");
      Map<String, String> analyticsPropertyMap = LoadSitePropertyCommand.loadAsMap("analytics");
      Map<String, String> ecommercePropertyMap = LoadSitePropertyCommand.loadAsMap("ecommerce");

      // Web Page Hits
      if (pageRef != null) {
        // Determine if this is a monitoring app
        if (request.getHeader("X-Monitor") == null) {
          SaveWebPageHitCommand.saveHit(request.getRemoteAddr(), request.getMethod(), pagePath, webPage, userSession);
        }
      }

      // Allow content admins to see a page
      if (pageRef == null &&
          (userSession.hasRole("admin") ||
              userSession.hasRole("content-manager"))) {
        pageRef = WebPageXmlLayoutCommand.retrievePage("_page_content_not_ready_");
      }

      // See if the site is in setup mode (allow any user?)
      if (!userSession.hasRole("admin") &&
          !userSession.hasRole("content-manager") &&
          "false".equals(sitePropertyMap.getOrDefault("site.online", "false"))) {
        if ("/".equals(pagePath)) {
          pageRef = WebPageXmlLayoutCommand.retrievePage("_new_install_");
//        } else if (!"/login".equals(pagePath)) {
          // @todo implement and test this...
          // Redirect to /, except for login page
        }
      }

      // Looks like a new install (do after admin above)
      if (pageRef == null && "/".equals(pagePath)) {
        pageRef = WebPageXmlLayoutCommand.retrievePage("_new_install_");
      }

      // Still no page? show an error
      if (pageRef == null) {
        LOG.error("PAGE NOT FOUND: " + pagePath + " " + request.getRemoteAddr());
        controllerSession.clearAllWidgetData();
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      // Verify the user has access to the page
      if (!WebComponentCommand.allowsUser(pageRef, userSession)) {
        LOG.warn("PAGE NOT ALLOWED: " + pagePath + " " +
            (!pageRef.getRoles().isEmpty() ? "[roles=" + pageRef.getRoles().toString() + "]" + " " : "") +
            (!pageRef.getGroups().isEmpty() ? "[groups=" + pageRef.getGroups().toString() + "]" + " " : "") +
            request.getRemoteAddr());
        controllerSession.clearAllWidgetData();
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      // Determine the core data to be used by local and remote widgets...
      Map<String, String> coreData = new HashMap<>();
      coreData.put("userId", String.valueOf(userSession.getUserId()));

      // Determine the global collection for this request
      Collection thisCollection = null;
      if (pageRef.checkForCollectionUniqueId()) {
        String collectionUniqueId = null;
        // Determine if Id is in Request or URI
        if (pageRef.getCollectionUniqueId().startsWith("?")) {
          if ("?collectionId".equals(pageRef.getCollectionUniqueId())) {
            String collectionIdValue = request.getParameter("collectionId");
            if (StringUtils.isNumeric(collectionIdValue)) {
              long collectionId = Long.parseLong(collectionIdValue);
              thisCollection = LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(collectionId, userSession.getUserId());
              if (thisCollection != null) {
                collectionUniqueId = thisCollection.getUniqueId();
              }
            }
          } else {
            collectionUniqueId = request.getParameter("collectionUniqueId");
          }
        } else if (pageRef.getCollectionUniqueId().startsWith("/")) {
          collectionUniqueId = requestURI.substring(pageRef.getCollectionUniqueId().indexOf("*"));
        }
        if (!StringUtils.isBlank(collectionUniqueId)) {
          if (thisCollection == null) {
            thisCollection = LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(collectionUniqueId, userSession.getUserId());
          }
          if (thisCollection == null) {
            LOG.error("COLLECTION NOT ALLOWED: " + pagePath + " [roles=" + pageRef.getRoles().toString() + "]");
            controllerSession.clearAllWidgetData();
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
          }
          coreData.put("collectionUniqueId", collectionUniqueId);
          LOG.debug("Added collection to coreData: " + collectionUniqueId);
        }
      }

      // Determine the global item for this request
      Item thisItem = null;
      if (pageRef.checkForItemUniqueId()) {
        String subTab = "";
        // Extract the item unique id
        String itemUniqueId = requestURI.substring(pageRef.getItemUniqueId().indexOf("*"));
        if (itemUniqueId.contains("/")) {
          subTab = "/_" + itemUniqueId.substring(itemUniqueId.indexOf("/") + 1) + "_";
          itemUniqueId = itemUniqueId.substring(0, itemUniqueId.indexOf("/"));
        }
        // User must be authorized here...
        thisItem = LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(itemUniqueId, userSession.getUserId());
        if (thisItem == null) {
          LOG.error("ITEM NOT ALLOWED: " + pagePath + " [roles=" + pageRef.getRoles().toString() + "]");
          controllerSession.clearAllWidgetData();
          response.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
        }
        LOG.debug("Added item to coreData: " + itemUniqueId);
        coreData.put("itemUniqueId", itemUniqueId);
        // Look for the collection page
        if (!coreData.containsKey("collectionUniqueId")) {
          thisCollection = LoadCollectionCommand.loadCollectionById(thisItem.getCollectionId());
          coreData.put("collectionUniqueId", thisCollection.getUniqueId());
          LOG.debug("Added item's collection to coreData: " + thisCollection.getUniqueId());
          // Check for a collection customized page
          // @note plan for sub-tabs too
          int slashIndex = pagePath.indexOf("/", 1);
          String itemMethod = pagePath.substring(1, slashIndex);
          String itemCollectionKey = "_" + itemMethod + "_" + thisCollection.getUniqueId() + "_" + subTab;
          LOG.debug("itemCollectionKey=" + itemCollectionKey);
          if (WebPageXmlLayoutCommand.containsPage(itemCollectionKey)) {
            pageRef = WebPageXmlLayoutCommand.retrievePage(itemCollectionKey);
          }
        }
      }

      // Determine the global collection category for this request
      Category thisCollectionCategory = null;
      if (thisItem != null) {
        thisCollectionCategory = LoadCategoryCommand.loadCategoryById(thisItem.getCategoryId());
      }

      // Setup the rendering info
      PageRenderInfo pageRenderInfo = new PageRenderInfo(pageRef, pagePath);
      if (pageRenderInfo.getName().startsWith("_")) {
        // Show the actual name from the request, not the template name
        pageRenderInfo.setName(pagePath);
      }
      if (thisCollection != null && thisItem != null) {
        pageRenderInfo.setTitle(thisItem.getName() + " | " + thisCollection.getName());
      } else {
        if (thisCollection != null) {
          pageRenderInfo.setTitle(thisCollection.getName());
        }
        if (thisItem != null) {
          pageRenderInfo.setTitle(thisItem.getName());
        }
      }
      if (webPage != null && StringUtils.isNotBlank(webPage.getImageUrl())) {
        pageRenderInfo.setImageUrl(webPage.getImageUrl());
      }

      // Finally... we have a page ready to be processed...
      if (LOG.isDebugEnabled()) {
        LOG.debug(request.getMethod() + " page " + pageRef.getName());
      }

      // Create a context for processing the widgets
      WebContainerContext webContainerContext = new WebContainerContext(request, response, controllerSession, widgetInstances, webPage, pageRef);

      // Validate post/delete/action calls
      if (webContainerContext.isTargeted()) {

        // Verify a target widget exists
        String targetWidget = request.getParameter("widget");
        if (StringUtils.isEmpty(targetWidget)) {
          LOG.error("DEVELOPER: TARGET WIDGET PARAMETER WAS NOT FOUND AND IS REQUIRED " + pagePath + " " + request.getRemoteAddr());
          controllerSession.clearAllWidgetData();
          response.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
        }
        pageRenderInfo.setTargetWidget(targetWidget);

        // Verify a token exists
        String formToken = request.getParameter("token");
        if (StringUtils.isEmpty(formToken)) {
          LOG.error("DEVELOPER: A FORM TOKEN IS REQUIRED " + pagePath + " " + request.getRemoteAddr());
          controllerSession.clearAllWidgetData();
          response.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
        }
      }

      // Render the page first
      if (WebContainerCommand.processWidgets(webContainerContext, pageRef.getSections(), pageRenderInfo, coreData, contextPath, pagePath, userSession, themePropertyMap)) {
        // The widget processor handled the response, immediately return
        return;
      }

      // Render the header
      Header requestHeader = null;
      if (pageRenderInfo.getName().startsWith("/checkout")) {
        requestHeader = WebContainerLayoutCommand.retrievePlainHeader(request.getServletContext(), widgetLibrary);
      } else {
        requestHeader = WebContainerLayoutCommand.retrieveHeader(request.getServletContext(), widgetLibrary);
      }
      HeaderRenderInfo headerRenderInfo = new HeaderRenderInfo(requestHeader, pagePath);
      WebContainerCommand.processWidgets(webContainerContext, requestHeader.getSections(), headerRenderInfo, coreData, contextPath, pagePath, userSession, themePropertyMap);

      // Render the footer
      Footer footer = WebContainerLayoutCommand.retrieveFooter(request.getServletContext(), widgetLibrary);
      FooterRenderInfo footerRenderInfo = new FooterRenderInfo(footer, pagePath);
      WebContainerCommand.processWidgets(webContainerContext, footer.getSections(), footerRenderInfo, coreData, contextPath, pagePath, userSession, themePropertyMap);

      // Finalize the controller session (zero out the widget's session data)
      controllerSession.clearAllWidgetData();

      // Provide values to the Tomcat web server log
      if (userSession.isLoggedIn()) {
        request.setAttribute(LOG_USER, String.valueOf(userSession.getUserId()));
      }

      // Error out if there are no widgets rendered or allowed
      if (!pageRenderInfo.hasWidgets()) {
        LOG.warn("NO WIDGETS - PAGE WILL NOT RENDER");
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      // Allow the layout to use the properties
      request.setAttribute("systemPropertyMap", systemPropertyMap);
      request.setAttribute("sitePropertyMap", sitePropertyMap);
      request.setAttribute("themePropertyMap", themePropertyMap);
      request.setAttribute("socialPropertyMap", socialPropertyMap);
      request.setAttribute("analyticsPropertyMap", analyticsPropertyMap);
      request.setAttribute("ecommercePropertyMap", ecommercePropertyMap);

      // Determine global items
      if (userSession.isLoggedIn() || "true".equals(sitePropertyMap.getOrDefault("site.online", "false"))) {
        // @todo determine if this is needed still (it is, but until all JSP layouts are removed?)
        // Load the main menu
        request.setAttribute(SHOW_MAIN_MENU, "true");
        List<MenuTab> menuTabList = LoadMenuTabsCommand.loadActiveIncludeMenuItemList();
        request.setAttribute(MASTER_MENU_TAB_LIST, menuTabList);

        // @note this is needed globally
        if (!"container".equals(request.getSession().getAttribute(SessionConstants.X_VIEW_MODE))) {
          TableOfContents footerStickyLinks = LoadTableOfContentsCommand.loadByUniqueId("footer-sticky-links", false);
          request.setAttribute(FOOTER_STICKY_LINKS, footerStickyLinks);
        }
      }

      long endRequestTime = System.currentTimeMillis();
      long totalTime = endRequestTime - startRequestTime;
      request.setAttribute(RENDER_TIME, totalTime);

      // Start rendering the page
      request.setAttribute(CONTEXT_PATH, contextPath);
      request.setAttribute(PAGE_RENDER_INFO, pageRenderInfo);
      if (thisCollection != null) {
        request.setAttribute(PAGE_COLLECTION, thisCollection);
      }
      if (thisCollectionCategory != null) {
        request.setAttribute(PAGE_COLLECTION_CATEGORY, thisCollectionCategory);
      }

      // Determine the custom stylesheets
      Stylesheet globalStylesheet = LoadStylesheetCommand.loadStylesheetByWebPageId(-1);
      if (globalStylesheet != null) {
        request.setAttribute("includeGlobalStylesheet", "true");
        request.setAttribute("includeGlobalStylesheetLastModified", globalStylesheet.getModified().getTime());
      }
      if (webPage != null) {
        Stylesheet pageStylesheet = LoadStylesheetCommand.loadStylesheetByWebPageId(webPage.getId());
        if (pageStylesheet != null) {
          request.setAttribute("includeStylesheet", pageStylesheet.getWebPageId());
          request.setAttribute("includeStylesheetLastModified", pageStylesheet.getModified().getTime());
        }
      }

      // Determine the output page requirements (css/scripts/etc)
      if (webContainerContext.isEmbedded()) {
        request.getServletContext().getRequestDispatcher("/WEB-INF/jsp/embedded.jsp").forward(request, response);
      } else {
        if ("container".equals(request.getSession().getAttribute(SessionConstants.X_VIEW_MODE))) {
          // For API content
          request.setAttribute(PAGE_BODY, "/WEB-INF/jsp/container-layout.jsp");
        } else {
          // For web content with a header and footer
          request.setAttribute(HEADER_RENDER_INFO, headerRenderInfo);
          request.setAttribute(FOOTER_RENDER_INFO, footerRenderInfo);
          request.setAttribute(PAGE_BODY, "/WEB-INF/jsp/layout.jsp");
        }
        request.getServletContext().getRequestDispatcher("/WEB-INF/jsp/main.jsp").forward(request, response);
      }
      LOG.debug("-----------------------------------------------------------------------");

    } catch (Exception e) {
      LOG.error("Page error caught: " + e.getMessage(), e);
    }
  }
}
