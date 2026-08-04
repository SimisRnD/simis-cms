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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * @author elizabeth houser
 */
class BlogPostReviewWidgetTest extends WidgetBase {

  private static BlogPost blogPost(long id) {
    BlogPost record = new BlogPost();
    record.setId(id);
    record.setBlogId(1L);
    record.setTitle("A Post");
    record.setBody("<p>Some content</p>");
    record.setPublished(null);
    return record;
  }

  @Test
  void executeLoadsBlogPostAndReviewStateForAnAuthorizedUser() {
    addQueryParameter(widgetContext, "blogPostId", "9");
    BlogPost post = blogPost(9L);

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<BlogPostRepository> postRepo = mockStatic(BlogPostRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      postRepo.when(() -> BlogPostRepository.findById(9L)).thenReturn(post);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required")).thenReturn(true);

      WidgetContext result = new BlogPostReviewWidget().execute(widgetContext);

      assertEquals("/admin/blog-post-review.jsp", result.getJsp());
      assertEquals(post, result.getRequest().getAttribute("blogPost"));
      assertEquals(ContentReviewCommand.OFFER_SUBMIT, result.getRequest().getAttribute("reviewOffer"));
    }
  }

  @Test
  void executeDeniesAUserWithoutEditPermission() {
    addQueryParameter(widgetContext, "blogPostId", "9");

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<BlogPostRepository> postRepo = mockStatic(BlogPostRepository.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(false);

      WidgetContext result = new BlogPostReviewWidget().execute(widgetContext);

      assertNull(result.getJsp());
      postRepo.verifyNoInteractions();
    }
  }

