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

package com.simisinc.platform.application.cms;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.http.HttpPostCommand;
import com.simisinc.platform.presentation.controller.SessionConstants;
import com.simisinc.platform.presentation.controller.UserSession;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author matt rajkowski
 * @created 5/3/2022 7:00 PM
 */
class CaptchaCommandTest {

  @Test
  void validateRequest() {

    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);

      WidgetContext context = mock(WidgetContext.class);
      HttpServletRequest request = mock(HttpServletRequest.class);
      HttpSession session = mock(HttpSession.class);

      when(context.getRequest()).thenReturn(request);
      when(request.getSession()).thenReturn(session);
      when(session.getAttribute(SessionConstants.CAPTCHA_TEXT)).thenReturn("12345");

      when(context.getParameter("captcha")).thenReturn("12345");
      Assertions.assertTrue(CaptchaCommand.validateRequest(context));

      when(context.getParameter("captcha")).thenReturn("00000");
      Assertions.assertFalse(CaptchaCommand.validateRequest(context));
    }
  }

  @Test
  void validateRequestTurnstileNotConfiguredSkipsCheck() {
    // Issue #519: captcha.service=turnstile but no site/secret key set yet -- must not fall back
    // to the drawn-image check the way a blank service would (captcha.google.sitekey is
    // irrelevant here and is deliberately left unstubbed to prove it's never consulted).
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn("turnstile");
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.turnstile.sitekey")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.turnstile.secretkey")).thenReturn(null);

      WidgetContext context = mock(WidgetContext.class);
      Assertions.assertTrue(CaptchaCommand.validateRequest(context));
    }
  }

  @Test
  void validateRequestTurnstileMissingResponseParameterFails() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn("turnstile");
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.turnstile.sitekey")).thenReturn("test-sitekey");
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.turnstile.secretkey")).thenReturn("test-secretkey");

      WidgetContext context = mock(WidgetContext.class);
      HttpServletRequest request = mock(HttpServletRequest.class);
      when(context.getRequest()).thenReturn(request);
      when(request.getRemoteAddr()).thenReturn("127.0.0.1");
      when(context.getParameter("cf-turnstile-response")).thenReturn(null);

      Assertions.assertFalse(CaptchaCommand.validateRequest(context));
    }
  }

  @Test
  void validateRequestTurnstileSuccessfulVerification() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpPostCommand> httpPost = mockStatic(HttpPostCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn("turnstile");
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.turnstile.sitekey")).thenReturn("test-sitekey");
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.turnstile.secretkey")).thenReturn("test-secretkey");

      WidgetContext context = mock(WidgetContext.class);
      HttpServletRequest request = mock(HttpServletRequest.class);
      UserSession userSession = mock(UserSession.class);
      when(context.getRequest()).thenReturn(request);
      when(context.getUserSession()).thenReturn(userSession);
      when(userSession.getIpAddress()).thenReturn("127.0.0.1");
      when(context.getParameter("cf-turnstile-response")).thenReturn("a-valid-token");

      httpPost.when(() -> HttpPostCommand.executeWithResponse(
          eq("https://challenges.cloudflare.com/turnstile/v0/siteverify"), anyMap()))
          .thenReturn(new HttpPostCommand.HttpPostResult(200, "{\"success\": true}"));

      Assertions.assertTrue(CaptchaCommand.validateRequest(context));

      httpPost.when(() -> HttpPostCommand.executeWithResponse(
          eq("https://challenges.cloudflare.com/turnstile/v0/siteverify"), anyMap()))
          .thenReturn(new HttpPostCommand.HttpPostResult(200,
              "{\"success\": false, \"error-codes\": [\"invalid-input-response\"]}"));

      Assertions.assertFalse(CaptchaCommand.validateRequest(context));
    }
  }

  @Test
  void populateWidgetAttributesGoogle() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn("google");
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.sitekey")).thenReturn("google-sitekey");

      WidgetContext context = mock(WidgetContext.class);
      HttpServletRequest request = mock(HttpServletRequest.class);
      when(context.getRequest()).thenReturn(request);

      CaptchaCommand.populateWidgetAttributes(context);

      verify(request).setAttribute("useCaptcha", "true");
      verify(request).setAttribute("captchaService", "google");
      verify(request).setAttribute("googleSiteKey", "google-sitekey");
      verify(request, never()).setAttribute(eq("turnstileSiteKey"), any());
    }
  }

  @Test
  void populateWidgetAttributesTurnstile() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn("turnstile");
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.turnstile.sitekey")).thenReturn("turnstile-sitekey");

      WidgetContext context = mock(WidgetContext.class);
      HttpServletRequest request = mock(HttpServletRequest.class);
      when(context.getRequest()).thenReturn(request);

      CaptchaCommand.populateWidgetAttributes(context);

      verify(request).setAttribute("useCaptcha", "true");
      verify(request).setAttribute("captchaService", "turnstile");
      verify(request).setAttribute("turnstileSiteKey", "turnstile-sitekey");
      verify(request, never()).setAttribute(eq("googleSiteKey"), any());
    }
  }

  @Test
  void populateWidgetAttributesNoServiceConfigured() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);

      WidgetContext context = mock(WidgetContext.class);
      HttpServletRequest request = mock(HttpServletRequest.class);
      when(context.getRequest()).thenReturn(request);

      CaptchaCommand.populateWidgetAttributes(context);

      verify(request).setAttribute("useCaptcha", "true");
      verify(request).setAttribute("captchaService", null);
      verify(request, never()).setAttribute(eq("googleSiteKey"), any());
      verify(request, never()).setAttribute(eq("turnstileSiteKey"), any());
    }
  }

  @Test
  void generateImage() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Assertions.assertEquals(0, out.size());
    try {
      CaptchaCommand.generateImage("test", out);
      out.close();
      Assertions.assertNotNull(out);
    } catch (Exception e) {
      fail("Should not have thrown any exception");
    }
    Assertions.assertTrue(out.size() > 0);
  }

  @Test
  void validateRequestTurnstileReadsTheErrorCodesOutOfA400() {
    // Issue 1616. Cloudflare reports a wrong secret as HTTP 400 with the codes in the body, and the
    // old call dropped the body of any non-2xx -- so this arrived as "Remote content is empty" and
    // a wrong secret was indistinguishable from a network fault. Google returns 200 with
    // success:false for the same class of error, which is why only one provider was diagnosable.
    //
    // The verify is the real assertion: the return value is false either way, so what has to be
    // pinned is that the status-and-body call is the one being made.
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpPostCommand> httpPost = mockStatic(HttpPostCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn("turnstile");
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.turnstile.sitekey")).thenReturn("test-sitekey");
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.turnstile.secretkey"))
          .thenReturn("a-stale-secret");

      WidgetContext context = mock(WidgetContext.class);
      HttpServletRequest request = mock(HttpServletRequest.class);
      when(context.getRequest()).thenReturn(request);
      when(context.getParameter("cf-turnstile-response")).thenReturn("a-valid-token");

      httpPost.when(() -> HttpPostCommand.executeWithResponse(
          eq("https://challenges.cloudflare.com/turnstile/v0/siteverify"), anyMap()))
          .thenReturn(new HttpPostCommand.HttpPostResult(400,
              "{\"error-codes\":[\"invalid-input-secret\"],\"success\":false,\"messages\":[]}"));

      Assertions.assertFalse(CaptchaCommand.validateRequest(context));

      httpPost.verify(() -> HttpPostCommand.executeWithResponse(
          eq("https://challenges.cloudflare.com/turnstile/v0/siteverify"), anyMap()));
    }
  }

  @Test
  void validateRequestTurnstileFailsWhenTheRequestCouldNotBeSent() {
    // Null result means no response at all -- a genuine network fault, which is the thing the 400
    // case used to be confused with.
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpPostCommand> httpPost = mockStatic(HttpPostCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn("turnstile");
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.turnstile.sitekey")).thenReturn("test-sitekey");
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.turnstile.secretkey")).thenReturn("test-secretkey");

      WidgetContext context = mock(WidgetContext.class);
      HttpServletRequest request = mock(HttpServletRequest.class);
      when(context.getRequest()).thenReturn(request);
      when(context.getParameter("cf-turnstile-response")).thenReturn("a-valid-token");

      httpPost.when(() -> HttpPostCommand.executeWithResponse(
          eq("https://challenges.cloudflare.com/turnstile/v0/siteverify"), anyMap()))
          .thenReturn(null);

      Assertions.assertFalse(CaptchaCommand.validateRequest(context));
    }
  }

}