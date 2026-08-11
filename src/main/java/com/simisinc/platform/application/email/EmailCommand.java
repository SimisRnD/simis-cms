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

package com.simisinc.platform.application.email;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.EmailConstants;
import org.apache.commons.mail.ImageHtmlEmail;
import org.apache.commons.mail.resolver.DataSourceUrlResolver;

import javax.mail.AuthenticationFailedException;
import javax.mail.SendFailedException;
import javax.net.ssl.SSLException;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;

/**
 * Prepares an official site email
 *
 * @author matt rajkowski
 * @created 6/26/18 7:35 AM
 */
public class EmailCommand {

  private static Log LOG = LogFactory.getLog(EmailCommand.class);

  public static ImageHtmlEmail prepareNewEmail() {
    return prepareNewEmail(null);
  }

  public static ImageHtmlEmail prepareNewEmail(String siteUrl) {

    String mailFromAddress = LoadSitePropertyCommand.loadByName("mail.from_address");
    String mailFromName = LoadSitePropertyCommand.loadByName("mail.from_name");
    String mailHostName = LoadSitePropertyCommand.loadByName("mail.host_name");
    String mailPort = LoadSitePropertyCommand.loadByName("mail.port");
    String mailUsername = LoadSitePropertyCommand.loadByName("mail.username");
    String mailPassword = LoadSitePropertyCommand.loadByName("mail.password");
    String mailSSL = LoadSitePropertyCommand.loadByName("mail.ssl");

    ImageHtmlEmail email = new ImageHtmlEmail();
    email.setCharset(EmailConstants.UTF_8);
    email.setHostName(mailHostName);
    if (StringUtils.isNotBlank(mailPort)) {
      email.setSmtpPort(Integer.parseInt(mailPort));
    }
    if (StringUtils.isNotBlank(mailUsername) && StringUtils.isNotBlank(mailPassword)) {
      email.setAuthenticator(new DefaultAuthenticator(mailUsername, mailPassword));
    }
    if ("true".equals(mailSSL)) {
      email.setSSLOnConnect(true);
    }
    // @todo use the bounce address for tracking because emails can come from different systems and users
    // email.setBounceAddress("bounce@example.com");

    try {
      if (StringUtils.isNotBlank(mailFromName)) {
        email.setFrom(mailFromAddress, mailFromName);
      } else {
        email.setFrom(mailFromAddress);
      }
    } catch (Exception e) {
      LOG.error("Error setting from address: " + mailFromAddress);
    }

    // Define your base URL to resolve relative resource locations
    if (StringUtils.isNotBlank(siteUrl)) {
      try {
        URL url = URI.create(siteUrl).toURL();
        email.setDataSourceResolver(new DataSourceUrlResolver(url));
      } catch (Exception e) {
        LOG.error("Could not set DataSourceUrlResolver for url: " + siteUrl);
      }
    }
    return email;
  }

  /**
   * Maps a mail-send failure to a stable, non-sensitive category for display to end users. Never
   * returns raw exception text, which can contain hostnames, ports, or credentials. Shared by any
   * synchronous, request-thread test-send (as opposed to EmailTask's background-job sends, which
   * log the full exception instead since nothing renders it to a user).
   */
  public static String categorizeSendFailure(Throwable throwable) {
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
