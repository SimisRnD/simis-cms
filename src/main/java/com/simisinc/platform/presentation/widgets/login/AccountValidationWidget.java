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

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.PasswordPolicyCommand;
import com.simisinc.platform.application.UserPasswordCommand;
import com.simisinc.platform.application.login.LogoutCommand;
import com.simisinc.platform.domain.events.cms.UserRegisteredEvent;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.UnsuspendRequest;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.login.UnsuspendRequestRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 6/21/18 5:15 PM
 */
public class AccountValidationWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/login/account-validated.jsp";
  static String NOT_FOUND_JSP = "/login/account-confirmation-not-found.jsp";
  static String FINISHED_JSP = "/login/account-confirmation-finished.jsp";

  static final String BASE_PATH = "/validate-account";

  /**
   * Resolve the account token from the request. Prefer it as a path segment
   * ({@code /validate-account/<token>}): a unique path per token, so an intermediary that caches by
   * path -- ignoring the query string and no-store headers, as some corporate proxies do -- cannot
   * serve one token's page (often a stale "expired" error) for another. Fall back to the legacy
   * {@code ?confirmation=<token>} query parameter so links already delivered before this change, and
   * the hidden field the password form posts back, keep working. See issue #1812.
   */
  static String resolveConfirmation(String uri, String queryConfirmation) {
    if (uri != null) {
      int idx = uri.indexOf(BASE_PATH + "/");
      if (idx != -1) {
        String segment = uri.substring(idx + BASE_PATH.length() + 1);
        int slash = segment.indexOf('/');
        if (slash != -1) {
          segment = segment.substring(0, slash);
        }
        if (StringUtils.isNotBlank(segment)) {
          return segment;
        }
      }
    }
    return queryConfirmation;
  }

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    String status = context.getParameter("status");
    if ("complete".equals(status)) {
      context.setJsp(FINISHED_JSP);
      return context;
    }

    // Check for an account token
    String confirmation = resolveConfirmation(context.getUri(), context.getParameter("confirmation"));
    if (StringUtils.isBlank(confirmation)) {
      LOG.warn("No account token was found!");
      return null;
    }

    // Match the user
    User user = UserRepository.findByAccountToken(confirmation);
    if (user == null) {
      LOG.warn("No user was found for token!");
      return notFound(context, confirmation);
    }

    // User needs to change their password to login
    if ("new".equals(user.getPassword()) || StringUtils.isNotBlank(user.getAccountToken())) {
      context.getRequest().setAttribute("doPassword", "true");
      context.getRequest().setAttribute("confirmation", confirmation);
    } else {
      // Make an update
      if (user.getValidated() == null) {
        UserRepository.updateValidated(user);
        LOG.debug("User finished registering... " + user.getEmail());
        // Trigger Events
        WorkflowManager.triggerWorkflowForEvent(new UserRegisteredEvent(user, context.getRequest().getRemoteAddr()));
      }
    }

    context.setJsp(JSP);
    return context;
  }

  /**
   * Render the "link did not work" page, distinguishing a lapsed link from an unrecognised one
   * (#1836).
   *
   * <p>The page previously guessed "already validated, or the request expired" for every failure.
   * At least five causes land here -- never issued, superseded by a newer link, expired, already
   * used, or a truncated URL -- and the guess was frequently wrong, which sent people looking for
   * the wrong remedy.
   *
   * <p>Only "expired" is separable from the data: an account holds a single token, so a superseded
   * link leaves no trace to distinguish it from one that never existed. The unknown case therefore
   * names the realistic causes and points at the newest email rather than asserting one.
   */
  private WidgetContext notFound(WidgetContext context, String confirmation) {
    boolean expired = UserRepository.findExpiredByAccountToken(confirmation) != null;
    context.getRequest().setAttribute("notFoundReason", expired ? "expired" : "unknown");
    context.setJsp(NOT_FOUND_JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    // Don't accept multiple form posts
    context.getUserSession().renewFormToken();

    // Check for an account token
    String confirmation = resolveConfirmation(context.getUri(), context.getParameter("confirmation"));
    if (StringUtils.isBlank(confirmation)) {
      LOG.warn("No account token was found!");
      return null;
    }

    // Match the user
    User user = UserRepository.findByAccountToken(confirmation);
    if (user == null) {
      LOG.warn("No user was found for token!");
      return notFound(context, confirmation);
    }

    // User needs to change their password to login, or they requested to
    if ("new".equals(user.getPassword()) || StringUtils.isNotBlank(user.getAccountToken())) {
      String password = context.getParameter("password");
      String password2 = context.getParameter("password2");
      if (!StringUtils.equals(password, password2)) {
        context.setWarningMessage("The password fields did not match, please try again");
        context.setRedirect(BASE_PATH + "/" + confirmation);
        return context;
      }
      String passwordViolation = PasswordPolicyCommand.validate(password.trim());
      if (passwordViolation != null) {
        context.setWarningMessage(passwordViolation);
        context.setRedirect(BASE_PATH + "/" + confirmation);
        return context;
      }

      // Capture before either update mutates state, so the audit event below reflects what this
      // completion actually was: a first-time activation, or a returning user's password reset.
      boolean wasNotValidated = (user.getValidated() == null);

      // Hash the password
      user.setPassword(UserPasswordCommand.hash(password));
      UserRepository.updatePassword(user);

      // Make an update
      if (wasNotValidated) {
        UserRepository.updateValidated(user);
        LOG.debug("User was validated... " + user.getEmail());
        // Trigger Events
        WorkflowManager.triggerWorkflowForEvent(new UserRegisteredEvent(user, context.getRequest().getRemoteAddr()));
        AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.registered",
            AuditEventCommand.SUCCESS, "user", String.valueOf(user.getId()), user.getEmail(), "self-service");
      } else {
        // #492: closes the gap where only the admin's *request* to reset a password was audited,
        // never the user's completion of it (self-service ForgotPasswordWidget flow).
        AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.password.reset.completed",
            AuditEventCommand.SUCCESS, "user", String.valueOf(user.getId()), user.getEmail(), "self-service");

        // #492 Phase 3: this completion may ALSO be the forced re-verification step of a
        // maker-checker unsuspend approval -- the account was already restored (enabled=true) with
        // its old password invalidated back when a second admin approved it; this is the moment the
        // account holder proves control and sets a real new one. Fired in addition to, never instead
        // of, the unconditional event above (which already covers a plain self-service reset).
        UnsuspendRequest pendingReverification = UnsuspendRequestRepository.findApprovedByTargetUserId(user.getId());
        if (pendingReverification != null) {
          UnsuspendRequestRepository.markReverified(pendingReverification.getId());
          AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.unsuspend.reverified",
              AuditEventCommand.SUCCESS, "user", String.valueOf(user.getId()), user.getEmail(),
              "requestId=" + pendingReverification.getId());
        }
      }
    }

    // Log the user out
    LogoutCommand.logout(context.getRequest(), context.getResponse());

    context.setRedirect("/validate-account?status=complete");
    return context;
  }
}
