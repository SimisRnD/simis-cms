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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.application.cms.LoadBlogCommand;
import com.simisinc.platform.application.mailinglists.SaveEmailCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.ecommerce.ShippingCountry;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingCountryRepository;
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

  private static MailingList list(long id) {
    MailingList mailingList = new MailingList();
    mailingList.setId(id);
    return mailingList;
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

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      listRepo.when(() -> MailingListRepository.findByName(SaveEmailCommand.DEFAULT_MAILING_LIST_NAME))
          .thenReturn(list(1L));

      WidgetContext result = new EmailSubscribeWidget().execute(widgetContext);

      assertEquals(EmailSubscribeWidget.JSP, result.getJsp());
    }
  }

  @Test
  void executeExposesAnEmptyEmailBeanWhenThereWasNoPriorFailedSubmission() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"emailSubscribe\"><useCaptcha>false</useCaptcha></widget>");

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      listRepo.when(() -> MailingListRepository.findByName(SaveEmailCommand.DEFAULT_MAILING_LIST_NAME))
          .thenReturn(list(1L));

      WidgetContext result = new EmailSubscribeWidget().execute(widgetContext);

      Email email = (Email) result.getRequest().getAttribute("email");
      assertNull(email.getFirstName());
    }
  }

  @Test
  void executeRedisplaysThePreviouslySubmittedValuesAfterAFailedAttempt() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"emailSubscribe\"><useCaptcha>false</useCaptcha></widget>");
    Email previousAttempt = new Email();
    previousAttempt.setFirstName("Jane");
    previousAttempt.setEmail("jane@example.com");
    widgetContext.setRequestObject(previousAttempt);

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      listRepo.when(() -> MailingListRepository.findByName(SaveEmailCommand.DEFAULT_MAILING_LIST_NAME))
          .thenReturn(list(1L));

      WidgetContext result = new EmailSubscribeWidget().execute(widgetContext);

      assertEquals(previousAttempt, result.getRequest().getAttribute("email"));
    }
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
      // Issue #1724: this form subscribes by the blog's list id, so drift in the mailingList name
      // preference is irrelevant to it and must not suppress it
      listRepo.verify(() -> MailingListRepository.findByName(any()), never());
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

      saveEmail.verify(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any(), eq(mailingList)));
      saveEmail.verify(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any(), any(String.class)), never());
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
    MailingList newsletter = list(1L);

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(true);
      saveEmail.when(() -> SaveEmailCommand.findMailingList(null, "Newsletter")).thenReturn(newsletter);

      new EmailSubscribeWidget().post(widgetContext);

      // The name preference still works; what changed is that the widget resolves it to a list
      // itself (so mailingListUniqueId can take priority) rather than handing the name down
      saveEmail.verify(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any(), eq(newsletter)));
    }
  }

  @Test
  void executeWithShowNamePopulatesOnlineMailingListsAndCountryList() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"emailSubscribe\"><showName>true</showName><useCaptcha>false</useCaptcha></widget>");
    MailingList list = new MailingList();
    list.setId(1L);
    ShippingCountry country = new ShippingCountry();
    country.setTitle("United States");

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<ShippingCountryRepository> countryRepo = mockStatic(ShippingCountryRepository.class)) {
      listRepo.when(MailingListRepository::findOnlineLists).thenReturn(List.of(list));
      countryRepo.when(ShippingCountryRepository::findAll).thenReturn(List.of(country));

      WidgetContext result = new EmailSubscribeWidget().execute(widgetContext);

      assertEquals(EmailSubscribeWidget.WITH_NAME_JSP, result.getJsp());
      assertEquals(List.of(list), result.getRequest().getAttribute("onlineMailingLists"));
      assertEquals(List.of(country), result.getRequest().getAttribute("countryList"));
    }
  }

  @Test
  void postWithSeveralCheckedMailingListsSubscribesToAllOfThem() throws Exception {
    // Issue #598's multi-list opt-in, ported to the with-name form's native POST (the inline
    // form's own version of this already goes through a separate AJAX endpoint).
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"emailSubscribe\"><showName>true</showName><useCaptcha>false</useCaptcha></widget>");
    addQueryParameter(widgetContext, "email", "subscriber@example.com");
    widgetContext.getParameterMap().put("mailingListId", new String[]{"1", "2"});
    MailingList listA = new MailingList();
    listA.setId(1L);
    MailingList listB = new MailingList();
    listB.setId(2L);

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(true);
      listRepo.when(() -> MailingListRepository.findById(1L)).thenReturn(listA);
      listRepo.when(() -> MailingListRepository.findById(2L)).thenReturn(listB);

      new EmailSubscribeWidget().post(widgetContext);

      saveEmail.verify(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any(), eq(List.of(listA, listB))));
      saveEmail.verify(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any(), any(String.class)), never());
    }
  }

  @Test
  void postFailsClosedWhenEveryCheckedMailingListIdIsInvalid() throws Exception {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"emailSubscribe\"><showName>true</showName><useCaptcha>false</useCaptcha></widget>");
    addQueryParameter(widgetContext, "email", "subscriber@example.com");
    widgetContext.getParameterMap().put("mailingListId", new String[]{"999"});

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(true);
      listRepo.when(() -> MailingListRepository.findById(999L)).thenReturn(null);

      WidgetContext result = new EmailSubscribeWidget().post(widgetContext);

      assertEquals("Please choose at least one list to subscribe to", result.getWarningMessage());
      saveEmail.verifyNoInteractions();
    }
  }

  @Test
  void postWithoutAnyMailingListIdFallsBackToTheNamedPreference() throws Exception {
    // Regression check: a page whose admin never configured any public (show_online) list must
    // keep behaving exactly as it did before issue #598's checkboxes existed.
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"emailSubscribe\"><mailingList>Newsletter</mailingList><showName>true</showName><useCaptcha>false</useCaptcha></widget>");
    addQueryParameter(widgetContext, "email", "subscriber@example.com");
    MailingList newsletter = list(1L);

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(true);
      saveEmail.when(() -> SaveEmailCommand.findMailingList(null, "Newsletter")).thenReturn(newsletter);

      new EmailSubscribeWidget().post(widgetContext);

      saveEmail.verify(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any(), eq(newsletter)));
    }
  }

  /**
   * Issue #1724: a submit no longer creates the named list when it doesn't resolve, so the same
   * reasoning as the blogUniqueId checks above applies -- don't render a form that can only ever
   * fail, and log which name an admin has to fix.
   */
  @Test
  void executeRendersNothingWhenTheNamedMailingListDoesNotExist() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"emailSubscribe\"><mailingList>Newsletter</mailingList><useCaptcha>false</useCaptcha></widget>");

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      listRepo.when(() -> MailingListRepository.findByName("Newsletter")).thenReturn(null);

      assertNull(new EmailSubscribeWidget().execute(widgetContext));
    }
  }

  @Test
  void executeRendersTheFormWhenTheNamedMailingListExists() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"emailSubscribe\"><mailingList>SimIS Updates</mailingList><useCaptcha>false</useCaptcha></widget>");

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      listRepo.when(() -> MailingListRepository.findByName("SimIS Updates")).thenReturn(list(7L));

      WidgetContext result = new EmailSubscribeWidget().execute(widgetContext);

      assertEquals(EmailSubscribeWidget.JSP, result.getJsp());
    }
  }

  /** The per-list checkboxes (issue #598) are a working path of their own, so a view offering them
   *  isn't broken just because the fallback name has drifted. */
  @Test
  void executeStillRendersWhenTheNamedListIsMissingButPublicListsOfferAChoice() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"emailSubscribe\"><mailingList>Newsletter</mailingList><showName>true</showName>"
            + "<useCaptcha>false</useCaptcha></widget>");

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<ShippingCountryRepository> countryRepo = mockStatic(ShippingCountryRepository.class)) {
      listRepo.when(MailingListRepository::findOnlineLists).thenReturn(List.of(list(2L)));
      listRepo.when(() -> MailingListRepository.findByName("Newsletter")).thenReturn(null);
      countryRepo.when(ShippingCountryRepository::findAll).thenReturn(List.of(new ShippingCountry()));

      WidgetContext result = new EmailSubscribeWidget().execute(widgetContext);

      assertEquals(EmailSubscribeWidget.WITH_NAME_JSP, result.getJsp());
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Issue #1724 follow-up: the mailingListUniqueId preference. mailing_lists.unique_id is assigned
  // when a list is created and never rewritten, so a page pointed at it survives a rename -- the
  // mailingList *name* preference cannot, because name is an editable field on the admin form.
  // ---------------------------------------------------------------------------------------------

  private static MailingList list(long id, String uniqueId) {
    MailingList mailingList = list(id);
    mailingList.setUniqueId(uniqueId);
    return mailingList;
  }

  @Test
  void executeResolvesTheUniqueIdPreferenceAndNeverConsultsTheName() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"emailSubscribe\"><mailingListUniqueId>newsletter</mailingListUniqueId>"
            + "<useCaptcha>false</useCaptcha></widget>");

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      listRepo.when(() -> MailingListRepository.findByUniqueId("newsletter")).thenReturn(list(7L, "newsletter"));

      WidgetContext result = new EmailSubscribeWidget().execute(widgetContext);

      assertEquals(EmailSubscribeWidget.JSP, result.getJsp());
      listRepo.verify(() -> MailingListRepository.findByName(any()), never());
    }
  }

  @Test
  void executeRendersNothingWhenTheUniqueIdPreferenceDoesNotResolve() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"emailSubscribe\"><mailingListUniqueId>deleted-list</mailingListUniqueId>"
            + "<useCaptcha>false</useCaptcha></widget>");

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      listRepo.when(() -> MailingListRepository.findByUniqueId("deleted-list")).thenReturn(null);

      assertNull(new EmailSubscribeWidget().execute(widgetContext));
    }
  }

  /** A uniqueId that doesn't resolve means the list was deleted, not renamed. Falling back to a
   *  (necessarily older) name preference would subscribe visitors to a list nobody configured for
   *  this form -- the silent wrong-list signup issue #1724 is about. */
  @Test
  void executeDoesNotFallBackToTheNameWhenTheUniqueIdPreferenceIsSetButMissing() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"emailSubscribe\"><mailingListUniqueId>deleted-list</mailingListUniqueId>"
            + "<mailingList>Newsletter</mailingList><useCaptcha>false</useCaptcha></widget>");

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      listRepo.when(() -> MailingListRepository.findByUniqueId("deleted-list")).thenReturn(null);
      listRepo.when(() -> MailingListRepository.findByName("Newsletter")).thenReturn(list(1L));

      assertNull(new EmailSubscribeWidget().execute(widgetContext));
      listRepo.verify(() -> MailingListRepository.findByName(any()), never());
    }
  }

  @Test
  void postSubscribesToTheListTheUniqueIdPreferenceNames() throws Exception {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"emailSubscribe\"><mailingListUniqueId>newsletter</mailingListUniqueId>"
            + "<useCaptcha>false</useCaptcha></widget>");
    addQueryParameter(widgetContext, "email", "subscriber@example.com");
    MailingList newsletter = list(7L, "newsletter");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(true);
      saveEmail.when(() -> SaveEmailCommand.findMailingList("newsletter", null)).thenReturn(newsletter);

      new EmailSubscribeWidget().post(widgetContext);

      saveEmail.verify(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any(), eq(newsletter)));
      saveEmail.verify(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any(), any(String.class)), never());
    }
  }

  @Test
  void postFailsClosedWhenTheConfiguredListDisappearedBetweenRenderAndSubmit() throws Exception {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"emailSubscribe\"><mailingListUniqueId>newsletter</mailingListUniqueId>"
            + "<useCaptcha>false</useCaptcha></widget>");
    addQueryParameter(widgetContext, "email", "subscriber@example.com");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(true);
      saveEmail.when(() -> SaveEmailCommand.findMailingList("newsletter", null)).thenReturn(null);

      WidgetContext result = new EmailSubscribeWidget().post(widgetContext);

      assertEquals(SaveEmailCommand.LIST_UNAVAILABLE_MESSAGE, result.getWarningMessage());
      saveEmail.verify(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any(), any(MailingList.class)), never());
    }
  }
}
