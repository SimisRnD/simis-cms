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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Covers the 5 bulk actions on /admin/blog-posts (issue #427, mirroring
 * CalendarEventListWidgetBulkActionsTest's shape for issue #882/PR #911): a batch over
 * MAX_BULK_SELECTION is rejected outright rather than truncated, an empty selection is rejected,
 * one id that no longer resolves never aborts the rest of the batch, bulkPublish reuses the exact
 * governed-publish-workflow gate {@link ContentReviewCommand#mayPublish} (not the record-independent
 * {@link ContentReviewCommand#mayPublishDirectly}) so a post still awaiting approval is a per-row
 * failure rather than a bypass, bulkUnpublish resets the governed-review fields on a
 * previously-published post exactly as {@code BlogEditorWidget} already does, and all 5 commands
 * share a single admin-or-content-manager gate matching {@code BlogPostWidget#action}'s single-item
 * delete gate.
 *
 * @author SimIS Inc.
 */
class AdminBlogPostListWidgetBulkActionsTest extends WidgetBase {

  private static BlogPost blogPostWithId(long id) {
    BlogPost blogPost = new BlogPost();
    blogPost.setId(id);
    blogPost.setBlogId(1L);
    blogPost.setTitle("Post " + id);
    return blogPost;
  }

  private static Blog blogWithId(long id) {
    Blog blog = new Blog();
    blog.setId(id);
    blog.setName("Blog " + id);
    return blog;
  }

  private void multiValue(String name, String... values) {
    widgetContext.getParameterMap().put(name, values);
  }

  // --- Permission gate ---

  @Test
  void nonAdminNonContentManagerCannotReachAnyBulkAction() {
    setRoles(widgetContext, COMMUNITY_MANAGER);
    multiValue("blogPostId", "5");
    addQueryParameter(widgetContext, "command", "bulkArchive");

    try (MockedStatic<BlogPostRepository> repo = mockStatic(BlogPostRepository.class)) {
      new AdminBlogPostListWidget().post(widgetContext);

      repo.verify(() -> BlogPostRepository.findById(anyLong()), never());
    }
  }

  @Test
  void contentManagerCanReachBulkActions() {
    setRoles(widgetContext, CONTENT_MANAGER);
    multiValue("blogPostId", "5");
    addQueryParameter(widgetContext, "command", "bulkArchive");

    BlogPost blogPost = blogPostWithId(5L);
    try (MockedStatic<BlogPostRepository> repo = mockStatic(BlogPostRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> BlogPostRepository.findById(5L)).thenReturn(blogPost);
      repo.when(() -> BlogPostRepository.save(blogPost)).thenReturn(blogPost);

      WidgetContext result = new AdminBlogPostListWidget().post(widgetContext);

      repo.verify(() -> BlogPostRepository.save(blogPost), times(1));
      assertTrue(result.getSuccessMessage().contains("1 of 1"));
    }
  }

  @Test
  void adminCanReachBulkDelete() {
    // Unlike WebPageListWidget's stricter admin-only delete gate, blog post delete matches
    // BlogPostWidget#action's own admin-or-content-manager gate -- content-manager suffices too.
    setRoles(widgetContext, CONTENT_MANAGER);
    multiValue("blogPostId", "5");
    addQueryParameter(widgetContext, "command", "bulkDelete");

    BlogPost blogPost = blogPostWithId(5L);
    try (MockedStatic<BlogPostRepository> repo = mockStatic(BlogPostRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> BlogPostRepository.findById(5L)).thenReturn(blogPost);
      repo.when(() -> BlogPostRepository.remove(blogPost)).thenReturn(true);

      WidgetContext result = new AdminBlogPostListWidget().post(widgetContext);

      repo.verify(() -> BlogPostRepository.remove(blogPost), times(1));
      assertTrue(result.getSuccessMessage().contains("1 of 1"));
    }
  }

  // --- Selection bounds (shared shape across all 5 commands; exercised once each) ---

  @Test
  void overCapSelectionIsRejectedWithNoRepositoryCalls() {
    setRoles(widgetContext, ADMIN);
    String[] tooMany = new String[AdminBlogPostListWidget.MAX_BULK_SELECTION + 1];
    for (int i = 0; i < tooMany.length; i++) {
      tooMany[i] = String.valueOf(i + 100);
    }
    multiValue("blogPostId", tooMany);
    addQueryParameter(widgetContext, "command", "bulkArchive");

    try (MockedStatic<BlogPostRepository> repo = mockStatic(BlogPostRepository.class)) {
      WidgetContext result = new AdminBlogPostListWidget().post(widgetContext);

      repo.verify(() -> BlogPostRepository.findById(anyLong()), never());
      assertTrue(result.getErrorMessage().contains("Too many blog posts"));
    }
  }

  @Test
  void emptySelectionIsRejectedForBulkDelete() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "bulkDelete");
    // No blogPostId parameters at all

    try (MockedStatic<BlogPostRepository> repo = mockStatic(BlogPostRepository.class)) {
      WidgetContext result = new AdminBlogPostListWidget().post(widgetContext);

      repo.verify(() -> BlogPostRepository.findById(anyLong()), never());
      assertEquals("No blog posts were selected", result.getErrorMessage());
    }
  }

  @Test
  void anIdThatNoLongerResolvesIsSkippedButTheRestOfTheBatchStillRuns() {
    setRoles(widgetContext, ADMIN);
    multiValue("blogPostId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkArchive");

    BlogPost found = blogPostWithId(5L);

    try (MockedStatic<BlogPostRepository> repo = mockStatic(BlogPostRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> BlogPostRepository.findById(5L)).thenReturn(found);
      repo.when(() -> BlogPostRepository.findById(6L)).thenReturn(null); // deleted concurrently / tampered id
      repo.when(() -> BlogPostRepository.save(found)).thenReturn(found);

      WidgetContext result = new AdminBlogPostListWidget().post(widgetContext);

      repo.verify(() -> BlogPostRepository.save(any()), times(1));
      assertTrue(result.getWarningMessage().contains("1 of 2"));
      assertTrue(result.getWarningMessage().contains("Not found: 1"));
    }
  }

  // --- bulkPublish ---

  @Test
  void bulkPublishSetsPublishedAndTriggersTheWorkflowWhenReviewIsNotRequired() {
    setRoles(widgetContext, ADMIN);
    multiValue("blogPostId", "5");
    addQueryParameter(widgetContext, "command", "bulkPublish");

    BlogPost blogPost = blogPostWithId(5L);
    // hasDraftContent() requires a non-blank body -- see bulkPublishSkipsAPostWithNoDraftContentAsAPerRowFailure
    // for the no-draft-content case this test must NOT exercise.
    blogPost.setBody("Some content");

    try (MockedStatic<BlogPostRepository> repo = mockStatic(BlogPostRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> BlogPostRepository.findById(5L)).thenReturn(blogPost);
      repo.when(() -> BlogPostRepository.save(blogPost)).thenReturn(blogPost);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required")).thenReturn(false);

      WidgetContext result = new AdminBlogPostListWidget().post(widgetContext);

      assertNotNull(blogPost.getPublished());
      repo.verify(() -> BlogPostRepository.save(blogPost), times(1));
      workflow.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), times(1));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.publish"),
          eq("success"), anyLong(), any(), any(), any(), eq("blog_post"), any(), any(), any()), times(1));
      assertTrue(result.getSuccessMessage().contains("1 of 1"));
    }
  }

  @Test
  void bulkPublishReportsAPostFailingContentReviewGateAsAPerRowFailureWithoutBlockingTheRestOfTheBatch() {
    // The load-bearing point (#427's acceptance criterion): a bulk publish must never bypass
    // governed review. This uses the real ContentReviewCommand state machine (not mocked) so the
    // gate is proven, not assumed.
    setRoles(widgetContext, ADMIN);
    multiValue("blogPostId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkPublish");

    BlogPost notApproved = blogPostWithId(5L);
    notApproved.setBody("Some content"); // has draft content, so the failure is purely the review gate
    // draftStatus null: never submitted, so mayPublish() is false under governed review.

    BlogPost approved = blogPostWithId(6L);
    approved.setBody("Some content");
    approved.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    approved.setSubmittedBy(2L);
    approved.setApprovedBy(3L); // a different user than the submitter

    try (MockedStatic<BlogPostRepository> repo = mockStatic(BlogPostRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> BlogPostRepository.findById(5L)).thenReturn(notApproved);
      repo.when(() -> BlogPostRepository.findById(6L)).thenReturn(approved);
      repo.when(() -> BlogPostRepository.save(approved)).thenReturn(approved);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required")).thenReturn(true);

      WidgetContext result = new AdminBlogPostListWidget().post(widgetContext);

      assertNull(notApproved.getPublished(), "an unapproved draft must never be published by the bulk action");
      assertNotNull(approved.getPublished(), "an approved draft must still be published");
      repo.verify(() -> BlogPostRepository.save(notApproved), never());
      repo.verify(() -> BlogPostRepository.save(approved), times(1));
      workflow.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), times(1));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.publish"),
          eq("failure"), anyLong(), any(), any(), any(), eq("blog_post"), any(), any(), any()), times(1));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.publish"),
          eq("success"), anyLong(), any(), any(), any(), eq("blog_post"), any(), any(), any()), times(1));
      assertTrue(result.getWarningMessage().contains("1 of 2"));
      assertTrue(result.getWarningMessage().contains("Failed: 1"));
    }
  }

  @Test
  void bulkPublishSkipsAPostWithNoDraftContentAsAPerRowFailure() {
    // Code-review fix (issue #427): bulkPublishAction must mirror BlogPostReviewWidget#
    // publishDirectly's hasDraftContent() guard, not just mayPublish() -- otherwise, with
    // blogPost.review.required off, selecting an already-published post (or one with no draft
    // content at all) for bulk Publish would re-stamp its published timestamp and re-fire
    // BlogPostPublishedEvent. blogPostWithId() leaves body blank, so hasDraftContent() is false
    // here without needing to fake an already-published state.
    setRoles(widgetContext, ADMIN);
    multiValue("blogPostId", "5");
    addQueryParameter(widgetContext, "command", "bulkPublish");

    BlogPost blogPost = blogPostWithId(5L);

    try (MockedStatic<BlogPostRepository> repo = mockStatic(BlogPostRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> BlogPostRepository.findById(5L)).thenReturn(blogPost);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required")).thenReturn(false);

      WidgetContext result = new AdminBlogPostListWidget().post(widgetContext);

      assertNull(blogPost.getPublished(), "a post with no draft content must not be re-published by the bulk action");
      repo.verify(() -> BlogPostRepository.save(any()), never());
      workflow.verifyNoInteractions();
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.publish"),
          eq("failure"), anyLong(), any(), any(), any(), eq("blog_post"), any(), any(), any()), times(1));
      assertTrue(result.getErrorMessage().startsWith("0 of 1 selected blog post published. Failed: 1."));
      assertTrue(result.getErrorMessage().contains("Post 5 (#5): no draft content to publish"));
    }
  }

  @Test
  void bulkPublishRecordsTheSuccessAuditEntryBeforeFiringTheWorkflowEventSoALostWorkflowCallCannotEraseIt() {
    // Review fix (issue #427): the workflow event now fires AFTER the audit record, not before --
    // so if WorkflowManager throws (e.g. the job-queue backend is unavailable), this row's SUCCESS
    // audit entry is already durably recorded, matching BlogPostReviewWidget#publishDirectly's
    // audit-before-workflow ordering. Previously the audit call for this row would never run.
    setRoles(widgetContext, ADMIN);
    multiValue("blogPostId", "5");
    addQueryParameter(widgetContext, "command", "bulkPublish");

    BlogPost blogPost = blogPostWithId(5L);
    blogPost.setBody("Some content");

    try (MockedStatic<BlogPostRepository> repo = mockStatic(BlogPostRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> BlogPostRepository.findById(5L)).thenReturn(blogPost);
      repo.when(() -> BlogPostRepository.save(blogPost)).thenReturn(blogPost);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required")).thenReturn(false);
      workflow.when(() -> WorkflowManager.triggerWorkflowForEvent(any()))
          .thenThrow(new IllegalStateException("job queue unavailable"));

      assertThrows(IllegalStateException.class, () -> new AdminBlogPostListWidget().post(widgetContext));

      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.publish"),
          eq("success"), anyLong(), any(), any(), any(), eq("blog_post"), any(), any(), any()), times(1));
    }
  }

  // --- bulkUnpublish ---

  @Test
  void bulkUnpublishResetsTheGovernedReviewFieldsOnAPreviouslyPublishedPost() {
    // Issue #407 phase 2 correctness requirement: skipping this reset would let a single editor
    // unpublish, edit, and republish without the new content ever being reviewed.
    setRoles(widgetContext, ADMIN);
    multiValue("blogPostId", "5");
    addQueryParameter(widgetContext, "command", "bulkUnpublish");

    BlogPost blogPost = blogPostWithId(5L);
    blogPost.setPublished(new Timestamp(System.currentTimeMillis()));
    blogPost.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    blogPost.setSubmittedBy(2L);
    blogPost.setApprovedBy(3L);
    blogPost.setReleaseReference("CR-1234");

    try (MockedStatic<BlogPostRepository> repo = mockStatic(BlogPostRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> BlogPostRepository.findById(5L)).thenReturn(blogPost);
      repo.when(() -> BlogPostRepository.save(blogPost)).thenReturn(blogPost);

      WidgetContext result = new AdminBlogPostListWidget().post(widgetContext);

      assertNull(blogPost.getPublished());
      assertNull(blogPost.getDraftStatus());
      assertEquals(-1, blogPost.getSubmittedBy());
      assertEquals(-1, blogPost.getApprovedBy());
      assertNull(blogPost.getReleaseReference());
      repo.verify(() -> BlogPostRepository.save(blogPost), times(1));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.unpublish"),
          eq("success"), anyLong(), any(), any(), any(), eq("blog_post"), any(), any(), any()), times(1));
      assertTrue(result.getSuccessMessage().contains("1 of 1"));
    }
  }

  @Test
  void bulkUnpublishDoesNotTouchReviewFieldsForAPostThatWasNotYetPublished() {
    setRoles(widgetContext, ADMIN);
    multiValue("blogPostId", "5");
    addQueryParameter(widgetContext, "command", "bulkUnpublish");

    BlogPost blogPost = blogPostWithId(5L);
    blogPost.setPublished(null);
    blogPost.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    blogPost.setSubmittedBy(2L);

    try (MockedStatic<BlogPostRepository> repo = mockStatic(BlogPostRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> BlogPostRepository.findById(5L)).thenReturn(blogPost);
      repo.when(() -> BlogPostRepository.save(blogPost)).thenReturn(blogPost);

      new AdminBlogPostListWidget().post(widgetContext);

      // Still pending review -- unpublishing a post that was never live must not disturb its
      // in-progress review state.
      assertEquals(ContentReviewCommand.STATUS_SUBMITTED, blogPost.getDraftStatus());
      assertEquals(2L, blogPost.getSubmittedBy());
    }
  }

  // --- bulkArchive ---

  @Test
  void bulkArchiveSetsTheArchivedTimestampOnEachResolvedPost() {
    setRoles(widgetContext, ADMIN);
    multiValue("blogPostId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkArchive");

    BlogPost first = blogPostWithId(5L);
    BlogPost second = blogPostWithId(6L);

    try (MockedStatic<BlogPostRepository> repo = mockStatic(BlogPostRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> BlogPostRepository.findById(5L)).thenReturn(first);
      repo.when(() -> BlogPostRepository.findById(6L)).thenReturn(second);
      repo.when(() -> BlogPostRepository.save(first)).thenReturn(first);
      repo.when(() -> BlogPostRepository.save(second)).thenReturn(second);

      WidgetContext result = new AdminBlogPostListWidget().post(widgetContext);

      assertNotNull(first.getArchived());
      assertNotNull(second.getArchived());
      repo.verify(() -> BlogPostRepository.save(first), times(1));
      repo.verify(() -> BlogPostRepository.save(second), times(1));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.archive"),
          eq("success"), anyLong(), any(), any(), any(), eq("blog_post"), any(), any(), any()), times(2));
      assertTrue(result.getSuccessMessage().contains("2 of 2"));
    }
  }

  // --- bulkMove ---

  @Test
  void bulkMoveWithoutAResolvableDestinationBlogIsRejectedWithNoRepositoryCalls() {
    setRoles(widgetContext, ADMIN);
    multiValue("blogPostId", "5");
    addQueryParameter(widgetContext, "command", "bulkMove");
    addQueryParameter(widgetContext, "blogId", "999");

    try (MockedStatic<BlogPostRepository> repo = mockStatic(BlogPostRepository.class);
        MockedStatic<BlogRepository> blogRepo = mockStatic(BlogRepository.class)) {
      blogRepo.when(() -> BlogRepository.findById(999L)).thenReturn(null);

      WidgetContext result = new AdminBlogPostListWidget().post(widgetContext);

      // Rejected before any post is even loaded -- the destination is resolved first
      repo.verify(() -> BlogPostRepository.findById(anyLong()), never());
      assertEquals("The destination blog was not found", result.getErrorMessage());
    }
  }

  @Test
  void bulkMoveUpdatesTheBlogIdOnEachResolvedPost() {
    setRoles(widgetContext, ADMIN);
    multiValue("blogPostId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkMove");
    addQueryParameter(widgetContext, "blogId", "42");

    Blog destination = blogWithId(42L);
    BlogPost first = blogPostWithId(5L);
    BlogPost second = blogPostWithId(6L);

    try (MockedStatic<BlogPostRepository> repo = mockStatic(BlogPostRepository.class);
        MockedStatic<BlogRepository> blogRepo = mockStatic(BlogRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      blogRepo.when(() -> BlogRepository.findById(42L)).thenReturn(destination);
      repo.when(() -> BlogPostRepository.findById(5L)).thenReturn(first);
      repo.when(() -> BlogPostRepository.findById(6L)).thenReturn(second);
      repo.when(() -> BlogPostRepository.save(first)).thenReturn(first);
      repo.when(() -> BlogPostRepository.save(second)).thenReturn(second);

      WidgetContext result = new AdminBlogPostListWidget().post(widgetContext);

      assertEquals(42L, first.getBlogId());
      assertEquals(42L, second.getBlogId());
      repo.verify(() -> BlogPostRepository.save(first), times(1));
      repo.verify(() -> BlogPostRepository.save(second), times(1));
      assertTrue(result.getSuccessMessage().contains("2 of 2"));
      assertTrue(result.getSuccessMessage().contains("Blog 42"));
    }
  }

  // --- bulkDelete ---

  @Test
  void bulkDeleteRemovesEachResolvedPost() {
    setRoles(widgetContext, ADMIN);
    multiValue("blogPostId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkDelete");

    BlogPost first = blogPostWithId(5L);
    BlogPost second = blogPostWithId(6L);

    try (MockedStatic<BlogPostRepository> repo = mockStatic(BlogPostRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> BlogPostRepository.findById(5L)).thenReturn(first);
      repo.when(() -> BlogPostRepository.findById(6L)).thenReturn(second);
      repo.when(() -> BlogPostRepository.remove(first)).thenReturn(true);
      repo.when(() -> BlogPostRepository.remove(second)).thenReturn(true);

      WidgetContext result = new AdminBlogPostListWidget().post(widgetContext);

      repo.verify(() -> BlogPostRepository.remove(first), times(1));
      repo.verify(() -> BlogPostRepository.remove(second), times(1));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.delete"),
          eq("success"), anyLong(), any(), any(), any(), eq("blog_post"), any(), any(), any()), times(2));
      assertTrue(result.getSuccessMessage().contains("2 of 2"));
    }
  }

  @Test
  void bulkDeleteCountsAFailedRemovalAsAFailureNotAnAbort() {
    setRoles(widgetContext, ADMIN);
    multiValue("blogPostId", "5");
    addQueryParameter(widgetContext, "command", "bulkDelete");

    BlogPost blogPost = blogPostWithId(5L);

    try (MockedStatic<BlogPostRepository> repo = mockStatic(BlogPostRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> BlogPostRepository.findById(5L)).thenReturn(blogPost);
      repo.when(() -> BlogPostRepository.remove(blogPost)).thenReturn(false);

      WidgetContext result = new AdminBlogPostListWidget().post(widgetContext);

      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.delete"),
          eq("failure"), anyLong(), any(), any(), any(), eq("blog_post"), any(), any(), any()), times(1));
      assertTrue(result.getErrorMessage().startsWith("0 of 1 selected blog post deleted. Failed: 1."));
      // Issue #427 code-review finding: the failed row's reason must reach the response, not just
      // the audit log (which a non-admin content-manager triggering this action cannot view).
      assertTrue(result.getErrorMessage().contains("Post 5 (#5): delete failed"));
    }
  }
}
