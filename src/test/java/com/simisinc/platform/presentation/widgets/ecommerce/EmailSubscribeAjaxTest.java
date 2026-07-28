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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.application.cms.CaptchaCommand;
import com.simisinc.platform.application.mailinglists.SaveEmailCommand;
import com.simisinc.platform.domain.model.mailinglists.Email;

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

  @Test
  void validSubmissionIsSavedWhenCaptchaAndRateLimitPass() {
    addValidParams();

    try (MockedStatic<CaptchaCommand> captcha = mockStatic(CaptchaCommand.class);
        MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {
      captcha.when(() -> CaptchaCommand.validateRequest(any())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(true);
      saveEmail.when(() -> SaveEmailCommand.saveEmail(any())).thenReturn(new Email());

      new EmailSubscribeAjax().execute(widgetContext);

      Assertions.assertEquals("{\"status\":\"0\"}", widgetContext.getJson());
      saveEmail.verify(() -> SaveEmailCommand.saveEmail(any()));
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
      saveEmail.verify(() -> SaveEmailCommand.saveEmail(any()), never());
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
      saveEmail.verify(() -> SaveEmailCommand.saveEmail(any()), never());
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
}
