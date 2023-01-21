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

package com.simisinc.platform.rest.controller;

import com.simisinc.platform.application.*;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.HostnameCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.application.login.AuthenticateLoginCommand;
import com.simisinc.platform.domain.model.App;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.UserLogin;
import com.simisinc.platform.domain.model.login.UserToken;
import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserTokenRepository;
import com.simisinc.platform.presentation.controller.ContextConstants;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.UserSession;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.conn.util.InetAddressUtils;

import javax.security.auth.login.LoginException;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.StringTokenizer;
import java.util.UUID;

import static com.simisinc.platform.presentation.controller.UserSession.API_SOURCE;
import static javax.servlet.http.HttpServletResponse.*;

/**
 * Authorizes the REST request
 *
 * @author matt rajkowski
 * @created 4/18/18 1:02 PM
 */
public class RestRequestFilter implements Filter {

  private static Log LOG = LogFactory.getLog(RestRequestFilter.class);

  private boolean requireSSL = false;

  @Override
  public void init(FilterConfig config) throws ServletException {
    LOG.info("RestRequestFilter starting up...");
    String startupSuccessful = (String) config.getServletContext().getAttribute(ContextConstants.STARTUP_SUCCESSFUL);
    if (!"true".equals(startupSuccessful)) {
      throw new ServletException("Startup failed due to previous error");
    }
    String ssl = LoadSitePropertyCommand.loadByName("system.ssl");
    if ("true".equals(ssl)) {
      LOG.info("SSL is required by system.ssl");
      requireSSL = true;
    }
  }

  @Override
  public void destroy() {
  }

