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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.mailinglists.NewsletterSendCommand;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

class NewsletterSendWidgetTest extends WidgetBase {

  private static MailingList mailingList(long id, String title, boolean enabled) {
    MailingList mailingList = new MailingList();
    mailingList.setId(id);
    mailingList.setTitle(title);
    mailingList.setEnabled(enabled);
    return mailingList;
  }

  private static BlogPost blogPost(long id, String title) {
    BlogPost blogPost = new BlogPost();
    blogPost.setId(id);
    blogPost.setTitle(title);
    return blogPost;
  }

  @Test
  void executeOnlyListsEnabledMailingLists() {
    List<MailingList> lists = new ArrayList<>();
    lists.add(mailingList(1L, "Active List", true));
    lists.add(mailingList(2L, "Disabled List", false));

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<BlogPostRepository> blogRepo = mockStatic(BlogPostRepository.class)) {
      listRepo.when(MailingListRepository::findAll).thenReturn(lists);
      blogRepo.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      WidgetContext result = new NewsletterSendWidget().execute(widgetContext);

      List<MailingList> shown = (List<MailingList>) result.getRequest().getAttribute("mailingLists");
      assertEquals(1, shown.size());
      assertEquals("Active List", shown.get(0).getTitle());
    }
  }

  @Test
  void postEnqueuesAndReportsTheQueuedCount() throws DataException {
    setRoles(widgetContext, ADMIN);
    MailingList mailingList = mailingList(1L, "News", true);
    BlogPost blogPost = blogPost(2L, "A Post");
    addQueryParameter(widgetContext, "mailingListId", "1");
    addQueryParameter(widgetContext, "blogPostId", "2");

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<BlogPostRepository> blogRepo = mockStatic(BlogPostRepository.class);
        MockedStatic<NewsletterSendCommand> sendCommand = mockStatic(NewsletterSendCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      listRepo.when(() -> MailingListRepository.findById(1L)).thenReturn(mailingList);
      blogRepo.when(() -> BlogPostRepository.findById(2L)).thenReturn(blogPost);
      sendCommand.when(() -> NewsletterSendCommand.sendBlogPostNotification(mailingList, blogPost, 1L))
          .thenReturn(42);

      WidgetContext result = new NewsletterSendWidget().post(widgetContext);

      assertEquals("42 subscribers will be notified.", result.getSuccessMessage());
      assertEquals("/admin/newsletter-send", result.getRedirect());
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONTENT), eq("newsletter.enqueue"),
          eq(AuditEventCommand.SUCCESS), eq("mailing_list"), any(), any(), any()));
    }
  }

  @Test
  void postReportsZeroQueuedDistinctlyFromASuccessfulNonZeroQueue() throws DataException {
    setRoles(widgetContext, ADMIN);
    MailingList mailingList = mailingList(1L, "News", true);
    BlogPost blogPost = blogPost(2L, "A Post");
    addQueryParameter(widgetContext, "mailingListId", "1");
    addQueryParameter(widgetContext, "blogPostId", "2");

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<BlogPostRepository> blogRepo = mockStatic(BlogPostRepository.class);
        MockedStatic<NewsletterSendCommand> sendCommand = mockStatic(NewsletterSendCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      listRepo.when(() -> MailingListRepository.findById(1L)).thenReturn(mailingList);
      blogRepo.when(() -> BlogPostRepository.findById(2L)).thenReturn(blogPost);
      sendCommand.when(() -> NewsletterSendCommand.sendBlogPostNotification(mailingList, blogPost, 1L))
          .thenReturn(0);

      WidgetContext result = new NewsletterSendWidget().post(widgetContext);

      assertEquals("No active subscribers were found on that list.", result.getSuccessMessage());
    }
  }

  @Test
  void postRequiresBothAMailingListAndABlogPostToBeSelected() {
    setRoles(widgetContext, ADMIN);
    // Neither mailingListId nor blogPostId params are set

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<BlogPostRepository> blogRepo = mockStatic(BlogPostRepository.class);
        MockedStatic<NewsletterSendCommand> sendCommand = mockStatic(NewsletterSendCommand.class)) {
      listRepo.when(MailingListRepository::findAll).thenReturn(new ArrayList<>());
      blogRepo.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      WidgetContext result = new NewsletterSendWidget().post(widgetContext);

      assertEquals("Choose a mailing list and a blog post", result.getWarningMessage());
      sendCommand.verify(() -> NewsletterSendCommand.sendBlogPostNotification(any(), any(), anyLong()), never());
    }
  }

  @Test
  void postDoesNothingForAUserWithoutTheRequiredRole() {
    // WidgetBase's default logged-in test user has no roles at all
    addQueryParameter(widgetContext, "mailingListId", "1");
    addQueryParameter(widgetContext, "blogPostId", "2");

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<BlogPostRepository> blogRepo = mockStatic(BlogPostRepository.class);
        MockedStatic<NewsletterSendCommand> sendCommand = mockStatic(NewsletterSendCommand.class)) {
      listRepo.when(MailingListRepository::findAll).thenReturn(new ArrayList<>());
      blogRepo.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      new NewsletterSendWidget().post(widgetContext);

      sendCommand.verify(() -> NewsletterSendCommand.sendBlogPostNotification(any(), any(), anyLong()), never());
    }
  }

  @Test
  void postAllowsACommunityManagerRole() throws DataException {
    setRoles(widgetContext, COMMUNITY_MANAGER);
    MailingList mailingList = mailingList(1L, "News", true);
    BlogPost blogPost = blogPost(2L, "A Post");
    addQueryParameter(widgetContext, "mailingListId", "1");
    addQueryParameter(widgetContext, "blogPostId", "2");

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<BlogPostRepository> blogRepo = mockStatic(BlogPostRepository.class);
        MockedStatic<NewsletterSendCommand> sendCommand = mockStatic(NewsletterSendCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      listRepo.when(() -> MailingListRepository.findById(1L)).thenReturn(mailingList);
      blogRepo.when(() -> BlogPostRepository.findById(2L)).thenReturn(blogPost);
      sendCommand.when(() -> NewsletterSendCommand.sendBlogPostNotification(mailingList, blogPost, 1L))
          .thenReturn(5);

      WidgetContext result = new NewsletterSendWidget().post(widgetContext);

      assertEquals("5 subscribers will be notified.", result.getSuccessMessage());
    }
  }
}
