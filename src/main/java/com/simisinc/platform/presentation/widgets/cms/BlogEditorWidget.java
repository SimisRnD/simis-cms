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

package com.simisinc.platform.presentation.widgets.cms;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.*;
import com.simisinc.platform.application.mailinglists.NewsletterSendCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.cms.BlogTag;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.cms.WebPageTemplate;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogTagRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageTemplateRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/7/18 10:47 AM
 */
public class BlogEditorWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/blog-editor.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // This page can return to different places
    String returnPage = context.getSharedRequestValue("returnPage");
    if (returnPage == null) {
      returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    }
    context.getRequest().setAttribute("returnPage", returnPage);

    // Determine the state of the blog post
    BlogPost blogPost = null;
    if (context.getRequestObject() != null) {
      blogPost = (BlogPost) context.getRequestObject();
      context.getRequest().setAttribute("blogPost", blogPost);
    } else {
      long blogPostId = context.getParameterAsLong("blogPostId");
      if (blogPostId > -1) {
        blogPost = LoadBlogPostCommand.loadBlogPostById(blogPostId);
        context.getRequest().setAttribute("blogPost", blogPost);
      }
    }

    // Governed publish workflow status (issue #407, phase 2): surfaced here so the editor links to
    // BlogPostReviewWidget's submit/approve/reject actions whenever there's a pending draft to act
    // on -- mirrors the status label WebPageListWidget/web-page-list.jsp show for web pages.
    if (blogPost != null && blogPost.hasDraftContent()) {
      context.getRequest().setAttribute("blogPostReviewStatus", ContentReviewCommand.listStatusLabel(blogPost));
    }

    // Determine the blog for this post
    Blog blog = null;
    String blogUniqueId = context.getParameter("blogUniqueId");
    if (StringUtils.isNotBlank(blogUniqueId)) {
      blog = LoadBlogCommand.loadBlogByUniqueId(blogUniqueId);
    } else if (blogPost != null) {
      blog = LoadBlogCommand.loadBlogById(blogPost.getBlogId());
    }
    // Make sure the blog exists
    if (blog == null) {
      // Auto-create it if an admin
      if (context.getUserSession().hasRole("admin")) {
        // The Blog needs an administrative record
        Blog blogBean = new Blog();
        blogBean.setName(blogUniqueId);
        blogBean.setUniqueId(GenerateBlogUniqueIdCommand.generateUniqueId(null, blogBean));
        blogBean.setCreatedBy(context.getUserId());
        blogBean.setModifiedBy(context.getUserId());
        blogBean.setEnabled(true);
        blog = BlogRepository.save(blogBean);
        // The blog needs a page template
        WebPageTemplate template = WebPageTemplateRepository.findByName("Blog Post Article Page");
        if (template != null) {
          String link = "/" + blogUniqueId + "/*";
          WebPage webPage = WebPageRepository.findByLink(link);
          if (webPage == null) {
            String pageXml = template.getPageXml();
            pageXml = StringUtils.replace(pageXml, "${webPageName}", blogBean.getUniqueId());
            webPage = new WebPage(link, pageXml);
            webPage.setCreatedBy(context.getUserId());
            try {
              SaveWebPageCommand.saveWebPage(webPage);
            } catch (DataException e) {
              // No concern yet
            }
          }
        }
      }
      if (blog == null) {
        context.setErrorMessage("The related blog is required");
        LOG.error("The related blog is required");
        return context;
      }
    }
    context.getRequest().setAttribute("blog", blog);

    // Provide the tag checklist (issue #633)
    List<BlogTag> tagList = BlogTagRepository.findAllByBlogId(blog.getId());
    context.getRequest().setAttribute("tagList", tagList);

    // For the "Notify subscribers" mailing list picker (issue #500)
    List<MailingList> allLists = MailingListRepository.findAll();
    List<MailingList> enabledLists = new ArrayList<>();
    if (allLists != null) {
      for (MailingList mailingList : allLists) {
        if (mailingList.getEnabled()) {
          enabledLists.add(mailingList);
        }
      }
    }
    context.getRequest().setAttribute("mailingLists", enabledLists);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Load the record to get all the fields (mirrors WebPageFormWidget.post()) -- this also gives
    // us wasAlreadyPublished below without a second lookup, and is the basis for the
    // mass-assignment guard immediately following.
    long blogPostId = context.getParameterAsLong("id");
    BlogPost blogPostBean = blogPostId > -1 ? LoadBlogPostCommand.loadBlogPostById(blogPostId) : null;
    boolean wasAlreadyPublished = blogPostBean != null && blogPostBean.getPublished() != null;
    if (blogPostBean == null) {
      blogPostBean = new BlogPost();
    }

    // Governed publish workflow fields (#407) must never be settable via this generic form save --
    // only BlogPostReviewWidget's explicit submit/approve/reject actions may change them. Captured
    // here, before BeanUtils.populate() below walks the entire raw parameter map, mirroring
    // WebPageFormWidget's identical guard for the same class of mass-assignment gap (#492/#730).
    // The gate just below reads mayPublishDirectly(reviewRequired) rather than this bean's own
    // approval state, so it isn't itself bypassable by a forged approvedBy/draftStatus parameter --
    // but this restore is still required: SaveBlogPostCommand.saveBlogPost() persists these four
    // fields straight off of blogPostBean (needed so the reset-on-unpublish below actually reaches
    // the database), so without this guard a forged approvedBy/draftStatus/submittedBy/
    // releaseReference parameter would be written to the record as-is.
    String existingDraftStatus = blogPostBean.getDraftStatus();
    long existingSubmittedBy = blogPostBean.getSubmittedBy();
    long existingApprovedBy = blogPostBean.getApprovedBy();
    String existingReleaseReference = blogPostBean.getReleaseReference();

    // Populate the fields
    BeanUtils.populate(blogPostBean, context.getParameterMap());

    // Restore the governed publish workflow fields captured above
    blogPostBean.setDraftStatus(existingDraftStatus);
    blogPostBean.setSubmittedBy(existingSubmittedBy);
    blogPostBean.setApprovedBy(existingApprovedBy);
    blogPostBean.setReleaseReference(existingReleaseReference);

    blogPostBean.setCreatedBy(context.getUserId());
    blogPostBean.setModifiedBy(context.getUserId());

    String enabled = context.getParameter("enabled");
    boolean isPublished = StringUtils.isNotBlank(enabled);
    boolean justPublished = isPublished && !wasAlreadyPublished;

    // Governed publish workflow gate (#407, phase 2): under blogPost.review.required, the
    // "Publish it?" checkbox can no longer take a post live on its own -- only the
    // submit -> approve path (BlogPostReviewWidget) can. This only gates the transition INTO being
    // published; an edit to an already-published post is not gated here, since a blog post has no
    // separate draft/live content split for an already-live post to stage a review against (see
    // issue #407 phase 2 research).
    boolean publishBlockedByReview = false;
    if (justPublished) {
      boolean blogPostReviewRequired = LoadSitePropertyCommand.loadByNameAsBoolean("blogPost.review.required");
      if (!ContentReviewCommand.mayPublishDirectly(blogPostReviewRequired)) {
        AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.publish", AuditEventCommand.FAILURE,
            "blog_post", String.valueOf(blogPostBean.getId()), blogPostBean.getTitle(),
            "blocked: draft not approved for release");
        isPublished = false;
        justPublished = false;
        publishBlockedByReview = true;
      }
    }
    if (isPublished) {
      blogPostBean.setPublished(new Timestamp(System.currentTimeMillis()));
    } else {
      blogPostBean.setPublished(null);
      if (wasAlreadyPublished) {
        // Issue #407 phase 2 review finding: unpublishing a post must invalidate any prior
        // approval, or a single editor could unpublish, edit the body to arbitrary new content,
        // and republish via BlogPostReviewWidget.publishDirectly() -- which reads mayPublish()
        // purely from these fields, with no correlation to what content was actually reviewed --
        // without the new content ever having been submitted or reviewed by anyone. Resetting to
        // the same "never submitted" defaults BlogPost itself starts with means the post must go
        // through submit -> approve again before it can be published a second time.
        blogPostBean.setDraftStatus(null);
        blogPostBean.setSubmittedBy(-1);
        blogPostBean.setApprovedBy(-1);
        blogPostBean.setReleaseReference(null);
      }
    }
    String eventType = isPublished ? "content.publish" : "content.unpublish";

    // Handle the tags (issue #633) -- a single shared-name checkbox group, mirroring how
    // EditItemFormWidget parses "tagId" for items (issue #632)
    List<Long> tagIdList = new ArrayList<>();
    String[] tagIdParams = context.getParameterMap().get("tagId");
    if (tagIdParams != null) {
      // Only accept tagIds that actually belong to this post's own blog, so a crafted tagId
      // for another blog's tag can't be attached to this post
      Set<Long> blogOwnTagIdSet = new HashSet<>();
      List<BlogTag> blogOwnTagList = BlogTagRepository.findAllByBlogId(blogPostBean.getBlogId());
      if (blogOwnTagList != null) {
        for (BlogTag blogOwnTag : blogOwnTagList) {
          blogOwnTagIdSet.add(blogOwnTag.getId());
        }
      }
      for (String rawTagId : tagIdParams) {
        if (StringUtils.isNumeric(rawTagId)) {
          Long parsedTagId = Long.valueOf(rawTagId);
          if (blogOwnTagIdSet.contains(parsedTagId) && !tagIdList.contains(parsedTagId)) {
            tagIdList.add(parsedTagId);
          }
        }
      }
    }
    blogPostBean.setTagIdList(tagIdList.toArray(new Long[0]));

    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));

    // wasAlreadyPublished/justPublished were already determined above (from the record loaded
    // before BeanUtils.populate()), and reused here for the "Notify subscribers" option below --
    // an edit to an already-published post, or unpublishing, must never (re-)send a notification.

    // A timestamp the converter cannot parse becomes null rather than raising -- PageServlet
    // registers SqlTimestampConverter with a null default and a single "MM-dd-yyyy HH:mm" pattern.
    // SaveBlogPostCommand then backfills a null startDate with the publish time, so a mistyped or
    // differently-formatted date silently became "today" and looked like it had saved correctly
    // (issue #1351). That is data loss on any back-dated post, and it is invisible until someone
    // notices the archive is all one day. Check here, where the raw parameter is still available
    // to tell "left blank" (legitimately null) apart from "typed something unparseable".
    String startDateParam = context.getParameter("startDate");
    if (StringUtils.isNotBlank(startDateParam) && blogPostBean.getStartDate() == null) {
      context.setErrorMessage(
          "The publish date could not be read. Use MM-DD-YYYY HH:MM, for example 08-04-2011 16:00.");
      context.setRequestObject(blogPostBean);
      context.addSharedRequestValue("returnPage", returnPage);
      return context;
    }
    String endDateParam = context.getParameter("endDate");
    if (StringUtils.isNotBlank(endDateParam) && blogPostBean.getEndDate() == null) {
      context.setErrorMessage(
          "The expiration date could not be read. Use MM-DD-YYYY HH:MM, for example 08-04-2011 16:00.");
      context.setRequestObject(blogPostBean);
      context.addSharedRequestValue("returnPage", returnPage);
      return context;
    }

    // Save the blog post
    BlogPost blogPost = null;
    try {
      blogPost = SaveBlogPostCommand.saveBlogPost(blogPostBean);
      if (blogPost == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, eventType, AuditEventCommand.FAILURE,
          "blog_post", String.valueOf(blogPostBean.getId()), blogPostBean.getTitle(), e.getMessage());
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(blogPostBean);
      context.addSharedRequestValue("returnPage", returnPage);
      return context;
    }

    // Record the publish/unpublish
    AuditEventCommand.record(context, AuditEventCommand.CONTENT, eventType, AuditEventCommand.SUCCESS,
        "blog_post", String.valueOf(blogPost.getId()), blogPost.getTitle(), null);

    // Notify subscribers (issue #500), only on the actual publish transition
    String notifiedSuffix = "";
    if (justPublished && StringUtils.isNotBlank(context.getParameter("notifySubscribers"))) {
      long mailingListId = context.getParameterAsLong("notifyMailingListId");
      MailingList mailingList = mailingListId > -1 ? MailingListRepository.findById(mailingListId) : null;
      if (mailingList != null) {
        try {
          int recipientCount = NewsletterSendCommand.sendBlogPostNotification(mailingList, blogPost, context.getUserId());
          AuditEventCommand.record(context, AuditEventCommand.CONTENT, "newsletter.enqueue", AuditEventCommand.SUCCESS,
              "mailing_list", String.valueOf(mailingList.getId()), mailingList.getName(),
              recipientCount + " recipient(s) for \"" + blogPost.getTitle() + "\"");
          notifiedSuffix = recipientCount == 0
              ? " No active subscribers were found on that list."
              : " " + recipientCount + " subscriber" + (recipientCount == 1 ? "" : "s") + " will be notified.";
        } catch (DataException e) {
          AuditEventCommand.record(context, AuditEventCommand.CONTENT, "newsletter.enqueue", AuditEventCommand.FAILURE,
              "mailing_list", String.valueOf(mailingList.getId()), mailingList.getName(), e.getMessage());
          notifiedSuffix = " Subscribers could not be notified: " + e.getMessage();
        }
      }
    }

    // Determine the page to return to
    String reviewNotice = publishBlockedByReview
        ? " This post requires review before it can go live; it was saved as a draft -- use \"Submit for Review\" to send it for approval."
        : "";
    context.setSuccessMessage("Blog post was saved" + notifiedSuffix + reviewNotice);
    if (StringUtils.isNotBlank(returnPage)) {
      context.setRedirect(returnPage);
    } else {
      // A blog post lives at /{blog}/{post} -- see BlogPost.getLink() and the sitemap/feed, which
      // both build it that way. Redirecting to /{post} alone dropped the blog segment and landed
      // the author on a 404 immediately after a successful save, which reads as the save having
      // failed. Falls back to the site root rather than that 404 when the blog cannot be resolved.
      Blog savedBlog = LoadBlogCommand.loadBlogById(blogPost.getBlogId());
      context.setRedirect(
          savedBlog != null ? "/" + savedBlog.getUniqueId() + "/" + blogPost.getUniqueId() : "/");
    }
    return context;
  }
}
