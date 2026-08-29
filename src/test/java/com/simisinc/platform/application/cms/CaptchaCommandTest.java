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

import com.github.fge.jackson.JsonLoader;
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
import static org.mockito.ArgumentMatchers.any;
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
  void validateRequestTurnstileNotConfiguredFallsBackRatherThanAcceptingEverything() {
    // This reverses issue #519, which asserted the opposite here: "captcha.service=turnstile but no
    // site/secret key set yet -- must not fall back to the drawn-image check the way a blank
    // service would", and returned true.
    //
    // Returning true means every submission is accepted by the control whose only job is to reject
    // some of them, while the page still renders a widget so it looks protected (issue 1614). The
    // reason #519 avoided the fallback looks like the renderer: populateWidgetAttributes read the
    // raw property, so falling back in validation alone would have put a Turnstile box on the page
    // and graded a drawn-image answer, failing every submission. Both now read usableService(), so
    // the fallback renders the challenge it grades and that objection no longer holds.
    //
    // captcha.google.sitekey is deliberately left unstubbed, as it was before, to prove the Google
    // keys are never consulted on this path.
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn("turnstile");
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.turnstile.sitekey")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.turnstile.secretkey")).thenReturn(null);

      WidgetContext context = mock(WidgetContext.class);
      HttpServletRequest request = mock(HttpServletRequest.class);
      HttpSession session = mock(HttpSession.class);
      when(context.getRequest()).thenReturn(request);
      when(request.getSession()).thenReturn(session);
      when(session.getAttribute(SessionConstants.CAPTCHA_TEXT)).thenReturn(null);

      Assertions.assertFalse(CaptchaCommand.validateRequest(context),
          "a service named without keys must not accept the submission");
    }
  }

  @Test
  void validateRequestUnrecognizedServiceDoesNotAcceptEverything() {
    // captcha.service is a free-text site property, not a fixed list, so this is a typo away:
    // "Google" with a capital G reached the !"google".equals(service) branch and returned true.
    for (String typo : new String[] { "Google", "recaptcha", "reCAPTCHA", "turnstile " }) {
      try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
        property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn(typo);
        property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.sitekey")).thenReturn("a-site-key");
        property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.secretkey")).thenReturn(null);

        WidgetContext context = mock(WidgetContext.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(context.getRequest()).thenReturn(request);
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute(SessionConstants.CAPTCHA_TEXT)).thenReturn(null);

        Assertions.assertFalse(CaptchaCommand.validateRequest(context),
            "captcha.service=\"" + typo + "\" must not accept the submission");
      }
    }
  }

  @Test
  void validateRequestGoogleWithoutASecretDoesNotAcceptEverything() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn("google");
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.sitekey")).thenReturn("a-site-key");
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.secretkey")).thenReturn(null);

      WidgetContext context = mock(WidgetContext.class);
      HttpServletRequest request = mock(HttpServletRequest.class);
      HttpSession session = mock(HttpSession.class);
      when(context.getRequest()).thenReturn(request);
      when(request.getSession()).thenReturn(session);
      when(session.getAttribute(SessionConstants.CAPTCHA_TEXT)).thenReturn(null);

      Assertions.assertFalse(CaptchaCommand.validateRequest(context));
    }
  }

  @Test
  void populateWidgetAttributesDoesNotRenderAWidgetTheCheckWillNotHonour() {
    // The half that makes the fallback safe. If this exposed turnstileSiteKey while validateRequest
    // graded a drawn-image answer, every submission would fail for a reason nothing on screen
    // explains -- which is the trap that made returning true look reasonable in #519.
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn("turnstile");
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.turnstile.sitekey")).thenReturn("a-site-key");
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.turnstile.secretkey")).thenReturn(null);

      WidgetContext context = mock(WidgetContext.class);
      HttpServletRequest request = mock(HttpServletRequest.class);
      when(context.getRequest()).thenReturn(request);

      CaptchaCommand.populateWidgetAttributes(context);

      verify(request, never()).setAttribute(eq("turnstileSiteKey"), any());
      verify(request).setAttribute("captchaService", null);
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
      // The secret is stubbed too: a widget is only rendered for a service that can
      // actually verify it, so populateWidgetAttributes now needs both keys (issue 1614).
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.secretkey")).thenReturn("a-secret");

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
      // The secret is stubbed too: a widget is only rendered for a service that can
      // actually verify it, so populateWidgetAttributes now needs both keys (issue 1614).
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.turnstile.secretkey")).thenReturn("a-secret");

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


  @Test
  void describeRejectionSurfacesTheCodesTheServiceReturned() throws Exception {
    String body = "{\"success\":false,\"error-codes\":[\"invalid-input-secret\"]}";

    String detail = CaptchaCommand.describeRejection(JsonLoader.fromString(body));

    Assertions.assertTrue(detail.contains("invalid-input-secret"));
  }

  @Test
  void describeRejectionSurfacesCloudflaresReadableMessage() throws Exception {
    // Issue 1624. The code alone said "bad-request", which does not say what is wrong. Cloudflare
    // puts the half a human can act on in "messages", and it named the defect outright.
    String body = "{\"error-codes\":[\"bad-request\"],\"success\":false,"
        + "\"messages\":[\"This API expects Content-Type to be \\\"application/json\\\", "
        + "\\\"application/x-www-form-urlencoded\\\", or \\\"multipart/form-data\\\".\"]}";

    String detail = CaptchaCommand.describeRejection(JsonLoader.fromString(body));

    Assertions.assertTrue(detail.contains("bad-request"));
    Assertions.assertTrue(detail.contains("expects Content-Type"),
        "the message is the half that says what to change");
  }

  @Test
  void describeRejectionAppendsTheHostnameWhenOneIsReturned() throws Exception {
    String body = "{\"success\":false,\"error-codes\":[\"hostname-mismatch\"],"
        + "\"hostname\":\"example.org\"}";

    String detail = CaptchaCommand.describeRejection(JsonLoader.fromString(body));

    Assertions.assertTrue(detail.contains("hostname-mismatch"));
    Assertions.assertTrue(detail.contains("example.org"));
  }

  @Test
  void describeRejectionSaysSoWhenTheServiceExplainedNothing() throws Exception {
    String detail = CaptchaCommand.describeRejection(JsonLoader.fromString("{\"success\":false}"));

    Assertions.assertTrue(detail.contains("no error codes returned"),
        "an empty rejection must read as empty, not as a blank line in the log");
  }

}