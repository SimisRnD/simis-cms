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

package com.simisinc.platform.presentation.widgets.admin;

import com.simisinc.platform.application.email.EmailCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.mail.ImageHtmlEmail;

import javax.mail.AuthenticationFailedException;
import javax.mail.SendFailedException;
import javax.net.ssl.SSLException;

import java.lang.reflect.InvocationTargetException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/30/18 8:38 AM
 */
public class SendMailWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  private static Log LOG = LogFactory.getLog(SendMailWidget.class);

  static String JSP = "/admin/send-mail-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the form
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Test the configured SMTP settings by sending a single message to the current admin
    User user = context.getUserSession().getUser();
    try {
      ImageHtmlEmail email = EmailCommand.prepareNewEmail();
      email.addTo(user.getEmail(), user.getFullName());
      email.setSubject("SimIS CMS Mail Test");
      email.setMsg("This is a test message sent from the Mail Server Settings page to confirm the configured SMTP settings are working.");
      email.send();
    } catch (Exception e) {
      String correlationId = UUID.randomUUID().toString();
      LOG.warn("Mail test failed to send [" + correlationId + "]", e);
      context.setErrorMessage(
          "Mail test failed (" + categorize(e) + "). Reference: " + correlationId + ". Check the server logs for details.");
      context.setRedirect("/admin/mail-properties");
      return context;
    }

    // Determine the page to return to (if other than this one)
    context.setSuccessMessage("A test email was sent to " + user.getEmail());
    context.setRedirect("/admin/mail-properties");
    return context;
  }

  /**
   * Maps a mail-send failure to a stable, non-sensitive category for display to admins.
   * Never returns raw exception text, which can contain hostnames, ports, or credentials.
   */
  private static String categorize(Throwable throwable) {
    Throwable current = throwable;
    for (int depth = 0; current != null && depth < 5; depth++, current = current.getCause()) {
      if (current instanceof AuthenticationFailedException) {
        return "auth";
      }
      if (current instanceof SendFailedException) {
        return "rejected";
      }
      if (current instanceof SSLException) {
        return "tls";
      }
      if (current instanceof SocketTimeoutException) {
        return "timeout";
      }
      if (current instanceof ConnectException || current instanceof UnknownHostException
          || current instanceof NoRouteToHostException) {
        return "connect";
      }
    }
    return "unknown";
  }
}
