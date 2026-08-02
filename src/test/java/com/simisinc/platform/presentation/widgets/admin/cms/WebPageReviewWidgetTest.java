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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.application.cms.EditorPermissionCommand;
import com.simisinc.platform.application.login.StepUpAuthCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.cache.PublishEventCachePurgeHandler;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * @author elizabeth houser
 */
class WebPageReviewWidgetTest extends WidgetBase {

  private static WebPage webPage(long id) {
    WebPage record = new WebPage();
    record.setId(id);
    record.setLink("/example");
    record.setDraftPageXml("<xml>draft</xml>");
    return record;
  }

  @Test
  void executeLoadsWebPageAndReviewStateForAnAuthorizedUser() {
    addQueryParameter(widgetContext, "webPageId", "9");
    WebPage page = webPage(9L);

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<WebPageRepository> pageRepo = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      pageRepo.when(() -> WebPageRepository.findById(9L)).thenReturn(page);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required")).thenReturn(true);

      WidgetContext result = new WebPageReviewWidget().execute(widgetContext);

      assertEquals("/admin/web-page-review.jsp", result.getJsp());
      assertEquals(page, result.getRequest().getAttribute("webPage"));
      assertEquals(ContentReviewCommand.OFFER_SUBMIT, result.getRequest().getAttribute("reviewOffer"));
    }
  }

  @Test
  void executeDeniesAUserWithoutEditPermission() {
    addQueryParameter(widgetContext, "webPageId", "9");

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<WebPageRepository> pageRepo = mockStatic(WebPageRepository.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(false);

      WidgetContext result = new WebPageReviewWidget().execute(widgetContext);

      assertNull(result.getJsp());
      pageRepo.verifyNoInteractions();
    }
  }

  @Test
  void actionSubmitForReviewSavesAndAudits() {
    addQueryParameter(widgetContext, "webPageId", "9");
    addQueryParameter(widgetContext, "action", "submitForReview");
    WebPage page = webPage(9L);

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<WebPageRepository> pageRepo = mockStatic(WebPageRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      pageRepo.when(() -> WebPageRepository.findById(9L)).thenReturn(page);

      WidgetContext result = new WebPageReviewWidget().action(widgetContext);

      assertEquals(ContentReviewCommand.STATUS_SUBMITTED, page.getDraftStatus());
      pageRepo.verify(() -> WebPageRepository.save(page));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONTENT), eq("content.submit"),
          eq(AuditEventCommand.SUCCESS), eq("web_page"), eq("9"), eq("/example"), any()));
      assertEquals("The page was submitted for review", result.getSuccessMessage());
    }
  }

  @Test
  void actionRejectSendsItBackToTheAuthor() {
    addQueryParameter(widgetContext, "webPageId", "9");
    addQueryParameter(widgetContext, "action", "reject");
    WebPage page = webPage(9L);
    page.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    page.setSubmittedBy(5L);

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<WebPageRepository> pageRepo = mockStatic(WebPageRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      pageRepo.when(() -> WebPageRepository.findById(9L)).thenReturn(page);
      // WidgetBase's default logged-in test user (id 1) is not the submitter (5), so separation of
      // duties is satisfied without needing to override the session's user id.

      WidgetContext result = new WebPageReviewWidget().action(widgetContext);

      assertEquals(ContentReviewCommand.STATUS_DRAFT, page.getDraftStatus());
      pageRepo.verify(() -> WebPageRepository.save(page));
      assertEquals("The page was returned to the author", result.getSuccessMessage());
    }
  }

  @Test
  void actionPublishDirectlyWhenReviewIsNotRequired() {
    addQueryParameter(widgetContext, "webPageId", "9");
    addQueryParameter(widgetContext, "action", "publish");
    WebPage page = webPage(9L);

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<WebPageRepository> pageRepo = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class);
        MockedStatic<PublishEventCachePurgeHandler> purge = mockStatic(PublishEventCachePurgeHandler.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      pageRepo.when(() -> WebPageRepository.findById(9L)).thenReturn(page);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required")).thenReturn(false);

      WidgetContext result = new WebPageReviewWidget().action(widgetContext);

      pageRepo.verify(() -> WebPageRepository.publish(page));
      purge.verify(() -> PublishEventCachePurgeHandler.onPageUpdated(page));
      assertEquals("The page was published", result.getSuccessMessage());
    }
  }

  @Test
  void actionPublishIsBlockedWhenReviewIsRequiredAndNotApproved() {
    addQueryParameter(widgetContext, "webPageId", "9");
    addQueryParameter(widgetContext, "action", "publish");
    WebPage page = webPage(9L);

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<WebPageRepository> pageRepo = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      pageRepo.when(() -> WebPageRepository.findById(9L)).thenReturn(page);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required")).thenReturn(true);

      WidgetContext result = new WebPageReviewWidget().action(widgetContext);

      pageRepo.verify(() -> WebPageRepository.publish(any()), never());
      assertEquals("This page must be submitted for review and approved before it can be published",
          result.getErrorMessage());
    }
  }

  @Test
  void postApprovePublishesAndPurgesWhenStepUpIsAlreadySatisfied() {
    addQueryParameter(widgetContext, "webPageId", "9");
    addQueryParameter(widgetContext, "action", "approve");
    addQueryParameter(widgetContext, "releaseReference", "CR-1234");
    WebPage page = webPage(9L);

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<WebPageRepository> pageRepo = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<StepUpAuthCommand> stepUpAuth = mockStatic(StepUpAuthCommand.class);
        MockedStatic<ContentReviewCommand> contentReview = mockStatic(ContentReviewCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class);
        MockedStatic<PublishEventCachePurgeHandler> purge = mockStatic(PublishEventCachePurgeHandler.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      pageRepo.when(() -> WebPageRepository.findById(9L)).thenReturn(page);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required")).thenReturn(true);
      stepUpAuth.when(() -> StepUpAuthCommand.isValid(any())).thenReturn(true);
      contentReview.when(() -> ContentReviewCommand.offerFor(any(), anyLong(), eq(true)))
          .thenReturn(ContentReviewCommand.OFFER_DECIDE);

      WidgetContext result = new WebPageReviewWidget().post(widgetContext);

      contentReview.verify(() -> ContentReviewCommand.approve(page, widgetContext.getUserId(), "CR-1234"));
      pageRepo.verify(() -> WebPageRepository.publish(page));
      purge.verify(() -> PublishEventCachePurgeHandler.onPageUpdated(page));
      assertEquals("The page was approved and published", result.getSuccessMessage());
    }
  }

  @Test
  void postApproveRequiresStepUpBeforeApproving() {
    addQueryParameter(widgetContext, "webPageId", "9");
    addQueryParameter(widgetContext, "action", "approve");
    WebPage page = webPage(9L);

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<WebPageRepository> pageRepo = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<StepUpAuthCommand> stepUpAuth = mockStatic(StepUpAuthCommand.class);
        MockedStatic<ContentReviewCommand> contentReview = mockStatic(ContentReviewCommand.class);
        MockedStatic<PublishEventCachePurgeHandler> purge = mockStatic(PublishEventCachePurgeHandler.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      pageRepo.when(() -> WebPageRepository.findById(9L)).thenReturn(page);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required")).thenReturn(true);
      stepUpAuth.when(() -> StepUpAuthCommand.isValid(any())).thenReturn(false);
      contentReview.when(() -> ContentReviewCommand.offerFor(any(), anyLong(), eq(true)))
          .thenReturn(ContentReviewCommand.OFFER_DECIDE);

      new WebPageReviewWidget().post(widgetContext);

      contentReview.verify(() -> ContentReviewCommand.approve(any(), anyLong(), any()), never());
      pageRepo.verify(() -> WebPageRepository.publish(any()), never());
      purge.verifyNoInteractions();
    }
  }

  @Test
  void postApproveFailsWhenSeparationOfDutiesIsViolated() {
    addQueryParameter(widgetContext, "webPageId", "9");
    addQueryParameter(widgetContext, "action", "approve");
    WebPage page = webPage(9L);

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<WebPageRepository> pageRepo = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<StepUpAuthCommand> stepUpAuth = mockStatic(StepUpAuthCommand.class);
        MockedStatic<ContentReviewCommand> contentReview = mockStatic(ContentReviewCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class);
        MockedStatic<PublishEventCachePurgeHandler> purge = mockStatic(PublishEventCachePurgeHandler.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      pageRepo.when(() -> WebPageRepository.findById(9L)).thenReturn(page);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required")).thenReturn(true);
      stepUpAuth.when(() -> StepUpAuthCommand.isValid(any())).thenReturn(true);
      contentReview.when(() -> ContentReviewCommand.offerFor(any(), anyLong(), eq(true)))
          .thenReturn(ContentReviewCommand.OFFER_DECIDE);
      contentReview.when(() -> ContentReviewCommand.approve(eq(page), anyLong(), any()))
          .thenThrow(new DataException("The approver must be different from the person who submitted the content (separation of duties)"));

      WidgetContext result = new WebPageReviewWidget().post(widgetContext);

      pageRepo.verify(() -> WebPageRepository.publish(any()), never());
      purge.verifyNoInteractions();
      assertEquals("The approver must be different from the person who submitted the content (separation of duties)",
          result.getErrorMessage());
    }
  }

  @Test
  void postIgnoresNonApproveActions() {
    addQueryParameter(widgetContext, "webPageId", "9");
    addQueryParameter(widgetContext, "action", "somethingElse");

    try (MockedStatic<ContentReviewCommand> contentReview = mockStatic(ContentReviewCommand.class)) {
      WidgetContext result = new WebPageReviewWidget().post(widgetContext);

      assertNull(result.getSuccessMessage());
      contentReview.verifyNoInteractions();
    }
  }

  /**
   * End-to-end with the real ContentReviewCommand (not mocked): the reviewer who was the same
   * person as the submitter must be refused, exactly as content blocks already are -- this is the
   * one guarantee that must hold identically for every {@link com.simisinc.platform.domain.model.cms.Reviewable}
   * type, so it's worth proving without mocking the state machine away.
   */
  @Test
  void postApproveRealSeparationOfDutiesBlocksTheSubmitterFromApprovingTheirOwnDraft() {
    addQueryParameter(widgetContext, "webPageId", "9");
    addQueryParameter(widgetContext, "action", "approve");
    WebPage page = webPage(9L);
    page.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    page.setSubmittedBy(widgetContext.getUserId());

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<WebPageRepository> pageRepo = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<StepUpAuthCommand> stepUpAuth = mockStatic(StepUpAuthCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      pageRepo.when(() -> WebPageRepository.findById(9L)).thenReturn(page);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required")).thenReturn(true);
      stepUpAuth.when(() -> StepUpAuthCommand.isValid(any())).thenReturn(true);

      WidgetContext result = new WebPageReviewWidget().post(widgetContext);

      pageRepo.verify(() -> WebPageRepository.publish(any()), never());
      assertEquals("The approver must be different from the person who submitted the content (separation of duties)",
          result.getErrorMessage());
    }
  }
}