  public void doFilter(ServletRequest request, ServletResponse servletResponse, FilterChain chain)
      throws ServletException, IOException {
    HttpServletRequest httpServletRequest = (HttpServletRequest) request;
    String requestMethod = ((HttpServletRequest) request).getMethod().toLowerCase();
    String scheme = request.getScheme();
    String contextPath = request.getServletContext().getContextPath();
    String requestURI = httpServletRequest.getRequestURI();
    String resource = requestURI.substring(contextPath.length());
    String ipAddress = request.getRemoteAddr();

    // Check allowed hostnames
    if (!HostnameCommand.passesCheck(request.getServerName())) {
      do404(servletResponse);
      return;
    }

    // Check if IP is rate limited
    if (!RateLimitCommand.isIpAllowedRightNow(ipAddress, false)) {
      do429(servletResponse);
      return;
    }

    // Redirect to SSL
    if (requireSSL && !"https".equalsIgnoreCase(scheme)) {
      if (!"localhost".equals(request.getServerName()) && !InetAddressUtils.isIPv4Address(request.getServerName())
          && !InetAddressUtils.isIPv6Address(request.getServerName())) {
        String requestURL = ((HttpServletRequest) request).getRequestURL().toString();
        requestURL = StringUtils.replace(requestURL, "http://", "https://");
        LOG.debug("Redirecting to: " + requestURL);
        do301(servletResponse, requestURL);
        return;
      }
    }

    boolean isAPIOnline = LoadSitePropertyCommand.loadByNameAsBoolean("site.api");
    if (!isAPIOnline) {
      LOG.debug("API is disabled");
      HttpServletResponse response = (HttpServletResponse) servletResponse;
      RestServlet.sendError(response, SC_FORBIDDEN, "API is disabled");
      return;
    }

    // Verify there is an endpoint specified
    if (resource.lastIndexOf("/") <= 0) {
      LOG.debug("No endpoint specified");
      do404(servletResponse);
      return;
    }

    // CORS OPTIONS request
    if ("options".equals(requestMethod)) {
      String siteUrl = LoadSitePropertyCommand.loadByName("site.url");
      HttpServletResponse response = (HttpServletResponse) servletResponse;
      if (StringUtils.isNotBlank(httpServletRequest.getHeader("Origin")) && StringUtils.isNotBlank(siteUrl)) {
        response.addHeader("Access-Control-Allow-Origin", siteUrl);
      }
      response.addHeader("Access-Control-Allow-Methods", "OPTIONS,GET,POST,PUT,DELETE");
      response.addHeader("Access-Control-Allow-Headers", "Content-Type,Authorization,User-Agent,X-API-Key");
      response.setStatus(200);
      return;
    }

    // Check for API Key
    String apiKey = httpServletRequest.getHeader("X-API-Key");
    if (StringUtils.isEmpty(apiKey)) {
      apiKey = request.getParameter("key");
    }
    if (StringUtils.isEmpty(apiKey)) {
      LOG.debug("No key");
      HttpServletResponse response = (HttpServletResponse) servletResponse;
      RestServlet.sendError(response, SC_UNAUTHORIZED, "Unauthorized, no key");
      return;
    }
    // Validate the app's key
    App thisApp = LoadAppCommand.loadAppByPublicKey(apiKey);
    if (thisApp == null || !thisApp.isEnabled()) {
      LOG.debug("Invalid key");

      // Limit the number of attempts per minute by ip for an invalid key
      if (!RateLimitCommand.isIpAllowedRightNow(ipAddress, true)) {
        do429(servletResponse);
        return;
      }

      // Unauthorized
      HttpServletResponse response = (HttpServletResponse) servletResponse;
      RestServlet.sendError(response, SC_UNAUTHORIZED, "Unauthorized");
      return;
    }
    request.setAttribute(RequestConstants.REST_APP, thisApp);

    if (LOG.isDebugEnabled()) {
      LOG.debug(requestMethod + " " + resource);
    }

    // Token authorization
    String token = checkBearerAuthorization(request);
    if (token == null) {
      // Look for a session request
      if ("/api/session".equals(resource) && "post".equals(requestMethod)) {
        doRecordSession(thisApp, httpServletRequest, servletResponse);
        return;
      }
      // Look for a token request
      if ("/api/oauth2/authorize".equals(resource)) {
        // Username and Password authorization (to get a token)
        User user = checkBasicAuthorization(request);
        if (user != null) {
          doReturnNewToken(thisApp, user, httpServletRequest, servletResponse);
          return;
        }
        doUnauthorized(servletResponse);
        return;
      }

      // Determine if the API is available as a guest, or if an authenticated user is required
      boolean siteIsOnline = LoadSitePropertyCommand.loadByNameAsBoolean("site.online");
      if (!siteIsOnline) {
        // An authenticated user is required when the site is not online
        doUnauthorized(servletResponse);
        return;
      }

      // Limit the number of hits per minute based on the successful use of the api key
      if (!RateLimitCommand.isAppAllowedRightNow(thisApp)) {
        do429(servletResponse);
        return;
      }

      // Demote to guest access
      User user = new User();
      user.setId(UserSession.GUEST_ID);

      // Let the REST service process this request
      request.setAttribute(RequestConstants.REST_USER, user);
      chain.doFilter(request, servletResponse);
      return;
    }

    // Validate the token
    LOG.debug("Found token: " + token);
    UserToken userToken = AuthenticateLoginCommand.getValidToken(token);
    if (userToken == null) {
      doExpiredToken(servletResponse);
      return;
    }

    User user = AuthenticateLoginCommand.getAuthenticatedUser(userToken);
    if (user != null) {

      LOG.debug("Got a token user: " + user.getId());

      // Limit the number of hits per minute based on the user and api key
      if (!RateLimitCommand.isAppUserAllowedRightNow(thisApp, user.getId())) {
        do429(servletResponse);
        return;
      }

      // If this request is the first for today, then record a new login and session
      if (userToken.getCreated().before(Timestamp.valueOf(LocalDate.now().atStartOfDay()))) {

        // Update the session details
        int twoWeeksSecondsInt = 14 * 24 * 60 * 60;
        AuthenticateLoginCommand.extendTokenExpiration(token, twoWeeksSecondsInt);

        // Start a new session
        String userAgent = httpServletRequest.getHeader("USER-AGENT");
        UserSession userSession = CreateSessionCommand.createSession(API_SOURCE,
            httpServletRequest.getSession().getId(), ipAddress, null, userAgent);
        userSession.setAppId(thisApp.getId());
        userSession.login(user);
        SaveSessionCommand.saveSession(userSession);

        // Track the login
        UserLogin userLogin = new UserLogin();
        userLogin.setSource(API_SOURCE);
        userLogin.setUserId(user.getId());
        userLogin.setIpAddress(request.getRemoteAddr());
        userLogin.setSessionId(httpServletRequest.getSession().getId());
        userLogin.setUserAgent(httpServletRequest.getHeader("USER-AGENT"));
        UserLoginRepository.save(userLogin);
      } else {

        // @todo consider reasons when there is a new session
        // if there are no requests in last hour, create a session
        // if there is a session header change, create a session

      }

      // Let the REST service process this request
      request.setAttribute(RequestConstants.REST_USER, user);
      chain.doFilter(request, servletResponse);
      return;
    }

    // User is invalid
    doExpiredToken(servletResponse);
  }

