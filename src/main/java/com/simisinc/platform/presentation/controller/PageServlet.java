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
    // 0, not 1: the legacy auditor this header enables has itself been a source of
    // information-disclosure bugs, and modern browsers have removed it outright. Where it is still
    // honoured, 0 turns it off and leaves XSS defence to the nonce-based CSP built below, which is
    // the real protection. ContentWidget used to set 0 on blocks containing <script>/<iframe>;
    // those overrides never won because this ran first, and are removed with this change.
    response.setHeader("X-XSS-Protection", "0");
    response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    // Disclaim capabilities the platform never uses, so neither author-supplied HTML nor a
    // third-party embed (YouTube, Vimeo, the careers iframe) can request them: an embed cannot ask
    // for a capability the top-level document has already given up. Each was verified unused
    // before being disclaimed -- no navigator.geolocation (the Leaflet map JSPs ship no locate
    // control), no getUserMedia, no navigator.usb, and no browser Payment Request API. The
    // *PaymentRequest types under application/ecommerce are the server-side Square SDK, not the
    // browser API; the Stripe/Square card fields tokenize in-page and post the token back here.
    response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
    // same-origin severs window.opener for cross-origin popups, which closes tabnabbing against
    // target="_blank" links -- and those can appear in author-supplied content. Verified rather
    // than assumed: nothing in the checkout JSPs opens a popup, the payment SDKs tokenize in-page
    // and post same-origin, and the platform's only window.open calls (the two calendar views)
    // target same-origin URLs, which this does not sever. If a payment SDK ever needs an
    // opener-bearing cross-origin popup, same-origin-allow-popups is the weaker fallback.
    response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
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
      // api.weather.gov is the third of that kind. WeatherWidget renders the National Weather
      // Service's own forecast icons as <img> elements straight from that host, so without it
      // the widget half-works: temperatures and conditions render, every icon becomes a broken
      // placeholder, and nothing errors anywhere a developer would look. It went unnoticed
      // because curl fetches the icon URL happily -- only a browser enforces CSP (issue #1805).
      // Unlike the tile server below it, this is a fixed government host with nothing to
      // configure, so it is a constant rather than a lookup.
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
              + "img-src 'self' data: https://img.youtube.com https://i.vimeocdn.com "
              + "https://api.weather.gov"
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
      // Where branded assets (favicon, apple-touch-icon, the logo variants) may be overridden.
      // Null when there is nowhere real to look, which is what stops the layouts probing a path
      // that cannot answer -- see resolveBrandedAssetContext.
      request.setAttribute("brandedAssetContext",
          resolveBrandedAssetContext(systemPropertyMap.get("system.www.context")));
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
        String jsonLd = StructuredDataCommand.generateJsonLdData(pageRenderInfo, siteUrl, pagePath, sitePropertyMap, thisItem, thisCollection, webPage, socialMediaLinkList,
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

  /** The seeded value of system.www.context, which nothing in this codebase serves. */
  static final String UNSERVED_ASSET_CONTEXT = "/web-content";

  /**
   * Where to look for operator-supplied branded assets, or null when there is nowhere to look.
   *
   * <p>The layouts prefer an operator's own favicon, apple-touch-icon and logo variants over the
   * bundled ones, discovering them by probing with an {@code Image()} before swapping. That is
   * worth a request only when the probe can succeed.
   *
   * <p>{@link #UNSERVED_ASSET_CONTEXT} is the seeded default, and no servlet mapping, static
   * resource or proxy in this codebase serves it -- so probing it 404s on every page load, for
   * every install that never configured a real asset host. Eleven probes fire across the header,
   * footer and checkout layouts, so this is several failed requests per page, not one. Treating
   * the shipped default as "not configured" is what it has always meant in practice.
   *
   * <p>Consequence worth stating: a deployment that added its own proxy serving exactly
   * {@code /web-content} stops being probed. Such a deployment should point this property at the
   * host actually serving those assets, which is what the property is for; leaving it on a value
   * the platform seeds and never serves cannot be distinguished from never having set it.
   */
  static String resolveBrandedAssetContext(String configured) {
    if (configured == null) {
      return null;
    }
    String trimmed = configured.trim();
    if (trimmed.isEmpty() || UNSERVED_ASSET_CONTEXT.equals(trimmed)) {
      return null;
    }
    return trimmed;
  }
}
