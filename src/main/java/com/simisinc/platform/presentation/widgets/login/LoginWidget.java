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

package com.simisinc.platform.presentation.widgets.login;

import static com.simisinc.platform.presentation.controller.UserSession.WEB_SOURCE;

import java.sql.Timestamp;
import java.util.UUID;

import javax.security.auth.login.LoginException;
import javax.servlet.http.Cookie;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.login.AuthenticateLoginCommand;
import com.simisinc.platform.application.oauth.OAuthConfigurationCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.UserLogin;
import com.simisinc.platform.domain.model.login.UserToken;
import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserTokenRepository;
import com.simisinc.platform.presentation.controller.CookieConstants;
import com.simisinc.platform.presentation.controller.SessionConstants;
import com.simisinc.platform.presentation.controller.UserSession;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/6/18 9:26 PM
 */
public class LoginWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/login/login-form.jsp";

  public WidgetContext execute(WidgetContext context) {
    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    if (OAuthConfigurationCommand.isEnabled()) {
      context.getRequest().setAttribute("oAuthProvider", LoadSitePropertyCommand.loadByName("oauth.provider"));
    }
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    // Populate the fields
    String email = context.getParameter("email");
    String password = context.getParameter("password");
    boolean stayLoggedIn = "on".equals(context.getParameter("stayLoggedIn"));

    // Attempt the login
    User user = null;
    try {
      if (StringUtils.isBlank(email) || StringUtils.isBlank(password)) {
        throw new DataException("Please check the fields and try again");
      }
      user = AuthenticateLoginCommand.getAuthenticatedUser(email.trim().toLowerCase(), password, context.getRequest().getRemoteAddr());
    } catch (DataException | LoginException e) {
      context.setErrorMessage(e.getMessage());
      return context;
    }

    // Update the user's session
    UserSession userSession = (UserSession) context.getRequest().getSession().getAttribute(SessionConstants.USER);
    if (user.getTimeZone() != null) {
      // Override the system timezone for this user session
//      Config.set(context.getRequest(), Config.FMT_TIME_ZONE, user.getTimeZone());
    }
    userSession.login(user);

    // Track the login
    UserLogin userLogin = new UserLogin();
    userLogin.setSource(WEB_SOURCE);
    userLogin.setUserId(user.getId());
    userLogin.setIpAddress(context.getRequest().getRemoteAddr());
    userLogin.setSessionId(userSession.getSessionId());
    userLogin.setUserAgent(context.getRequest().getHeader("USER-AGENT"));
    UserLoginRepository.save(userLogin);

    // Optionally store a token for future access
    int twoWeeksSecondsInt = 14 * 24 * 60 * 60;
    String loginToken = UUID.randomUUID().toString() + user.getId();
    if (stayLoggedIn) {
      UserToken userToken = new UserToken();
      userToken.setUserId(user.getId());
      userToken.setLoginId(userLogin.getId());
      userToken.setToken(loginToken);
      userToken.setExpires(new Timestamp(System.currentTimeMillis() + (twoWeeksSecondsInt * 1000)));
      UserTokenRepository.add(userToken);
    }

    // Set the browser cookie
    Cookie cookie = new Cookie(CookieConstants.USER_TOKEN, loginToken);
    if (context.isSecure()) {
      cookie.setSecure(true);
    }
    cookie.setHttpOnly(true);
    cookie.setPath("/");
    if (stayLoggedIn) {
      cookie.setMaxAge(twoWeeksSecondsInt);
    } else {
      cookie.setMaxAge(-1);
    }
    context.getResponse().addCookie(cookie);

    // Redirect to the success page
    boolean siteIsOnline = LoadSitePropertyCommand.loadByNameAsBoolean("site.online");
    if (siteIsOnline) {
      // Site is open, so go to the user's page
      context.setRedirect("/my-page");
    } else {
      // Site is closed, so go to the main page
      context.setRedirect("/");
    }
    return context;
  }
}
