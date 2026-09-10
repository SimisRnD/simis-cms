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

package com.simisinc.platform.presentation.widgets.ecommerce;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.application.cms.CaptchaCommand;
import com.simisinc.platform.application.mailinglists.SaveEmailCommand;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;

/**
 * Issue #484 -- this endpoint (the real target of the footer newsletter signup form's AJAX call,
 * distinct from EmailSubscribeWidget.post()) previously had no CAPTCHA or rate-limit check at all.
 *
 * @author Elizabeth Houser
 */
class EmailSubscribeAjaxTest extends WidgetBase {

  private void addValidParams() {
    addQueryParameter(widgetContext, "token", widgetContext.getUserSession().getFormToken());
    addQueryParameter(widgetContext, "email", "subscriber@example.com");
  }

  private static MailingList mailingList(long id, String title) {
    MailingList mailingList = new MailingList();
    mailingList.setId(id);
    mailingList.setTitle(title);
    return mailingList;
  }

  @Test
  void validSubmissionIsSavedWhenCaptchaAndRateLimitPass() {
    // No online lists configured (a default/fresh install) -- preserves the exact previous
    // single hardcoded-list behavior.
    addValidParams();

    try (MockedStatic<CaptchaCommand> captcha = mockStatic(CaptchaCommand.class);
        MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {
      captcha.when(() -> CaptchaCommand.validateRequest(any())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(true);
      listRepo.when(MailingListRepository::findOnlineLists).thenReturn(null);
      saveEmail.when(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any())).thenReturn(new Email());

      new EmailSubscribeAjax().execute(widgetContext);

      Assertions.assertEquals("{\"status\":\"0\"}", widgetContext.getJson());
      saveEmail.verify(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any()));
      saveEmail.verify(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any(), anyList()), never());
    }
  }

  @Test
  void captchaFailureRejectsWithoutSaving() {
    addValidParams();

    try (MockedStatic<CaptchaCommand> captcha = mockStatic(CaptchaCommand.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {
      captcha.when(() -> CaptchaCommand.validateRequest(any())).thenReturn(false);

      new EmailSubscribeAjax().execute(widgetContext);

      String json = widgetContext.getJson();
      Assertions.assertTrue(json.contains("\"status\":\"1\""), json);
      Assertions.assertTrue(json.contains("verify you're human"), json);
      saveEmail.verify(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any()), never());
    }
  }

  @Test
  void rateLimitFailureRejectsWithoutSaving() {
    addValidParams();

    try (MockedStatic<CaptchaCommand> captcha = mockStatic(CaptchaCommand.class);
        MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {
      captcha.when(() -> CaptchaCommand.validateRequest(any())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(false);

      new EmailSubscribeAjax().execute(widgetContext);

      String json = widgetContext.getJson();
      Assertions.assertTrue(json.contains("\"status\":\"1\""), json);
      Assertions.assertTrue(json.contains(RateLimitCommand.INVALID_ATTEMPTS), json);
      saveEmail.verify(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any()), never());
    }
  }

  @Test
  void captchaIsCheckedBeforeRateLimit() {
    // Order matters for the "first rejection reason wins" style used elsewhere in this codebase --
    // confirm rate limiting isn't even consulted when the captcha already failed.
    addValidParams();

    try (MockedStatic<CaptchaCommand> captcha = mockStatic(CaptchaCommand.class);
        MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class)) {
      captcha.when(() -> CaptchaCommand.validateRequest(any())).thenReturn(false);

      new EmailSubscribeAjax().execute(widgetContext);

      rateLimit.verifyNoInteractions();
    }
  }

  @Test
  void invalidEmailIsRejectedBeforeCaptchaIsEvenChecked() {
    // Pre-existing behavior must survive: malformed input shouldn't burn a captcha/rate-limit check.
    addQueryParameter(widgetContext, "token", widgetContext.getUserSession().getFormToken());
    addQueryParameter(widgetContext, "email", "not-an-email");

    try (MockedStatic<CaptchaCommand> captcha = mockStatic(CaptchaCommand.class)) {
      new EmailSubscribeAjax().execute(widgetContext);

      Assertions.assertEquals("[]", widgetContext.getJson());
      captcha.verifyNoInteractions();
    }
  }

  // -- Issue #598: multi-list checkbox selection --------------------------------------------

  @Test
  void subscribesOnlyToTheListsThatWereChecked() {
    addValidParams();
    addQueryParameter(widgetContext, "mailingListId", "2");
    List<MailingList> onlineLists = new ArrayList<>();
    onlineLists.add(mailingList(1L, "News"));
    onlineLists.add(mailingList(2L, "Cybersecurity Bulletin"));

    try (MockedStatic<CaptchaCommand> captcha = mockStatic(CaptchaCommand.class);
        MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {
      captcha.when(() -> CaptchaCommand.validateRequest(any())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(true);
      listRepo.when(MailingListRepository::findOnlineLists).thenReturn(onlineLists);
      saveEmail.when(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any(), anyList())).thenReturn(new Email());

      new EmailSubscribeAjax().execute(widgetContext);

      Assertions.assertEquals("{\"status\":\"0\"}", widgetContext.getJson());
      saveEmail.verify(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any(), eq(List.of(onlineLists.get(1)))));
    }
  }

  @Test
  void ignoresASubmittedIdThatIsNotAnOnlineList() {
    // A forged/stale id (e.g. a private list) must never subscribe someone to it.
    addValidParams();
    addQueryParameter(widgetContext, "mailingListId", "99");
    List<MailingList> onlineLists = new ArrayList<>();
    onlineLists.add(mailingList(1L, "News"));

    try (MockedStatic<CaptchaCommand> captcha = mockStatic(CaptchaCommand.class);
        MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {
      captcha.when(() -> CaptchaCommand.validateRequest(any())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(true);
      listRepo.when(MailingListRepository::findOnlineLists).thenReturn(onlineLists);

      new EmailSubscribeAjax().execute(widgetContext);

      String json = widgetContext.getJson();
      Assertions.assertTrue(json.contains("\"status\":\"1\""), json);
      Assertions.assertTrue(json.contains("choose at least one list"), json);
      saveEmail.verify(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any(), anyList()), never());
      saveEmail.verify(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any()), never());
    }
  }

  @Test
  void rejectsWithNoListsCheckedWhenOnlineListsExist() {
    addValidParams();
    // No mailingListId parameter submitted at all
    List<MailingList> onlineLists = new ArrayList<>();
    onlineLists.add(mailingList(1L, "News"));

    try (MockedStatic<CaptchaCommand> captcha = mockStatic(CaptchaCommand.class);
        MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {
      captcha.when(() -> CaptchaCommand.validateRequest(any())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(true);
      listRepo.when(MailingListRepository::findOnlineLists).thenReturn(onlineLists);

      new EmailSubscribeAjax().execute(widgetContext);

      String json = widgetContext.getJson();
      Assertions.assertTrue(json.contains("\"status\":\"1\""), json);
      Assertions.assertTrue(json.contains("choose at least one list"), json);
      saveEmail.verifyNoInteractions();
    }
  }

  @Test
  void subscribesToMultipleCheckedLists() {
    addValidParams();
    addQueryParameter(widgetContext, "mailingListId", "1");
    // A second value for the same param name -- WidgetBase's addQueryParameter overwrites rather
    // than appends, so set the raw parameter map entry directly to simulate two checked boxes.
    widgetContext.getParameterMap().put("mailingListId", new String[] { "1", "2" });
    List<MailingList> onlineLists = new ArrayList<>();
    onlineLists.add(mailingList(1L, "News"));
    onlineLists.add(mailingList(2L, "Cybersecurity Bulletin"));

    try (MockedStatic<CaptchaCommand> captcha = mockStatic(CaptchaCommand.class);
        MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {
      captcha.when(() -> CaptchaCommand.validateRequest(any())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(true);
      listRepo.when(MailingListRepository::findOnlineLists).thenReturn(onlineLists);
      saveEmail.when(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any(), anyList())).thenReturn(new Email());

      new EmailSubscribeAjax().execute(widgetContext);

      Assertions.assertEquals("{\"status\":\"0\"}", widgetContext.getJson());
      saveEmail.verify(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any(), eq(onlineLists)));
    }
  }

  /**
   * Issue #1724: this used to answer "[]" for any DataException, which the inline form's handler
   * renders as its generic "re-enter your email in a proper format" -- wrong and unactionable when
   * the signup failed because its mailing list doesn't exist.
   */
  @Test
  void returnsTheActualReasonWhenTheSignupCannotBeSaved() {
    addValidParams();

    try (MockedStatic<CaptchaCommand> captcha = mockStatic(CaptchaCommand.class);
        MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {
      captcha.when(() -> CaptchaCommand.validateRequest(any())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(true);
      listRepo.when(MailingListRepository::findOnlineLists).thenReturn(new ArrayList<>());
      saveEmail.when(() -> SaveEmailCommand.saveEmailRequiringConfirmation(any()))
          .thenThrow(new DataException(SaveEmailCommand.LIST_UNAVAILABLE_MESSAGE));

      new EmailSubscribeAjax().execute(widgetContext);

      Assertions.assertEquals(
          "{\"status\":\"1\",\"message\":\"Sorry, this signup isn't available right now.\"}",
          widgetContext.getJson());
    }
  }
}
