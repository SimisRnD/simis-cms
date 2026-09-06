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

package com.simisinc.platform.presentation.widgets.cms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.sql.Timestamp;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.application.cms.LoadBlogCommand;
import com.simisinc.platform.application.cms.LoadBlogPostCommand;
import com.simisinc.platform.application.cms.SaveBlogPostCommand;
import com.simisinc.platform.application.mailinglists.NewsletterSendCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.cms.BlogTag;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.cms.BlogTagRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

class BlogEditorWidgetTest extends WidgetBase {

  private static BlogPost blogPost(long id, Timestamp published) {
    BlogPost blogPost = new BlogPost();
    blogPost.setId(id);
    blogPost.setTitle("A Post");
    blogPost.setPublished(published);
    return blogPost;
  }

  private static MailingList mailingList(long id) {
    MailingList mailingList = new MailingList();
    mailingList.setId(id);
    mailingList.setTitle("News");
    mailingList.setName("news");
    return mailingList;
  }

  private static Blog blog(long id) {
    Blog blog = new Blog();
    blog.setId(id);
    blog.setUniqueId("news");
    blog.setName("News");
    return blog;
  }

  private static BlogTag blogTag(long id, long blogId, String name) {
    BlogTag blogTag = new BlogTag();
    blogTag.setId(id);
    blogTag.setBlogId(blogId);
    blogTag.setName(name);
    return blogTag;
  }

  @Test
  void postNotifiesSubscribersWhenAnUnpublishedPostIsPublishedWithNotifyChecked()
      throws InvocationTargetException, IllegalAccessException {
    BlogPost existing = blogPost(5L, null); // was unpublished
    BlogPost saved = blogPost(5L, new Timestamp(System.currentTimeMillis()));
    MailingList mailingList = mailingList(9L);
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "enabled", "true");
    addQueryParameter(widgetContext, "notifySubscribers", "true");
    addQueryParameter(widgetContext, "notifyMailingListId", "9");

    try (MockedStatic<LoadBlogPostCommand> loadPost = mockStatic(LoadBlogPostCommand.class);
        MockedStatic<SaveBlogPostCommand> savePost = mockStatic(SaveBlogPostCommand.class);
        MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<NewsletterSendCommand> sendCommand = mockStatic(NewsletterSendCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      loadPost.when(() -> LoadBlogPostCommand.loadBlogPostById(5L)).thenReturn(existing);
      savePost.when(() -> SaveBlogPostCommand.saveBlogPost(any())).thenReturn(saved);
      listRepo.when(() -> MailingListRepository.findById(9L)).thenReturn(mailingList);
      sendCommand.when(() -> NewsletterSendCommand.sendBlogPostNotification(mailingList, saved, 1L)).thenReturn(7);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required")).thenReturn(false);

      WidgetContext result = new BlogEditorWidget().post(widgetContext);

      sendCommand.verify(() -> NewsletterSendCommand.sendBlogPostNotification(mailingList, saved, 1L));
      assertTrue(result.getSuccessMessage().contains("7 subscribers will be notified"), result.getSuccessMessage());
    }
  }

  @Test
  void postDoesNotNotifyWhenEditingAnAlreadyPublishedPost()
      throws InvocationTargetException, IllegalAccessException {
    BlogPost existing = blogPost(5L, new Timestamp(System.currentTimeMillis())); // already published
    BlogPost saved = blogPost(5L, existing.getPublished());
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "enabled", "true");
    addQueryParameter(widgetContext, "notifySubscribers", "true");
    addQueryParameter(widgetContext, "notifyMailingListId", "9");

