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
import com.simisinc.platform.presentation.controller.SessionConstants;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

public class CaptchaCommandTest {

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
}