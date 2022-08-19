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

import com.simisinc.platform.application.CreateSessionCommand;
import com.simisinc.platform.application.LoadVisitorCommand;
import com.simisinc.platform.application.SaveSessionCommand;
import com.simisinc.platform.application.SaveVisitorCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.oauth.OAuthLogoutCommand;
import com.simisinc.platform.application.oauth.OAuthRequestCommand;
import com.simisinc.platform.application.cms.BlockedIPListCommand;
import com.simisinc.platform.application.cms.HostnameCommand;
import com.simisinc.platform.application.cms.LoadBlockedIPListCommand;
import com.simisinc.platform.application.cms.LoadRedirectsCommand;
import com.simisinc.platform.application.ecommerce.CartCommand;
import com.simisinc.platform.application.ecommerce.LoadCartCommand;
import com.simisinc.platform.application.ecommerce.PricingRuleCommand;
import com.simisinc.platform.application.login.AuthenticateLoginCommand;
import com.simisinc.platform.application.login.LogoutCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.Visitor;
import com.simisinc.platform.domain.model.ecommerce.Cart;
import com.simisinc.platform.domain.model.ecommerce.PricingRule;
import com.simisinc.platform.domain.model.login.UserLogin;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.conn.util.InetAddressUtils;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.jstl.core.Config;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;

import static com.simisinc.platform.presentation.controller.UserSession.WEB_SOURCE;
import static javax.servlet.http.HttpServletResponse.*;

/**
 * Sets up the framework for the visitor
 *
 * @author matt rajkowski
 * @created 4/6/18 8:23 AM
 */
public class WebRequestFilter implements Filter {

  private static Log LOG = LogFactory.getLog(WebRequestFilter.class);

  private boolean requireSSL = false;
  private Map<String, String> redirectMap = null;