  private void doRecordSession(App app, HttpServletRequest httpServletRequest, ServletResponse response)
      throws IOException {
    // Start a new session
    String ipAddress = httpServletRequest.getRemoteAddr();
    String userAgent = httpServletRequest.getHeader("USER-AGENT");
    UserSession userSession = CreateSessionCommand.createSession(API_SOURCE, httpServletRequest.getSession().getId(),
        ipAddress, null, userAgent);
    userSession.setAppId(app.getId());
    SaveSessionCommand.saveSession(userSession);

    // Make a response
    String json = "{\n" +
        "\"type\":\"session\",\n" +
        "\"id\":\"" + httpServletRequest.getSession().getId() + "\"\n" +
        "}";
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.setContentLength(json.length());
    PrintWriter out = response.getWriter();
    out.print(json);
    out.flush();
  }

  private void doReturnNewToken(App app, User user, HttpServletRequest httpServletRequest, ServletResponse response)
      throws IOException {

    // Start a new session
    String ipAddress = httpServletRequest.getRemoteAddr();
    String userAgent = httpServletRequest.getHeader("USER-AGENT");
    UserSession userSession = CreateSessionCommand.createSession(API_SOURCE, httpServletRequest.getSession().getId(),
        ipAddress, null, userAgent);
    userSession.setAppId(app.getId());
    SaveSessionCommand.saveSession(userSession);

    // Track the login
    UserLogin userLogin = new UserLogin();
    userLogin.setSource(API_SOURCE);
    userLogin.setUserId(user.getId());
    userLogin.setIpAddress(httpServletRequest.getRemoteAddr());
    userLogin.setSessionId(httpServletRequest.getSession().getId());
    userLogin.setUserAgent(httpServletRequest.getHeader("USER-AGENT"));
    UserLoginRepository.save(userLogin);

    // Create a 30-day token
    String loginToken = "API-" + UUID.randomUUID().toString() + user.getId();
    long tokenExpirationInSeconds = 30 * 24 * 60 * 60;
    UserToken userToken = new UserToken();
    userToken.setUserId(user.getId());
    userToken.setLoginId(userLogin.getId());
    userToken.setToken(loginToken);
    userToken.setExpires(new Timestamp(System.currentTimeMillis() + (tokenExpirationInSeconds * 1000)));
    UserTokenRepository.add(userToken);

    // Make a response
    // https://tools.ietf.org/html/rfc6750
    String json = "{\n" +
        "\"access_token\":\"" + loginToken + "\",\n" +
        "\"token_type\":\"bearer\",\n" +
        "\"expires_in\":" + tokenExpirationInSeconds + ",\n" +
        //        "\"refresh_token\":\"IwOGYzYTlmM2YxOTQ5MGE3YmNmMDFkNTVk\",\n" +
        "\"name\":\"" + JsonCommand.toJson(UserCommand.name(user)) + "\",\n" +
        "\"first_name\":\"" + JsonCommand.toJson(user.getFirstName()) + "\",\n" +
        "\"last_name\":\"" + JsonCommand.toJson(user.getLastName()) + "\",\n" +
        "\"scope\":\"create\"\n" +
        "}";
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.setContentLength(json.length());
    PrintWriter out = response.getWriter();
    out.print(json);
    out.flush();
  }

