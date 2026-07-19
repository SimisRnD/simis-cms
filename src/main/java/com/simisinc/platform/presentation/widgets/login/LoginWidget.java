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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.oauth.OAuthRequestCommand;
import com.simisinc.platform.application.login.AuthenticateLoginCommand;
import com.simisinc.platform.application.login.TotpCommand;
import com.simisinc.platform.application.login.UserMfaRecoveryCodeCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.domain.model.login.UserLogin;
import com.simisinc.platform.domain.model.login.UserToken;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserTokenRepository;
import com.simisinc.platform.presentation.controller.CookieConstants;
import com.simisinc.platform.presentation.controller.SessionConstants;
import com.simisinc.platform.presentation.controller.UserSession;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import javax.security.auth.login.LoginException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;
import java.sql.Timestamp;
import java.util.UUID;

import static com.simisinc.platform.presentation.controller.UserSession.WEB_SOURCE;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/6/18 9:26 PM
 */
public class LoginWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/login/login-form.jsp";

  // How long a user has to enter their MFA code after passing the password step before the
  // pending state expires and they must sign in with their password again.
  static final long MFA_PENDING_TIMEOUT_MS = 5 * 60 * 1000L;

  public WidgetContext execute(WidgetContext context) {
    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    if (OAuthRequestCommand.isEnabled()) {
      context.getRequest().setAttribute("oAuthProvider", LoadSitePropertyCommand.loadByName("oauth.provider"));
    }
    // If a user passed the password check and is waiting to enter an MFA code, show that prompt --
    // unless the pending state has timed out, in which case clear it and show the normal sign-in.
    HttpSession session = context.getRequest().getSession();
    if (session.getAttribute(SessionConstants.MFA_PENDING_USER_ID) != null) {
      if (isMfaPendingExpired(session)) {
        clearMfaPending(session);
      } else {
        context.getRequest().setAttribute("mfaRequired", "true");
      }
    }
    context.setJsp(JSP);
    return context;
  }

  /** True when an MFA-pending state exists but has passed the timeout window (or was never stamped). */
  private boolean isMfaPendingExpired(HttpSession session) {
    if (session.getAttribute(SessionConstants.MFA_PENDING_USER_ID) == null) {
      return false;
    }
    Long pendingSince = (Long) session.getAttribute(SessionConstants.MFA_PENDING_SINCE);
    return pendingSince == null || System.currentTimeMillis() - pendingSince > MFA_PENDING_TIMEOUT_MS;
  }

  private void clearMfaPending(HttpSession session) {
    session.removeAttribute(SessionConstants.MFA_PENDING_USER_ID);
    session.removeAttribute(SessionConstants.MFA_PENDING_SINCE);
  }

  public WidgetContext post(WidgetContext context) {

    boolean stayLoggedIn = "on".equals(context.getParameter("stayLoggedIn"));
    HttpSession httpSession = context.getRequest().getSession();

    // Second step: a user who already passed the password check is submitting their MFA code
    Long mfaPendingUserId = (Long) httpSession.getAttribute(SessionConstants.MFA_PENDING_USER_ID);
    if (mfaPendingUserId != null) {
      // Expire a stale MFA-pending state rather than leaving the second-factor gate open
      // indefinitely -- the user must re-enter their password to start over.
      if (isMfaPendingExpired(httpSession)) {
        clearMfaPending(httpSession);
        context.setErrorMessage("Your sign-in timed out. Please enter your email and password again.");
        return context;
      }
      // Throttle guesses so the second factor cannot be brute-forced, mirroring the password step.
      // The account is pinned by the pending user id, so key the per-account limit on that.
      String ipAddress = context.getRequest().getRemoteAddr();
      String rateLimitKey = "mfa:" + mfaPendingUserId;
      if (!RateLimitCommand.isUsernameAllowedRightNow(rateLimitKey, false)
          || (StringUtils.isNotBlank(ipAddress) && !RateLimitCommand.isIpAllowedRightNow(ipAddress, false))) {
        context.setErrorMessage(RateLimitCommand.INVALID_ATTEMPTS);
        context.getRequest().setAttribute("mfaRequired", "true");
        return context;
      }
      User user = UserRepository.findByUserId(mfaPendingUserId);
      String code = context.getParameter("code");
      // Accept either a current TOTP code or a single-use recovery code
      boolean verified = user != null && user.getMfaEnabled()
          && (TotpCommand.verifyCode(user.getMfaSecret(), code)
              || UserMfaRecoveryCodeCommand.consume(user, code));
      if (!verified) {
        // Record the failed attempt so repeated guesses get locked out
        RateLimitCommand.isUsernameAllowedRightNow(rateLimitKey, true);
        if (StringUtils.isNotBlank(ipAddress)) {
          RateLimitCommand.isIpAllowedRightNow(ipAddress, true);
        }
        context.setErrorMessage("That code was invalid. Enter a code from your authenticator app, or one of your recovery codes.");
        context.getRequest().setAttribute("mfaRequired", "true");
        return context;
      }
      clearMfaPending(httpSession);
      return finalizeLogin(context, user, stayLoggedIn);
    }

    // First step: verify the email and password
    String email = context.getParameter("email");
    String password = context.getParameter("password");
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

    // If MFA is enabled, do not establish the session yet -- hold the login and require a valid code first
    if (user.getMfaEnabled() && StringUtils.isNotBlank(user.getMfaSecret())) {
      httpSession.setAttribute(SessionConstants.MFA_PENDING_USER_ID, user.getId());
      httpSession.setAttribute(SessionConstants.MFA_PENDING_SINCE, System.currentTimeMillis());
      context.getRequest().setAttribute("mfaRequired", "true");
      return context;
    }

    return finalizeLogin(context, user, stayLoggedIn);
  }

  private WidgetContext finalizeLogin(WidgetContext context, User user, boolean stayLoggedIn) {

    // Update the user's session
    UserSession userSession = (UserSession) context.getRequest().getSession().getAttribute(SessionConstants.USER);
    // Rotate the servlet session id now that the user has authenticated: any session id an
    // attacker may have fixed on the browser before login is invalidated (session-fixation defense).
    context.getRequest().changeSessionId();
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
