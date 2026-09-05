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

import static com.simisinc.platform.presentation.controller.UserSession.WEB_SOURCE;
import static jakarta.servlet.http.HttpServletResponse.SC_MOVED_PERMANENTLY;
import static jakarta.servlet.http.HttpServletResponse.SC_MOVED_TEMPORARILY;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.Enumeration;
import java.util.Map;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.jsp.jstl.core.Config;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hc.core5.net.InetAddressUtils;

import com.simisinc.platform.application.DoNotTrackCommand;
import com.simisinc.platform.application.CreateSessionCommand;
import com.simisinc.platform.application.HealthCommand;
import com.simisinc.platform.application.DailyVisitorHashCommand;
import com.simisinc.platform.application.LoadVisitorCommand;
import com.simisinc.platform.application.SaveSessionCommand;
import com.simisinc.platform.application.SaveVisitorCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.BlockedIPListCommand;
import com.simisinc.platform.application.cms.FormatDateCommand;
import com.simisinc.platform.application.cms.HostnameCommand;
import com.simisinc.platform.application.cms.LoadBlockedIPListCommand;
import com.simisinc.platform.application.cms.LoadRedirectsCommand;
import com.simisinc.platform.application.cms.LoadWebRedirectCommand;
import com.simisinc.platform.application.ecommerce.CartCommand;
import com.simisinc.platform.application.ecommerce.LoadCartCommand;
import com.simisinc.platform.application.ecommerce.PricingRuleCommand;
import com.simisinc.platform.application.login.AuthenticateLoginCommand;
import com.simisinc.platform.application.login.LogoutCommand;
import com.simisinc.platform.application.login.BreakGlassAlertCommand;
import com.simisinc.platform.application.login.MfaEnforcementCommand;
import com.simisinc.platform.application.oauth.OAuthLogoutCommand;
import com.simisinc.platform.application.oauth.OAuthRequestCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.Visitor;
import com.simisinc.platform.domain.model.cms.WebRedirect;
import com.simisinc.platform.domain.model.ecommerce.Cart;
import com.simisinc.platform.domain.model.ecommerce.PricingRule;
import com.simisinc.platform.domain.model.login.UserLogin;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;

/**
 * Sets up the framework for the visitor
 *
 * @author matt rajkowski
 * @created 4/6/18 8:23 AM
 */
public class WebRequestFilter implements Filter {

  private static Log LOG = LogFactory.getLog(WebRequestFilter.class);

  private boolean requireSSL = false;

  // Static (rather than an instance field): a servlet container creates exactly one instance of this
  // filter per webapp, but WebRedirectListWidget needs a way to reach in and purge a single entry
  // from the already-loaded legacy CSV fallback map when an admin deletes a database-backed redirect
  // (issue #408) -- see purgeCsvFallback(). Without that, redirectMap (loaded once, here, at startup)
  // would keep serving a from_path the admin just deleted for the rest of this server's uptime, since
  // nothing else ever refreshes or invalidates it.
  private static volatile Map<String, String> redirectMap = null;

  public void init(FilterConfig config) throws ServletException {
    LOG.info("WebRequestFilter starting up...");
    String startupSuccessful = (String) config.getServletContext().getAttribute(ContextConstants.STARTUP_SUCCESSFUL);
    if (!"true".equals(startupSuccessful)) {
      throw new ServletException("Startup failed due to previous error");
    }
    String ssl = LoadSitePropertyCommand.loadByName("system.ssl");
    if ("true".equals(ssl)) {
      LOG.info("SSL is required by system.ssl");
      requireSSL = true;
    }

    // @todo option to reload
    redirectMap = LoadRedirectsCommand.load();

    // Preload the blocked IP list
    LoadBlockedIPListCommand.retrieveCachedIpAddressList();
  }

  public void destroy() {
  }