  private User checkBasicAuthorization(ServletRequest servletRequest) {
    HttpServletRequest request = (HttpServletRequest) servletRequest;
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null) {
      return null;
    }
    StringTokenizer st = new StringTokenizer(authHeader);
    if (!st.hasMoreTokens()) {
      return null;
    }
    String basic = st.nextToken();
    if (!basic.equalsIgnoreCase("Basic")) {
      LOG.debug("Client must use BASIC authentication");
      return null;
    }
    try {
      String credentials = new String(Base64.decodeBase64(st.nextToken()), "UTF-8");
      int p = credentials.indexOf(":");
      if (p == -1) {
        LOG.debug("Client did not specify credentials");
        return null;
      }

      // Attempt the login
      String email = credentials.substring(0, p).trim().toLowerCase();
      String password = credentials.substring(p + 1).trim();
      try {
        return AuthenticateLoginCommand.getAuthenticatedUser(email, password, request.getRemoteAddr());
      } catch (DataException | LoginException e) {
        LOG.debug("Login error: " + e.getMessage());
        return null;
      }
    } catch (UnsupportedEncodingException e) {
      LOG.error("UnsupportedEncodingException: " + e.getMessage());
      return null;
    }
  }

  private String checkBearerAuthorization(ServletRequest servletRequest) {
    HttpServletRequest request = (HttpServletRequest) servletRequest;
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null) {
      return null;
    }
    LOG.debug("checkBearerAuthorization header: " + authHeader);
    StringTokenizer st = new StringTokenizer(authHeader);
    if (!st.hasMoreTokens()) {
      return null;
    }
    String bearer = st.nextToken();
    if (!bearer.equalsIgnoreCase("Bearer")) {
      LOG.debug("Client must use BEARER authentication");
      return null;
    }
    return st.nextToken();
  }

  private void doUnauthorized(ServletResponse servletResponse) throws IOException {
    LOG.debug("Returning 401...");
    HttpServletResponse response = (HttpServletResponse) servletResponse;
    response.setHeader("WWW-Authenticate", "Basic realm=\"Protected\"");
    RestServlet.sendError(response, SC_UNAUTHORIZED, "Unauthorized");
  }

  private void doExpiredToken(ServletResponse servletResponse) throws IOException {
    // https://tools.ietf.org/html/rfc6750
    HttpServletResponse response = (HttpServletResponse) servletResponse;
    response.setHeader("WWW-Authenticate",
        "Bearer realm=\"Protected\", " +
            "error=\"invalid_token\", " +
            "error_description=\"The access token expired\"");
    RestServlet.sendError(response, SC_UNAUTHORIZED, "Unauthorized");
  }

  private void do301(ServletResponse servletResponse, String redirectLocation) throws IOException {
    HttpServletResponse response = (HttpServletResponse) servletResponse;
    response.setHeader("Location", redirectLocation);
    RestServlet.sendError(response, SC_MOVED_PERMANENTLY, redirectLocation);
  }

  private void do404(ServletResponse servletResponse) throws IOException {
    HttpServletResponse response = (HttpServletResponse) servletResponse;
    RestServlet.sendError(response, SC_NOT_FOUND, "Not found");
  }

  private void do429(ServletResponse servletResponse) throws IOException {
    HttpServletResponse response = (HttpServletResponse) servletResponse;
    RestServlet.sendError(response, 429, "Too many requests");
  }
}
