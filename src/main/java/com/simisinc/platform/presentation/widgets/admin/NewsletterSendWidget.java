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

package com.simisinc.platform.presentation.widgets.admin;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.mailinglists.MailChimpCommand;
import com.simisinc.platform.application.mailinglists.NewsletterSendCommand;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostSpecification;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin UI for issue #600: an admin picks a mailing list and a published blog post, and queues a
 * newsletter email to every current subscriber of that list. Sending itself happens
 * asynchronously afterward, via NewsletterQueueJob.
 *
 * @author SimIS Inc.
 */
public class NewsletterSendWidget extends GenericWidget {

  static final long serialVersionUID = 6217552971314772871L;

  static String JSP = "/admin/newsletter-send.jsp";

  public WidgetContext execute(WidgetContext context) {
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

    BlogPostSpecification specification = new BlogPostSpecification();
    specification.setPublishedOnly(true);
    DataConstraints constraints = new DataConstraints(1, 50);
    // The repository overwrites defaultColumnToSortBy with "post_id", so the newest-first order
    // this screen wants never reached the SQL -- an admin picking posts for a newsletter saw them
    // in insertion order. columnsToSortBy survives the repository. Issue 1604.
    constraints.setColumnsToSortBy(new String[] { "published DESC" });
    List<BlogPost> blogPosts = BlogPostRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("blogPosts", blogPosts);

    // Catch the #1 real-world failure mode before an admin wastes a send on it: neither delivery
    // path is configured at all, so every recipient would silently fail (SMTP) or the enqueue
    // itself would error out (MailChimp -- NewsletterSendCommand.sendViaMailChimp requires a
    // reachable, correctly-keyed account). MailChimp being enabled makes the SMTP host irrelevant,
    // so this only warns when BOTH are unset.
    boolean mailChimpEnabled = MailChimpCommand.isEnabled();
    boolean smtpConfigured = StringUtils.isNotBlank(LoadSitePropertyCommand.loadByName("mail.host_name"));
    context.getRequest().setAttribute("sendMethodConfigured", mailChimpEnabled || smtpConfigured);
    context.getRequest().setAttribute("mailChimpEnabled", mailChimpEnabled);

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {
    // Defense in depth: this page is already role-gated at the page-config level
    // (admin-layout.xml), matching SeoSitemapWidget's convention for a state-changing POST. The
    // role set spans both halves of this action -- content-manager (picks the blog post, per
    // /blog-editor's own role) and community-manager (owns mailing lists, per /admin/mailing-lists).
    if (!context.hasRole("admin") && !context.hasRole("content-manager") && !context.hasRole("community-manager")) {
      return execute(context);
    }

    long mailingListId = context.getParameterAsLong("mailingListId");
    long blogPostId = context.getParameterAsLong("blogPostId");

    MailingList mailingList = mailingListId > -1 ? MailingListRepository.findById(mailingListId) : null;
    BlogPost blogPost = blogPostId > -1 ? BlogPostRepository.findById(blogPostId) : null;
    if (mailingList == null || blogPost == null) {
      context.setWarningMessage("Choose a mailing list and a blog post");
      return execute(context);
    }

    int recipientCount;
    try {
      recipientCount = NewsletterSendCommand.sendBlogPostNotification(mailingList, blogPost, context.getUserId());
    } catch (DataException e) {
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "newsletter.enqueue", AuditEventCommand.FAILURE,
          "mailing_list", String.valueOf(mailingList.getId()), mailingList.getName(), e.getMessage());
      context.setErrorMessage(e.getMessage());
      return execute(context);
    }

    AuditEventCommand.record(context, AuditEventCommand.CONTENT, "newsletter.enqueue", AuditEventCommand.SUCCESS,
        "mailing_list", String.valueOf(mailingList.getId()), mailingList.getName(),
        recipientCount + " recipient(s) for \"" + blogPost.getTitle() + "\"");

    context.setSuccessMessage(recipientCount == 0
        ? "No active subscribers were found on that list."
        : recipientCount + " subscriber" + (recipientCount == 1 ? "" : "s") + " will be notified.");
    context.setRedirect("/admin/newsletter-send");
    return context;
  }
}