  public void doFilter(ServletRequest request, ServletResponse servletResponse, FilterChain chain)
      throws ServletException, IOException {

    HttpServletRequest httpServletRequest = (HttpServletRequest) request;
    String scheme = request.getScheme();
    String contextPath = request.getServletContext().getContextPath();
    String requestURI = httpServletRequest.getRequestURI();
    String resource = requestURI.substring(contextPath.length());

    // Assets whose URL already identifies their content can be cached indefinitely, so a repeat
    // visit revalidates nothing instead of re-fetching. Wrapping the response here, rather than
    // setting the header at each chain.doFilter site, means every path through this filter gets
    // the same treatment; the wrapper withdraws the header if the response turns out not to be a
    // successful read.
    if (servletResponse instanceof HttpServletResponse) {
      if (isImmutableAsset(resource)
          || isStampedPlatformAsset(resource, httpServletRequest.getQueryString())) {
        servletResponse = new ImmutableAssetResponse((HttpServletResponse) servletResponse);
      } else if (isRevalidatedAsset(resource)) {
        // Order matters: the webfonts under /css/<vendor>/webfonts/ are claimed above, and so are
        // the platform's own stamped assets. What is left under /css, /javascript and /images is
        // everything with no trustworthy stamp -- the vendored libraries, which are referenced
        // without any ?v= at all -- and that still has to revalidate.
        servletResponse = new ImmutableAssetResponse((HttpServletResponse) servletResponse,
            REVALIDATE_CACHE_CONTROL);
      }
    }
    String ipAddress = request.getRemoteAddr();
    String referer = httpServletRequest.getHeader("Referer");
    String userAgent = httpServletRequest.getHeader("USER-AGENT");

    // Show the resource and headers
    if (LOG.isTraceEnabled()) {
      LOG.trace("Resource: " + resource);
      Enumeration<?> headerNames = httpServletRequest.getHeaderNames();
      while (headerNames.hasMoreElements()) {
        String name = (String) headerNames.nextElement();
        LOG.debug("Header: " + name + "=" + httpServletRequest.getHeader(name));
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug(httpServletRequest.getMethod() + " uri " + resource);
    }

    // Answer the platform readiness probe early -- before host, blocked-IP, SSL, and session handling --
    // so an internal health check (Azure App Service Health check / container HEALTHCHECK / k8s probe) is
    // never rejected by those gates. Returns no detail (avoids version/topology disclosure).
    if (resource.equals("/healthz")) {
      doHealthCheck(request, servletResponse);
      return;
    }

    // Check allowed host names
    if (!HostnameCommand.passesCheck(request.getServerName())) {
      do404(servletResponse);
      return;
    }

    // Block and log certain requests -- except for the browser resource paths exempted below (css/js/
    // images/fonts/etc), which are always allowed through regardless of IP-block status. This matters
    // because a blocked IP's page request gets response.sendError(404), which Tomcat forwards
    // internally to error-404.jsp WITHOUT re-running this filter for that forward -- so the error
    // page's own HTML renders fine. But error-404.jsp loads its stylesheet via a normal <link> tag,
    // which is a fresh, separate browser request that comes back through this filter from scratch; if
    // that request were blocked too, a blocked visitor's 404 page would render with no CSS at all,
    // while an ordinary "page really doesn't exist" 404 renders fully styled -- a visible tell that the
    // visitor was specifically blocked, contradicting this filter's documented intent
    // (docs/ip-blocking.md) that a block should be indistinguishable from a real 404. Checking
    // isBrowserResourcePath() here (rather than moving the whole exemption block above this check)
    // keeps this widening scoped to only the IP-block check -- the exemption still runs in its original
    // position below, after the redirect/logout/SSL-redirect/REST-API checks, so those keep taking
    // precedence over it for these same paths exactly as before (see e.g.
    // WebRequestFilterTest#purgeCsvFallbackStopsTheCsvFallbackFromServingADeletedPath, which relies on
    // a CSV redirect for a /css/... path still winning over this exemption).
    if (!isBrowserResourcePath(resource) && !BlockedIPListCommand.passesCheck(resource, ipAddress)) {
      do404(servletResponse);
      return;
    }

    // Allow if an SSL renewal request
    if (resource.startsWith("/.well-known/acme-challenge")) {
      chain.doFilter(request, servletResponse);
      return;
    }

    // Check redirects. The admin-managed, database-backed redirect (issue #408) is checked first and
    // takes precedence over the legacy CSV file when both define a rule for the same path: the
    // database is the new source of truth admins interact with at /admin/web-redirects, so an
    // admin's edit should immediately shadow a stale/not-yet-migrated CSV entry rather than be
    // silently overridden by it. The CSV lookup remains as a fallback during the transition period
    // (see LoadRedirectsCommand/ImportLegacyRedirectsCommand) -- but only for a from_path the
    // database has no opinion about at all. A from_path that IS a database row, even a disabled one,
    // is fully governed by the database from that point on: falling through to the CSV map for a
    // disabled row would silently resurrect an admin's own "disable" click via the legacy fallback it
    // was specifically trying to turn off (issue #408 review). Deleting the row (rather than
    // disabling it) is handled the same way for the life of this server via purgeCsvFallback() below
    // -- see WebRedirectListWidget.remove().
    WebRedirect webRedirect = LoadWebRedirectCommand.matchByFromPath(resource);
    if (webRedirect != null) {
      // The database has an explicit answer for this from_path -- never consult the CSV map below,
      // whether that answer is "redirect" (enabled) or "don't" (disabled; falls through to normal
      // request handling further down, the same as if no redirect existed at all)
      if (webRedirect.getEnabled()) {
        if (webRedirect.getStatusCode() == WebRedirect.TEMPORARY) {
          do302(servletResponse, webRedirect.getToUrl());
        } else {
          do301(servletResponse, webRedirect.getToUrl());
        }
        return;
      }
    } else if (redirectMap != null) {
      String redirect = redirectMap.get(resource);
      if (redirect != null) {
        // Handle a redirect immediately
        do301(servletResponse, redirect);
        return;
      }
    }

    // Send a trailing-slash URL to its canonical form. /news/ used to 404 while /news served the
    // page, so anyone arriving from an older link, a bookmark or a search result with the slash on
    // the end hit a dead end -- 17 distinct visitors on /news/ alone in six hours, and the same for
    // every other page. Placed AFTER the admin-managed redirect lookup above so an explicit rule for
    // a slashed path still wins, and 301 rather than serving the same page at both URLs, which would
    // split search ranking between them.
    if (("GET".equals(httpServletRequest.getMethod()) || "HEAD".equals(httpServletRequest.getMethod()))) {
      String canonical = trailingSlashRedirect(resource, httpServletRequest.getQueryString());
      if (canonical != null) {
        do301(servletResponse, canonical);
        return;
      }
    }

    // Handle logouts immediately
    if (resource.equals("/logout")) {
      // CSRF: validate the session token before processing logout
      HttpSession httpSession = httpServletRequest.getSession(false);
      if (httpSession != null) {
        UserSession logoutUserSession = (UserSession) httpSession.getAttribute(SessionConstants.USER);
        if (logoutUserSession != null && logoutUserSession.isLoggedIn()) {
          String providedToken = httpServletRequest.getParameter("token");
          if (!logoutUserSession.getFormToken().equals(providedToken)) {
            do302(servletResponse, contextPath + "/");
            return;
          }
        }
      }
      // Log out of the system
      LogoutCommand.logout((HttpServletRequest) request, ((HttpServletResponse) servletResponse));
      // Redirect to OAuth Provider via the home page
      if (OAuthRequestCommand.isEnabled()) {
        String redirectURL = OAuthLogoutCommand.getLogoutRedirect();
        do302(servletResponse, redirectURL);
        return;
      }
    }

    // Redirect to SSL
    if (requireSSL && !"https".equalsIgnoreCase(scheme)) {
      if (!"localhost".equals(request.getServerName()) && !InetAddressUtils.isIPv4(request.getServerName())
          && !InetAddressUtils.isIPv6(request.getServerName())) {
        String redirectURL;
        if (HostnameCommand.isExplicitlyAllowed(request.getServerName())) {
          // Operator-listed hostname: safe to echo the Host header back in the Location
          redirectURL = StringUtils.replace(httpServletRequest.getRequestURL().toString(), "http://", "https://");
        } else {
          String siteUrl = StringUtils.trimToNull(LoadSitePropertyCommand.loadByName("site.url"));
          if (siteUrl == null) {
            // No site.url and no allow list — cannot determine the canonical host safely; skip SSL redirect
            LOG.warn("SSL redirect skipped: site.url is not configured and hostname is not allow-listed");
            chain.doFilter(request, servletResponse);
            return;
          }
          redirectURL = StringUtils.removeEnd(siteUrl, "/") + safeRedirectPath(requestURI);
        }
        LOG.debug("Redirecting to: " + redirectURL);
        do301(servletResponse, redirectURL);
        return;
      }
    }

    // REST API has own clients
    if (resource.startsWith("/api")) {
      // Chain to RestRequestFilter
      chain.doFilter(request, servletResponse);
      return;
    }

    // Allow some browser resources
    if (isBrowserResourcePath(resource)) {
      chain.doFilter(request, servletResponse);
      return;
    }

    // If OAuth is required, and the user is not verified, redirect to provider
    String oauthRedirect = OAuthRequestCommand.handleRequest((HttpServletRequest) request,
        (HttpServletResponse) servletResponse, resource);
    if (oauthRedirect != null) {
      if (StringUtils.isBlank(oauthRedirect)) {
        LOG.error("OAUTH: A redirect url could not be created");
        do401(servletResponse);
        return;
      }
      LOG.debug("OAUTH: Redirecting to " + oauthRedirect);
      do302(servletResponse, oauthRedirect);
      return;
    }

    // Allow this request to forward to the sitemap.xml processor
    if (resource.equals("/sitemap.xml")) {
      chain.doFilter(request, servletResponse);
      return;
    }

    // A method to retain controller data between GET requests
    HttpSession session = httpServletRequest.getSession();
    ControllerSession controllerSession = (ControllerSession) session.getAttribute(SessionConstants.CONTROLLER);
    if (controllerSession == null) {
      synchronized (httpServletRequest.getSession()) {
        controllerSession = (ControllerSession) session.getAttribute(SessionConstants.CONTROLLER);
        if (controllerSession == null) {
          LOG.debug("Creating a new controller session");
          controllerSession = new ControllerSession();
          httpServletRequest.getSession().setAttribute(SessionConstants.CONTROLLER, controllerSession);
        }
      }
    }

    // Determine several values from user cookies to use in functions
    Cookie[] cookies = httpServletRequest.getCookies();
    String cookieViewMode = null;
    String cookieVisitorToken = null;
    String cookieCartToken = null;
    String cookieUserToken = null;
    if (cookies != null) {
      for (Cookie thisCookie : cookies) {
        if (thisCookie.getName().equals(CookieConstants.VIEW_MODE)) {
          cookieViewMode = StringUtils.trimToNull(thisCookie.getValue());
        } else if (thisCookie.getName().equals(CookieConstants.USER_TOKEN)) {
          cookieUserToken = StringUtils.trimToNull(thisCookie.getValue());
        } else if (thisCookie.getName().equals(CookieConstants.VISITOR_TOKEN)) {
          cookieVisitorToken = StringUtils.trimToNull(thisCookie.getValue());
        } else if (thisCookie.getName().equals(CookieConstants.CART_TOKEN)) {
          cookieCartToken = StringUtils.trimToNull(thisCookie.getValue());
        }
      }
    }

    // Check headers to see if this is a container-only experience (no menus/footers)
    if ("container".equals(httpServletRequest.getHeader(SessionConstants.X_VIEW_MODE))) {
      // Add a cookie in case session invalidates
      Cookie cookie = new Cookie(CookieConstants.VIEW_MODE, "container");
      if (request.isSecure()) {
        cookie.setSecure(true);
      }
      cookie.setHttpOnly(true);
      cookie.setPath("/");
      cookie.setMaxAge(-1);
      ((HttpServletResponse) servletResponse).addCookie(cookie);
      session.setAttribute(SessionConstants.X_VIEW_MODE, "container");
    } else if ("normal".equals(httpServletRequest.getHeader(SessionConstants.X_VIEW_MODE))) {
      // Remove the cookie
      Cookie cookie = new Cookie(CookieConstants.VIEW_MODE, "");
      if (request.isSecure()) {
        cookie.setSecure(true);
      }
      cookie.setHttpOnly(true);
      cookie.setPath("/");
      cookie.setMaxAge(0);
      ((HttpServletResponse) servletResponse).addCookie(cookie);
      session.setAttribute(SessionConstants.X_VIEW_MODE, "normal");
    } else {
      // Set the session either way for efficiency
      if (session.getAttribute(SessionConstants.X_VIEW_MODE) == null) {
        boolean foundCookie = false;
        if (cookieViewMode != null && "container".equals(cookieViewMode)) {
          foundCookie = true;
          session.setAttribute(SessionConstants.X_VIEW_MODE, "container");
        }
        if (!foundCookie) {
          // This is a normal web request
          session.setAttribute(SessionConstants.X_VIEW_MODE, "normal");
        }
      }
    }

    // Make sure the web visitor has session information
    LOG.debug("Checking session...");
    UserSession userSession = (UserSession) session.getAttribute(SessionConstants.USER);
    boolean doNotTrack = DoNotTrackCommand.isDoNotTrack(httpServletRequest.getHeader("DNT"),
        httpServletRequest.getHeader("Sec-GPC"));
    boolean doSaveSession = false;
    if (userSession == null) {
      synchronized (httpServletRequest.getSession()) {
        userSession = (UserSession) session.getAttribute(SessionConstants.USER);
        if (userSession == null) {
          LOG.debug("Creating user session...");
          // Start a new session
          userSession = CreateSessionCommand.createSession(WEB_SOURCE, httpServletRequest.getSession().getId(),
              ipAddress, referer, userAgent);
          httpServletRequest.getSession().setAttribute(SessionConstants.USER, userSession);
          // Skip tracking for monitoring apps, and for requests that ask not to be tracked (DNT / GPC)
          if (httpServletRequest.getHeader("X-Monitor") == null && !doNotTrack) {
            doSaveSession = true;
          }
        }
      }
      if (doSaveSession) {
        // Save the new session
        SaveSessionCommand.saveSession(userSession);
      }
    }

    // Check once to see if this browser has a cookie for the user
    boolean userVerifiedThisRequest = false;
    if (!userSession.isLoggedIn() && !userSession.isCookieChecked() && !resource.equals("/logout")) {
      // Only check for the cookie once per session
      userSession.setCookieChecked(true);

      // Determine whether analytics runs cookieless: no persistent visitor cookie, and returning visitors within
      // the same day are recognized by a daily rotating hash of the request fingerprint instead of a stored token.
      boolean cookielessAnalytics = LoadSitePropertyCommand.loadByNameAsBoolean("analytics.cookieless");
      String visitorToken = cookielessAnalytics
          ? DailyVisitorHashCommand.dailyHash(ipAddress, userAgent, httpServletRequest.getServerName())
          : cookieVisitorToken;

      // Determine if this is a returning visitor
      Visitor visitor = null;
      if (StringUtils.isNotBlank(visitorToken)) {
        visitor = LoadVisitorCommand.loadVisitorByToken(visitorToken);
        if (visitor != null) {
          userSession.setVisitorId(visitor.getId());
        }
      }

      // Determine if there is a cart
      if (StringUtils.isNotBlank(cookieCartToken)) {
        LOG.debug("Setting an existing cart from token: " + cookieCartToken);
        Cart cart = LoadCartCommand.loadCartByToken(cookieCartToken);
        if (cart != null) {
          LOG.debug("Cart was found in database: " + cookieCartToken);
          userSession.setCart(cart);
        }
      }

      // Make sure the visitor has a token
      if (visitor == null) {
        if (!doNotTrack) {
          // Create and store a new token
          LOG.debug("Creating a visitor token...");
          visitor = (cookielessAnalytics && StringUtils.isNotBlank(visitorToken))
              ? SaveVisitorCommand.saveVisitor(userSession, visitorToken)
              : SaveVisitorCommand.saveVisitor(userSession);
        }
      } else {
        // Make sure the sessionId is set
        if (doSaveSession) {
          SessionRepository.updateVisitorId(userSession, visitor);
        }
      }

      // Persist the visitor identity in a cookie -- skipped entirely when running cookieless or when DNT/GPC is set
      if (!cookielessAnalytics && !doNotTrack && visitor != null) {
        int oneYearSecondsInt = 365 * 24 * 60 * 60;
        Cookie cookie = new Cookie(CookieConstants.VISITOR_TOKEN, visitor.getToken());
        if (request.isSecure()) {
          cookie.setSecure(true);
        }
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(oneYearSecondsInt);
        ((HttpServletResponse) servletResponse).addCookie(cookie);
      }

      // Check the visitor's cart
      if ("true".equals(LoadSitePropertyCommand.loadByName("site.cart"))) {
        // Instantiate the visitor's cart for reference
        if (userSession.getCart() != null) {
          // Create or extend the cart cookie
          int twoWeeksSecondsInt = 14 * 24 * 60 * 60;
          Cookie cookie = new Cookie(CookieConstants.CART_TOKEN, userSession.getCart().getToken());
          if (request.isSecure()) {
            cookie.setSecure(true);
          }
          cookie.setHttpOnly(true);
          cookie.setPath("/");
          cookie.setMaxAge(twoWeeksSecondsInt);
          ((HttpServletResponse) servletResponse).addCookie(cookie);
        } else {
          // Cleanup the cookie since the token is no longer valid
          Cookie cookie = new Cookie(CookieConstants.CART_TOKEN, "");
          if (request.isSecure()) {
            cookie.setSecure(true);
          }
          cookie.setHttpOnly(true);
          cookie.setPath("/");
          cookie.setMaxAge(0);
          ((HttpServletResponse) servletResponse).addCookie(cookie);
        }
      }

      // Attempt to login the user
      if (cookieUserToken != null) {
        User user = AuthenticateLoginCommand.getAuthenticatedUser(cookieUserToken);
        // "Show login?" (site.login) is enforced here as well as in LoginWidget.finalizeLogin.
        // Restoring a remember-me cookie is a sign-in by this codebase's own reckoning -- it emits
        // authentication.login.success, raises the break-glass alert, and writes a user_logins row --
        // so leaving it ungated let a non-admin who ticked "Stay logged in" before the toggle was
        // turned off keep minting authenticated sessions from the cookie. Not merely for the
        // fortnight the cookie was issued for, either: every restore re-extends both the token row
        // and the cookie by another two weeks (below), so visiting once a fortnight renewed the
        // bypass indefinitely. Evaluating the CURRENT setting at restore time rather than freezing
        // it at issue time matches how this same block already re-checks MFA enrollment.
        // Deliberately NOT the same as revoking the token: the cookie and its user_tokens row are
        // left intact, so flipping the setting back on restores these users without a fresh sign-in.
        // The admin exemption mirrors finalizeLogin so a misconfigured toggle can never lock the
        // site owner out. An already-established HttpSession is out of scope by design -- site.login
        // governs becoming authenticated, not staying so (compare site.online, which PageServlet
        // explicitly exempts logged-in users from) -- so a live session persists until it times out.
        boolean signInsDisabled = user != null && !user.hasRole("admin")
            && !"true".equals(LoadSitePropertyCommand.loadByName("site.login"));
        if (signInsDisabled) {
          // The enclosing isCookieChecked() guard runs this at most once per HttpSession, so the
          // refusal is audited once per session rather than on every request
          SaveAuditEventCommand.recordAuthentication("authentication.login.failure", "failure", user.getId(),
              user.getEmail(), ipAddress, userSession.getSessionId(), "Sign-ins are currently disabled");
        }
        if (user != null && !signInsDisabled) {
          // Let the request know an authenticated user was retrieved
          userVerifiedThisRequest = true;
          // Log the user in
          LOG.debug("Got a token user: " + user.getId());
          // Rotate the servlet session id before establishing the login, so a session id an attacker
          // may have fixed on the victim is not carried into the authenticated session (session
          // fixation). Mirrors the interactive login path (LoginWidget). Guarded on an existing
          // session: with none, the id is server-generated on login and there is nothing to rotate.
          if (httpServletRequest.getSession(false) != null) {
            httpServletRequest.changeSessionId();
          }
          userSession.login(user);
          if (user.getTimeZone() != null) {
            Config.set(request, Config.FMT_TIME_ZONE, user.getTimeZone());
          }
          // Track the login. This also stamps lastLoginTrackedDate so the daily-activity check
          // further below does not write a second row for today later in this same request (see
          // trackDailyLogin).
          trackDailyLogin(userSession, LocalDate.now(FormatDateCommand.getSiteZoneId()), ipAddress, userAgent);
          // Audit the cookie-token (remember-me) auto-login for the SIEM; source marker "token"
          SaveAuditEventCommand.recordAuthentication("authentication.login.success", "success",
              user.getId(), user.getEmail(), ipAddress, userSession.getSessionId(), "token");
          // A remember-me cookie establishes a session without anyone typing a password, so this
          // path needs the break-glass alert just as much as the sign-in form does
          BreakGlassAlertCommand.recordLogin(user, ipAddress, userSession.getSessionId(), "token");
          // Enforce org-level MFA before the user accesses any page (IA-2(1))
          if (MfaEnforcementCommand.requiresEnrollment(userSession, user)) {
            String enrollUrl = MfaEnforcementCommand.getEnrollmentUrl();
            if (!MfaEnforcementCommand.isExemptUrl(resource, enrollUrl)) {
              do302(servletResponse, enrollUrl);
              return;
            }
          }
          // Extend the token expiration date
          int twoWeeksSecondsInt = 14 * 24 * 60 * 60;
          AuthenticateLoginCommand.extendTokenExpiration(cookieUserToken, twoWeeksSecondsInt);
          // Extend the cookie
          Cookie cookie = new Cookie(CookieConstants.USER_TOKEN, cookieUserToken);
          if (request.isSecure()) {
            cookie.setSecure(true);
          }
          cookie.setHttpOnly(true);
          cookie.setPath("/");
          cookie.setMaxAge(twoWeeksSecondsInt);
          ((HttpServletResponse) servletResponse).addCookie(cookie);
        } else if (user == null) {
          // Cleanup the cookie since the token is no longer valid
          Cookie cookie = new Cookie(CookieConstants.USER_TOKEN, "");
          if (request.isSecure()) {
            cookie.setSecure(true);
          }
          cookie.setHttpOnly(true);
          cookie.setPath("/");
          cookie.setMaxAge(0);
          ((HttpServletResponse) servletResponse).addCookie(cookie);
        }
      }
    }

    // Verify the user record on each request
    if (!userVerifiedThisRequest && userSession.isLoggedIn()) {
      // Verify the roles every request for dynamic changes. Re-check the CURRENT session's user by id --
      // NOT via cookieUserToken, which only resolves when the user opted into "stay logged in"; keying this
      // check on it force-logs-out every other session on its very next request.
      User user = AuthenticateLoginCommand.getAuthenticatedUser(userSession.getUserId());
      if (user == null) {
        // Logout
        LogoutCommand.logout((HttpServletRequest) request, ((HttpServletResponse) servletResponse));
        // Return to login
        do302(servletResponse, "/login");
        return;
      }

      // Update user roles and groups
      LOG.debug("Updating user roles and groups");
      userSession.setRoleList(user.getRoleList());
      userSession.setGroupList(user.getGroupList());

      // Enforce org-level MFA on every request for users whose role requires it (IA-2(1))
      if (MfaEnforcementCommand.requiresEnrollment(userSession, user)) {
        String enrollUrl = MfaEnforcementCommand.getEnrollmentUrl();
        if (!MfaEnforcementCommand.isExemptUrl(resource, enrollUrl)) {
          do302(servletResponse, enrollUrl);
          return;
        }
      }
    }

    // Track daily activity for the Daily/Monthly Active Users dashboard tiles (SiteStatsWidget,
    // via UserLoginRepository.findUniqueDailyLogins/findUniqueMonthlyLogins, which COUNT(DISTINCT
    // user_id) from user_logins). A row used to be written only once per fresh HttpSession, at the
    // moment it first became authenticated; since the session timeout (60 min, web.xml) refreshes
    // on activity, a continuously-active user could stay logged in for days without a new
    // authentication event, and so was never counted again after their first day. Comparing an
    // in-memory date on the UserSession (see trackDailyLogin) avoids a DB read on this hot path --
    // a write only happens on the first request of a new calendar day for an already-logged-in
    // session.
    if (userSession.isLoggedIn()) {
      trackDailyLogin(userSession, LocalDate.now(FormatDateCommand.getSiteZoneId()), ipAddress, userAgent);
    }

    // The home page can show an overlay (a couple of different kinds)
    if ("get".equalsIgnoreCase(httpServletRequest.getMethod())) {
      // See if this request has an instant promo code
      boolean hasPricingRule = false;
      String promoCode = httpServletRequest.getParameter(RequestConstants.PROMO_CODE);
      if (StringUtils.isNotBlank(promoCode)) {
        PricingRule pricingRule = PricingRuleCommand.findValidPromoCode(promoCode, null);
        if (pricingRule != null) {
          hasPricingRule = true;
          if (userSession.getCart() == null) {
            CartCommand.createCart(userSession);
          }
          userSession.getCart().setPromoCode(promoCode);
          httpServletRequest.setAttribute(RequestConstants.PRICING_RULE, pricingRule);
          LOG.debug("Found promo code overlay: " + promoCode);
        }
      }
      // If on the home page, and not an instant promo code, check if the site has a promo overlay
      if (resource.equals("/") && !hasPricingRule) {
        if ("true".equals(LoadSitePropertyCommand.loadByName("site.newsletter.overlay"))) {
          String headline = LoadSitePropertyCommand.loadByName("site.newsletter.headline");
          String message = LoadSitePropertyCommand.loadByName("site.newsletter.message");
          if (StringUtils.isNotBlank(headline) && StringUtils.isNotBlank(message)) {
            httpServletRequest.setAttribute(RequestConstants.OVERLAY_HEADLINE, headline);
            httpServletRequest.setAttribute(RequestConstants.OVERLAY_MESSAGE, message);
          }
        }
      }
    }

    // Default states coordinated by cookies
    /* changed to main.jsp
    userSession.setShowSiteConfirmation(!userSession.isLoggedIn());
    userSession.setShowSiteNewsletterSignup(true);
    // Check the request cookies
    Cookie[] cookies = httpServletRequest.getCookies();
    if (cookies != null) {
      // User values
      for (Cookie thisCookie : cookies) {
        if (thisCookie.getName().equals(CookieConstants.SHOW_SITE_CONFIRMATION)) {
          // Found a saved value
          userSession.setShowSiteConfirmation(false);
        } else if (thisCookie.getName().equals(CookieConstants.SHOW_SITE_NEWSLETTER)) {
          // Found a saved value
          userSession.setShowSiteNewsletterSignup(false);
        }
      }
    }
    */

    chain.doFilter(request, servletResponse);
  }

  /**
   * Restricts a request path so it can only ever be appended to the configured site URL as an absolute path on that
   * site. This keeps the path from changing the host of the redirect (a protocol-relative "//host" or a backslash
   * variant) or from splitting the response header (an embedded control character). Anything unexpected collapses to
   * the site root.
   *
   * @param requestURI the request path from HttpServletRequest.getRequestURI()
   * @return the same path when it is a plain absolute path, otherwise "/"
   */
  static String safeRedirectPath(String requestURI) {
    return HostnameCommand.safeRedirectPath(requestURI);
  }

  /**
   * Matches the browser resource paths (stylesheets, scripts, images, fonts, etc.) that are always
   * allowed through, regardless of IP-block status -- see the exemption of the IP-block check in
   * {@link #doFilter} for why this same test is applied there, ahead of
   * {@code BlockedIPListCommand.passesCheck}, as well as at its original spot further down where it
   * actually serves the request via {@code chain.doFilter}.
   *
   * @param resource the request path, relative to the context path
   * @return true if the path is one of the always-allowed browser resource paths
   */
  /** One year, plus immutable so a reload does not even revalidate. */
  static final String IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable";

  /**
   * True for assets whose URL already identifies their content, so a changed asset is necessarily
   * a changed URL and a year-long cache cannot serve anything stale:
   *
   * <ul>
   * <li>{@code /assets/img/<upload-timestamp>-<id>/...} -- re-uploading yields a new directory.</li>
   * <li>{@code /fonts/...} -- the version is in the filename (inter-v11-latin-regular.woff2).</li>
   * <li>{@code /css/<vendor>/webfonts/...} -- the version is in the vendor directory name
   * (fontawesome-free-6.1.1-web).</li>
   * </ul>
   *
   * <p>Stylesheets and scripts are deliberately excluded. They are cache-busted by a {@code ?v=}
   * stamp read from ApplicationInfo.VERSION, which is edited by hand and is currently stale --
   * platform.css has changed since the value it carries. Caching those for a year would mean a
   * deployed CSS fix never reaching anyone who had already visited the site. They keep the existing
   * revalidation behaviour, which stays correct whether or not that stamp is remembered. The site
   * stylesheet is separate again: StylesheetServlet already serves it with Last-Modified and an
   * ETag, so an admin edit is picked up on the next conditional request.
   *
   * <p>Each prefix carries a trailing slash so it is anchored at a path boundary, for the same
   * reason isBrowserResourcePath() documents: a bare startsWith would also match an ordinary page
   * slug such as /fonts-of-the-world.
   */
  static boolean isImmutableAsset(String resource) {
    if (resource == null) {
      return false;
    }
    return resource.startsWith("/assets/img/")
        || resource.startsWith("/fonts/")
        || (resource.startsWith("/css/") && resource.contains("/webfonts/"));
  }

  /** Cache, but check with the server before every use. */
  static final String REVALIDATE_CACHE_CONTROL = "no-cache";

  /**
   * True for the bundled static assets that must NOT be cached blind: stylesheets, scripts and the
   * bundled images.
   *
   * <p>Most of what is left here carries no {@code ?v=} stamp at all: the vendored libraries under
   * /css and /javascript (animate.min.css, ace.js, spectrum.css and the rest) are referenced from
   * the JSPs by bare path, so there is no token that could ever bust them and they must not be
   * cached blind. The platform's own assets used to be in the same position -- their stamp came
   * from the hand-edited ApplicationInfo.VERSION and had gone stale -- but since #1872 it is
   * derived from their modification times, so {@link #isStampedPlatformAsset} claims those before
   * this method is reached. Sending no header at all is not the neutral choice
   * it looks like: with neither an expiry nor a validator, browsers fall back to HEURISTIC
   * freshness, typically a fraction of the file's age, and a visitor can be served a stale
   * stylesheet for an unpredictable stretch after a deploy. {@code no-cache} keeps the copy but
   * requires a conditional request before reuse, so a deploy always lands and the usual answer is a
   * cheap 304 rather than a re-download.
   *
   * <p>Checked only after {@link #isImmutableAsset}, which claims the webfonts living under
   * {@code /css/<vendor>/webfonts/}; those are content-addressed by their vendor directory and keep
   * the year-long cache.
   *
   * <p>Prefixes are anchored at a path boundary for the reason isBrowserResourcePath() documents: a
   * bare startsWith would also match an ordinary page slug such as /images-of-our-team.
   */
  /**
   * True for a platform asset that may be cached for a year because its URL genuinely identifies
   * its content.
   *
   * <p>Two conditions, and both are load-bearing:
   *
   * <p><b>The path must be one the stamp is computed from.</b> The {@code ?v=} token is the newest
   * modification time across {@link ContextListener#STAMPED_ASSET_PATHS}, so a change to any of
   * those files moves the token for all of them -- over-invalidating, never under-invalidating.
   * An asset outside that set has no such guarantee: it either carries no stamp (every vendored
   * library) or carries one that does not track its own content, so a year-long cache could pin a
   * stale copy with no way to recall it.
   *
   * <p><b>The request must actually carry a stamp.</b> The JSPs always append one, but a bare
   * {@code /css/platform.css} -- typed, bookmarked, or requested by a monitor -- addresses no
   * particular version, and answering that with {@code immutable} would freeze whatever happened to
   * be current for a year. Without a {@code v} parameter the request falls through to
   * revalidation, which is correct rather than merely cautious.
   */
  static boolean isStampedPlatformAsset(String resource, String queryString) {
    return resource != null
        && ContextListener.STAMPED_ASSET_PATH_SET.contains(resource)
        && hasVersionStamp(queryString);
  }

  /**
   * True when the query string carries a non-empty {@code v} parameter.
   *
   * <p>Parsed by hand rather than through {@code request.getParameter}: this filter runs on every
   * request, and asking the container for a parameter forces it to parse the request body on a
   * POST, which would consume the stream before anything downstream can read it.
   */
  static boolean hasVersionStamp(String queryString) {
    if (queryString == null || queryString.isEmpty()) {
      return false;
    }
    for (String pair : queryString.split("&")) {
      int equals = pair.indexOf('=');
      if (equals <= 0 || !"v".equals(pair.substring(0, equals))) {
        continue;
      }
      return equals + 1 < pair.length();
    }
    return false;
  }

  static boolean isRevalidatedAsset(String resource) {
    if (resource == null) {
      return false;
    }
    return resource.startsWith("/css/")
        || resource.startsWith("/javascript/")
        || resource.startsWith("/images/");
  }

  /**
   * Sets the immutable cache header up front, then withdraws it if the response is not a successful
   * read. Without the withdrawal a transient 404 -- a variant not yet generated, a file missing
   * after a bad deploy -- would be cached for a year by every browser that saw it, with no way to
   * recall it.
   */
  static final class ImmutableAssetResponse extends HttpServletResponseWrapper {

    ImmutableAssetResponse(HttpServletResponse response) {
      this(response, IMMUTABLE_CACHE_CONTROL);
    }

    ImmutableAssetResponse(HttpServletResponse response, String cacheControl) {
      super(response);
      response.setHeader("Cache-Control", cacheControl);
    }

    @Override
    public void sendError(int sc) throws IOException {
      withdrawCaching(sc);
      super.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
      withdrawCaching(sc);
      super.sendError(sc, msg);
    }

    @Override
    public void setStatus(int sc) {
      withdrawCaching(sc);
      super.setStatus(sc);
    }

    /**
     * Only an error withdraws the caching. The first version of this tested "not 200 and not 304",
     * which withdrew on every other status a container may legitimately set on its way to serving a
     * 200 -- and Tomcat's DefaultServlet, which serves /fonts and /css, does exactly that. The
     * result was that fonts went out with no-store: a guaranteed re-download on every visit, worse
     * than the missing header this was meant to fix. Assets under /assets/img go through PageServlet
     * instead, never hit that path, and cached correctly, which is what made the bug look
     * path-specific rather than logical.
     */
    private void withdrawCaching(int sc) {
      if (sc >= 400 && !isCommitted()) {
        setHeader("Cache-Control", "no-store");
      }
    }
  }

  /**
   * The canonical location for a path that arrived with a trailing slash, or null to leave the
   * request alone.
   *
   * <p>Only GET and HEAD reach this (the caller checks): redirecting a POST would drop the body.
   *
   * <p>Deliberately does not check whether the target exists. A slashed URL for a page that is gone
   * ends at a 404 either way, and probing first would put a lookup on every request to buy nothing.
   *
   * <p>The Location is a path, never an absolute URL, so nothing here can be steered onto another
   * host by a Host header. It is still run through {@link HostnameCommand#safeRedirectPath} for the
   * protocol-relative ("//evil.example") and control-character cases, and a query string is only
   * carried over once it is known to hold no control characters -- a header value is being built.
   */
  static String trailingSlashRedirect(String resource, String queryString) {
    if (resource == null || !resource.endsWith("/")) {
      return null;
    }
    String stripped = StringUtils.stripEnd(resource, "/");
    if (stripped.isEmpty()) {
      // "/" and "//" -- the site root is served, and there is nothing shorter to send them to
      return null;
    }
    if (isPathOrPrefix(stripped, "/api")) {
      // REST clients are not browsers; a 301 is not theirs to follow and the path may be meaningful
      return null;
    }
    if (isBrowserResourcePath(stripped)) {
      // Static directories belong to the default servlet, not to page routing
      return null;
    }
    String path = HostnameCommand.safeRedirectPath(stripped);
    if ("/".equals(path)) {
      // The sanitiser rejected it; send nothing rather than bouncing the visitor to the home page
      return null;
    }
    if (StringUtils.isNotEmpty(queryString) && !hasControlCharacter(queryString)) {
      return path + "?" + queryString;
    }
    return path;
  }

  private static boolean hasControlCharacter(String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < 0x20 || c == 0x7f) {
        return true;
      }
    }
    return false;
  }

  static boolean isBrowserResourcePath(String resource) {
    // Path-boundary anchored, not a bare startsWith: web.xml maps /css/*, /fonts/*, /html/*,
    // /images/*, /javascript/* as directories, so an unanchored prefix match here would also
    // exempt any ordinary page whose slug merely starts with the same letters (e.g.
    // /images-of-our-team, /css-tutorial-2026, /javascript-basics) from the IP-block check
    // entirely -- a full, unmitigated bypass, since WebRequestFilter is the only place
    // BlockedIPListCommand.passesCheck() is called.
    //
    // /favicon.ico is matched exactly rather than as a prefix. It is a single file at the site
    // root, not a directory, so there is no /favicon/ tree to cover -- and an exact match cannot
    // be the bypass the anchoring above exists to prevent. This entry used to read "/favicon",
    // which an earlier bare startsWith did match /favicon.ico with; anchoring the prefixes left it
    // matching only a "/favicon" path that no mapping serves, so the real request stopped being
    // exempt and began falling through to the full page pipeline. See web.xml, which maps
    // /favicon.ico to the default servlet.
    return resource.equals("/favicon.ico") ||
        isPathOrPrefix(resource, "/css") ||
        isPathOrPrefix(resource, "/fonts") ||
        isPathOrPrefix(resource, "/html") ||
        isPathOrPrefix(resource, "/images") ||
        isPathOrPrefix(resource, "/javascript") ||
        resource.equals("/combined.css") ||
        resource.equals("/combined.js");
  }

  private static boolean isPathOrPrefix(String resource, String path) {
    return resource.equals(path) || resource.startsWith(path + "/");
  }

  /**
   * Writes a user_logins row for {@code userSession} once per calendar day, rather than once per
   * HttpSession lifetime. Compares {@code today} against the date this session last recorded
   * activity for (an in-memory field on UserSession, not a database read), so an already-logged-
   * today session costs nothing on this hot path, while a session whose HttpSession survives past
   * midnight gets a fresh row -- and is therefore still counted -- on its first request of the new
   * day. See UserSession.lastLoginTrackedDate for the full rationale.
   *
   * @param userSession the current request's (already authenticated) session
   * @param today the current date, in the site's configured timezone
   * @param ipAddress the current request's remote address
   * @param userAgent the current request's USER-AGENT header
   * @return true if a new row was written (the tracked date had not yet been recorded for today)
   */
  static boolean trackDailyLogin(UserSession userSession, LocalDate today, String ipAddress, String userAgent) {
    if (today.equals(userSession.getLastLoginTrackedDate())) {
      return false;
    }
    UserLogin userLogin = new UserLogin();
    userLogin.setSource(userSession.getSource());
    userLogin.setUserId(userSession.getUserId());
    userLogin.setIpAddress(ipAddress);
    userLogin.setSessionId(userSession.getSessionId());
    userLogin.setUserAgent(userAgent);
    UserLoginRepository.save(userLogin);
    userSession.setLastLoginTrackedDate(today);
    return true;
  }

  /**
   * Removes a single from_path from the in-memory legacy redirects.csv fallback map (issue #408),
   * so it stops being served by the CSV-backed fallback in {@link #doFilter} for the rest of this
   * server's uptime. {@code redirectMap} is parsed once at filter startup and, unlike the
   * database-backed redirect, has no TTL or write-time invalidation of its own -- called by
   * {@code WebRedirectListWidget} when an admin deletes a database row, so a from_path that also
   * happens to be in the (deprecated but still-present) CSV file can't be silently resurrected by
   * the fallback for the remainder of this run. This does not touch the CSV file on disk: if it
   * still contains the same line, a subsequent server restart will re-populate this map (and
   * ImportLegacyRedirectsCommand will re-import the row) -- removing the deprecated file, as the
   * startup warning in LoadRedirectsCommand already recommends, is what makes a deletion permanent.
   *
   * @param fromPath the from_path an admin just removed from the database
   */
  public static void purgeCsvFallback(String fromPath) {
    if (redirectMap != null && fromPath != null) {
      redirectMap.remove(fromPath);
    }
  }

  private void doHealthCheck(ServletRequest request, ServletResponse servletResponse) throws IOException {
    boolean ready = HealthCommand.isReady(request.getServletContext());
    HttpServletResponse response = (HttpServletResponse) servletResponse;
    response.setStatus(ready ? 200 : 503);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");
    PrintWriter out = response.getWriter();
    out.print(ready ? "{\"status\":\"UP\"}" : "{\"status\":\"DOWN\"}");
    out.flush();
  }

  private void do301(ServletResponse servletResponse, String redirectLocation) throws IOException {
    HttpServletResponse response = (HttpServletResponse) servletResponse;
    response.setHeader("Location", redirectLocation);
    response.setStatus(SC_MOVED_PERMANENTLY);
  }

  private void do302(ServletResponse servletResponse, String redirectLocation) throws IOException {
    HttpServletResponse response = (HttpServletResponse) servletResponse;
    response.setHeader("Location", redirectLocation);
    response.setStatus(SC_MOVED_TEMPORARILY);
  }

  private void do401(ServletResponse servletResponse) throws IOException {
    HttpServletResponse response = (HttpServletResponse) servletResponse;
    response.sendError(SC_UNAUTHORIZED);
  }

  private void do404(ServletResponse servletResponse) throws IOException {
    HttpServletResponse response = (HttpServletResponse) servletResponse;
    response.sendError(SC_NOT_FOUND);
  }

}