  public void init(FilterConfig config) throws ServletException {
    LOG.info("WebRequestFilter starting up...");
    String startupSuccessful = (String) config.getServletContext().getAttribute("STARTUP_SUCCESSFUL");
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

    // Check hostnames
    if (!HostnameCommand.passesCheck(request.getServerName())) {
      do404(servletResponse);
      return;
    }

    // Block and log certain requests
    if (!BlockedIPListCommand.passesCheck(resource, ipAddress)) {
      do404(servletResponse);
      return;
    }

    // Allow if an SSL renewal request
    if (resource.startsWith("/.well-known/acme-challenge")) {
      chain.doFilter(request, servletResponse);
      return;
    }

    // Check redirects
    if (redirectMap != null) {
      String redirect = redirectMap.get(resource);
      if (redirect != null) {
        // Handle a redirect immediately
        do301(servletResponse, redirect);
        return;
      }
    }

    // Handle logouts immediately
    if (resource.equals("/logout")) {
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
      if (!"localhost".equals(request.getServerName()) && !InetAddressUtils.isIPv4Address(request.getServerName())
          && !InetAddressUtils.isIPv6Address(request.getServerName())) {
        String requestURL = httpServletRequest.getRequestURL().toString();
        requestURL = StringUtils.replace(requestURL, "http://", "https://");
        LOG.debug("Redirecting to: " + requestURL);
        do301(servletResponse, requestURL);
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
    if (resource.startsWith("/favicon") ||
        resource.startsWith("/favicon.ico") ||
        resource.startsWith("/css") ||
        resource.startsWith("/fonts") ||
        resource.startsWith("/html") ||
        resource.startsWith("/images") ||
        resource.startsWith("/javascript") ||
        resource.startsWith("/combined.css") ||
        resource.startsWith("/combined.js") ||
        resource.startsWith("/css/custom/")) {
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

    // Allow this request to access the sitemap.xml
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

    // See if this is a container-only experience (no menus/footers)
    if ("container".equals(httpServletRequest.getHeader("X-View-Mode"))) {
      // Add a cookie in case session invalidates
      Cookie cookie = new Cookie(CookieConstants.VIEW_MODE, "container");
      if (request.isSecure()) {
        cookie.setSecure(true);
      }
      cookie.setHttpOnly(true);
      cookie.setPath("/");
      cookie.setMaxAge(-1);
      ((HttpServletResponse) servletResponse).addCookie(cookie);
      session.setAttribute("X-View-Mode", "container");
    } else if ("normal".equals(httpServletRequest.getHeader("X-View-Mode"))) {
      // Remove the cookie
      Cookie cookie = new Cookie(CookieConstants.VIEW_MODE, "");
      if (request.isSecure()) {
        cookie.setSecure(true);
      }
      cookie.setHttpOnly(true);
      cookie.setPath("/");
      cookie.setMaxAge(0);
      ((HttpServletResponse) servletResponse).addCookie(cookie);
      session.setAttribute("X-View-Mode", "normal");
    } else {
      // Set the session either way for efficiency
      if (session.getAttribute("X-View-Mode") == null) {
        boolean foundCookie = false;
        Cookie[] cookies = httpServletRequest.getCookies();
        if (cookies != null) {
          for (Cookie thisCookie : cookies) {
            if (thisCookie.getName().equals(CookieConstants.VIEW_MODE)) {
              if ("container".equals(thisCookie.getValue())) {
                // This is a container mode
                foundCookie = true;
                session.setAttribute("X-View-Mode", "container");
              }
            }
          }
        }
        if (!foundCookie) {
          // This is a normal web request
          session.setAttribute("X-View-Mode", "normal");
        }
      }
    }

    // Make sure the web visitor has session information
    LOG.debug("Checking session...");
    UserSession userSession = (UserSession) session.getAttribute(SessionConstants.USER);
    boolean doSaveSession = false;
    if (userSession == null) {
      synchronized (httpServletRequest.getSession()) {
        userSession = (UserSession) session.getAttribute(SessionConstants.USER);
        if (userSession == null) {
          LOG.debug("Creating session...");
          // Start a new session
          userSession = CreateSessionCommand.createSession(WEB_SOURCE, httpServletRequest.getSession().getId(),
              ipAddress, referer, userAgent);
          httpServletRequest.getSession().setAttribute(SessionConstants.USER, userSession);
          // Determine if this is a monitoring app
          if (httpServletRequest.getHeader("X-Monitor") == null) {
            doSaveSession = true;
          }
        }
      }
      if (doSaveSession) {
        // Save the new session
        SaveSessionCommand.saveSession(userSession);
      }
    }

    // Update the roles every request for dynamic changes
    if (userSession.isLoggedIn()) {
      LOG.debug("Updating user roles and groups");
      userSession.setRoleList(RoleRepository.findAllByUserId(userSession.getUser().getId()));
      userSession.setGroupList(GroupRepository.findAllByUserId(userSession.getUser().getId()));
    }

    // Check once to see if this browser has a cookie for the user
    if (!userSession.isLoggedIn() && !userSession.isCookieChecked() && !resource.equals("/logout")) {
      // Only check for the cookie once per session
      userSession.setCookieChecked(true);

      // Check the cookies for tokens
      Visitor visitor = null;
      String userToken = null;
      Cookie[] cookies = httpServletRequest.getCookies();
      if (cookies != null) {
        for (Cookie thisCookie : cookies) {
          // Look for tokens
          if (thisCookie.getName().equals(CookieConstants.VISITOR_TOKEN)) {
            // Found a visitor token
            String visitorToken = StringUtils.trimToNull(thisCookie.getValue());
            visitor = LoadVisitorCommand.loadVisitorByToken(visitorToken);
            if (visitor != null) {
              userSession.setVisitorId(visitor.getId());
            }
          } else if (thisCookie.getName().equals(CookieConstants.CART_TOKEN)) {
            // Found a cart token
            String cartToken = StringUtils.trimToNull(thisCookie.getValue());
            LOG.debug("Setting an existing cart from token: " + cartToken);
            Cart cart = LoadCartCommand.loadCartByToken(cartToken);
            if (cart != null) {
              LOG.debug("Cart was found in database: " + cartToken);
              userSession.setCart(cart);
            }
          } else if (thisCookie.getName().equals(CookieConstants.USER_TOKEN)) {
            // Found a user token
            userToken = StringUtils.trimToNull(thisCookie.getValue());
            LOG.trace(thisCookie.getName() + "=" + userToken);
          }
        }
      }

      // Make sure the visitor has a token
      if (visitor == null) {
        // Create and store a new token
        LOG.debug("Creating a visitor token...");
        visitor = SaveVisitorCommand.saveVisitor(userSession);
      } else {
        // Make sure the sessionId is set
        if (doSaveSession) {
          SessionRepository.updateVisitorId(userSession, visitor);
        }
      }
      {
        // Create or extend the visitor cookie
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
      if (userToken != null) {
        User user = AuthenticateLoginCommand.getAuthenticatedUser(userToken);
        if (user != null) {
          // Log the user in
          LOG.debug("Got a token user: " + user.getId());
          userSession.login(user);
          if (user.getTimeZone() != null) {
            Config.set(request, Config.FMT_TIME_ZONE, user.getTimeZone());
          }
          // Track the login
          UserLogin userLogin = new UserLogin();
          userLogin.setSource(WEB_SOURCE);
          userLogin.setUserId(user.getId());
          userLogin.setIpAddress(ipAddress);
          userLogin.setSessionId(userSession.getSessionId());
          userLogin.setUserAgent(httpServletRequest.getHeader("USER-AGENT"));
          UserLoginRepository.save(userLogin);
          // Extend the token expiration date
          int twoWeeksSecondsInt = 14 * 24 * 60 * 60;
          AuthenticateLoginCommand.extendTokenExpiration(userToken, twoWeeksSecondsInt);
          // Extend the cookie
          Cookie cookie = new Cookie(CookieConstants.USER_TOKEN, userToken);
          if (request.isSecure()) {
            cookie.setSecure(true);
          }
          cookie.setHttpOnly(true);
          cookie.setPath("/");
          cookie.setMaxAge(twoWeeksSecondsInt);
          ((HttpServletResponse) servletResponse).addCookie(cookie);
        } else {
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

  private void do301(ServletResponse servletResponse, String redirectLocation) throws IOException {
    HttpServletResponse response = (HttpServletResponse) servletResponse;
    response.setHeader("Location", redirectLocation);
    response.sendError(SC_MOVED_PERMANENTLY);
  }

  private void do302(ServletResponse servletResponse, String redirectLocation) throws IOException {
    HttpServletResponse response = (HttpServletResponse) servletResponse;
    response.setHeader("Location", redirectLocation);
    response.sendError(SC_MOVED_TEMPORARILY);
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
