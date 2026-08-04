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

import java.sql.Timestamp;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.application.cms.EditorPermissionCommand;
import com.simisinc.platform.application.login.StepUpAuthCommand;
import com.simisinc.platform.domain.events.cms.BlogPostPublishedEvent;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * The /admin/blog-post-review admin page (issue #407, phase 2): extends the governed publish
 * workflow ({@link ContentReviewCommand}, originally built for content blocks, then generalized to
 * web pages) to blog posts -- submit an unpublished post for review, approve it (with step-up
 * re-authentication, mirroring {@code WebPageReviewWidget}/content's own
 * {@code performContentApproval}) and publish, or reject it back to the author.
 *
 * <p>Unlike a web page, a blog post has no separate draft/live content split to promote -- its own
 * {@code body} field already is what's live once {@code published} is set -- so "publish" here is
 * simply setting {@code published} and persisting, rather than a transactional
 * draft-XML-to-live-XML promotion. This also means the governed gate only ever applies to the
 * initial unpublished -&gt; published transition; a subsequent edit to an already-published post is
 * not gated (see issue #407 phase 2 research notes).
 *
 * <p>When {@code blogPost.review.required} is off, this also offers the classic direct-publish
 * affordance ({@link ContentReviewCommand#offerFor}'s {@code OFFER_PUBLISH}), matching the parity
 * web pages have between {@code BlogEditorWidget}'s own "Publish it?" checkbox and this dedicated
 * admin page.
 *
 * @author elizabeth houser
 */
public class BlogPostReviewWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908894L;

  static String JSP = "/admin/blog-post-review.jsp";

  public WidgetContext execute(WidgetContext context) {

    if (!EditorPermissionCommand.canEditContent(context.getUserSession())) {
      return context;
    }

    BlogPost blogPost = loadBlogPost(context);
    if (blogPost == null) {
      context.setErrorMessage("Blog post was not found");
      return context;
    }
    context.getRequest().setAttribute("blogPost", blogPost);

    boolean reviewRequired = LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required");
    context.getRequest().setAttribute("reviewOffer",
        ContentReviewCommand.offerFor(blogPost, context.getUserId(), reviewRequired));
    context.getRequest().setAttribute("reviewStatus", ContentReviewCommand.listStatusLabel(blogPost));

    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext action(WidgetContext context) {

    if (!EditorPermissionCommand.canEditContent(context.getUserSession())) {
      return context;
    }
    BlogPost blogPost = loadBlogPost(context);
    if (blogPost == null) {
      context.setErrorMessage("Blog post was not found");
      return context;
    }

    String action = context.getParameter("action");
    if ("submitForReview".equals(action)) {
      return submitForReview(context, blogPost);
    } else if ("reject".equals(action)) {
      return rejectDraft(context, blogPost);
    } else if ("publish".equals(action)) {
      boolean reviewRequired = LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required");
      return publishDirectly(context, blogPost, reviewRequired);
    }
    return context;
  }

  public WidgetContext post(WidgetContext context) {
    // The approve form is POSTed so the step-up credential never travels over GET (matches
    // WebPageReviewWidget.post()/ContentWidget.post()). execute() re-loads blogPost/reviewOffer
    // fresh so a step-up failure can re-render this same page with the approve form still present.
    if ("approve".equals(context.getParameter("action"))) {
      execute(context);
      if (!context.hasJsp()) {
        return context;
      }
      return performApproval(context);
    }
    return context;
  }

  private BlogPost loadBlogPost(WidgetContext context) {
    long blogPostId = context.getParameterAsLong("blogPostId", -1);
    return blogPostId > -1 ? BlogPostRepository.findById(blogPostId) : null;
  }

  private WidgetContext submitForReview(WidgetContext context, BlogPost blogPost) {
    try {
      ContentReviewCommand.submitForReview(blogPost, context.getUserId());
      BlogPostRepository.save(blogPost);
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.submit", AuditEventCommand.SUCCESS,
          "blog_post", String.valueOf(blogPost.getId()), blogPost.getTitle(), null);
      context.setSuccessMessage("The post was submitted for review");
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
    }
    return context;
  }

  private WidgetContext rejectDraft(WidgetContext context, BlogPost blogPost) {
    try {
      ContentReviewCommand.reject(blogPost, context.getUserId());
      BlogPostRepository.save(blogPost);
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.reject", AuditEventCommand.SUCCESS,
          "blog_post", String.valueOf(blogPost.getId()), blogPost.getTitle(), null);
      context.setSuccessMessage("The post was returned to the author");
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
    }
    return context;
  }

  private WidgetContext publishDirectly(WidgetContext context, BlogPost blogPost, boolean reviewRequired) {
    if (!blogPost.hasDraftContent()) {
      return context;
    }
    if (!ContentReviewCommand.mayPublish(blogPost, reviewRequired)) {
      // Not reachable through this widget's own UI (offerFor() would not have offered "publish"),
      // but this call site checks explicitly too, the same defense-in-depth
      // WebPageReviewWidget.publishDirectly() applies.
      context.setErrorMessage("This post must be submitted for review and approved before it can be published");
      return context;
    }
    publishNow(blogPost);
    BlogPostRepository.save(blogPost);
    AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.publish", AuditEventCommand.SUCCESS,
        "blog_post", String.valueOf(blogPost.getId()), blogPost.getTitle(), null);
    WorkflowManager.triggerWorkflowForEvent(new BlogPostPublishedEvent(blogPost.getId()));
    context.setSuccessMessage("The post was published");
    return context;
  }

  /**
   * Handles the POST-based approval with step-up re-authentication, mirroring
   * {@code WebPageReviewWidget.performApproval()}/{@code ContentHtmlCommand.performContentApproval}
   * exactly. Must be called after {@code execute()} has already set the JSP on the context, so a
   * step-up prompt can re-render the same page.
   */
  private WidgetContext performApproval(WidgetContext context) {
    BlogPost blogPost = loadBlogPost(context);
    if (blogPost == null) {
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
      // approve() enforces separation of duties (the approver cannot be the submitter); the post is
      // then published (there is no separate draft-to-live promotion step for blog posts) and the
      // named approver + release authority are recorded in the audit trail.
      ContentReviewCommand.approve(blogPost, context.getUserId(), releaseReference);
      publishNow(blogPost);
      BlogPostRepository.save(blogPost);
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.approve", AuditEventCommand.SUCCESS,
          "blog_post", String.valueOf(blogPost.getId()), blogPost.getTitle(),
          StringUtils.isNotBlank(releaseReference) ? "release authority: " + releaseReference : null);
      WorkflowManager.triggerWorkflowForEvent(new BlogPostPublishedEvent(blogPost.getId()));
      context.setSuccessMessage("The post was approved and published");
    } catch (DataException e) {
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.approve", AuditEventCommand.FAILURE,
          "blog_post", String.valueOf(blogPost.getId()), blogPost.getTitle(), e.getMessage());
      context.setErrorMessage(e.getMessage());
    }
    return context;
  }

  /** Sets published (mirroring SaveBlogPostCommand's own defaulting of startDate on first publish). */
  private void publishNow(BlogPost blogPost) {
    blogPost.setPublished(new Timestamp(System.currentTimeMillis()));
    if (blogPost.getStartDate() == null) {
      blogPost.setStartDate(blogPost.getPublished());
    }
  }
}
