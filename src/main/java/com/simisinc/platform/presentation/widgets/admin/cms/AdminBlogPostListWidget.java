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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.domain.events.cms.BlogPostPublishedEvent;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * The /admin/blog-posts admin page (issue #427): a searchable, filterable, paginated table of
 * blog posts across all blogs -- there was previously no admin list of individual posts anywhere
 * in the codebase (only a per-blog post count on /admin/blogs, and BlogPostReviewWidget's
 * single-post governed-review page). Named distinctly from the existing public-facing
 * {@code cms.BlogPostListWidget} (which renders published posts on live site pages) to avoid
 * confusion between the two "blog post list" widgets.
 *
 * <p>Mirrors the bulk-action mechanics PR #911 shipped for /admin/calendars'
 * {@code CalendarEventListWidget}, and the shape {@code WebPageListWidget} added for
 * /admin/web-pages (issue #427's web-pages slice): row checkboxes + a bulk-action toolbar
 * offering all 5 actions #427 needs for blog posts -- Publish, Unpublish, Archive, Move, and
 * Delete. Publish/Unpublish reuse the exact governed-publish-workflow gate
 * ({@link ContentReviewCommand#mayPublish}) and reset-on-unpublish shape
 * {@code BlogPostReviewWidget}/{@code BlogEditorWidget} already use for a single post, Archive is
 * a plain repository save against the {@code archived} column that already exists on
 * {@code blog_posts}, Move reassigns a post to a different {@link Blog} (directly analogous to the
 * calendar precedent's move-to-different-Calendar), and Delete reuses
 * {@code BlogPostWidget#action}'s single-item delete path's role gate exactly.
 *
 * @author elizabeth houser
 */
public class AdminBlogPostListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908895L;

  static String JSP = "/admin/blog-post-list.jsp";

  // A crafted POST is the only thing this bounds -- normal usage never approaches it, since
  // selection is scoped to the current page (default page size 25). An id list over this cap is
  // rejected outright, never silently truncated. Mirrors CalendarEventListWidget/WebPageListWidget's
  // MAX_BULK_SELECTION exactly (issue #427).
  static final int MAX_BULK_SELECTION = 100;

  // Code-review fix (issue #427): per-row failure reasons were previously recorded only to the
  // audit log and never returned in the response. Only the first MAX_DETAIL_LINES failed/not-found
  // rows are spelled out inline in the aggregate message, to keep a large batch's message readable;
  // the rest are still counted and still fully detailed in the audit log.
  static final int MAX_DETAIL_LINES = 5;

  public WidgetContext execute(WidgetContext context) {

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "25"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    // Order the admin list the same way the public listing does (BlogPostListWidget sorts
    // start_date desc), so the two views agree. Sorting on post_id ordered by row identity, i.e.
    // the sequence posts were entered -- invisible while an author writes chronologically, and
    // permanently wrong once an archive is back-filled out of order (issue #1362).
    //
    // NULLS LAST is deliberate: start_date is non-null for anything published (SaveBlogPostCommand
    // backfills it from published), but an unpublished draft never given a date can be null, and
    // Postgres sorts nulls first on DESC -- which would float undated drafts above the archive.
    // post_id DESC breaks ties, which back-filled posts sharing a date frequently produce.
    // setColumnsToSortBy, not setDefaultColumnToSortBy: the "default" setter belongs to the
    // repository, and BlogPostRepository#findAll overwrites it with "post_id" one line after
    // receiving these constraints, so this list has been ordered by insertion id rather than by
    // date -- on the screen an editor uses to find recent work. columnsToSortBy is read first by
    // DB#appendSortClause and the repositories never touch it. Issue 1604.
    constraints.setColumnsToSortBy(new String[] { "start_date DESC NULLS LAST", "post_id DESC" });
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    BlogPostSpecification specification = buildSpecification(context);

    // Load the list
    List<BlogPost> blogPostList = BlogPostRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("blogPostList", blogPostList);

    // The Blog filter dropdown (mirrors CalendarEventListWidget's calendarList)
    List<Blog> blogList = BlogRepository.findAll();
    context.getRequest().setAttribute("blogList", blogList);

    // Resolves each row's blog name/link in the JSP in O(1) -- previously the JSP itself re-looped
    // the entire blogList for every single post row to find its matching blog (O(posts x blogs)).
    // Bounded by page size so it was never urgent, but cheap to fix while pagination work is
    // already touching this same admin/cms area (issue: /admin/blogs guidance pass).
    Map<Long, Blog> blogMap = new HashMap<>();
    for (Blog blog : blogList) {
      blogMap.put(blog.getId(), blog);
    }
    context.getRequest().setAttribute("blogMap", blogMap);

    // Governed publish workflow status per post (#407), keyed by post id -- only posts with a
    // pending draft carry an interesting label; mirrors WebPageListWidget's webPageReviewStatusMap.
    Map<Long, String> blogPostReviewStatusMap = new HashMap<>();
    for (BlogPost blogPost : blogPostList) {
      if (blogPost.hasDraftContent()) {
        blogPostReviewStatusMap.put(blogPost.getId(), ContentReviewCommand.listStatusLabel(blogPost));
      }
    }
    context.getRequest().setAttribute("blogPostReviewStatusMap", blogPostReviewStatusMap);

    // Echo the filter values back so the form keeps its state
    echoFilterParameters(context);

    // Carry the filters through pagination (paging_control.jspf appends this to each page link).
    // URL-encoded here so the free-text search term cannot break the query string or the href.
    StringBuilder pagingParams = new StringBuilder();
    appendParam(pagingParams, "q", context.getParameter("q"));
    appendParam(pagingParams, "blogId", context.getParameter("blogId"));
    appendParam(pagingParams, "status", context.getParameter("status"));
    context.getRequest().setAttribute("recordPagingParams", pagingParams.toString());

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  /**
   * Bulk actions selected from /admin/blog-posts' checkbox + action-bar UI (issue #427, mirroring
   * the bulk-action mechanics PR #911 shipped for /admin/calendars). All 5 commands share a single
   * gate -- admin-or-content-manager -- matching {@code BlogPostWidget#action}'s single-item delete
   * gate, {@code BlogEditorWidget}'s own page access, and every other {@code /admin/blog*} page's
   * role, rather than the stricter admin-only delete gate the web-pages slice uses (that gate
   * matches web pages' own single-item delete precedent, which is admin-only; blog posts' single-item
   * delete precedent is not, so the gate here follows suit).
   */
  public WidgetContext post(WidgetContext context) {

    if (!(context.hasRole("admin") || context.hasRole("content-manager"))) {
      LOG.warn("No permission to modify blog posts");
      return context;
    }

    // Don't accept multiple form posts
    context.getUserSession().renewFormToken();

    String command = context.getParameter("command");
    if ("bulkPublish".equals(command)) {
      return bulkPublishAction(context);
    }
    if ("bulkUnpublish".equals(command)) {
      return bulkUnpublishAction(context);
    }
    if ("bulkArchive".equals(command)) {
      return bulkArchiveAction(context);
    }
    if ("bulkExcludeFromFeed".equals(command)) {
      return bulkFeedVisibilityAction(context, true);
    }
    if ("bulkIncludeInFeed".equals(command)) {
      return bulkFeedVisibilityAction(context, false);
    }
    if ("bulkMove".equals(command)) {
      return bulkMoveAction(context);
    }
    if ("bulkDelete".equals(command)) {
      return bulkDeleteAction(context);
    }
    return context;
  }

  /**
   * Reuses the exact single-item publish gates {@code BlogPostReviewWidget#publishDirectly} already
   * checks -- first {@link BlogPost#hasDraftContent()}, then {@link ContentReviewCommand#mayPublish}
   * (not the record-independent {@link ContentReviewCommand#mayPublishDirectly}) -- so a post with
   * nothing new to release, or one still awaiting approval under governed review, is reported as a
   * per-row failure rather than having its published timestamp re-stamped and its publish event
   * re-fired. There is no "always call this" command for a metadata-only status flip on a blog post
   * (unlike {@code SaveWebPageCommand} for web pages); this mirrors {@code BlogPostReviewWidget}'s
   * own shape instead: save via the repository directly, then manually re-fire
   * {@link BlogPostPublishedEvent}, since {@link BlogPostRepository#save} does not fire it itself.
   */
  private WidgetContext bulkPublishAction(WidgetContext context) {
    List<Long> blogPostIds = resolveSelectedBlogPostIds(context);
    if (blogPostIds == null) {
      return rejectBulkSelection(context);
    }
    if (blogPostIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    boolean reviewRequired = LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required");
    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    List<String> rowIssues = new ArrayList<>();
    for (Long blogPostId : blogPostIds) {
      BlogPost blogPost = BlogPostRepository.findById(blogPostId);
      if (blogPost == null) {
        ++notFound;
        rowIssues.add("#" + blogPostId + ": not found");
        continue;
      }
      if (!blogPost.hasDraftContent()) {
        // Mirrors BlogPostReviewWidget.publishDirectly()'s own guard: a post with no draft content
        // (already published, or never drafted) is a per-row no-op, not something bulk Publish
        // should re-stamp with a fresh published timestamp and re-fire the publish event for.
        ++failed;
        rowIssues.add(blogPost.getTitle() + " (#" + blogPost.getId() + "): no draft content to publish");
        AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.publish", AuditEventCommand.FAILURE,
            "blog_post", String.valueOf(blogPost.getId()), blogPost.getTitle(),
            "blocked: no draft content to publish (bulk)");
        continue;
      }
      if (!ContentReviewCommand.mayPublish(blogPost, reviewRequired)) {
        // Not reachable through this widget's own UI intent, but checked explicitly the same as
        // BlogPostReviewWidget.publishDirectly() -- a row that fails this gate is a per-row
        // failure, not a batch-ending error, and the row is left untouched.
        ++failed;
        rowIssues.add(blogPost.getTitle() + " (#" + blogPost.getId() + "): draft not approved for release");
        AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.publish", AuditEventCommand.FAILURE,
            "blog_post", String.valueOf(blogPost.getId()), blogPost.getTitle(),
            "blocked: draft not approved for release (bulk)");
        continue;
      }
      publishNow(blogPost);
      blogPost.setModifiedBy(context.getUserId());
      BlogPost result = BlogPostRepository.save(blogPost);
      String outcome = result != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (result != null) {
        ++succeeded;
      } else {
        ++failed;
        rowIssues.add(blogPost.getTitle() + " (#" + blogPost.getId() + "): save failed");
      }
      // Audit BEFORE firing the workflow event (matches BlogPostReviewWidget#publishDirectly) --
      // WorkflowManager.triggerWorkflowForEvent enqueues background jobs with no try/catch of its
      // own, so if that throws, this row's SUCCESS is already recorded rather than lost, and the
      // exception can only affect rows after this one instead of erasing this row's own audit trail.
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.publish", outcome,
          "blog_post", String.valueOf(blogPost.getId()), blogPost.getTitle(), "(bulk)");
      if (result != null) {
        WorkflowManager.triggerWorkflowForEvent(new BlogPostPublishedEvent(blogPost.getId()));
      }
    }

    setBulkResultMessage(context, "published", succeeded, blogPostIds.size(), notFound, failed, rowIssues);
    context.setRedirect("/admin/blog-posts");
    return context;
  }

  /**
   * Unpublishing must reset the governed-review fields on a previously-published post, exactly as
   * {@code BlogEditorWidget#post}'s unpublish branch already does -- otherwise a single editor
   * could unpublish, edit, and republish via {@code BlogPostReviewWidget#publishDirectly} (which
   * reads {@link ContentReviewCommand#mayPublish} purely from these fields) without the new content
   * ever having been submitted or reviewed.
   */
  private WidgetContext bulkUnpublishAction(WidgetContext context) {
    List<Long> blogPostIds = resolveSelectedBlogPostIds(context);
    if (blogPostIds == null) {
      return rejectBulkSelection(context);
    }
    if (blogPostIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    List<String> rowIssues = new ArrayList<>();
    for (Long blogPostId : blogPostIds) {
      BlogPost blogPost = BlogPostRepository.findById(blogPostId);
      if (blogPost == null) {
        ++notFound;
        rowIssues.add("#" + blogPostId + ": not found");
        continue;
      }
      boolean wasPublished = blogPost.getPublished() != null;
      blogPost.setPublished(null);
      if (wasPublished) {
        // Issue #407 phase 2 review finding, mirrored from BlogEditorWidget#post: resetting to the
        // same "never submitted" defaults BlogPost itself starts with means the post must go
        // through submit -> approve again before it can be published a second time.
        blogPost.setDraftStatus(null);
        blogPost.setSubmittedBy(-1);
        blogPost.setApprovedBy(-1);
        blogPost.setReleaseReference(null);
      }
      blogPost.setModifiedBy(context.getUserId());
      BlogPost result = BlogPostRepository.save(blogPost);
      String outcome = result != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (result != null) {
        ++succeeded;
      } else {
        ++failed;
        rowIssues.add(blogPost.getTitle() + " (#" + blogPost.getId() + "): save failed");
      }
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.unpublish", outcome,
          "blog_post", String.valueOf(blogPost.getId()), blogPost.getTitle(), "(bulk)");
    }

    setBulkResultMessage(context, "unpublished", succeeded, blogPostIds.size(), notFound, failed, rowIssues);
    context.setRedirect("/admin/blog-posts");
    return context;
  }

  /**
   * Archiving is a plain repository save against the {@code archived} column that already exists
   * on {@code blog_posts} -- not a publish-workflow event.
   */
  /**
   * Flips the per-post syndication opt-out (#1419) on the selected posts. Deliberately separate
   * from archiving: an excluded post stays published, searchable and at its own URL, and only
   * FeedServlet filters on it -- so this is a metadata-only change with no visibility side
   * effects. Shares bulkArchiveAction's per-row outcome accounting and audit shape.
   *
   * @param exclude true to take the posts out of the feeds, false to put them back
   */
  private WidgetContext bulkFeedVisibilityAction(WidgetContext context, boolean exclude) {
    List<Long> blogPostIds = resolveSelectedBlogPostIds(context);
    if (blogPostIds == null) {
      return rejectBulkSelection(context);
    }
    if (blogPostIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    List<String> rowIssues = new ArrayList<>();
    for (Long blogPostId : blogPostIds) {
      BlogPost blogPost = BlogPostRepository.findById(blogPostId);
      if (blogPost == null) {
        ++notFound;
        rowIssues.add("#" + blogPostId + ": not found");
        continue;
      }
      blogPost.setExcludeFromFeed(exclude);
      blogPost.setModifiedBy(context.getUserId());
      BlogPost result = BlogPostRepository.save(blogPost);
      String outcome = result != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (result != null) {
        ++succeeded;
      } else {
        ++failed;
        rowIssues.add(blogPost.getTitle() + " (#" + blogPost.getId() + "): save failed");
      }
      AuditEventCommand.record(context, AuditEventCommand.CONTENT,
          exclude ? "content.feed.exclude" : "content.feed.include", outcome,
          "blog_post", String.valueOf(blogPost.getId()), blogPost.getTitle(), "(bulk)");
    }

    setBulkResultMessage(context, exclude ? "removed from the feed" : "restored to the feed",
        succeeded, blogPostIds.size(), notFound, failed, rowIssues);
    context.setRedirect("/admin/blog-posts");
    return context;
  }

  private WidgetContext bulkArchiveAction(WidgetContext context) {
    List<Long> blogPostIds = resolveSelectedBlogPostIds(context);
    if (blogPostIds == null) {
      return rejectBulkSelection(context);
    }
    if (blogPostIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    Timestamp now = new Timestamp(System.currentTimeMillis());
    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    List<String> rowIssues = new ArrayList<>();
    for (Long blogPostId : blogPostIds) {
      BlogPost blogPost = BlogPostRepository.findById(blogPostId);
      if (blogPost == null) {
        ++notFound;
        rowIssues.add("#" + blogPostId + ": not found");
        continue;
      }
      blogPost.setArchived(now);
      blogPost.setModifiedBy(context.getUserId());
      BlogPost result = BlogPostRepository.save(blogPost);
      String outcome = result != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (result != null) {
        ++succeeded;
      } else {
        ++failed;
        rowIssues.add(blogPost.getTitle() + " (#" + blogPost.getId() + "): save failed");
      }
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.archive", outcome,
          "blog_post", String.valueOf(blogPost.getId()), blogPost.getTitle(), "(bulk)");
    }

    setBulkResultMessage(context, "archived", succeeded, blogPostIds.size(), notFound, failed, rowIssues);
    context.setRedirect("/admin/blog-posts");
    return context;
  }

  /**
   * Moves the selected posts to a different {@link Blog}, directly analogous to the calendar
   * precedent's move-to-different-Calendar (Blog:BlogPost :: Calendar:CalendarEvent structurally).
   * The destination is resolved before any post is loaded, mirroring
   * {@code CalendarEventListWidget#bulkMoveAction}'s destination-first-then-events order exactly.
   */
  private WidgetContext bulkMoveAction(WidgetContext context) {
    long targetBlogId = context.getParameterAsLong("blogId", -1);
    Blog targetBlog = targetBlogId > -1 ? BlogRepository.findById(targetBlogId) : null;
    if (targetBlog == null) {
      context.setErrorMessage("The destination blog was not found");
      context.setRedirect("/admin/blog-posts");
      return context;
    }

    List<Long> blogPostIds = resolveSelectedBlogPostIds(context);
    if (blogPostIds == null) {
      return rejectBulkSelection(context);
    }
    if (blogPostIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    List<String> rowIssues = new ArrayList<>();
    for (Long blogPostId : blogPostIds) {
      BlogPost blogPost = BlogPostRepository.findById(blogPostId);
      if (blogPost == null) {
        ++notFound;
        rowIssues.add("#" + blogPostId + ": not found");
        continue;
      }
      blogPost.setBlogId(targetBlog.getId());
      blogPost.setModifiedBy(context.getUserId());
      BlogPost result = BlogPostRepository.save(blogPost);
      String outcome = result != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (result != null) {
        ++succeeded;
      } else {
        ++failed;
        rowIssues.add(blogPost.getTitle() + " (#" + blogPost.getId() + "): save failed");
      }
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.move", outcome,
          "blog_post", String.valueOf(blogPost.getId()), blogPost.getTitle(),
          "movedTo=" + targetBlog.getName() + " (bulk)");
    }

    setBulkResultMessage(context, "moved to " + targetBlog.getName(), succeeded, blogPostIds.size(), notFound, failed,
        rowIssues);
    context.setRedirect("/admin/blog-posts");
    return context;
  }

  /**
   * Reuses {@code BlogPostWidget#action}'s "deletePost" role gate ({@link #post} already checked
   * it above, so this is a plain loop). No domain-event precedent exists for blog post deletion
   * (neither the single-item path nor {@code BlogRepository}'s cascade-delete-on-blog-removal fires
   * one), so none is required here to "match individual" -- the acceptance criterion is satisfied
   * vacuously. A confirmation reveal modal is still required by #427's acceptance criteria
   * regardless (see blog-post-list.jsp).
   */
  private WidgetContext bulkDeleteAction(WidgetContext context) {
    List<Long> blogPostIds = resolveSelectedBlogPostIds(context);
    if (blogPostIds == null) {
      return rejectBulkSelection(context);
    }
    if (blogPostIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    List<String> rowIssues = new ArrayList<>();
    for (Long blogPostId : blogPostIds) {
      BlogPost blogPost = BlogPostRepository.findById(blogPostId);
      if (blogPost == null) {
        ++notFound;
        rowIssues.add("#" + blogPostId + ": not found");
        continue;
      }
      boolean removed = BlogPostRepository.remove(blogPost);
      String outcome = removed ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (removed) {
        ++succeeded;
      } else {
        ++failed;
        rowIssues.add(blogPost.getTitle() + " (#" + blogPost.getId() + "): delete failed");
      }
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.delete", outcome,
          "blog_post", String.valueOf(blogPost.getId()), blogPost.getTitle(), "(bulk)");
    }

    setBulkResultMessage(context, "deleted", succeeded, blogPostIds.size(), notFound, failed, rowIssues);
    context.setRedirect("/admin/blog-posts");
    return context;
  }

  /** Sets published (mirrors BlogPostReviewWidget#publishNow exactly). */
  private void publishNow(BlogPost blogPost) {
    blogPost.setPublished(new Timestamp(System.currentTimeMillis()));
    if (blogPost.getStartDate() == null) {
      blogPost.setStartDate(blogPost.getPublished());
    }
  }

  /**
   * Parses and dedupes the selected post ids from the repeated {@code blogPostId} hidden inputs
   * the bulk modals inject, silently dropping any non-numeric entry (a tampered value is not a
   * batch-ending error). Returns {@code null} when the list exceeds {@link #MAX_BULK_SELECTION} --
   * the whole request is then rejected rather than silently truncated, since truncation could apply
   * the action to a different subset of posts than the one the admin reviewed and confirmed.
   * Mirrors CalendarEventListWidget#resolveSelectedEventIds exactly.
   */
  private List<Long> resolveSelectedBlogPostIds(WidgetContext context) {
    String[] rawIds = context.getParameterMap().get("blogPostId");
    Set<Long> ids = new LinkedHashSet<>();
    if (rawIds != null) {
      for (String rawId : rawIds) {
        try {
          ids.add(Long.parseLong(rawId.trim()));
        } catch (NumberFormatException e) {
          // Dropped, not treated as a batch-ending error
        }
      }
    }
    if (ids.size() > MAX_BULK_SELECTION) {
      LOG.warn("Bulk blog post action rejected: " + ids.size() + " ids exceeds MAX_BULK_SELECTION ("
          + MAX_BULK_SELECTION + ")");
      return null;
    }
    return new ArrayList<>(ids);
  }

  private WidgetContext rejectBulkSelection(WidgetContext context) {
    context.setErrorMessage("Too many blog posts were selected (maximum " + MAX_BULK_SELECTION
        + "). Select fewer posts and try again.");
    context.setRedirect("/admin/blog-posts");
    return context;
  }

  private WidgetContext rejectEmptySelection(WidgetContext context) {
    context.setErrorMessage("No blog posts were selected");
    context.setRedirect("/admin/blog-posts");
    return context;
  }

  /**
   * Sets the single aggregate result message every bulk action reports (page_messages.jspf renders
   * exactly one of success/warning/error). Mirrors CalendarEventListWidget#setBulkResultMessage.
   *
   * <p>{@code rowIssues} carries a human-readable "title (#id): reason" entry for every not-found
   * or failed row (issue #427 code-review finding: these reasons were previously recorded only to
   * the admin-only audit log, never returned to the caller). Only the first
   * {@link #MAX_DETAIL_LINES} are spelled out inline to keep the message readable; the full detail
   * for every row remains in the audit log regardless.
   */
  private void setBulkResultMessage(WidgetContext context, String verb, int succeeded, int totalSelected,
      int notFound, int failed, List<String> rowIssues) {
    StringBuilder sb = new StringBuilder();
    sb.append(succeeded).append(" of ").append(totalSelected).append(" selected blog post")
        .append(totalSelected == 1 ? "" : "s").append(" ").append(verb).append(".");
    if (notFound > 0) {
      sb.append(" Not found: ").append(notFound).append(".");
    }
    if (failed > 0) {
      sb.append(" Failed: ").append(failed).append(".");
    }
    appendRowIssueDetails(sb, rowIssues);
    if (succeeded == 0) {
      context.setErrorMessage(sb.toString());
    } else if (succeeded != totalSelected) {
      context.setWarningMessage(sb.toString());
    } else {
      context.setSuccessMessage(sb.toString());
    }
  }

  /**
   * Appends up to {@link #MAX_DETAIL_LINES} "title (#id): reason" entries to the aggregate message,
   * summarizing any remainder by count only. See {@link #setBulkResultMessage} for why this exists.
   */
  private void appendRowIssueDetails(StringBuilder sb, List<String> rowIssues) {
    if (rowIssues == null || rowIssues.isEmpty()) {
      return;
    }
    sb.append(" (");
    int shown = Math.min(rowIssues.size(), MAX_DETAIL_LINES);
    for (int i = 0; i < shown; i++) {
      if (i > 0) {
        sb.append("; ");
      }
      sb.append(rowIssues.get(i));
    }
    if (rowIssues.size() > shown) {
      sb.append("; and ").append(rowIssues.size() - shown).append(" more");
    }
    sb.append(")");
  }

  /** Builds the filter specification from request parameters. */
  private BlogPostSpecification buildSpecification(WidgetContext context) {
    String q = context.getParameter("q");
    long blogId = context.getParameterAsLong("blogId", -1);
    String status = context.getParameter("status");

    BlogPostSpecification specification = new BlogPostSpecification();
    if (StringUtils.isNotBlank(q)) {
      specification.setSearchTerm(q.trim());
    }
    if (blogId > -1) {
      specification.setBlogId(blogId);
    }
    // issue #427: archived posts are excluded from this list by default -- "Archived" is its own
    // status option (rather than combined with published/draft) since a post's archived state is
    // orthogonal to whether it was ever published. Mirrors CalendarEventListWidget's identical
    // status-dropdown handling.
    if ("archived".equals(status)) {
      specification.setArchivedOnly(true);
    } else {
      specification.setArchivedOnly(false);
      if ("published".equals(status)) {
        specification.setPublishedOnly(true);
      } else if ("draft".equals(status)) {
        specification.setPublishedOnly(false);
      }
    }
    return specification;
  }

  /** Echoes the raw filter parameters back to the request so the filter form keeps its state. */
  private void echoFilterParameters(WidgetContext context) {
    context.getRequest().setAttribute("q", context.getParameter("q"));
    context.getRequest().setAttribute("blogId", context.getParameter("blogId"));
    context.getRequest().setAttribute("status", context.getParameter("status"));
  }

  /** Appends {@code name=urlEncoded(value)} to the paging query string when the value is present. */
  private void appendParam(StringBuilder sb, String name, String value) {
    if (StringUtils.isBlank(value)) {
      return;
    }
    if (sb.length() > 0) {
      sb.append("&");
    }
    sb.append(name).append("=").append(URLEncoder.encode(value, StandardCharsets.UTF_8));
  }
}
