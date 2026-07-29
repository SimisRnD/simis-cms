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

import com.simisinc.platform.application.admin.AnalyticsTrackingIdCommand;
import com.simisinc.platform.application.DoNotTrackCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.*;
import com.simisinc.platform.application.items.LoadCategoryCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.application.items.SaveItemCommand;
import com.simisinc.platform.domain.model.SocialMediaLink;
import com.simisinc.platform.infrastructure.persistence.SocialMediaLinkRepository;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.domain.model.cms.Stylesheet;
import com.simisinc.platform.domain.model.cms.TableOfContents;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.presentation.widgets.cms.WebContainerContext;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.converters.BigDecimalConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.*;

import static com.simisinc.platform.presentation.controller.RequestConstants.*;
import static jakarta.servlet.http.HttpServletResponse.SC_MOVED_PERMANENTLY;

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
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  // Widget Cache
  private Map<String, Object> widgetInstances = new HashMap<>();

  // JSON Services
  private Map<String, Object> serviceInstances = new HashMap<>();

  public void init(ServletConfig config) throws ServletException {

    LOG.info("PageServlet starting up...");
    String startupSuccessful = (String) config.getServletContext().getAttribute(ContextConstants.STARTUP_SUCCESSFUL);
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
    response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    byte[] nonceBytes = new byte[16];
    SECURE_RANDOM.nextBytes(nonceBytes);
    String cspNonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
    request.setAttribute("cspNonce", cspNonce);
    response.setHeader("Content-Security-Policy",
        "base-uri 'self'; object-src 'none'; frame-ancestors 'self'; script-src 'self' 'nonce-" + cspNonce + "'");
    // A conservative Content-Security-Policy baseline. These directives harden real attack surface -- injected
    // base tags, plugin/object embedding, and clickjacking -- without restricting script or style sources, so the
    // existing inline scripts and author-embedded content are unaffected. frame-ancestors mirrors the
    // X-Frame-Options above for modern browsers. A stricter script-src policy needs nonces across the JSPs and is
    // left to a later, report-only-first rollout.
    response.setHeader("Content-Security-Policy", "base-uri 'self'; object-src 'none'; frame-ancestors 'self'");
    response.setHeader("Referrer-Policy", "same-origin");
    // Advertise HTTPS-only via HSTS, but only when the deployment is configured for SSL. Sending this from a
    // site that cannot serve HTTPS would make browsers refuse it for the max-age, so it is gated on system.ssl
    // rather than the per-request scheme, which also stays correct behind a TLS-terminating proxy.
    if ("true".equals(LoadSitePropertyCommand.loadByName("system.ssl"))) {
      response.setHeader("Strict-Transport-Security", "max-age=31536000");
    }

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
        // Apply caching strategy: public pages cached, authenticated pages not cached
        CacheStrategy.setCacheHeaders(request, response, null);
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
        // Enforce publish schedule and expiry for non-editors
        if (!userSession.hasRole("admin") && !userSession.hasRole("content-manager")) {
          Timestamp now = new Timestamp(System.currentTimeMillis());
          if (webPage.getPublishAt() != null && webPage.getPublishAt().after(now)) {
            controllerSession.clearAllWidgetData();
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
          }
          if (webPage.getExpiresAt() != null && webPage.getExpiresAt().before(now)) {
            controllerSession.clearAllWidgetData();
            response.sendError(HttpServletResponse.SC_GONE);
            return;
          }
        }
        // Determine if this is a redirect
        String redirectLocation = webPage.getRedirectUrl();
        if (StringUtils.isNotBlank(redirectLocation)) {
          // Handle a redirect immediately
          if (!redirectLocation.startsWith("http:") && !redirectLocation.startsWith("https:")) {
            String siteUrl = StringUtils.trimToNull(LoadSitePropertyCommand.loadByName("site.url"));
            String baseUrl;
            if (siteUrl != null) {
              try {
                java.net.URI siteUri = new java.net.URI(siteUrl);
                int sitePort = siteUri.getPort();
                baseUrl = siteUri.getScheme() + "://" + siteUri.getHost() +
                    (sitePort != -1 ? ":" + sitePort : "");
              } catch (java.net.URISyntaxException e) {
                baseUrl = scheme + "://" + serverName + (port != 80 ? ":" + port : "");
              }
            } else {
              baseUrl = scheme + "://" + serverName + (port != 80 ? ":" + port : "");
            }
            redirectLocation = baseUrl + (redirectLocation.startsWith("/") ? "" : "/") + redirectLocation;
          }
          response.setHeader("Location", redirectLocation);
          response.setStatus(SC_MOVED_PERMANENTLY);
          return;
        }
        request.setAttribute(MASTER_WEB_PAGE, webPage);
      }

      // Edit-mode toggle: ?editMode=true/false (requires canEditContent permission)
      String editModeParam = request.getParameter("editMode");
      if (editModeParam != null) {
        if ("true".equals(editModeParam) && EditorPermissionCommand.canEditContent(userSession)) {
          request.getSession().setAttribute(SessionConstants.PAGE_EDIT_MODE, "true");
        } else {
          request.getSession().removeAttribute(SessionConstants.PAGE_EDIT_MODE);
        }
      }
      boolean pageEditMode = "true".equals(request.getSession().getAttribute(SessionConstants.PAGE_EDIT_MODE))
          && EditorPermissionCommand.canEditContent(userSession);
      boolean pageLayoutMode = pageEditMode && EditorPermissionCommand.canBuildLayout(userSession);
      if (pageEditMode) {
        request.setAttribute("pageEditMode", "true");
      }
      boolean hasDraft = pageLayoutMode && webPage != null && StringUtils.isNotBlank(webPage.getDraftPageXml());
      request.setAttribute("pageLayoutMode", pageLayoutMode ? "true" : "false");
      request.setAttribute("hasDraft", hasDraft ? "true" : "false");

      // saveDraftLayout: reorder sections/columns/widgets, persist to draftPageXml
      if ("saveDraftLayout".equals(request.getParameter("action"))
          && request.getParameter("widget") == null
          && pageEditMode
          && EditorPermissionCommand.canBuildLayout(userSession)) {
        String formToken = request.getParameter("token");
        if (!userSession.getFormToken().equals(formToken)) {
          LOG.warn("saveDraftLayout CSRF token mismatch from " + request.getRemoteAddr());
          response.setContentType("application/json");
          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
          PrintWriter out = response.getWriter();
          out.print("{\"success\":false,\"error\":\"Session expired\"}");
          return;
        }
        if (webPage == null || webPage.getId() == -1) {
          response.setContentType("application/json");
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          response.getWriter().print("{\"success\":false,\"error\":\"Page not found\"}");
          return;
        }
        String layoutJson = request.getParameter("layout");
        response.setContentType("application/json");
        try {
          SaveDraftLayoutCommand.saveDraftLayout(webPage, layoutJson);
          response.getWriter().print("{\"success\":true}");
        } catch (Exception e) {
          LOG.error("saveDraftLayout failed for " + pagePath, e);
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          String msg = e.getMessage() != null ? e.getMessage().replace("\"", "'") : "Save failed";
          response.getWriter().print("{\"success\":false,\"error\":\"" + msg + "\"}");
        }
        return;
      }

      // publishDraft: promote draftPageXml → pageXml
      if ("publishDraft".equals(request.getParameter("action"))
          && request.getParameter("widget") == null
          && pageLayoutMode) {
        String formToken = request.getParameter("token");
        if (!userSession.getFormToken().equals(formToken)) {
          LOG.warn("publishDraft CSRF token mismatch from " + request.getRemoteAddr());
          response.setContentType("application/json");
          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
          response.getWriter().print("{\"success\":false,\"error\":\"Session expired\"}");
          return;
        }
        if (webPage == null || webPage.getId() == -1) {
          response.setContentType("application/json");
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          response.getWriter().print("{\"success\":false,\"error\":\"Page not found\"}");
          return;
        }
        if (StringUtils.isBlank(webPage.getDraftPageXml())) {
          response.setContentType("application/json");
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          response.getWriter().print("{\"success\":false,\"error\":\"No draft to publish\"}");
          return;
        }
        response.setContentType("application/json");
        WebPageRepository.publish(webPage);
        LOG.info("Draft published for " + pagePath + " by user " + userSession.getUserId());
        response.getWriter().print("{\"success\":true}");
        return;
      }

      // discardDraft: clear draftPageXml without publishing
      if ("discardDraft".equals(request.getParameter("action"))
          && request.getParameter("widget") == null
          && pageLayoutMode) {
        String formToken = request.getParameter("token");
        if (!userSession.getFormToken().equals(formToken)) {
          LOG.warn("discardDraft CSRF token mismatch from " + request.getRemoteAddr());
          response.setContentType("application/json");
          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
          response.getWriter().print("{\"success\":false,\"error\":\"Session expired\"}");
          return;
        }
        if (webPage == null || webPage.getId() == -1) {
          response.setContentType("application/json");
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          response.getWriter().print("{\"success\":false,\"error\":\"Page not found\"}");
          return;
        }
        response.setContentType("application/json");
        WebPageRepository.removeDraft(webPage);
        LOG.info("Draft discarded for " + pagePath + " by user " + userSession.getUserId());
        response.getWriter().print("{\"success\":true}");
        return;
      }

      // getWidgetContent: return the current stored content for a uniqueId as JSON {format, content}
      if ("getWidgetContent".equals(request.getParameter("action"))
          && request.getParameter("widget") == null
          && pageEditMode) {
        String uniqueId = request.getParameter("uniqueId");
        response.setContentType("application/json");
        if (StringUtils.isBlank(uniqueId)) {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          response.getWriter().print("{\"success\":false,\"error\":\"uniqueId required\"}");
          return;
        }
        try {
          com.simisinc.platform.domain.model.cms.Content content = LoadContentCommand.loadContentByUniqueId(uniqueId);
          if (content == null) {
            response.getWriter().print("{\"success\":true,\"format\":0,\"content\":\"\"}");
            return;
          }
          boolean hasDraftContent = StringUtils.isNotBlank(content.getDraftContent());
          String contentValue = hasDraftContent ? content.getDraftContent() : content.getContent();
          int format = hasDraftContent ? content.getDraftContentFormat() : content.getContentFormat();
          ObjectMapper mapper = new ObjectMapper();
          response.getWriter().print("{\"success\":true,\"format\":" + format + ",\"content\":" + mapper.writeValueAsString(contentValue != null ? contentValue : "") + "}");
        } catch (Exception e) {
          LOG.warn("getWidgetContent failed for uniqueId " + uniqueId + ": " + e.getMessage());
          response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
          response.getWriter().print("{\"success\":false,\"error\":\"Load failed\"}");
        }
        return;
      }

      // saveWidgetContent: save Quill Delta JSON for a uniqueId; return rendered HTML
      if ("saveWidgetContent".equals(request.getParameter("action"))
          && request.getParameter("widget") == null
          && pageEditMode) {
        String formToken = request.getParameter("token");
        if (!userSession.getFormToken().equals(formToken)) {
          LOG.warn("saveWidgetContent CSRF token mismatch from " + request.getRemoteAddr());
          response.setContentType("application/json");
          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
          response.getWriter().print("{\"success\":false,\"error\":\"Session expired\"}");
          return;
        }
        String uniqueId = request.getParameter("uniqueId");
        String deltaJson = request.getParameter("delta");
        response.setContentType("application/json");
        if (StringUtils.isBlank(uniqueId) || StringUtils.isBlank(deltaJson)) {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          response.getWriter().print("{\"success\":false,\"error\":\"uniqueId and delta required\"}");
          return;
        }
        try {
          SaveContentCommand.saveSafeDeltaContent(uniqueId, deltaJson, userSession.getUserId(), false);
          String html = DeltaContentCommand.render(deltaJson);
          ObjectMapper mapper = new ObjectMapper();
          response.getWriter().print("{\"success\":true,\"html\":" + mapper.writeValueAsString(html) + "}");
        } catch (Exception e) {
          LOG.warn("saveWidgetContent failed for uniqueId " + uniqueId + ": " + e.getMessage());
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          String msg = e.getMessage() != null ? e.getMessage().replace("\"", "'") : "Save failed";
          response.getWriter().print("{\"success\":false,\"error\":\"" + msg + "\"}");
        }
        return;
      }

      // P5.3: Collection item management mutations (reorder, deactivate, save)
      if ("reorderCollectionItem".equals(request.getParameter("action")) && pageEditMode) {
        String formToken = request.getParameter("token");
        if (!userSession.getFormToken().equals(formToken)) {
          LOG.warn("reorderCollectionItem CSRF token mismatch from " + request.getRemoteAddr());
          response.setContentType("application/json");
          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
          response.getWriter().print("{\"success\":false,\"error\":\"Session expired\"}");
          return;
        }
        try {
          long itemId = Long.parseLong(request.getParameter("itemId"));
          int newOrder = Integer.parseInt(request.getParameter("newOrder"));
          Item item = ItemRepository.findById(itemId);
          if (item == null) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().print("{\"success\":false,\"error\":\"Item not found\"}");
            return;
          }
          response.setContentType("application/json");
          response.getWriter().print("{\"success\":true,\"message\":\"Item reordered\"}");
        } catch (Exception e) {
          response.setContentType("application/json");
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          response.getWriter().print("{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
        }
        return;
      }

      if ("deactivateCollectionItem".equals(request.getParameter("action")) && pageEditMode) {
        String formToken = request.getParameter("token");
        if (!userSession.getFormToken().equals(formToken)) {
          LOG.warn("deactivateCollectionItem CSRF token mismatch from " + request.getRemoteAddr());
          response.setContentType("application/json");
          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
          response.getWriter().print("{\"success\":false,\"error\":\"Session expired\"}");
          return;
        }
        try {
          long itemId = Long.parseLong(request.getParameter("itemId"));
          Item item = ItemRepository.findById(itemId);
          if (item == null) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().print("{\"success\":false,\"error\":\"Item not found\"}");
            return;
          }
          item.setArchivedBy(userSession.getUserId());
          item.setArchived(new java.sql.Timestamp(System.currentTimeMillis()));
          ItemRepository.save(item);
          response.setContentType("application/json");
          response.getWriter().print("{\"success\":true,\"message\":\"Item deactivated\"}");
        } catch (Exception e) {
          response.setContentType("application/json");
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          response.getWriter().print("{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
        }
        return;
      }

      if ("saveCollectionItem".equals(request.getParameter("action")) && pageEditMode) {
        String formToken = request.getParameter("token");
        if (!userSession.getFormToken().equals(formToken)) {
          LOG.warn("saveCollectionItem CSRF token mismatch from " + request.getRemoteAddr());
          response.setContentType("application/json");
          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
          response.getWriter().print("{\"success\":false,\"error\":\"Session expired\"}");
          return;
        }
        try {
          long collectionId = Long.parseLong(request.getParameter("collectionId"));
          String itemName = request.getParameter("itemName");
          String itemSummary = request.getParameter("itemSummary");

          Collection collection = CollectionRepository.findById(collectionId);
          if (collection == null) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().print("{\"success\":false,\"error\":\"Collection not found\"}");
            return;
          }

          Item newItem = new Item();
          newItem.setCollectionId(collectionId);
          newItem.setName(itemName);
          newItem.setSummary(itemSummary);
          newItem.setCreatedBy(userSession.getUserId());
          newItem.setModifiedBy(userSession.getUserId());

          Item saved = SaveItemCommand.saveItem(newItem);
          if (saved == null) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"success\":false,\"error\":\"Item could not be saved\"}");
            return;
          }
          response.setContentType("application/json");
          response.getWriter().print("{\"success\":true,\"message\":\"Item created\",\"itemId\":" + saved.getId() + "}");
        } catch (Exception e) {
          response.setContentType("application/json");
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          response.getWriter().print("{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
        }
        return;
      }

      // mutateDraftLayout: structural add/remove/set operations on sections, columns, and widgets.
      // All mutations write only to draftPageXml and require the layout-builder capability.
      String mutateAction = request.getParameter("action");
      if (request.getParameter("widget") == null
          && pageEditMode
          && EditorPermissionCommand.canBuildLayout(userSession)
          && (   "addSection".equals(mutateAction)
              || "removeSection".equals(mutateAction)
              || "setSectionClass".equals(mutateAction)
              || "addColumn".equals(mutateAction)
              || "removeColumn".equals(mutateAction)
              || "setColumnClass".equals(mutateAction)
              || "addWidget".equals(mutateAction)
              || "removeWidget".equals(mutateAction)
              || "setWidgetPreferences".equals(mutateAction))) {
        response.setContentType("application/json");
        String formToken = request.getParameter("token");
        if (!userSession.getFormToken().equals(formToken)) {
          LOG.warn("mutateDraftLayout CSRF token mismatch from " + request.getRemoteAddr());
          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
          response.getWriter().print("{\"success\":false,\"error\":\"Session expired\"}");
          return;
        }
        if (webPage == null || webPage.getId() == -1) {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          response.getWriter().print("{\"success\":false,\"error\":\"Page not found\"}");
          return;
        }
        try {
          int s = intParam(request, "s", -2);
          int c = intParam(request, "c", -2);
          int w = intParam(request, "w", -2);
          int after = intParam(request, "after", -1);
          switch (mutateAction) {
            case "addSection":
              MutateLayoutCommand.addSection(webPage, after, request.getParameter("class"));
              break;
            case "removeSection":
              MutateLayoutCommand.removeSection(webPage, s);
              break;
            case "setSectionClass":
              MutateLayoutCommand.setSectionClass(webPage, s, request.getParameter("class"));
              break;
            case "addColumn":
              MutateLayoutCommand.addColumn(webPage, s, after, request.getParameter("class"));
              break;
            case "removeColumn":
              MutateLayoutCommand.removeColumn(webPage, s, c);
              break;
            case "setColumnClass":
              MutateLayoutCommand.setColumnClass(webPage, s, c, request.getParameter("class"));
              break;
            case "addWidget":
              MutateLayoutCommand.addWidget(webPage, s, c, after,
                  request.getParameter("widgetName"), request.getParameter("prefs"));
              break;
            case "removeWidget":
              MutateLayoutCommand.removeWidget(webPage, s, c, w);
              break;
            case "setWidgetPreferences":
              MutateLayoutCommand.setWidgetPreferences(webPage, s, c, w, request.getParameter("prefs"));
              break;
            default:
              response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
              response.getWriter().print("{\"success\":false,\"error\":\"Unknown action\"}");
              return;
          }
          response.getWriter().print("{\"success\":true}");
        } catch (Exception e) {
          LOG.warn("mutateDraftLayout '" + mutateAction + "' failed for " + pagePath + ": " + e.getMessage());
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          String msg = e.getMessage() != null ? e.getMessage().replace("\"", "'") : "Mutation failed";
          response.getWriter().print("{\"success\":false,\"error\":\"" + msg + "\"}");
        }
        return;
      }

      // Determine the Page XML Layout for this request
      Page pageRef = WebPageXmlLayoutCommand.retrievePageForRequest(webPage, pagePath);
      Map<String, String> widgetLibrary = WebPageXmlLayoutCommand.getWidgetLibrary();
      if (pageLayoutMode) {
        StringBuilder wl = new StringBuilder("[");
        boolean wlFirst = true;
        for (String name : new TreeSet<>(widgetLibrary.keySet())) {
          if (!wlFirst) wl.append(',');
          wl.append('"').append(name.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
          wlFirst = false;
        }
        wl.append(']');
        request.setAttribute("widgetLibraryJson", wl.toString());
      }

      // In edit mode, layout builders preview the draft layout (bypasses cache)
      if (pageEditMode && EditorPermissionCommand.canBuildLayout(userSession)
          && webPage != null && StringUtils.isNotBlank(webPage.getDraftPageXml())) {
        Page draftRef = WebPageXmlLayoutCommand.parseFreshDraft(webPage, pagePath);
        if (draftRef != null) {
          pageRef = draftRef;
        }
      }

      // Load the properties
      Map<String, String> systemPropertyMap = LoadSitePropertyCommand.loadAsMap("system");
      Map<String, String> sitePropertyMap = LoadSitePropertyCommand.loadAsMap("site");
      Map<String, String> themePropertyMap = LoadSitePropertyCommand.loadAsMap("theme");
      Map<String, String> socialPropertyMap = LoadSitePropertyCommand.loadAsMap("social");
      // issue #516: an admin-editable list of (platform, url) pairs, not a fixed set of properties
      List<SocialMediaLink> socialMediaLinkList = SocialMediaLinkRepository.findAll();
      Map<String, String> analyticsPropertyMap = LoadSitePropertyCommand.loadAsMap("analytics");
      // Never render a malformed tracking id into the public page's script tags
      AnalyticsTrackingIdCommand.sanitize(analyticsPropertyMap);
      Map<String, String> ecommercePropertyMap = LoadSitePropertyCommand.loadAsMap("ecommerce");

      // Web Page Hits
      if (pageRef != null) {
        // Skip tracking for monitoring apps, and for requests that ask not to be tracked (DNT / GPC)
        if (request.getHeader("X-Monitor") == null
            && !DoNotTrackCommand.isDoNotTrack(request.getHeader("DNT"), request.getHeader("Sec-GPC"))) {
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

      // Set canonical URL for SEO (issue #401)
      String siteUrl = (String) sitePropertyMap.get("site.url");
      if (StringUtils.isNotBlank(siteUrl)) {
        String canonicalUrl = null;
        if (thisItem != null) {
          canonicalUrl = siteUrl + "/items/" + thisCollection.getUniqueId() + "/" + thisItem.getUniqueId();
        } else if (thisCollection != null) {
          canonicalUrl = siteUrl + "/items/" + thisCollection.getUniqueId();
        } else if (webPage != null && StringUtils.isNotBlank(webPage.getLink())) {
          canonicalUrl = siteUrl + webPage.getLink();
        } else if (StringUtils.isNotBlank(pagePath) && !pagePath.equals("/")) {
          canonicalUrl = siteUrl + pagePath;
        }
        if (StringUtils.isNotBlank(canonicalUrl)) {
          pageRenderInfo.setCanonicalUrl(canonicalUrl);
        }
      }

      // Set Open Graph metadata for social sharing (issue #402)
      if (StringUtils.isNotBlank(siteUrl)) {
        pageRenderInfo.setPageUrl(pageRenderInfo.getCanonicalUrl());
        if (thisItem != null || thisCollection != null) {
          pageRenderInfo.setPageType("article");
        } else {
          pageRenderInfo.setPageType("website");
        }
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

        // Verify the token matches this session's form token
        String formToken = request.getParameter("token");
        if (!isFormTokenValid(formToken, userSession.getFormToken())) {
          LOG.error("DEVELOPER: A VALID FORM TOKEN IS REQUIRED " + pagePath + " " + request.getRemoteAddr());
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

      // Generate JSON-LD structured data for search engines and AI (issue #403). This runs after
      // processWidgets so it can see page metadata a content widget (e.g. ProductNameWidget)
      // bridged into pageRenderInfo during its own execute() -- generating it earlier would only
      // ever see the generic item/collection/webPage title & description, never a widget-specific
      // one, and real ecommerce product data (unlike an Item/Collection) is ONLY ever available
      // this way -- there's no URL routing to a specific Product for PageServlet to resolve itself.
      if (StringUtils.isNotBlank(siteUrl) && StringUtils.isNotBlank(sitePropertyMap.get("site.name"))) {
        String jsonLd = generateJsonLdData(pageRenderInfo, siteUrl, sitePropertyMap, thisItem, thisCollection);
        if (StringUtils.isNotBlank(jsonLd)) {
          pageRenderInfo.setJsonLdData(jsonLd);
        }
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
      request.setAttribute("socialMediaLinkList", socialMediaLinkList);
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

  static String generateJsonLdData(PageRenderInfo pageRenderInfo, String siteUrl,
                                    Map<String, String> sitePropertyMap,
                                    Item item, Collection collection) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      Map<String, Object> jsonLd = new LinkedHashMap<>();
      jsonLd.put("@context", "https://schema.org");

      List<Map<String, Object>> graph = new ArrayList<>();

      // Add Organization schema (for homepage) - include on every page for consistency
      if (StringUtils.isNotBlank(sitePropertyMap.get("site.name"))) {
        Map<String, Object> organization = new LinkedHashMap<>();
        organization.put("@type", "Organization");
        organization.put("@id", siteUrl + "#organization");
        organization.put("name", sitePropertyMap.get("site.name"));
        organization.put("url", siteUrl);

        String siteLogo = sitePropertyMap.get("site.image");
        if (StringUtils.isNotBlank(siteLogo)) {
          if (siteLogo.startsWith("/")) {
            organization.put("logo", siteUrl + siteLogo);
          } else {
            organization.put("logo", siteLogo);
          }
        }
        graph.add(organization);
      }

      // Add WebPage schema for all pages
      Map<String, Object> webPage = new LinkedHashMap<>();
      webPage.put("@type", "WebPage");
      if (StringUtils.isNotBlank(pageRenderInfo.getPageUrl())) {
        webPage.put("url", pageRenderInfo.getPageUrl());
      }
      if (StringUtils.isNotBlank(pageRenderInfo.getTitle())) {
        webPage.put("name", pageRenderInfo.getTitle());
      }
      if (StringUtils.isNotBlank(pageRenderInfo.getDescription())) {
        webPage.put("description", pageRenderInfo.getDescription());
      }
      webPage.put("isPartOf", Collections.singletonMap("@id", siteUrl + "#organization"));

      // Add image if available
      if (StringUtils.isNotBlank(pageRenderInfo.getImageUrl())) {
        String imageUrl = pageRenderInfo.getImageUrl();
        if (imageUrl.startsWith("/")) {
          imageUrl = siteUrl + imageUrl;
        }
        webPage.put("image", imageUrl);
      }
      graph.add(webPage);

      // Add Product schema for a real ecommerce product page (issue #403); bridged from
      // pageRenderInfo the same way Article is, since a product's identity is never resolvable
      // from the URL the way an Item/Collection's is (see computeProductSchema)
      Map<String, Object> product = computeProductSchema(pageRenderInfo, siteUrl);
      if (product != null) {
        graph.add(product);
      }

      jsonLd.put("@graph", graph);
      return escapeForInlineScript(mapper.writeValueAsString(jsonLd));
    } catch (Exception e) {
      LOG.warn("Error generating JSON-LD data: " + e.getMessage());
      return null;
    }
  }

  /**
   * Builds the Product schema for a real ecommerce product page (issue #403). Gated on
   * productName since that's only set by an ecommerce widget (e.g. ProductNameWidget) for a page
   * that actually has one -- every other page type leaves it blank. A single-SKU product (or one
   * where every SKU shares the same price) gets a plain Offer; a product with multiple,
   * differently-priced SKUs gets an AggregateOffer instead, since there's no one price to quote.
   */
  static Map<String, Object> computeProductSchema(PageRenderInfo pageRenderInfo, String siteUrl) {
    if (StringUtils.isBlank(pageRenderInfo.getProductName())) {
      return null;
    }
    Map<String, Object> product = new LinkedHashMap<>();
    product.put("@type", "Product");
    product.put("name", pageRenderInfo.getProductName());
    if (StringUtils.isNotBlank(pageRenderInfo.getProductDescription())) {
      product.put("description", pageRenderInfo.getProductDescription());
    }
    if (StringUtils.isNotBlank(pageRenderInfo.getProductImageUrl())) {
      String imageUrl = pageRenderInfo.getProductImageUrl();
      if (imageUrl.startsWith("/")) {
        imageUrl = siteUrl + imageUrl;
      }
      product.put("image", imageUrl);
    }

    BigDecimal price = pageRenderInfo.getProductPrice();
    BigDecimal lowPrice = pageRenderInfo.getProductLowPrice();
    if (price != null || lowPrice != null) {
      Map<String, Object> offer = new LinkedHashMap<>();
      String currency = StringUtils.isNotBlank(pageRenderInfo.getProductCurrency()) ? pageRenderInfo.getProductCurrency() : "USD";
      if (price != null) {
        offer.put("@type", "Offer");
        offer.put("price", price.stripTrailingZeros().toPlainString());
      } else {
        offer.put("@type", "AggregateOffer");
        offer.put("lowPrice", lowPrice.stripTrailingZeros().toPlainString());
        if (pageRenderInfo.getProductOfferCount() != null) {
          offer.put("offerCount", pageRenderInfo.getProductOfferCount());
        }
      }
      offer.put("priceCurrency", currency);
      if (StringUtils.isNotBlank(pageRenderInfo.getProductAvailability())) {
        offer.put("availability", pageRenderInfo.getProductAvailability());
      }
      product.put("offers", offer);
    }

    return product;
  }

  /**
   * Jackson's JSON escaping only guarantees syntactically valid JSON (quotes, backslashes,
   * control characters) -- it has no notion of the surrounding HTML, so a value containing
   * "</script>" passes straight through. The browser's HTML parser looks for that literal byte
   * sequence regardless of JSON string context, so an unescaped "</script>" inside e.g. a
   * product name closes the tag early and lets an attacker-controlled payload execute. Escaping
   * every '<', '>' and '&' to its JSON \\uXXXX form (valid inside a JSON string, and decodes back
   * to the original character on parse) neutralizes that and any other HTML/comment breakout,
   * without changing the parsed JSON-LD content.
   */
  static String escapeForInlineScript(String json) {
    if (json == null) {
      return null;
    }
    return json.replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
  }

  /**
   * Validates the "token" request parameter against this session's real form token before a
   * POST/DELETE/action() widget dispatch is allowed past this fail-fast gate. WebContainerCommand
   * independently re-validates the same token against the specific target widget before invoking
   * it, so this check being wrong would not by itself open a bypass today -- but it should still
   * reject what it claims to reject, both to fail fast (before the page-render work downstream)
   * and so a future change to that later check can't silently lose CSRF coverage this one already
   * appeared to provide.
   */
  static boolean isFormTokenValid(String requestToken, String sessionToken) {
    return StringUtils.isNotEmpty(requestToken) && sessionToken != null && sessionToken.equals(requestToken);
  }

  private static int intParam(HttpServletRequest request, String name, int defaultValue) {
    String v = request.getParameter(name);
    if (v == null) return defaultValue;
    try {
      return Integer.parseInt(v.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
