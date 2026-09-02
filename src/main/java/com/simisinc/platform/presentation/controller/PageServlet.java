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
import com.simisinc.platform.application.cms.AllowedIframeHostCommand;
import com.simisinc.platform.application.maps.FindMapTilesCredentialsCommand;
import com.simisinc.platform.application.cms.*;
import com.simisinc.platform.application.items.LoadCategoryCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.application.items.SaveItemCommand;
import com.simisinc.platform.domain.model.SocialMediaLink;
import com.simisinc.platform.infrastructure.persistence.SocialMediaLinkRepository;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.domain.model.cms.FaqQuestion;
import com.simisinc.platform.domain.model.cms.MenuItem;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.domain.model.cms.Stylesheet;
import com.simisinc.platform.domain.model.cms.TableOfContents;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.cms.WebPagePreviewToken;
import com.simisinc.platform.infrastructure.cache.PublishEventCachePurgeHandler;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPagePreviewTokenRepository;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
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
    // form-action 'self' keeps an injected <form> from posting to an attacker's origin. Every form
    // the platform serves is same-origin: the payment forms tokenize client-side through the
    // Stripe/Square SDK and then post the token back here, so none of them are affected.
    // style-src and font-src close the two directives that were silently ungoverned: with no
    // default-src, an absent directive falls back to nothing at all, so stylesheets and fonts could
    // be fetched from any origin. Measured against every published page before choosing these --
    // all 12 served stylesheets (including the site-specific one) reference zero external origins,
    // and the Inter webfont is self-hosted under /css/google-fonts, so 'self' breaks nothing.
    // 'unsafe-inline' is unavoidable: the theme's colour tokens are emitted as an inline <style>
    // block by design, and several hundred inline style attributes exist across the JSPs. It still
    // leaves style-src strictly stronger than absent, because a foreign stylesheet is now refused.
    //
    // img-src and default-src are deliberately NOT set yet (issue #1430). img-src is the directive
    // that would close the CSS-based exfiltration channel, but published content still references
    // images on an external origin, so setting it now would break real pages. default-src has to
    // come last, after img-src and frame-src, or the video and careers iframes fall through to it.
      // img-src is the directive that actually closes the exfiltration channel #1430 describes:
      // with it absent and no default-src, a CSS attribute selector can encode a field value
      // into a background-image URL and send it to any host. Setting it was blocked until now
      // by published content referencing images elsewhere; those references are gone (#1449),
      // and a crawl of all 135 sitemap pages finds 4,565 image sources, every one same-origin.
      //
      // data: is allowed rather than needed -- nothing emits an inline image today, but the
      // editor can produce one on paste, and a data: URI cannot exfiltrate anything: it carries
      // its own bytes and makes no request. Omitting it would buy no safety and would break a
      // paste that looks perfectly reasonable to an author.
      //
      // The two video hosts are not optional. VideoWidget renders a YouTube poster as a CSS
      // background-image from img.youtube.com, and video.jsp sets the Vimeo equivalent from the
      // oEmbed response, which serves thumbnails off i.vimeocdn.com. A site-wide crawl does not
      // reveal either, because this pilot happens not to use the widget -- the requirement is in
      // the platform, not in the content, which is exactly the kind of gap a crawl cannot close.
      //
      // frame-src is emitted from AllowedIframeHostCommand, the same list HtmlCommand enforces
      // when content is saved. Two layers on one list: the sanitizer stops a disallowed embed
      // becoming stored content and tells the author while they can still fix it, and this stops
      // anything that got stored another way -- content predating a host's removal, a direct
      // database write, a sanitizer bug -- from loading. Either alone leaves a real gap. It is a
      // list rather than a constant because this is a product: sites embed different vendors, and
      // hardcoding one site's would mean a platform release every time another needed an embed.
      // The hosts the platform's own widgets require are always included and cannot be removed by
      // clearing the setting.
      //
      // default-src remains deliberately absent and has to stay last (see #1430): every directive
      // it would back-stop must be set first, or the content it governs falls through to it.
      // connect-src has had no inventory taken -- video.jsp alone calls Vimeo's oEmbed endpoint
      // from the browser -- and must not be inherited from a backstop before it does.
      // Leaflet fetches map tiles as images, so the configured tile host has to be here or the
      // map widget renders its controls and marker over an empty grey square -- the same class of
      // gap as the two video hosts above, and one a content crawl cannot find either, because the
      // requirement is in the platform rather than in any page.
      String tileSource = FindMapTilesCredentialsCommand.cspImageSource();
      String mapTileImageSource = tileSource == null ? "" : " " + tileSource;

      response.setHeader("Content-Security-Policy",
          "base-uri 'self'; object-src 'none'; frame-ancestors 'self'; form-action 'self'; "
              + "style-src 'self' 'unsafe-inline'; font-src 'self'; "
              + "img-src 'self' data: https://img.youtube.com https://i.vimeocdn.com"
              + mapTileImageSource + "; "
              + "frame-src " + AllowedIframeHostCommand.cspFrameSourceList() + "; "
              + "script-src 'self' 'nonce-" + cspNonce + "'");

      // The candidate policy from Security Settings, when one is configured. Report-only cannot
      // block a resource, so this is safe to run against live traffic -- which is the point: the
      // directives #1430 still needs (connect-src, and default-src behind it) cannot be written by
      // reading the source, because the hosts a third-party script calls only exist at runtime.
      //
      // Reporting-Endpoints goes with it. report-to names an endpoint; without this header the
      // name resolves to nothing and the browser evaluates the policy and reports to nobody, which
      // is indistinguishable from a policy that found no violations.
      String reportOnlyPolicy = CspPolicyCommand.reportOnlyPolicy(cspNonce);
      if (reportOnlyPolicy != null) {
        response.setHeader("Content-Security-Policy-Report-Only", reportOnlyPolicy);
        response.setHeader("Reporting-Endpoints", CspPolicyCommand.reportingEndpointsHeader());
      }
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

      // Draft preview links (#419): a valid, unexpired bearer token lets an anonymous visitor
      // view this page's current draft content at its real URL, before it's reviewed or
      // published. Checked here, ahead of the draft-blocking and layout-resolution logic below,
      // so both can be bypassed for exactly this one request.
      boolean validPreviewToken = webPage != null
          && WebPagePreviewTokenRepository.findValidToken(request.getParameter("previewToken"), webPage.getId(), pagePath) != null;
      if (validPreviewToken) {
        // A preview link renders unreviewed content -- never let it leak into a search index.
        response.setHeader("X-Robots-Tag", "noindex");
      }

      if (webPage != null) {
        // Determine if this is a draft page
        if (!validPreviewToken && isDraftBlockedFromPublicAccess(webPage, userSession)) {
          LOG.error("DRAFT FOUND, no access: " + pagePath + " " + request.getRemoteAddr());
          controllerSession.clearAllWidgetData();
          response.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
        }
        // Determine if this is an archived page (issue #427)
        if (isArchivedBlockedFromPublicAccess(webPage, userSession)) {
          controllerSession.clearAllWidgetData();
          response.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
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
        // Internal pages (#1688): refuse here, ahead of the redirect below -- a gate placed after it
        // would still hand an internal page's redirect target to anyone who asked. Deliberately NOT
        // wrapped in !validPreviewToken: a preview link is handed to reviewers by design, and letting
        // it bypass a staff-only gate would turn every preview link into an anonymous handout.
        if (InternalPageAccessCommand.isBlocked(webPage, userSession)) {
          controllerSession.clearAllWidgetData();
          response.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
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
      // "pageEditMode"/"pageLayoutMode"/"hasDraft"/"widgetLibraryJson" (below) are page-level
      // request attributes read via JSP EL (main.jsp, content.jsp) and directly by widgets
      // (ItemsListWidget) throughout the rest of this request, including inside
      // WebContainerCommand.processWidgets()'s per-widget loop -- their names must stay in sync
      // with WebContainerCommand.PAGE_LEVEL_ATTRIBUTE_NAMES, which exempts them from that loop's
      // per-widget request attribute reset. A name published above the walk and left out of that
      // set is wiped by the first widget and reads as the empty string with no error anywhere
      // (issue #944); tools/check-page-level-attributes.py fails CI when the two drift apart.
      //
      // pageEditMode must be published unconditionally, like pageLayoutMode below -- leaving it
      // unset on the false path lets JSP EL's implicit page/request/session/application scope
      // search fall through to the raw session attribute read above, which can still be "true"
      // from a previously-authenticated, more-privileged user on this same HttpSession.
      request.setAttribute("pageEditMode", pageEditMode ? "true" : "false");
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
          SaveDraftLayoutCommand.saveDraftLayout(webPage, layoutJson, userSession.getUserId());
          response.getWriter().print("{\"success\":true}");
        } catch (Exception e) {
          LOG.error("saveDraftLayout failed for " + pagePath, e);
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          String msg = e.getMessage() != null ? e.getMessage().replace("\"", "'") : "Save failed";
          AuditEventCommand.record(request, userSession, AuditEventCommand.CONTENT, "page_layout.reorder",
              AuditEventCommand.FAILURE, "web_page", String.valueOf(webPage.getId()), webPage.getLink(), msg);
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
        // Governed publish workflow (#407): with webPage.review.required on, an unapproved draft
        // cannot be published directly -- the only path to live is submit -> approve via
        // WebPageReviewWidget. Mirrors ContentHtmlCommand.publishContent()'s identical gate.
        boolean webPageReviewRequired = LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required");
        if (!ContentReviewCommand.mayPublish(webPage, webPageReviewRequired)) {
          response.setContentType("application/json");
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          AuditEventCommand.record(request, userSession, AuditEventCommand.CONTENT, "content.publish",
              AuditEventCommand.FAILURE, "web_page", String.valueOf(webPage.getId()), webPage.getLink(),
              "blocked: draft not approved for release");
          response.getWriter().print(
              "{\"success\":false,\"error\":\"This page must be submitted for review and approved before it can be published\"}");
          return;
        }
        response.setContentType("application/json");
        int versionHistoryLimit = WebPageRepository.resolveVersionHistoryLimit(
            LoadSitePropertyCommand.loadByName("webPage.versionHistoryLimit"));
        WebPageRepository.publish(webPage, userSession.getUserId(), versionHistoryLimit);
        PublishEventCachePurgeHandler.onPageUpdated(webPage);
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

      // generatePreviewLink: issue a bearer token (#419) that lets an anonymous visitor view this
      // page's current draft at its real URL, without publishing it
      if ("generatePreviewLink".equals(request.getParameter("action"))
          && request.getParameter("widget") == null
          && pageLayoutMode) {
        String formToken = request.getParameter("token");
        if (!userSession.getFormToken().equals(formToken)) {
          LOG.warn("generatePreviewLink CSRF token mismatch from " + request.getRemoteAddr());
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
          response.getWriter().print("{\"success\":false,\"error\":\"No draft to preview\"}");
          return;
        }
        response.setContentType("application/json");
        try {
          WebPagePreviewToken previewToken = GeneratePreviewLinkCommand.generateFor(webPage, pagePath, userSession.getUserId());
          ObjectMapper mapper = new ObjectMapper();
          String link = buildPreviewLink(pagePath, request.getParameter("originalQuery"), previewToken.getToken());
          response.getWriter().print("{\"success\":true,\"link\":" + mapper.writeValueAsString(link)
              + ",\"expiresAt\":" + mapper.writeValueAsString(previewToken.getExpiresAt().toInstant().toString()) + "}");
        } catch (Exception e) {
          LOG.warn("generatePreviewLink failed for " + pagePath + ": " + e.getMessage());
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          String msg = e.getMessage() != null ? e.getMessage().replace("\"", "'") : "Could not create the preview link";
          response.getWriter().print("{\"success\":false,\"error\":\"" + msg + "\"}");
        }
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
          // Issue #903: pageEditMode is a generic sitewide capability with no collection scoping,
          // so confirm the current user is actually authorized for this item's collection before
          // mutating it -- otherwise any content-editor could reorder items in a private/restricted
          // collection they were never granted access to. Mirrors the ForAuthorizedUser resolution
          // already used for the page-level collection/item lookups above (~line 762/775/802).
          if (LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(item.getCollectionId(), userSession.getUserId()) == null) {
            LOG.warn("reorderCollectionItem COLLECTION NOT ALLOWED for user " + userSession.getUserId()
                + " collectionId=" + item.getCollectionId() + " itemId=" + itemId + " from " + request.getRemoteAddr());
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().print("{\"success\":false,\"error\":\"Item not found\"}");
            return;
          }
          // Issue #815: items.item_order now exists, so persist the new position (renumbering the
          // rest of the collection) instead of the previous NOT_IMPLEMENTED/501 response.
          boolean reordered = ItemRepository.reorderItem(item.getCollectionId(), itemId, newOrder);
          if (!reordered) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().print("{\"success\":false,\"error\":\"Item could not be reordered\"}");
            return;
          }
          response.setContentType("application/json");
          response.getWriter().print("{\"success\":true,\"message\":\"Item order updated\"}");
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
          // Issue #903: see reorderCollectionItem above -- confirm collection access before mutating.
          if (LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(item.getCollectionId(), userSession.getUserId()) == null) {
            LOG.warn("deactivateCollectionItem COLLECTION NOT ALLOWED for user " + userSession.getUserId()
                + " collectionId=" + item.getCollectionId() + " itemId=" + itemId + " from " + request.getRemoteAddr());
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

          // Issue #903: resolve the collection through the same ForAuthorizedUser lookup used
          // elsewhere in this file (see loadCollectionByIdForAuthorizedUser above, ~line 762) rather
          // than a raw findById -- otherwise any content-editor in pageEditMode could inject an item
          // into a private/restricted collection they were never granted access to.
          Collection collection = LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(collectionId, userSession.getUserId());
          if (collection == null) {
            LOG.warn("saveCollectionItem COLLECTION NOT ALLOWED for user " + userSession.getUserId()
                + " collectionId=" + collectionId + " from " + request.getRemoteAddr());
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().print("{\"success\":false,\"error\":\"Collection not found\"}");
            return;
          }

          Item newItem = new Item();
          newItem.setCollectionId(collectionId);
          // Issue #815: append at the end of the collection's current order rather than leaving
          // the domain model's static default, which would otherwise collide with (or sort ahead
          // of) items that have already been manually reordered.
          newItem.setItemOrder(ItemRepository.getNextItemOrder(collectionId));
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
      // Built from the raw parameters (not the parsed s/c/w/after locals below, which are scoped to the
      // try block) so the exact same summary is available to both the success and failure audit calls.
      String mutateDetails = "s=" + request.getParameter("s") + " c=" + request.getParameter("c")
          + " w=" + request.getParameter("w") + " after=" + request.getParameter("after");
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
              MutateLayoutCommand.addSection(webPage, after, request.getParameter("class"), userSession.getUserId());
              break;
            case "removeSection":
              MutateLayoutCommand.removeSection(webPage, s, userSession.getUserId());
              break;
            case "setSectionClass":
              MutateLayoutCommand.setSectionClass(webPage, s, request.getParameter("class"), userSession.getUserId());
              break;
            case "addColumn":
              MutateLayoutCommand.addColumn(webPage, s, after, request.getParameter("class"), userSession.getUserId());
              break;
            case "removeColumn":
              MutateLayoutCommand.removeColumn(webPage, s, c, userSession.getUserId());
              break;
            case "setColumnClass":
              MutateLayoutCommand.setColumnClass(webPage, s, c, request.getParameter("class"), userSession.getUserId());
              break;
            case "addWidget":
              MutateLayoutCommand.addWidget(webPage, s, c, after,
                  request.getParameter("widgetName"), request.getParameter("prefs"), userSession.getUserId());
              break;
            case "removeWidget":
              MutateLayoutCommand.removeWidget(webPage, s, c, w, userSession.getUserId());
              break;
            case "setWidgetPreferences":
              MutateLayoutCommand.setWidgetPreferences(webPage, s, c, w, request.getParameter("prefs"), userSession.getUserId());
              break;
            default:
              response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
              response.getWriter().print("{\"success\":false,\"error\":\"Unknown action\"}");
              return;
          }
          AuditEventCommand.record(request, userSession, AuditEventCommand.CONTENT, "page_layout." + mutateAction,
              AuditEventCommand.SUCCESS, "web_page", String.valueOf(webPage.getId()), webPage.getLink(),
              mutateDetails);
          response.getWriter().print("{\"success\":true}");
        } catch (Exception e) {
          LOG.warn("mutateDraftLayout '" + mutateAction + "' failed for " + pagePath + ": " + e.getMessage());
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          String msg = e.getMessage() != null ? e.getMessage().replace("\"", "'") : "Mutation failed";
          AuditEventCommand.record(request, userSession, AuditEventCommand.CONTENT, "page_layout." + mutateAction,
              AuditEventCommand.FAILURE, "web_page", String.valueOf(webPage.getId()), webPage.getLink(),
              mutateDetails + " error=" + msg);
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
        request.setAttribute("widgetSchemaJson", LoadWidgetSchemaCommand.getWidgetSchemaJson(request.getServletContext()));
      }

      // In edit mode, layout builders preview the draft layout (bypasses cache)
      boolean previewingDraft = false;
      if (pageEditMode && EditorPermissionCommand.canBuildLayout(userSession)
          && webPage != null && StringUtils.isNotBlank(webPage.getDraftPageXml())) {
        Page draftRef = WebPageXmlLayoutCommand.parseFreshDraft(webPage, pagePath);
        if (draftRef != null) {
          pageRef = draftRef;
        }
      } else if (validPreviewToken && webPage != null && StringUtils.isNotBlank(webPage.getDraftPageXml())) {
        // Draft preview links (#419): the same live-updating draft render an editor gets above,
        // for an anonymous visitor holding a valid token instead. A token that has outlived its
        // draft (published or discarded since the link was generated) simply falls back to
        // whatever pageRef already resolved to -- the current live page.
        Page draftRef = WebPageXmlLayoutCommand.parseFreshDraft(webPage, pagePath);
        if (draftRef != null) {
          pageRef = draftRef;
          previewingDraft = true;
        }
      }
      // Page-level request attribute read via JSP EL (main.jsp) -- must stay in sync with
      // WebContainerCommand.PAGE_LEVEL_ATTRIBUTE_NAMES, like pageEditMode/hasDraft above.
      request.setAttribute("previewingDraft", previewingDraft ? "true" : "false");

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

      // Publish these before any widget renders (below), not just for main.jsp/layout.jsp
      // afterward -- a widget JSP (e.g. ActivityListWidget's activity-list.jsp) that reads
      // systemPropertyMap/etc directly needs them during its own JSP turn, not just once the
      // whole page/header/footer walk is done. WebContainerCommand.PAGE_LEVEL_ATTRIBUTE_NAMES
      // must keep exempting these names from that walk's per-widget request attribute reset.
      request.setAttribute("systemPropertyMap", systemPropertyMap);
      request.setAttribute("sitePropertyMap", sitePropertyMap);
      request.setAttribute("themePropertyMap", themePropertyMap);
      request.setAttribute("socialPropertyMap", socialPropertyMap);
      request.setAttribute("socialMediaLinkList", socialMediaLinkList);
      request.setAttribute("analyticsPropertyMap", analyticsPropertyMap);
      request.setAttribute("ecommercePropertyMap", ecommercePropertyMap);

      // Allow content admins to see a page
      if (pageRef == null &&
          (userSession.hasRole("admin") ||
              userSession.hasRole("content-manager"))) {
        pageRef = WebPageXmlLayoutCommand.retrievePage("_page_content_not_ready_");
      }

      // See if the site is in setup mode (allow any user?)
      // A valid draft preview token (#419) takes precedence over the setup-mode placeholder,
      // same as it does over isDraftBlockedFromPublicAccess above -- otherwise a customer
      // building a pre-launch site (site.online=false) could never preview their own homepage
      // draft before flipping site.online to true.
      if (!validPreviewToken &&
          !userSession.hasRole("admin") &&
          !userSession.hasRole("content-manager") &&
          "false".equals(sitePropertyMap.getOrDefault("site.online", "false"))) {
        if ("/".equals(pagePath)) {
          pageRef = WebPageXmlLayoutCommand.retrievePage("_new_install_");
        } else if (!isGuestAuthPage(pagePath)) {
          // The site isn't open yet -- keep every other page behind the homepage splash. The
          // 3 guest-facing auth pages (issue #1005: /login, /register, /forgot-password) stay
          // reachable so a guest can still sign in/up before launch. /logout never reaches this
          // servlet; it's handled directly in WebRequestFilter.
          response.sendRedirect(contextPath + "/");
          return;
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
        // User must be authorized here... Issue #827: a deactivated item must not stay reachable
        // on a genuinely public item route (no role/group/capability restriction on the matched
        // page) just because the caller knows its uniqueId -- but an admin-gated route (e.g.
        // /edit/{uniqueId}, /show/*/settings) still needs to resolve it so it can be managed.
        thisItem = LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(itemUniqueId, userSession.getUserId(),
            isPubliclyUnrestrictedPage(pageRef));
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
      String canonicalUrl = computeCanonicalUrl(siteUrl, pagePath, webPage, thisItem, thisCollection);
      if (StringUtils.isNotBlank(canonicalUrl)) {
        pageRenderInfo.setCanonicalUrl(canonicalUrl);
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
      if (WebContainerCommand.processWidgets(webContainerContext, pageRef.getSections(), pageRenderInfo, coreData, contextPath, pagePath, userSession, themePropertyMap, pageLayoutMode)) {
        // The widget processor handled the response, immediately return
        return;
      }

      // Generate JSON-LD structured data for search engines and AI (issue #403). This runs after
      // processWidgets so it can see page metadata a content widget (e.g. ProductNameWidget)
      // bridged into pageRenderInfo during its own execute() -- generating it earlier would only
      // ever see the generic item/collection/webPage title & description, never a widget-specific
      // one, and real ecommerce product data (unlike an Item/Collection) is ONLY ever available
      // this way -- there's no URL routing to a specific Product for PageServlet to resolve itself.
      // processWidgets so it can see page metadata a content widget (e.g. BlogPostWidget) bridged
      // into pageRenderInfo during its own execute() -- generating it earlier would only ever see
      // the generic item/collection/webPage title & description, never a widget-specific one.
      // Computed here rather than further down where the nav uses it, because the breadcrumb
      // trail below needs the same menu the nav gets, gated the same way (issue #1795).
      boolean siteVisibleToUser = userSession.isLoggedIn()
          || "true".equals(sitePropertyMap.getOrDefault("site.online", "false"));
      if (StringUtils.isNotBlank(siteUrl) && StringUtils.isNotBlank(sitePropertyMap.get("site.name"))) {
        String jsonLd = generateJsonLdData(pageRenderInfo, siteUrl, pagePath, sitePropertyMap, thisItem, thisCollection, webPage, socialMediaLinkList,
            resolveMasterMenuTabList(siteVisibleToUser));
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
      WebContainerCommand.processWidgets(webContainerContext, requestHeader.getSections(), headerRenderInfo, coreData, contextPath, pagePath, userSession, themePropertyMap, pageLayoutMode);

      // Render the footer
      Footer footer = WebContainerLayoutCommand.retrieveFooter(request.getServletContext(), widgetLibrary);
      FooterRenderInfo footerRenderInfo = new FooterRenderInfo(footer, pagePath);
      WebContainerCommand.processWidgets(webContainerContext, footer.getSections(), footerRenderInfo, coreData, contextPath, pagePath, userSession, themePropertyMap, pageLayoutMode);

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

      // Web Page Hits -- recorded here, not at request entry, so a request that never actually
      // renders (access denied, collection/item not found, missing widget param, invalid form
      // token, or a wildcard-matched page whose item later 404s) doesn't over-count (issue #856).
      // Skip tracking for monitoring apps, and for requests that ask not to be tracked (DNT / GPC).
      if (request.getHeader("X-Monitor") == null
          && !DoNotTrackCommand.isDoNotTrack(request.getHeader("DNT"), request.getHeader("Sec-GPC"))) {
        SaveWebPageHitCommand.saveHit(request.getRemoteAddr(), request.getMethod(), pagePath, webPage, userSession);
        // Conversion funnel tracking (issue #565, phase 1) -- a no-op unless this pagePath is the
        // site's admin-configured contact-form page
        FunnelEventCommand.recordContactFormPageView(pagePath, userSession != null ? userSession.getSessionId() : null);
      }

      // Determine global items
      // Guest-facing auth pages (issue #1005): a guest hitting these while the site is still
      // offline (e.g. before initial setup is finished) otherwise gets a bare form with no
      // branding at all, since the header is the only thing on /login that carries the logo.
      // siteVisibleToUser (not isGuestAuthPage alone) still gates the REAL nav/footer-link data:
      // MenuTab/MenuItem carry no per-visitor visibility of their own (roleIdList is never read
      // back from the DB -- see MenuItemRepository), so site.online was the only thing keeping
      // an anonymous guest from seeing the full, real site structure before launch. Review of
      // #1005 caught an earlier version of this fix that showed the real menu to guests too;
      // MainMenuWidget and LlmsTxtServlet gate this same data the same way. This version shows
      // just the header/branding shell to a guest on these 3 pages, with an empty nav underneath.
      boolean isGuestAuthPage = isGuestAuthPage(pagePath);
      if (siteVisibleToUser || isGuestAuthPage) {
        // @todo determine if this is needed still (it is, but until all JSP layouts are removed?)
        // Load the main menu
        request.setAttribute(SHOW_MAIN_MENU, "true");
        List<MenuTab> menuTabList = resolveMasterMenuTabList(siteVisibleToUser);
        request.setAttribute(MASTER_MENU_TAB_LIST, menuTabList);

        // @note this is needed globally
        if (siteVisibleToUser && !"container".equals(request.getSession().getAttribute(SessionConstants.X_VIEW_MODE))) {
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

  /** True for the guest-facing auth pages that need header/branding even while site.online is false (issue #1005). */
  static boolean isGuestAuthPage(String pagePath) {
    return "/login".equals(pagePath) || "/register".equals(pagePath) || "/forgot-password".equals(pagePath);
  }

  /**
   * Must return a real ArrayList (never Collections.emptyList()) -- layout.jsp's
   * {@code <jsp:useBean id="masterMenuTabList" class="java.util.ArrayList" scope="request"/>} casts
   * the existing request attribute to ArrayList instead of instantiating a new one, so any other
   * List implementation throws a ClassCastException at render time.
   */
  static List<MenuTab> resolveMasterMenuTabList(boolean siteVisibleToUser) {
    return siteVisibleToUser ? LoadMenuTabsCommand.loadActiveIncludeMenuItemList() : new ArrayList<>();
  }

  /**
   * Without a menu, for callers that have none. The menu only feeds the breadcrumb fallback in
   * {@link #computeMenuBreadcrumbList}, so omitting it yields exactly the output this method
   * produced before issue #1795 -- which is what the existing tests assert.
   */
  static String generateJsonLdData(PageRenderInfo pageRenderInfo, String siteUrl, String pagePath,
                                    Map<String, String> sitePropertyMap,
                                    Item item, Collection collection, WebPage webPage,
                                    List<SocialMediaLink> socialMediaLinkList) {
    return generateJsonLdData(pageRenderInfo, siteUrl, pagePath, sitePropertyMap, item, collection,
        webPage, socialMediaLinkList, null);
  }

  static String generateJsonLdData(PageRenderInfo pageRenderInfo, String siteUrl, String pagePath,
                                    Map<String, String> sitePropertyMap,
                                    Item item, Collection collection, WebPage webPage,
                                    List<SocialMediaLink> socialMediaLinkList,
                                    List<MenuTab> menuTabList) {
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

        // The same site.description that already feeds the meta description, the Open Graph tags,
        // llms.txt and the feeds -- it was simply never put on the Organization node (issue #1795).
        // Jackson serialises the graph and escapeForInlineScript handles the script context, so the
        // admin-entered value goes in as-is rather than being escaped twice.
        String siteDescription = sitePropertyMap.get("site.description");
        if (StringUtils.isNotBlank(siteDescription)) {
          organization.put("description", siteDescription);
        }

        // The postal address and founding year an administrator can now enter on Site Settings
        // (issue #1795). Every part is optional and a blank one is left out rather than emitted
        // empty, so a site that fills in only a city and country says exactly that much and no
        // more. When nothing is filled in there is no address key at all, which is why an existing
        // site's output is byte-identical until someone sets one.
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("@type", "PostalAddress");
        putIfNotBlank(address, "streetAddress", sitePropertyMap.get("site.address.street"));
        putIfNotBlank(address, "addressLocality", sitePropertyMap.get("site.address.city"));
        putIfNotBlank(address, "addressRegion", sitePropertyMap.get("site.address.state"));
        putIfNotBlank(address, "postalCode", sitePropertyMap.get("site.address.postalCode"));
        putIfNotBlank(address, "addressCountry", sitePropertyMap.get("site.address.country"));
        // Size 1 is the @type alone -- an address node saying only that it is an address is worse
        // than none, so it is not attached.
        if (address.size() > 1) {
          organization.put("address", address);
        }

        // schema.org foundingDate is an ISO 8601 date, and a bare year is one. The field is
        // labelled "Year founded" to steer that; a value that is not a date is passed through
        // rather than guessed at, the same as every other admin-entered value in this graph.
        putIfNotBlank(organization, "foundingDate", sitePropertyMap.get("site.founded"));

        String siteLogo = sitePropertyMap.get("site.image");
        if (StringUtils.isNotBlank(siteLogo)) {
          if (siteLogo.startsWith("/")) {
            organization.put("logo", siteUrl + siteLogo);
          } else {
            organization.put("logo", siteLogo);
          }
        }

        // sameAs links this Organization to its social profiles (issue #403). Passed in rather
        // than queried here -- the caller already loaded this same list once for the page's own
        // footer/socialMediaLinks-widget rendering (PageServlet.service()); re-querying it a
        // second time per request was a redundant, uncached DB round trip on every page view.
        if (socialMediaLinkList != null && !socialMediaLinkList.isEmpty()) {
          List<String> sameAs = new ArrayList<>();
          for (SocialMediaLink socialMediaLink : socialMediaLinkList) {
            if (StringUtils.isNotBlank(socialMediaLink.getUrl())) {
              sameAs.add(socialMediaLink.getUrl());
            }
          }
          if (!sameAs.isEmpty()) {
            organization.put("sameAs", sameAs);
          }
        }

        graph.add(organization);
      }

      // Add WebPage schema for all pages
      Map<String, Object> webPageSchema = new LinkedHashMap<>();
      webPageSchema.put("@type", "WebPage");
      if (StringUtils.isNotBlank(pageRenderInfo.getPageUrl())) {
        webPageSchema.put("url", pageRenderInfo.getPageUrl());
      }
      if (StringUtils.isNotBlank(pageRenderInfo.getTitle())) {
        webPageSchema.put("name", pageRenderInfo.getTitle());
      }
      if (StringUtils.isNotBlank(pageRenderInfo.getDescription())) {
        webPageSchema.put("description", pageRenderInfo.getDescription());
      }
      webPageSchema.put("isPartOf", Collections.singletonMap("@id", siteUrl + "#organization"));

      // Add image if available
      if (StringUtils.isNotBlank(pageRenderInfo.getImageUrl())) {
        String imageUrl = pageRenderInfo.getImageUrl();
        if (imageUrl.startsWith("/")) {
          imageUrl = siteUrl + imageUrl;
        }
        webPageSchema.put("image", imageUrl);
      }

      // dateModified/datePublished are freshness signals AI answer engines weigh for citation
      // (issue #403). datePublished prefers publishAt (the page's actual go-live date, which can
      // differ from when the row was first created via scheduled publishing) over created.
      if (webPage != null) {
        if (webPage.getModified() != null) {
          webPageSchema.put("dateModified", webPage.getModified().toInstant().toString());
        }
        Timestamp publishedDate = webPage.getPublishAt() != null ? webPage.getPublishAt() : webPage.getCreated();
        if (publishedDate != null) {
          webPageSchema.put("datePublished", publishedDate.toInstant().toString());
        }
      }

      graph.add(webPageSchema);

      // Add Article schema for blog post pages (issue #403)
      Map<String, Object> article = computeArticleSchema(pageRenderInfo, siteUrl);
      if (article != null) {
        graph.add(article);
      }

      // Add BreadcrumbList schema for pages more than one level deep (issue #403)
      List<Map<String, Object>> breadcrumbItemList = computeBreadcrumbList(siteUrl, pagePath, item, collection);
      if (breadcrumbItemList == null || breadcrumbItemList.isEmpty()) {
        // A flat URL is not a shallow page. Since issue #1728 the navigation carries three
        // levels, so a product page sits two below a tab while its URL has one segment -- the
        // URL-segment trail above cannot see that, and returned nothing for every ordinary page
        // on the site. The menu knows the position; use it when the URL does not (issue #1795).
        breadcrumbItemList = computeMenuBreadcrumbList(siteUrl, pagePath, menuTabList);
      }
      if (breadcrumbItemList != null && !breadcrumbItemList.isEmpty()) {
        Map<String, Object> breadcrumbList = new LinkedHashMap<>();
        breadcrumbList.put("@type", "BreadcrumbList");
        breadcrumbList.put("itemListElement", breadcrumbItemList);
        graph.add(breadcrumbList);
      }

      // Add FAQPage schema if this page has a FaqWidget (issue #416)
      Map<String, Object> faqPage = computeFaqSchema(pageRenderInfo);
      if (faqPage != null) {
        graph.add(faqPage);
      }

      // Add Product schema for a real ecommerce product page (issue #403); bridged from
      // pageRenderInfo the same way Article is, since a product's identity is never resolvable
      // from the URL the way an Item/Collection's is (see computeProductSchema)
      Map<String, Object> product = computeProductSchema(pageRenderInfo, siteUrl);
      if (product != null) {
        graph.add(product);
      }

      // Add Event schema for a single calendar event page (issue #1181); bridged like Product,
      // since a calendar event is not resolvable from the URL by PageServlet itself
      Map<String, Object> event = computeEventSchema(pageRenderInfo, siteUrl);
      if (event != null) {
        graph.add(event);
      }

      jsonLd.put("@graph", graph);
      return escapeForInlineScript(mapper.writeValueAsString(jsonLd));
    } catch (Exception e) {
      LOG.warn("Error generating JSON-LD data: " + e.getMessage());
      return null;
    }
  }

  /**
   * Builds the Article schema for a blog post page (issue #403). Gated on articleHeadline since
   * that's only set by a content widget (BlogPostWidget) for a post that's actually published --
   * every other page type leaves it blank, so this doubles as the "is this a blog post" check.
   */
  static Map<String, Object> computeArticleSchema(PageRenderInfo pageRenderInfo, String siteUrl) {
    if (StringUtils.isBlank(pageRenderInfo.getArticleHeadline())) {
      return null;
    }
    Map<String, Object> article = new LinkedHashMap<>();
    // NewsArticle rather than the generic Article parent (issue #1366): this schema is only built
    // for blog posts, and Google's news surfaces look for the specific subtype. BlogPosting is the
    // other candidate -- if a site ever runs a blog that is not news (engineering notes, say), the
    // right answer is to derive this per blog rather than to fall back to the generic parent.
    article.put("@type", "NewsArticle");
    article.put("headline", pageRenderInfo.getArticleHeadline());
    if (pageRenderInfo.getArticlePublishedDate() != null) {
      article.put("datePublished", pageRenderInfo.getArticlePublishedDate().toInstant().toString());
    }
    if (pageRenderInfo.getArticleModifiedDate() != null) {
      article.put("dateModified", pageRenderInfo.getArticleModifiedDate().toInstant().toString());
    }
    if (StringUtils.isNotBlank(pageRenderInfo.getArticleAuthorName())) {
      Map<String, Object> author = new LinkedHashMap<>();
      author.put("@type", "Person");
      author.put("name", pageRenderInfo.getArticleAuthorName());
      article.put("author", author);
    }
    // Google's Article guidance treats an image as strongly recommended -- without one a post is
    // unlikely to qualify for rich results however correct the rest is. Absolutised the same way
    // the WebPage node above does it, since a relative path is not resolvable by a consumer that
    // only has the JSON-LD.
    String imageUrl = pageRenderInfo.getImageUrl();
    if (StringUtils.isNotBlank(imageUrl) && StringUtils.isNotBlank(siteUrl)) {
      article.put("image", imageUrl.startsWith("/") ? siteUrl + imageUrl : imageUrl);
    }
    // Referenced by @id rather than repeating the object -- the Organization node is already in
    // the graph, and duplicating it would let the two copies drift.
    if (StringUtils.isNotBlank(siteUrl)) {
      article.put("publisher", Collections.singletonMap("@id", siteUrl + "#organization"));
    }
    return article;
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
   * Builds the Event schema for a single calendar event page (issue #1181). Gated on the bridged
   * CalendarEvent, which CalendarEventDetailsWidget only sets after its own calendar-enabled
   * visibility check -- so a non-null event here is already one the visitor can read. Like Product,
   * the record is bridged rather than resolved here: /calendar-event{/event-unique-id} is a
   * wildcard page and only the widget performs the uniqueId lookup.
   */
  static Map<String, Object> computeEventSchema(PageRenderInfo pageRenderInfo, String siteUrl) {
    CalendarEvent calendarEvent = pageRenderInfo.getCalendarEvent();
    if (calendarEvent == null || StringUtils.isBlank(calendarEvent.getTitle())) {
      return null;
    }

    Map<String, Object> event = new LinkedHashMap<>();
    event.put("@type", "Event");
    event.put("name", calendarEvent.getTitle());

    if (StringUtils.isNotBlank(siteUrl) && StringUtils.isNotBlank(calendarEvent.getUniqueId())) {
      event.put("url", siteUrl + "/calendar-event/" + calendarEvent.getUniqueId());
    }

    // startDate is required by Google for Event rich results; endDate is optional but strongly
    // recommended. An all-day event is a calendar date rather than an instant, so it's emitted as
    // a bare yyyy-MM-dd resolved in the site's timezone -- rendering it as a UTC instant would
    // shift the day for any site west of Greenwich.
    String startDate = formatEventDate(calendarEvent.getStartDate(), calendarEvent.getAllDay());
    if (startDate != null) {
      event.put("startDate", startDate);
    }
    String endDate = formatEventDate(calendarEvent.getEndDate(), calendarEvent.getAllDay());
    if (endDate != null) {
      event.put("endDate", endDate);
    }

    // Prefer the curated summary; fall back to the body with markup stripped, since JSON-LD
    // description is plain text and raw HTML there is ignored at best
    String description = StringUtils.trimToNull(calendarEvent.getSummary());
    if (description == null && StringUtils.isNotBlank(calendarEvent.getBody())) {
      description = StringUtils.trimToNull(HtmlCommand.text(calendarEvent.getBody()));
    }
    if (description != null) {
      event.put("description", description);
    }

    if (StringUtils.isNotBlank(calendarEvent.getImageUrl())) {
      String imageUrl = calendarEvent.getImageUrl();
      if (imageUrl.startsWith("/") && StringUtils.isNotBlank(siteUrl)) {
        imageUrl = siteUrl + imageUrl;
      }
      event.put("image", imageUrl);
    }

    Map<String, Object> location = computeEventLocation(calendarEvent);
    if (location != null) {
      event.put("location", location);
      // Only claim an attendance mode when there's a real place backing it; asserting "offline"
      // for an event with no location at all would be inventing data
      event.put("eventAttendanceMode", "https://schema.org/OfflineEventAttendanceMode");
    }

    event.put("eventStatus", "https://schema.org/EventScheduled");

    return event;
  }

  /**
   * Formats a calendar event date for schema.org: a bare calendar date for an all-day event
   * (resolved in the site timezone) and a full ISO-8601 instant otherwise. Returns null for a
   * missing date so the caller can omit the property rather than emit an empty one.
   */
  static String formatEventDate(Timestamp timestamp, boolean allDay) {
    if (timestamp == null) {
      return null;
    }
    if (allDay) {
      return timestamp.toInstant().atZone(FormatDateCommand.getSiteZoneId()).toLocalDate().toString();
    }
    return timestamp.toInstant().toString();
  }

  /**
   * Builds the Place sub-object for an Event (issue #1181). Returns null when the record carries
   * neither a location name nor any address line, since a Place with no identifying detail adds
   * nothing and Google treats an empty location as a validation error.
   */
  static Map<String, Object> computeEventLocation(CalendarEvent calendarEvent) {
    boolean hasAddress = StringUtils.isNotBlank(calendarEvent.getStreet())
        || StringUtils.isNotBlank(calendarEvent.getCity())
        || StringUtils.isNotBlank(calendarEvent.getState())
        || StringUtils.isNotBlank(calendarEvent.getPostalCode())
        || StringUtils.isNotBlank(calendarEvent.getCountry());
    if (StringUtils.isBlank(calendarEvent.getLocation()) && !hasAddress) {
      return null;
    }

    Map<String, Object> place = new LinkedHashMap<>();
    place.put("@type", "Place");
    if (StringUtils.isNotBlank(calendarEvent.getLocation())) {
      place.put("name", calendarEvent.getLocation());
    }

    if (hasAddress) {
      Map<String, Object> address = new LinkedHashMap<>();
      address.put("@type", "PostalAddress");
      if (StringUtils.isNotBlank(calendarEvent.getStreet())) {
        address.put("streetAddress", calendarEvent.getStreet());
      }
      if (StringUtils.isNotBlank(calendarEvent.getCity())) {
        address.put("addressLocality", calendarEvent.getCity());
      }
      if (StringUtils.isNotBlank(calendarEvent.getState())) {
        address.put("addressRegion", calendarEvent.getState());
      }
      if (StringUtils.isNotBlank(calendarEvent.getPostalCode())) {
        address.put("postalCode", calendarEvent.getPostalCode());
      }
      if (StringUtils.isNotBlank(calendarEvent.getCountry())) {
        address.put("addressCountry", calendarEvent.getCountry());
      }
      place.put("address", address);
    }

    // 0.0/0.0 is the model's default for "never geocoded", not a real point in the Atlantic
    if (calendarEvent.getLatitude() != 0.0 || calendarEvent.getLongitude() != 0.0) {
      Map<String, Object> geo = new LinkedHashMap<>();
      geo.put("@type", "GeoCoordinates");
      geo.put("latitude", calendarEvent.getLatitude());
      geo.put("longitude", calendarEvent.getLongitude());
      place.put("geo", geo);
    }

    return place;
  }

  /**
   * Builds the BreadcrumbList itemListElement array for pages at a URL depth of two or more
   * (issue #403); shallower pages return null since a single-level trail is redundant with the
   * site nav. Each ancestor segment's name is resolved the same way the page itself would be
   * resolved (LoadWebPageCommand, including wildcard/template pages) so a breadcrumb never shows
   * a path segment that the app wouldn't actually route to; a segment with no matching page falls
   * back to a humanized version of the URL segment rather than leaving a gap in the trail.
   */
  static List<Map<String, Object>> computeBreadcrumbList(String siteUrl, String pagePath, Item item, Collection collection) {
    if (StringUtils.isBlank(siteUrl) || StringUtils.isBlank(pagePath)) {
      return null;
    }
    List<String> segments = new ArrayList<>();
    for (String segment : pagePath.split("/")) {
      if (StringUtils.isNotBlank(segment)) {
        segments.add(segment);
      }
    }
    if (segments.size() < 2) {
      return null;
    }

    List<Map<String, Object>> itemListElement = new ArrayList<>();
    itemListElement.add(breadcrumbListItem(1, "Home", siteUrl));

    StringBuilder pathSoFar = new StringBuilder();
    for (int i = 0; i < segments.size(); i++) {
      String segment = segments.get(i);
      pathSoFar.append('/').append(segment);
      boolean isLeaf = (i == segments.size() - 1);

      String name = null;
      if (isLeaf && item != null && StringUtils.isNotBlank(item.getName())) {
        name = item.getName();
      } else if (collection != null && segment.equalsIgnoreCase(collection.getUniqueId())) {
        // The collection's own segment, whether it's the leaf (collection listing page) or an
        // ancestor of the leaf (an item detail page nested under it)
        name = collection.getName();
      }
      if (StringUtils.isBlank(name)) {
        WebPage segmentPage = LoadWebPageCommand.loadByLink(pathSoFar.toString());
        if (segmentPage != null && StringUtils.isNotBlank(segmentPage.getTitle())) {
          name = segmentPage.getTitle();
        }
      }
      if (StringUtils.isBlank(name)) {
        name = humanizeUrlSegment(segment);
      }

      itemListElement.add(breadcrumbListItem(i + 2, name, siteUrl + pathSoFar));
    }
    return itemListElement;
  }

  /**
   * Builds the BreadcrumbList trail from the navigation menu, for a page whose URL cannot supply one
   * (issue #1795).
   *
   * <p>{@link #computeBreadcrumbList} derives the trail from URL path segments and returns null below
   * two of them. That held while the menu was two levels and a flat URL usually meant a shallow page.
   * Since issue #1728 the navigation carries three, so a page can sit two levels below a tab and
   * still have a one-segment URL -- and every ordinary page on a site therefore emitted no breadcrumb
   * at all.
   *
   * <p>Position in the menu is the trail: tab, then item, then sub-item. A page that IS a tab gets
   * none, matching the existing rule that a single-level trail is redundant with the nav itself.
   *
   * <p>The first match wins. A page linked from more than one place in the menu has no one true
   * trail, and picking the first is both stable and the same answer the nav's own highlighting gives.
   */
  static List<Map<String, Object>> computeMenuBreadcrumbList(String siteUrl, String pagePath,
      List<MenuTab> menuTabList) {
    if (StringUtils.isBlank(siteUrl) || StringUtils.isBlank(pagePath) || menuTabList == null) {
      return null;
    }
    for (MenuTab menuTab : menuTabList) {
      if (menuTab.getMenuItemList() == null) {
        continue;
      }
      for (MenuItem menuItem : menuTab.getMenuItemList()) {
        if (pagePath.equals(menuItem.getLink())) {
          return menuTrail(siteUrl, menuTab, menuItem, null);
        }
        if (menuItem.getMenuItemList() == null) {
          continue;
        }
        for (MenuItem subMenuItem : menuItem.getMenuItemList()) {
          if (pagePath.equals(subMenuItem.getLink())) {
            return menuTrail(siteUrl, menuTab, menuItem, subMenuItem);
          }
        }
      }
    }
    return null;
  }

  /**
   * Home, then each ancestor that is a distinct destination, then the page. A tab whose link is the
   * same page as the item beneath it is skipped rather than repeated -- a trail that lists the same
   * URL twice is worse than a shorter one.
   */
  private static List<Map<String, Object>> menuTrail(String siteUrl, MenuTab menuTab, MenuItem menuItem,
      MenuItem subMenuItem) {
    List<Map<String, Object>> itemListElement = new ArrayList<>();
    itemListElement.add(breadcrumbListItem(1, "Home", siteUrl));
    int position = 2;
    String lastLink = null;
    if (StringUtils.isNotBlank(menuTab.getLink()) && !"/".equals(menuTab.getLink())) {
      itemListElement.add(breadcrumbListItem(position++, menuTab.getName(), siteUrl + menuTab.getLink()));
      lastLink = menuTab.getLink();
    }
    if (StringUtils.isNotBlank(menuItem.getLink()) && !menuItem.getLink().equals(lastLink)) {
      itemListElement.add(breadcrumbListItem(position++, menuItem.getName(), siteUrl + menuItem.getLink()));
      lastLink = menuItem.getLink();
    }
    if (subMenuItem != null && StringUtils.isNotBlank(subMenuItem.getLink())
        && !subMenuItem.getLink().equals(lastLink)) {
      itemListElement.add(breadcrumbListItem(position, subMenuItem.getName(), siteUrl + subMenuItem.getLink()));
    }
    // Home plus one is a single-level trail, which computeBreadcrumbList already treats as redundant
    // with the nav; emitting it here would contradict that for no gain.
    return itemListElement.size() >= 3 ? itemListElement : null;
  }

  /** Puts the value only when it has one, so an unset setting leaves the key off the node entirely. */
  private static void putIfNotBlank(Map<String, Object> target, String key, String value) {
    if (StringUtils.isNotBlank(value)) {
      target.put(key, value);
    }
  }

  private static Map<String, Object> breadcrumbListItem(int position, String name, String url) {
    Map<String, Object> listItem = new LinkedHashMap<>();
    listItem.put("@type", "ListItem");
    listItem.put("position", position);
    listItem.put("name", name);
    listItem.put("item", url);
    return listItem;
  }

  /**
   * Builds the FAQPage schema for a page with one or more FaqWidgets (issue #416). Uses
   * FaqQuestion's pre-stripped answerText, not the widget's own rendered HTML, since Google's FAQ
   * rich result requires the acceptedAnswer text to contain no markup.
   */
  static Map<String, Object> computeFaqSchema(PageRenderInfo pageRenderInfo) {
    List<FaqQuestion> faqQuestionList = pageRenderInfo.getFaqQuestions();
    if (faqQuestionList == null || faqQuestionList.isEmpty()) {
      return null;
    }
    List<Map<String, Object>> mainEntity = new ArrayList<>();
    for (FaqQuestion faqQuestion : faqQuestionList) {
      Map<String, Object> question = new LinkedHashMap<>();
      question.put("@type", "Question");
      question.put("name", faqQuestion.getQuestion());
      Map<String, Object> acceptedAnswer = new LinkedHashMap<>();
      acceptedAnswer.put("@type", "Answer");
      acceptedAnswer.put("text", faqQuestion.getAnswerText());
      question.put("acceptedAnswer", acceptedAnswer);
      mainEntity.add(question);
    }
    Map<String, Object> faqPage = new LinkedHashMap<>();
    faqPage.put("@type", "FAQPage");
    faqPage.put("mainEntity", mainEntity);
    return faqPage;
  }

  /**
   * Turns a URL segment like "getting-started" into "Getting Started" for use as a breadcrumb
   * label when no page title is available to describe that part of the path.
   */
  static String humanizeUrlSegment(String segment) {
    String decoded;
    try {
      decoded = java.net.URLDecoder.decode(segment, java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      decoded = segment;
    }
    String[] words = decoded.replace('-', ' ').replace('_', ' ').split(" ");
    StringBuilder result = new StringBuilder();
    for (String word : words) {
      if (word.isEmpty()) {
        continue;
      }
      if (result.length() > 0) {
        result.append(' ');
      }
      result.append(Character.toUpperCase(word.charAt(0)));
      if (word.length() > 1) {
        result.append(word.substring(1));
      }
    }
    return result.length() > 0 ? result.toString() : segment;
  }

  /**
   * Jackson's JSON escaping only guarantees syntactically valid JSON (quotes, backslashes,
   * control characters) -- it has no notion of the surrounding HTML, so a value containing
   * {@code "</script>"} passes straight through. The browser's HTML parser looks for that literal
   * byte sequence regardless of JSON string context, so an unescaped {@code "</script>"} inside
   * e.g. a product name closes the tag early and lets an attacker-controlled payload execute.
   * Escaping every '<', '>' and '&' to its JSON \\uXXXX form (valid inside a JSON string, and
   * decodes back to the original character on parse) neutralizes that and any other HTML/comment
   * breakout, without changing the parsed JSON-LD content.
   */
  static String escapeForInlineScript(String json) {
    if (json == null) {
      return null;
    }
    return json.replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
  }

  /**
   * Computes the canonical URL for a page response (issue #401), or null when there's nothing to
   * canonicalize (blank site.url, or no page-identity source matched). pagePath is always safe to
   * append as-is: it comes from request.getRequestURI(), which never carries the query string, so
   * this can't reflect attacker-controlled query parameters into the tag. A wildcard/dynamic-page
   * match (see LoadWebPageCommand#loadByLink) returns the template's own link (e.g. "/news/*"),
   * not a real URL, so that case is excluded in favor of the actual pagePath.
   */
  static String computeCanonicalUrl(String siteUrl, String pagePath, WebPage webPage, Item item, Collection collection) {
    if (StringUtils.isBlank(siteUrl)) {
      return null;
    }
    if (item != null && collection != null) {
      return siteUrl + "/items/" + collection.getUniqueId() + "/" + item.getUniqueId();
    }
    if (collection != null) {
      return siteUrl + "/items/" + collection.getUniqueId();
    }
    if (webPage != null && StringUtils.isNotBlank(webPage.getLink()) && !webPage.getLink().endsWith("/*")) {
      return siteUrl + webPage.getLink();
    }
    if (StringUtils.isNotBlank(pagePath)) {
      return siteUrl + pagePath;
    }
    return null;
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

  /**
   * Builds a shareable preview link (#419) from the page path plus the caller's original query
   * string (already percent-encoded by the client's own URLSearchParams), so a page addressed by a
   * query parameter (e.g. {@code ?collectionId=5}) previews the same content the editor was
   * looking at, not just the bare path. Any {@code previewToken} pair already present in {@code
   * originalQuery} is dropped so the caller cannot smuggle in a second, conflicting token value.
   */
  static String buildPreviewLink(String pagePath, String originalQuery, String token) {
    StringBuilder link = new StringBuilder(pagePath).append('?');
    if (StringUtils.isNotBlank(originalQuery)) {
      for (String pair : originalQuery.split("&")) {
        if (pair.isEmpty()) {
          continue;
        }
        String key = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
        if ("previewToken".equals(key)) {
          continue;
        }
        link.append(pair).append('&');
      }
    }
    return link.append("previewToken=").append(token).toString();
  }

  /**
   * A pending draft (draftPageXml) must not take an already-published page offline for the
   * public: retrievePageForRequest() below always renders from pageXml, and draftPageXml is only
   * ever substituted in for a layout builder previewing in edit mode (see the pageEditMode block
   * further down) -- so a non-blank pageXml means there is still valid, unaffected published
   * content to serve. Only a page that has never been published (pageXml blank) should 404 here
   * for a non-editor while draft is true.
   */
  static boolean isDraftBlockedFromPublicAccess(WebPage webPage, UserSession userSession) {
    if (!webPage.getDraft() || StringUtils.isNotBlank(webPage.getPageXml())) {
      return false;
    }
    return !userSession.hasRole("admin") && !userSession.hasRole("content-manager");
  }

  /**
   * Issue #427: an archived page must actually come offline for the public, mirroring the
   * draft-blocking check above -- admins/content-managers can still preview it (e.g. to confirm
   * it's the right page before restoring), but everyone else gets the same 404 a deleted page
   * would give.
   */
  static boolean isArchivedBlockedFromPublicAccess(WebPage webPage, UserSession userSession) {
    if (webPage.getArchived() == null) {
      return false;
    }
    return !userSession.hasRole("admin") && !userSession.hasRole("content-manager");
  }

  /**
   * Issue #827: a matched page with no role/group/capability restriction at all is reachable by
   * any guest (see WebComponentCommand.allowsUser -- an unrestricted page always passes), so it's
   * a genuinely public route (e.g. an item detail page like /show/{uniqueId}) and must not still
   * resolve a deactivated item. A page gated by any of the three is an admin/management route
   * (e.g. /edit/{uniqueId}, /show/*&#47;settings) and must keep resolving one so it can still be
   * managed once deactivated.
   */
  static boolean isPubliclyUnrestrictedPage(Page pageRef) {
    return pageRef.getRoles().isEmpty() && pageRef.getGroups().isEmpty() && pageRef.getCapabilities().isEmpty();
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