    try (MockedStatic<LoadBlogPostCommand> loadPost = mockStatic(LoadBlogPostCommand.class);
        MockedStatic<SaveBlogPostCommand> savePost = mockStatic(SaveBlogPostCommand.class);
        MockedStatic<NewsletterSendCommand> sendCommand = mockStatic(NewsletterSendCommand.class)) {
      loadPost.when(() -> LoadBlogPostCommand.loadBlogPostById(5L)).thenReturn(existing);
      savePost.when(() -> SaveBlogPostCommand.saveBlogPost(any())).thenReturn(saved);

      WidgetContext result = new BlogEditorWidget().post(widgetContext);

      sendCommand.verify(() -> NewsletterSendCommand.sendBlogPostNotification(any(), any(), anyLong()), never());
      assertEquals("Blog post was saved", result.getSuccessMessage());
    }
  }

  @Test
  void postNotifiesSubscribersForABrandNewPostPublishedImmediately()
      throws InvocationTargetException, IllegalAccessException {
    BlogPost saved = blogPost(6L, new Timestamp(System.currentTimeMillis()));
    MailingList mailingList = mailingList(9L);
    // No "id" param at all -- a brand new post
    addQueryParameter(widgetContext, "enabled", "true");
    addQueryParameter(widgetContext, "notifySubscribers", "true");
    addQueryParameter(widgetContext, "notifyMailingListId", "9");

    try (MockedStatic<LoadBlogPostCommand> loadPost = mockStatic(LoadBlogPostCommand.class);
        MockedStatic<SaveBlogPostCommand> savePost = mockStatic(SaveBlogPostCommand.class);
        MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<NewsletterSendCommand> sendCommand = mockStatic(NewsletterSendCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      savePost.when(() -> SaveBlogPostCommand.saveBlogPost(any())).thenReturn(saved);
      listRepo.when(() -> MailingListRepository.findById(9L)).thenReturn(mailingList);
      sendCommand.when(() -> NewsletterSendCommand.sendBlogPostNotification(mailingList, saved, 1L)).thenReturn(3);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required")).thenReturn(false);

      new BlogEditorWidget().post(widgetContext);

      sendCommand.verify(() -> NewsletterSendCommand.sendBlogPostNotification(mailingList, saved, 1L));
      loadPost.verify(() -> LoadBlogPostCommand.loadBlogPostById(anyLong()), never());
    }
  }

  @Test
  void postDoesNotNotifyWhenTheCheckboxIsUnchecked() throws InvocationTargetException, IllegalAccessException {
    BlogPost existing = blogPost(5L, null);
    BlogPost saved = blogPost(5L, new Timestamp(System.currentTimeMillis()));
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "enabled", "true");
    // notifySubscribers not set at all

    try (MockedStatic<LoadBlogPostCommand> loadPost = mockStatic(LoadBlogPostCommand.class);
        MockedStatic<SaveBlogPostCommand> savePost = mockStatic(SaveBlogPostCommand.class);
        MockedStatic<NewsletterSendCommand> sendCommand = mockStatic(NewsletterSendCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      loadPost.when(() -> LoadBlogPostCommand.loadBlogPostById(5L)).thenReturn(existing);
      savePost.when(() -> SaveBlogPostCommand.saveBlogPost(any())).thenReturn(saved);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required")).thenReturn(false);

      WidgetContext result = new BlogEditorWidget().post(widgetContext);

      sendCommand.verify(() -> NewsletterSendCommand.sendBlogPostNotification(any(), any(), anyLong()), never());
      assertEquals("Blog post was saved", result.getSuccessMessage());
    }
  }

  @Test
  void postDoesNotNotifyWhenUnpublishing() throws InvocationTargetException, IllegalAccessException {
    BlogPost existing = blogPost(5L, new Timestamp(System.currentTimeMillis()));
    BlogPost saved = blogPost(5L, null);
    addQueryParameter(widgetContext, "id", "5");
    // "enabled" checkbox absent -- unpublishing
    addQueryParameter(widgetContext, "notifySubscribers", "true");
    addQueryParameter(widgetContext, "notifyMailingListId", "9");

    try (MockedStatic<LoadBlogPostCommand> loadPost = mockStatic(LoadBlogPostCommand.class);
        MockedStatic<SaveBlogPostCommand> savePost = mockStatic(SaveBlogPostCommand.class);
        MockedStatic<NewsletterSendCommand> sendCommand = mockStatic(NewsletterSendCommand.class)) {
      loadPost.when(() -> LoadBlogPostCommand.loadBlogPostById(5L)).thenReturn(existing);
      savePost.when(() -> SaveBlogPostCommand.saveBlogPost(any())).thenReturn(saved);

      new BlogEditorWidget().post(widgetContext);

      sendCommand.verify(() -> NewsletterSendCommand.sendBlogPostNotification(any(), any(), anyLong()), never());
    }
  }

  @Test
  void executeProvidesTheTagListForTheCheckboxGroup() {
    addQueryParameter(widgetContext, "blogUniqueId", "news");
    Blog blog = blog(5L);
    List<BlogTag> tagList = List.of(blogTag(10L, 5L, "Updates"));

    try (MockedStatic<LoadBlogCommand> loadBlogCommand = mockStatic(LoadBlogCommand.class);
        MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<BlogTagRepository> tagRepository = mockStatic(BlogTagRepository.class)) {
      loadBlogCommand.when(() -> LoadBlogCommand.loadBlogByUniqueId("news")).thenReturn(blog);
      listRepo.when(MailingListRepository::findAll).thenReturn(new ArrayList<>());
      tagRepository.when(() -> BlogTagRepository.findAllByBlogId(5L)).thenReturn(tagList);

      new BlogEditorWidget().execute(widgetContext);

      assertEquals(tagList, widgetContext.getRequest().getAttribute("tagList"),
          "the tag checkbox group must render the actual tag list, not just whatever the repository call happened to return somewhere");
    }
  }

  @Test
  void postParsesTheSharedTagIdCheckboxGroupOntoTheBlogPost()
      throws InvocationTargetException, IllegalAccessException {
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "title", "A Post");
    addQueryParameter(widgetContext, "blogId", "1");
    widgetContext.getParameterMap().put("tagId", new String[] { "10", "20", "10" });

    BlogPost existing = blogPost(5L, null);
    BlogPost saved = blogPost(5L, null);
    List<BlogTag> blogOwnTagList = List.of(blogTag(10L, 1L, "Updates"), blogTag(20L, 1L, "News"));

    try (MockedStatic<LoadBlogPostCommand> loadPost = mockStatic(LoadBlogPostCommand.class);
        MockedStatic<SaveBlogPostCommand> savePost = mockStatic(SaveBlogPostCommand.class);
        MockedStatic<BlogTagRepository> tagRepository = mockStatic(BlogTagRepository.class)) {
      loadPost.when(() -> LoadBlogPostCommand.loadBlogPostById(5L)).thenReturn(existing);
      savePost.when(() -> SaveBlogPostCommand.saveBlogPost(any())).thenReturn(saved);
      tagRepository.when(() -> BlogTagRepository.findAllByBlogId(1L)).thenReturn(blogOwnTagList);

      new BlogEditorWidget().post(widgetContext);

      ArgumentCaptor<BlogPost> blogPostCaptor = ArgumentCaptor.forClass(BlogPost.class);
      savePost.verify(() -> SaveBlogPostCommand.saveBlogPost(blogPostCaptor.capture()));
      assertArrayEquals(new Long[] { 10L, 20L }, blogPostCaptor.getValue().getTagIdList(),
          "duplicate tagId values in the submitted checkbox group must be de-duplicated");
    }
  }

  @Test
  void postRejectsATagIdThatDoesNotBelongToThePostsOwnBlog()
      throws InvocationTargetException, IllegalAccessException {
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "title", "A Post");
    addQueryParameter(widgetContext, "blogId", "1");
    // tagId 20 belongs to blog 1 (this post's own blog); tagId 99 belongs to a different blog
    widgetContext.getParameterMap().put("tagId", new String[] { "20", "99" });

    BlogPost existing = blogPost(5L, null);
    BlogPost saved = blogPost(5L, null);
    List<BlogTag> blogOwnTagList = List.of(blogTag(20L, 1L, "News"));

    try (MockedStatic<LoadBlogPostCommand> loadPost = mockStatic(LoadBlogPostCommand.class);
        MockedStatic<SaveBlogPostCommand> savePost = mockStatic(SaveBlogPostCommand.class);
        MockedStatic<BlogTagRepository> tagRepository = mockStatic(BlogTagRepository.class)) {
      loadPost.when(() -> LoadBlogPostCommand.loadBlogPostById(5L)).thenReturn(existing);
      savePost.when(() -> SaveBlogPostCommand.saveBlogPost(any())).thenReturn(saved);
      tagRepository.when(() -> BlogTagRepository.findAllByBlogId(1L)).thenReturn(blogOwnTagList);

      new BlogEditorWidget().post(widgetContext);

      ArgumentCaptor<BlogPost> blogPostCaptor = ArgumentCaptor.forClass(BlogPost.class);
      savePost.verify(() -> SaveBlogPostCommand.saveBlogPost(blogPostCaptor.capture()));
      assertArrayEquals(new Long[] { 20L }, blogPostCaptor.getValue().getTagIdList(),
          "a tagId that does not belong to this post's own blog must not be attached, even if it was submitted");
    }
  }

  @Test
  void postSetsAnEmptyTagIdListWhenNoTagCheckboxesAreSubmitted()
      throws InvocationTargetException, IllegalAccessException {
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "title", "A Post");
    // No "tagId" params at all -- every checkbox left unchecked

    BlogPost existing = blogPost(5L, null);
    BlogPost saved = blogPost(5L, null);

    try (MockedStatic<LoadBlogPostCommand> loadPost = mockStatic(LoadBlogPostCommand.class);
        MockedStatic<SaveBlogPostCommand> savePost = mockStatic(SaveBlogPostCommand.class)) {
      loadPost.when(() -> LoadBlogPostCommand.loadBlogPostById(5L)).thenReturn(existing);
      savePost.when(() -> SaveBlogPostCommand.saveBlogPost(any())).thenReturn(saved);

      new BlogEditorWidget().post(widgetContext);

      ArgumentCaptor<BlogPost> blogPostCaptor = ArgumentCaptor.forClass(BlogPost.class);
      savePost.verify(() -> SaveBlogPostCommand.saveBlogPost(blogPostCaptor.capture()));
      assertArrayEquals(new Long[0], blogPostCaptor.getValue().getTagIdList());
    }
  }

  /**
   * Regression test for the mass-assignment guard (issue #407, phase 2), mirroring
   * WebPageFormWidgetTest's {@code postCannotInjectGovernedWorkflowFieldsViaFormSave}: the governed
   * publish workflow fields must never be settable through this generic form save, only through
   * BlogPostReviewWidget's explicit submit/approve/reject actions.
   */
  @Test
  void postCannotInjectGovernedWorkflowFieldsViaFormSave() throws InvocationTargetException, IllegalAccessException {
    BlogPost existing = blogPost(5L, null);
    existing.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    existing.setSubmittedBy(5L);
    existing.setApprovedBy(-1L);
    existing.setReleaseReference(null);
    BlogPost saved = blogPost(5L, null);

    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "title", "A Post");
    // The attack: try to self-approve by injecting every governed-workflow field directly.
    addQueryParameter(widgetContext, "draftStatus", "approved");
    addQueryParameter(widgetContext, "submittedBy", "999");
    addQueryParameter(widgetContext, "approvedBy", "999");
    addQueryParameter(widgetContext, "releaseReference", "forged");

    try (MockedStatic<LoadBlogPostCommand> loadPost = mockStatic(LoadBlogPostCommand.class);
        MockedStatic<SaveBlogPostCommand> savePost = mockStatic(SaveBlogPostCommand.class)) {
      loadPost.when(() -> LoadBlogPostCommand.loadBlogPostById(5L)).thenReturn(existing);
      savePost.when(() -> SaveBlogPostCommand.saveBlogPost(any())).thenReturn(saved);

      new BlogEditorWidget().post(widgetContext);

      ArgumentCaptor<BlogPost> blogPostCaptor = ArgumentCaptor.forClass(BlogPost.class);
      savePost.verify(() -> SaveBlogPostCommand.saveBlogPost(blogPostCaptor.capture()));
      BlogPost saveArgument = blogPostCaptor.getValue();
      assertEquals(ContentReviewCommand.STATUS_SUBMITTED, saveArgument.getDraftStatus());
      assertEquals(5L, saveArgument.getSubmittedBy());
      assertEquals(-1L, saveArgument.getApprovedBy());
      assertNull(saveArgument.getReleaseReference());
    }
  }

  /**
   * Regression test for the review-bypass finding raised on issue #407 phase 2: nothing previously
   * reset draftStatus/submittedBy/approvedBy/releaseReference when a post was unpublished, so a
   * single editor could unpublish an already-approved post, edit the body to arbitrary new content,
   * and republish it via BlogPostReviewWidget.publishDirectly() -- which reads mayPublish() purely
   * from those stale fields -- without the new content ever having been submitted or reviewed by
   * anyone. Unpublishing must invalidate the prior approval so the post has to go through
   * submit -&gt; approve again before it can be published a second time.
   */
  @Test
  void postResetsGovernedWorkflowFieldsWhenUnpublishingAnAlreadyApprovedPost()
      throws InvocationTargetException, IllegalAccessException {
    BlogPost existing = blogPost(5L, new Timestamp(System.currentTimeMillis()));
    existing.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    existing.setSubmittedBy(3L);
    existing.setApprovedBy(7L);
    existing.setReleaseReference("CR-OLD");
    BlogPost saved = blogPost(5L, null);

    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "title", "A Post");
    // "enabled" checkbox absent -- unpublishing an already-published, already-approved post

    try (MockedStatic<LoadBlogPostCommand> loadPost = mockStatic(LoadBlogPostCommand.class);
        MockedStatic<SaveBlogPostCommand> savePost = mockStatic(SaveBlogPostCommand.class)) {
      loadPost.when(() -> LoadBlogPostCommand.loadBlogPostById(5L)).thenReturn(existing);
      savePost.when(() -> SaveBlogPostCommand.saveBlogPost(any())).thenReturn(saved);

      new BlogEditorWidget().post(widgetContext);

      ArgumentCaptor<BlogPost> blogPostCaptor = ArgumentCaptor.forClass(BlogPost.class);
      savePost.verify(() -> SaveBlogPostCommand.saveBlogPost(blogPostCaptor.capture()));
      BlogPost saveArgument = blogPostCaptor.getValue();
      assertNull(saveArgument.getDraftStatus(), "an unpublish must clear the stale approval, not carry it forward");
      assertEquals(-1L, saveArgument.getSubmittedBy());
      assertEquals(-1L, saveArgument.getApprovedBy());
      assertNull(saveArgument.getReleaseReference());
    }
  }

  /**
   * Companion to the reset above: a post that is saved while it is ALREADY unpublished (e.g. an
   * in-progress edit to a draft still awaiting review) must not have its pending
   * submit/reject state wiped just because "enabled" happens to be unchecked on this particular
   * save -- the reset above is keyed on the published-&gt;unpublished transition specifically
   * (wasAlreadyPublished), not on "isPublished is currently false".
   */
  @Test
  void postDoesNotResetGovernedWorkflowFieldsWhenAlreadyUnpublishedPostIsSavedAgain()
      throws InvocationTargetException, IllegalAccessException {
    BlogPost existing = blogPost(5L, null);
    existing.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    existing.setSubmittedBy(3L);
    BlogPost saved = blogPost(5L, null);

    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "title", "A Post");
    // "enabled" checkbox absent -- but the post was already unpublished, not transitioning

    try (MockedStatic<LoadBlogPostCommand> loadPost = mockStatic(LoadBlogPostCommand.class);
        MockedStatic<SaveBlogPostCommand> savePost = mockStatic(SaveBlogPostCommand.class)) {
      loadPost.when(() -> LoadBlogPostCommand.loadBlogPostById(5L)).thenReturn(existing);
      savePost.when(() -> SaveBlogPostCommand.saveBlogPost(any())).thenReturn(saved);

      new BlogEditorWidget().post(widgetContext);

      ArgumentCaptor<BlogPost> blogPostCaptor = ArgumentCaptor.forClass(BlogPost.class);
      savePost.verify(() -> SaveBlogPostCommand.saveBlogPost(blogPostCaptor.capture()));
      BlogPost saveArgument = blogPostCaptor.getValue();
      assertEquals(ContentReviewCommand.STATUS_SUBMITTED, saveArgument.getDraftStatus(),
          "a post already awaiting review must not lose that state on an ordinary intermediate save");
      assertEquals(3L, saveArgument.getSubmittedBy());
    }
  }

  /**
   * Governed publish workflow gate (issue #407, phase 2): under blogPost.review.required, checking
   * "Publish it?" on a post that has never been published can no longer take it live directly --
   * the save degrades to a draft save (mirrors ContentReviewCommand.mayPublishDirectly()'s own
   * javadoc), and the block is recorded in the audit trail the same way
   * PageServlet.publishDraft records a blocked web-page publish attempt.
   */
  @Test
  void postDegradesToADraftSaveWhenReviewIsRequiredAndBlockedPublishIsAudited()
      throws InvocationTargetException, IllegalAccessException {
    BlogPost saved = blogPost(6L, null);
    addQueryParameter(widgetContext, "title", "A New Post");
    addQueryParameter(widgetContext, "enabled", "true");
    // No "id" param -- a brand new, never-published post

    try (MockedStatic<SaveBlogPostCommand> savePost = mockStatic(SaveBlogPostCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      savePost.when(() -> SaveBlogPostCommand.saveBlogPost(any())).thenReturn(saved);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required")).thenReturn(true);

      WidgetContext result = new BlogEditorWidget().post(widgetContext);

      ArgumentCaptor<BlogPost> blogPostCaptor = ArgumentCaptor.forClass(BlogPost.class);
      savePost.verify(() -> SaveBlogPostCommand.saveBlogPost(blogPostCaptor.capture()));
      assertNull(blogPostCaptor.getValue().getPublished(),
          "a blocked direct-publish attempt must degrade to a draft save, not go live");
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONTENT), eq("content.publish"),
          eq(AuditEventCommand.FAILURE), eq("blog_post"), any(), any(), eq("blocked: draft not approved for release")));
      assertTrue(result.getSuccessMessage().contains("requires review"), result.getSuccessMessage());
    }
  }

  @Test
  void postRedirectsToTheFullBlogPostPathNotJustThePostSlug()
      throws InvocationTargetException, IllegalAccessException {
    BlogPost existing = blogPost(5L, null);
    BlogPost saved = blogPost(5L, new Timestamp(System.currentTimeMillis()));
    saved.setBlogId(3L);
    saved.setUniqueId("simis-supports-house-bill-858");
    Blog blog = new Blog();
    blog.setId(3L);
    blog.setUniqueId("news");
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "enabled", "true");

    try (MockedStatic<LoadBlogPostCommand> loadPost = mockStatic(LoadBlogPostCommand.class);
        MockedStatic<SaveBlogPostCommand> savePost = mockStatic(SaveBlogPostCommand.class);
        MockedStatic<LoadBlogCommand> loadBlog = mockStatic(LoadBlogCommand.class);
        MockedStatic<NewsletterSendCommand> sendCommand = mockStatic(NewsletterSendCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      loadPost.when(() -> LoadBlogPostCommand.loadBlogPostById(5L)).thenReturn(existing);
      savePost.when(() -> SaveBlogPostCommand.saveBlogPost(any())).thenReturn(saved);
      loadBlog.when(() -> LoadBlogCommand.loadBlogById(3L)).thenReturn(blog);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required")).thenReturn(false);

      WidgetContext result = new BlogEditorWidget().post(widgetContext);

      // Without the blog segment this is /simis-supports-house-bill-858, which 404s -- the author
      // lands on an error page immediately after a save that actually succeeded
      assertEquals("/news/simis-supports-house-bill-858", result.getRedirect());
    }
  }

  @Test
  void postFallsBackToTheSiteRootRatherThanA404WhenTheBlogCannotBeResolved()
      throws InvocationTargetException, IllegalAccessException {
    BlogPost existing = blogPost(5L, null);
    BlogPost saved = blogPost(5L, new Timestamp(System.currentTimeMillis()));
    saved.setBlogId(3L);
    saved.setUniqueId("orphaned-post");
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "enabled", "true");

    try (MockedStatic<LoadBlogPostCommand> loadPost = mockStatic(LoadBlogPostCommand.class);
        MockedStatic<SaveBlogPostCommand> savePost = mockStatic(SaveBlogPostCommand.class);
        MockedStatic<LoadBlogCommand> loadBlog = mockStatic(LoadBlogCommand.class);
        MockedStatic<NewsletterSendCommand> sendCommand = mockStatic(NewsletterSendCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      loadPost.when(() -> LoadBlogPostCommand.loadBlogPostById(5L)).thenReturn(existing);
      savePost.when(() -> SaveBlogPostCommand.saveBlogPost(any())).thenReturn(saved);
      loadBlog.when(() -> LoadBlogCommand.loadBlogById(3L)).thenReturn(null);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required")).thenReturn(false);

      WidgetContext result = new BlogEditorWidget().post(widgetContext);

      assertEquals("/", result.getRedirect());
    }
  }

  @Test
  void postStillHonoursAnExplicitReturnPage() throws InvocationTargetException, IllegalAccessException {
    BlogPost existing = blogPost(5L, null);
    BlogPost saved = blogPost(5L, new Timestamp(System.currentTimeMillis()));
    saved.setBlogId(3L);
    saved.setUniqueId("a-post");
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "enabled", "true");
    addQueryParameter(widgetContext, "returnPage", "/news");

    try (MockedStatic<LoadBlogPostCommand> loadPost = mockStatic(LoadBlogPostCommand.class);
        MockedStatic<SaveBlogPostCommand> savePost = mockStatic(SaveBlogPostCommand.class);
        MockedStatic<LoadBlogCommand> loadBlog = mockStatic(LoadBlogCommand.class);
        MockedStatic<NewsletterSendCommand> sendCommand = mockStatic(NewsletterSendCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      loadPost.when(() -> LoadBlogPostCommand.loadBlogPostById(5L)).thenReturn(existing);
      savePost.when(() -> SaveBlogPostCommand.saveBlogPost(any())).thenReturn(saved);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required")).thenReturn(false);

      WidgetContext result = new BlogEditorWidget().post(widgetContext);

      assertEquals("/news", result.getRedirect());
      loadBlog.verifyNoInteractions();
    }
  }

}
