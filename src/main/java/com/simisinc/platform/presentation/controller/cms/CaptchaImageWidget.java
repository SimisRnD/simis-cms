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

package com.simisinc.platform.presentation.controller.cms;

import com.simisinc.platform.application.cms.CaptchaCommand;
import com.simisinc.platform.presentation.controller.SessionConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.text.RandomStringGenerator;

import java.io.OutputStream;

/**
 * Every time the user visits a page with a captcha, reset the values and show a
 * new image
 *
 * @author matt rajkowski
 * @created 9/30/20 1:00 PM
 */
public class CaptchaImageWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  private static Log LOG = LogFactory.getLog(CaptchaImageWidget.class);

  public WidgetContext execute(WidgetContext context) {

    // Determine the captcha text
    RandomStringGenerator generator = new RandomStringGenerator.Builder()
        .selectFrom("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz0123456789".toCharArray()).build();

    String text = generator.generate(5);

    // Store it in the session / or in the timed pool so it expires
    // by context.getUserSession().getVisitorId()
    context.getRequest().getSession().setAttribute(SessionConstants.CAPTCHA_TEXT, text);

    // Send the image
    context.getResponse().setDateHeader("Last-Modified", System.currentTimeMillis());
    context.getResponse().setContentType("image/png");
    // context.getResponse().setContentLength((int) file.length());
    try {
      OutputStream out = context.getResponse().getOutputStream();
      CaptchaCommand.generateImage(text, out);
      out.close();
    } catch (Exception e) {
      LOG.debug("Stream error: " + e.getMessage());
    }
    context.setHandledResponse(true);
    return context;
  }
}
