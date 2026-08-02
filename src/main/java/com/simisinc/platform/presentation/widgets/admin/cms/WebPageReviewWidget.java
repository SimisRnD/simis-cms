/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.presentation.widgets.admin.cms;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.application.cms.EditorPermissionCommand;
import com.simisinc.platform.application.login.StepUpAuthCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.cache.PublishEventCachePurgeHandler;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * The /admin/web-page-review admin page (issue #407): extends the governed publish workflow
 * ({@link ContentReviewCommand}, originally built for content blocks) to web pages -- submit a
 * pending page-layout draft for review, approve it (with step-up re-authentication, mirroring
 * content's own {@code performContentApproval}) and publish, or reject it back to the author.
 *
 * <p>When {@code webPage.review.required} is off, this also offers the classic direct-publish
 * affordance ({@link ContentReviewCommand#offerFor}'s {@code OFFER_PUBLISH}), matching the parity
 * content blocks have between {@code content.jsp}'s inline overlay and this dedicated admin page.
 *
 * @author elizabeth houser
 */
public class WebPageReviewWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908892L;

  static String JSP = "/admin/web-page-review.jsp";

  public WidgetContext execute(WidgetContext context) {

    if (!EditorPermissionCommand.canEditContent(context.getUserSession())) {
      return context;
    }

    WebPage webPage = loadWebPage(context);
    if (webPage == null) {
      context.setErrorMessage("Web page was not found");
      return context;
    }
    context.getRequest().setAttribute("webPage", webPage);

    boolean reviewRequired = LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required");
    context.getRequest().setAttribute("reviewOffer",
        ContentReviewCommand.offerFor(webPage, context.getUserId(), reviewRequired));
    context.getRequest().setAttribute("reviewStatus", ContentReviewCommand.listStatusLabel(webPage));

    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext action(WidgetContext context) {

    if (!EditorPermissionCommand.canEditContent(context.getUserSession())) {
      return context;
    }
    WebPage webPage = loadWebPage(context);
    if (webPage == null) {
      context.setErrorMessage("Web page was not found");
      return context;
    }

    String action = context.getParameter("action");
    if ("submitForReview".equals(action)) {
      return submitForReview(context, webPage);
    } else if ("reject".equals(action)) {
      return rejectDraft(context, webPage);
    } else if ("publish".equals(action)) {
      boolean reviewRequired = LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required");
      return publishDirectly(context, webPage, reviewRequired);
    }
    return context;
  }

  public WidgetContext post(WidgetContext context) {
    // The approve form is POSTed so the step-up credential never travels over GET (matches
    // ContentWidget.post()). execute() re-loads webPage/reviewOffer fresh so a step-up failure can
    // re-render this same page with the approve form still present.
    if ("approve".equals(context.getParameter("action"))) {
      execute(context);
      if (!context.hasJsp()) {
        return context;
      }
      return performApproval(context);
    }
    return context;
  }

  private WebPage loadWebPage(WidgetContext context) {
    long webPageId = context.getParameterAsLong("webPageId", -1);
    return webPageId > -1 ? WebPageRepository.findById(webPageId) : null;
  }

  private WidgetContext submitForReview(WidgetContext context, WebPage webPage) {
    try {
      ContentReviewCommand.submitForReview(webPage, context.getUserId());
      WebPageRepository.save(webPage);
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.submit", AuditEventCommand.SUCCESS,
          "web_page", String.valueOf(webPage.getId()), webPage.getLink(), null);
      context.setSuccessMessage("The page was submitted for review");
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
    }
    return context;
  }

  private WidgetContext rejectDraft(WidgetContext context, WebPage webPage) {
    try {
      ContentReviewCommand.reject(webPage, context.getUserId());
      WebPageRepository.save(webPage);
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.reject", AuditEventCommand.SUCCESS,
          "web_page", String.valueOf(webPage.getId()), webPage.getLink(), null);
      context.setSuccessMessage("The page was returned to the author");
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
    }
    return context;
  }

  private WidgetContext publishDirectly(WidgetContext context, WebPage webPage, boolean reviewRequired) {
    if (!webPage.hasDraftContent()) {
      return context;
    }
    if (!ContentReviewCommand.mayPublish(webPage, reviewRequired)) {
      // Not reachable through this widget's own UI (offerFor() would not have offered "publish"),
      // but WebPageRepository.publish() has no gate of its own, so this call site checks explicitly
      // too -- the same defense-in-depth PageServlet.publishDraft applies.
      context.setErrorMessage("This page must be submitted for review and approved before it can be published");
      return context;
    }
    WebPageRepository.publish(webPage);
    AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.publish", AuditEventCommand.SUCCESS,
        "web_page", String.valueOf(webPage.getId()), webPage.getLink(), null);
    PublishEventCachePurgeHandler.onPageUpdated(webPage);
    context.setSuccessMessage("The page was published");
    return context;
  }

  /**
   * Handles the POST-based approval with step-up re-authentication, mirroring
   * {@code ContentHtmlCommand.performContentApproval}/{@code approveContent} exactly. Must be
   * called after {@code execute()} has already set the JSP on the context, so a step-up prompt can
   * re-render the same page.
   */
  private WidgetContext performApproval(WidgetContext context) {
    WebPage webPage = loadWebPage(context);
    if (webPage == null) {
      return context;
    }
    String stepUpCredential = context.getParameter("stepUpCredential");
    if (!StepUpAuthCommand.isValid(context.getUserSession())) {
      if (StringUtils.isBlank(stepUpCredential)) {
        context.addSharedRequestValue("stepUpRequired", "true");
        return context;
      }
      User actingUser = LoadUserCommand.loadUser(context.getUserId());
      if (!StepUpAuthCommand.verify(context.getUserSession(), actingUser, stepUpCredential)) {
        context.setErrorMessage("Re-authentication failed. Enter your password or authenticator code.");
        context.addSharedRequestValue("stepUpRequired", "true");
        return context;
      }
    }
    String releaseReference = context.getParameter("releaseReference");
    try {
      // approve() enforces separation of duties (the approver cannot be the submitter); approval
      // then promotes the draft to live and records the named approver + release authority in the
      // audit trail.
      ContentReviewCommand.approve(webPage, context.getUserId(), releaseReference);
      WebPageRepository.publish(webPage);
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.approve", AuditEventCommand.SUCCESS,
          "web_page", String.valueOf(webPage.getId()), webPage.getLink(),
          StringUtils.isNotBlank(releaseReference) ? "release authority: " + releaseReference : null);
      PublishEventCachePurgeHandler.onPageUpdated(webPage);
      context.setSuccessMessage("The page was approved and published");
    } catch (DataException e) {
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.approve", AuditEventCommand.FAILURE,
          "web_page", String.valueOf(webPage.getId()), webPage.getLink(), e.getMessage());
      context.setErrorMessage(e.getMessage());
    }
    return context;
  }
}
