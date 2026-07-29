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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.sql.Timestamp;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.LoadBlogPostCommand;
import com.simisinc.platform.application.cms.SaveBlogPostCommand;
import com.simisinc.platform.application.mailinglists.NewsletterSendCommand;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
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
        MockedStatic<NewsletterSendCommand> sendCommand = mockStatic(NewsletterSendCommand.class)) {
      loadPost.when(() -> LoadBlogPostCommand.loadBlogPostById(5L)).thenReturn(existing);
      savePost.when(() -> SaveBlogPostCommand.saveBlogPost(any())).thenReturn(saved);
      listRepo.when(() -> MailingListRepository.findById(9L)).thenReturn(mailingList);
      sendCommand.when(() -> NewsletterSendCommand.enqueueBlogPostNotification(mailingList, saved, 1L)).thenReturn(7);

      WidgetContext result = new BlogEditorWidget().post(widgetContext);

      sendCommand.verify(() -> NewsletterSendCommand.enqueueBlogPostNotification(mailingList, saved, 1L));
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

      sendCommand.verify(() -> NewsletterSendCommand.enqueueBlogPostNotification(any(), any(), anyLong()), never());
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
        MockedStatic<NewsletterSendCommand> sendCommand = mockStatic(NewsletterSendCommand.class)) {
      savePost.when(() -> SaveBlogPostCommand.saveBlogPost(any())).thenReturn(saved);
      listRepo.when(() -> MailingListRepository.findById(9L)).thenReturn(mailingList);
      sendCommand.when(() -> NewsletterSendCommand.enqueueBlogPostNotification(mailingList, saved, 1L)).thenReturn(3);

      new BlogEditorWidget().post(widgetContext);

      sendCommand.verify(() -> NewsletterSendCommand.enqueueBlogPostNotification(mailingList, saved, 1L));
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
        MockedStatic<NewsletterSendCommand> sendCommand = mockStatic(NewsletterSendCommand.class)) {
      loadPost.when(() -> LoadBlogPostCommand.loadBlogPostById(5L)).thenReturn(existing);
      savePost.when(() -> SaveBlogPostCommand.saveBlogPost(any())).thenReturn(saved);

      WidgetContext result = new BlogEditorWidget().post(widgetContext);

      sendCommand.verify(() -> NewsletterSendCommand.enqueueBlogPostNotification(any(), any(), anyLong()), never());
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

      sendCommand.verify(() -> NewsletterSendCommand.enqueueBlogPostNotification(any(), any(), anyLong()), never());
    }
  }
}