  @Test
  void actionSubmitForReviewSavesAndAudits() {
    addQueryParameter(widgetContext, "blogPostId", "9");
    addQueryParameter(widgetContext, "action", "submitForReview");
    BlogPost post = blogPost(9L);

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<BlogPostRepository> postRepo = mockStatic(BlogPostRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      postRepo.when(() -> BlogPostRepository.findById(9L)).thenReturn(post);

      WidgetContext result = new BlogPostReviewWidget().action(widgetContext);

      assertEquals(ContentReviewCommand.STATUS_SUBMITTED, post.getDraftStatus());
      postRepo.verify(() -> BlogPostRepository.save(post));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONTENT), eq("content.submit"),
          eq(AuditEventCommand.SUCCESS), eq("blog_post"), eq("9"), eq("A Post"), any()));
      assertEquals("The post was submitted for review", result.getSuccessMessage());
    }
  }

  @Test
  void actionRejectSendsItBackToTheAuthor() {
    addQueryParameter(widgetContext, "blogPostId", "9");
    addQueryParameter(widgetContext, "action", "reject");
    BlogPost post = blogPost(9L);
    post.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    post.setSubmittedBy(5L);

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<BlogPostRepository> postRepo = mockStatic(BlogPostRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      postRepo.when(() -> BlogPostRepository.findById(9L)).thenReturn(post);
      // WidgetBase's default logged-in test user (id 1) is not the submitter (5), so separation of
      // duties is satisfied without needing to override the session's user id.

      WidgetContext result = new BlogPostReviewWidget().action(widgetContext);

      assertEquals(ContentReviewCommand.STATUS_DRAFT, post.getDraftStatus());
      postRepo.verify(() -> BlogPostRepository.save(post));
      assertEquals("The post was returned to the author", result.getSuccessMessage());
    }
  }

  @Test
  void actionPublishDirectlyWhenReviewIsNotRequired() {
    addQueryParameter(widgetContext, "blogPostId", "9");
    addQueryParameter(widgetContext, "action", "publish");
    BlogPost post = blogPost(9L);

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<BlogPostRepository> postRepo = mockStatic(BlogPostRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      postRepo.when(() -> BlogPostRepository.findById(9L)).thenReturn(post);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required")).thenReturn(false);

      WidgetContext result = new BlogPostReviewWidget().action(widgetContext);

      assertNotNull(post.getPublished(), "publishDirectly() must actually set the post live, not just save it");
      postRepo.verify(() -> BlogPostRepository.save(post));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONTENT), eq("content.publish"),
          eq(AuditEventCommand.SUCCESS), eq("blog_post"), eq("9"), eq("A Post"), any()));
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()));
      assertEquals("The post was published", result.getSuccessMessage());
    }
  }

  @Test
  void actionPublishIsBlockedWhenReviewIsRequiredAndNotApproved() {
    addQueryParameter(widgetContext, "blogPostId", "9");
    addQueryParameter(widgetContext, "action", "publish");
    BlogPost post = blogPost(9L);

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<BlogPostRepository> postRepo = mockStatic(BlogPostRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      postRepo.when(() -> BlogPostRepository.findById(9L)).thenReturn(post);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required")).thenReturn(true);

      WidgetContext result = new BlogPostReviewWidget().action(widgetContext);

      postRepo.verify(() -> BlogPostRepository.save(any()), never());
      assertEquals("This post must be submitted for review and approved before it can be published",
          result.getErrorMessage());
    }
  }

  @Test
  void postApprovePublishesAndTriggersWorkflowWhenStepUpIsAlreadySatisfied() {
    addQueryParameter(widgetContext, "blogPostId", "9");
    addQueryParameter(widgetContext, "action", "approve");
    addQueryParameter(widgetContext, "releaseReference", "CR-1234");
    BlogPost post = blogPost(9L);

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<BlogPostRepository> postRepo = mockStatic(BlogPostRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<StepUpAuthCommand> stepUpAuth = mockStatic(StepUpAuthCommand.class);
        MockedStatic<ContentReviewCommand> contentReview = mockStatic(ContentReviewCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      postRepo.when(() -> BlogPostRepository.findById(9L)).thenReturn(post);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required")).thenReturn(true);
      stepUpAuth.when(() -> StepUpAuthCommand.isValid(any())).thenReturn(true);
      contentReview.when(() -> ContentReviewCommand.offerFor(any(), anyLong(), eq(true)))
          .thenReturn(ContentReviewCommand.OFFER_DECIDE);

      WidgetContext result = new BlogPostReviewWidget().post(widgetContext);

      contentReview.verify(() -> ContentReviewCommand.approve(post, widgetContext.getUserId(), "CR-1234"));
      assertNotNull(post.getPublished(), "performApproval() must actually set the post live, not just save it");
      postRepo.verify(() -> BlogPostRepository.save(post));
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()));
      assertEquals("The post was approved and published", result.getSuccessMessage());
    }
  }

  @Test
  void postApproveRequiresStepUpBeforeApproving() {
    addQueryParameter(widgetContext, "blogPostId", "9");
    addQueryParameter(widgetContext, "action", "approve");
    BlogPost post = blogPost(9L);

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<BlogPostRepository> postRepo = mockStatic(BlogPostRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<StepUpAuthCommand> stepUpAuth = mockStatic(StepUpAuthCommand.class);
        MockedStatic<ContentReviewCommand> contentReview = mockStatic(ContentReviewCommand.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      postRepo.when(() -> BlogPostRepository.findById(9L)).thenReturn(post);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required")).thenReturn(true);
      stepUpAuth.when(() -> StepUpAuthCommand.isValid(any())).thenReturn(false);
      contentReview.when(() -> ContentReviewCommand.offerFor(any(), anyLong(), eq(true)))
          .thenReturn(ContentReviewCommand.OFFER_DECIDE);

      new BlogPostReviewWidget().post(widgetContext);

      contentReview.verify(() -> ContentReviewCommand.approve(any(), anyLong(), any()), never());
      postRepo.verify(() -> BlogPostRepository.save(any()), never());
      workflowManager.verifyNoInteractions();
    }
  }

  @Test
  void postApproveFailsWhenSeparationOfDutiesIsViolated() {
    addQueryParameter(widgetContext, "blogPostId", "9");
    addQueryParameter(widgetContext, "action", "approve");
    BlogPost post = blogPost(9L);

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<BlogPostRepository> postRepo = mockStatic(BlogPostRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<StepUpAuthCommand> stepUpAuth = mockStatic(StepUpAuthCommand.class);
        MockedStatic<ContentReviewCommand> contentReview = mockStatic(ContentReviewCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      postRepo.when(() -> BlogPostRepository.findById(9L)).thenReturn(post);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required")).thenReturn(true);
      stepUpAuth.when(() -> StepUpAuthCommand.isValid(any())).thenReturn(true);
      contentReview.when(() -> ContentReviewCommand.offerFor(any(), anyLong(), eq(true)))
          .thenReturn(ContentReviewCommand.OFFER_DECIDE);
      contentReview.when(() -> ContentReviewCommand.approve(eq(post), anyLong(), any()))
          .thenThrow(new DataException("The approver must be different from the person who submitted the content (separation of duties)"));

      WidgetContext result = new BlogPostReviewWidget().post(widgetContext);

      postRepo.verify(() -> BlogPostRepository.save(any()), never());
      workflowManager.verifyNoInteractions();
      assertEquals("The approver must be different from the person who submitted the content (separation of duties)",
          result.getErrorMessage());
    }
  }

  @Test
  void postIgnoresNonApproveActions() {
    addQueryParameter(widgetContext, "blogPostId", "9");
    addQueryParameter(widgetContext, "action", "somethingElse");

    try (MockedStatic<ContentReviewCommand> contentReview = mockStatic(ContentReviewCommand.class)) {
      WidgetContext result = new BlogPostReviewWidget().post(widgetContext);

      assertNull(result.getSuccessMessage());
      contentReview.verifyNoInteractions();
    }
  }

  /**
   * End-to-end with the real ContentReviewCommand (not mocked): the reviewer who was the same
   * person as the submitter must be refused, exactly as content blocks and web pages already are --
   * this is the one guarantee that must hold identically for every
   * {@link com.simisinc.platform.domain.model.cms.Reviewable} type, so it's worth proving without
   * mocking the state machine away.
   */
  @Test
  void postApproveRealSeparationOfDutiesBlocksTheSubmitterFromApprovingTheirOwnDraft() {
    addQueryParameter(widgetContext, "blogPostId", "9");
    addQueryParameter(widgetContext, "action", "approve");
    BlogPost post = blogPost(9L);
    post.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    post.setSubmittedBy(widgetContext.getUserId());

    try (MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<BlogPostRepository> postRepo = mockStatic(BlogPostRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<StepUpAuthCommand> stepUpAuth = mockStatic(StepUpAuthCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      postRepo.when(() -> BlogPostRepository.findById(9L)).thenReturn(post);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required")).thenReturn(true);
      stepUpAuth.when(() -> StepUpAuthCommand.isValid(any())).thenReturn(true);

      WidgetContext result = new BlogPostReviewWidget().post(widgetContext);

      postRepo.verify(() -> BlogPostRepository.save(any()), never());
      assertEquals("The approver must be different from the person who submitted the content (separation of duties)",
          result.getErrorMessage());
    }
  }
}
