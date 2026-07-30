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

package com.simisinc.platform.presentation.widgets.mailinglists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.application.cms.LoadBlogCommand;
import com.simisinc.platform.application.mailinglists.SaveEmailCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Issue #601 -- a blogUniqueId preference scopes the signup to that blog's associated list.
 */
class EmailSubscribeWidgetTest extends WidgetBase {

  private static Blog blog(String uniqueId, long mailingListId) {
    Blog blog = new Blog();
    blog.setUniqueId(uniqueId);
    blog.setMailingListId(mailingListId);
    return blog;
  }

  private static void addBlogScopedPreferences(WidgetContext context, String blogUniqueId) {
    addPreferencesFromWidgetXml(context,
        "<widget name=\"emailSubscribe\">\n" +
            "  <blogUniqueId>" + blogUniqueId + "</blogUniqueId>\n" +
            "  <useCaptcha>false</useCaptcha>\n" +
            "</widget>");
  }

  @Test
  void executeRendersNormallyWithoutABlogUniqueIdPreference() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"emailSubscribe\"><useCaptcha>false</useCaptcha></widget>");

    WidgetContext result = new EmailSubscribeWidget().execute(widgetContext);

    assertEquals(EmailSubscribeWidget.JSP, result.getJsp());
  }

  @Test
  void executeRendersNothingWhenTheBlogDoesNotExist() {
    addBlogScopedPreferences(widgetContext, "missing");

    try (MockedStatic<LoadBlogCommand> loadBlog = mockStatic(LoadBlogCommand.class)) {
      loadBlog.when(() -> LoadBlogCommand.loadBlogByUniqueId("missing")).thenReturn(null);

      WidgetContext result = new EmailSubscribeWidget().execute(widgetContext);

      assertNull(result);
    }
  }

  @Test
  void executeRendersNothingWhenTheBlogHasNoMailingListAssociation() {
    addBlogScopedPreferences(widgetContext, "news");
    Blog blog = blog("news", -1);

    try (MockedStatic<LoadBlogCommand> loadBlog = mockStatic(LoadBlogCommand.class)) {
      loadBlog.when(() -> LoadBlogCommand.loadBlogByUniqueId("news")).thenReturn(blog);

      WidgetContext result = new EmailSubscribeWidget().execute(widgetContext);

      assertNull(result);
    }
  }

  @Test
  void executeRendersTheFormWhenTheBlogHasAMailingListAssociation() {
    addBlogScopedPreferences(widgetContext, "news");
    Blog blog = blog("news", 5L);
    MailingList mailingList = new MailingList();
    mailingList.setId(5L);

    try (MockedStatic<LoadBlogCommand> loadBlog = mockStatic(LoadBlogCommand.class);
        MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      loadBlog.when(() -> LoadBlogCommand.loadBlogByUniqueId("news")).thenReturn(blog);
      listRepo.when(() -> MailingListRepository.findById(5L)).thenReturn(mailingList);

      WidgetContext result = new EmailSubscribeWidget().execute(widgetContext);

      assertEquals(EmailSubscribeWidget.JSP, result.getJsp());
    }
  }

  @Test
  void postSubscribesToTheBlogsMailingListRatherThanTheNamedPreference() throws Exception {
    addBlogScopedPreferences(widgetContext, "news");
    addQueryParameter(widgetContext, "email", "subscriber@example.com");
    Blog blog = blog("news", 5L);
    MailingList mailingList = new MailingList();
    mailingList.setId(5L);

    try (MockedStatic<LoadBlogCommand> loadBlog = mockStatic(LoadBlogCommand.class);
        MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {
      loadBlog.when(() -> LoadBlogCommand.loadBlogByUniqueId("news")).thenReturn(blog);
      listRepo.when(() -> MailingListRepository.findById(5L)).thenReturn(mailingList);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(true);

      new EmailSubscribeWidget().post(widgetContext);

      saveEmail.verify(() -> SaveEmailCommand.saveEmail(any(), eq(mailingList)));
      saveEmail.verify(() -> SaveEmailCommand.saveEmail(any(), any(String.class)), never());
    }
  }

  @Test
  void postFailsClosedWhenTheBlogsAssociationDisappearedBetweenRenderAndSubmit() throws Exception {
    addBlogScopedPreferences(widgetContext, "news");
    addQueryParameter(widgetContext, "email", "subscriber@example.com");
    Blog blog = blog("news", -1);

    try (MockedStatic<LoadBlogCommand> loadBlog = mockStatic(LoadBlogCommand.class);
        MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {
      loadBlog.when(() -> LoadBlogCommand.loadBlogByUniqueId("news")).thenReturn(blog);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(true);

      WidgetContext result = new EmailSubscribeWidget().post(widgetContext);

      assertEquals("Sorry, this signup isn't available right now.", result.getWarningMessage());
      saveEmail.verifyNoInteractions();
    }
  }

  @Test
  void postUsesTheNamedMailingListPreferenceWithoutABlogUniqueId() throws Exception {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"emailSubscribe\"><mailingList>Newsletter</mailingList><useCaptcha>false</useCaptcha></widget>");
    addQueryParameter(widgetContext, "email", "subscriber@example.com");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(true);

      new EmailSubscribeWidget().post(widgetContext);

      saveEmail.verify(() -> SaveEmailCommand.saveEmail(any(), eq("Newsletter")));
    }
  }
}
