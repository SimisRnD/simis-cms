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
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailConstants;
import org.apache.commons.mail.ImageHtmlEmail;
import org.apache.commons.mail.resolver.DataSourceUrlResolver;

import javax.mail.AuthenticationFailedException;
import javax.mail.SendFailedException;
import javax.net.ssl.SSLException;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
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

  /** The from address seeded by the installer (NEW_10000__new_database.sql) */
  private static final String INSTALL_DEFAULT_FROM_ADDRESS = "auto-sender@site.local";

  /**
   * Whether outbound mail has actually been set up for this site.
   *
   * <p>A fresh install seeds working-looking values -- host {@code 127.0.0.1}, port 25, from
   * {@code auto-sender@site.local} -- so a blank-host check reports "configured" on a site that
   * cannot deliver anything. The from address is the reliable signal: a deployment that really
   * relays through localhost still has to set a deliverable from address, because recipients
   * reject {@code site.local}. So the seeded value being untouched means nobody configured mail.
   *
   * <p>Callers use this to avoid telling an admin that a message was sent when it could not be.
   *
   * @return true when the site has mail settings that could plausibly deliver
   */
  public static boolean isOutboundMailConfigured() {
    if (StringUtils.isBlank(LoadSitePropertyCommand.loadByName("mail.host_name"))) {
      return false;
    }
    String fromAddress = LoadSitePropertyCommand.loadByName("mail.from_address");
    return StringUtils.isNotBlank(fromAddress)
        && !INSTALL_DEFAULT_FROM_ADDRESS.equalsIgnoreCase(fromAddress.trim());
  }

  public static ImageHtmlEmail prepareNewEmail() {
    return prepareNewEmail(null);
  }

  /**
   * Applies the configured SMTP transport security to an email.
   *
   * <p>The two schemes are alternatives, not layers, and a server offers one or the other on a given
   * port: implicit SSL/TLS ({@code mail.ssl}) encrypts from the moment the socket opens, traditionally
   * on port 465; STARTTLS ({@code mail.starttls}) opens in plain text, traditionally on port 587, and
   * upgrades afterward. Enabling both is a misconfiguration -- implicit SSL wins, because a connection
   * that is already encrypted has nothing to upgrade, and a warning is logged so the setting that was
   * ignored is discoverable rather than silent.
   *
   * <p>STARTTLS is set to <em>required</em>, not merely enabled. Enabled-only silently falls back to an
   * unencrypted connection when a server does not advertise STARTTLS, which would send the SMTP
   * password in clear text -- the opposite of what an admin who turned this on asked for. Requiring it
   * fails the send instead, which surfaces as a "tls" category in {@link #categorizeSendFailure}.
   *
   * <p>Package-private so it can be unit tested against a real {@link Email}: commons-email types
   * cannot be mocked (their setters are final), but a real instance's flags can be read back.
   *
   * @param email the email being prepared
   * @param mailSSL the {@code mail.ssl} site property value
   * @param mailStartTLS the {@code mail.starttls} site property value
   */
  static void applyTransportSecurity(Email email, String mailSSL, String mailStartTLS) {
    boolean useSSL = "true".equals(mailSSL);
    boolean useStartTLS = "true".equals(mailStartTLS);
    if (useSSL && useStartTLS) {
      LOG.warn("Both mail.ssl and mail.starttls are enabled; these are alternatives, not layers. "
          + "Using implicit SSL and ignoring STARTTLS. Enable only the one your provider documents.");
      useStartTLS = false;
    }
    if (useSSL) {
      email.setSSLOnConnect(true);
    }
    if (useStartTLS) {
      email.setStartTLSEnabled(true);
      email.setStartTLSRequired(true);
    }
  }

  public static ImageHtmlEmail prepareNewEmail(String siteUrl) {

    String mailFromAddress = LoadSitePropertyCommand.loadByName("mail.from_address");
    String mailFromName = LoadSitePropertyCommand.loadByName("mail.from_name");
    String mailHostName = LoadSitePropertyCommand.loadByName("mail.host_name");
    String mailPort = LoadSitePropertyCommand.loadByName("mail.port");
    String mailUsername = LoadSitePropertyCommand.loadByName("mail.username");
    String mailPassword = LoadSitePropertyCommand.loadByName("mail.password");
    String mailSSL = LoadSitePropertyCommand.loadByName("mail.ssl");
    String mailStartTLS = LoadSitePropertyCommand.loadByName("mail.starttls");

    ImageHtmlEmail email = new ImageHtmlEmail();
    email.setCharset(EmailConstants.UTF_8);
    email.setHostName(mailHostName);
    if (StringUtils.isNotBlank(mailPort)) {
      email.setSmtpPort(Integer.parseInt(mailPort));
    }
    if (StringUtils.isNotBlank(mailUsername) && StringUtils.isNotBlank(mailPassword)) {
      email.setAuthenticator(new DefaultAuthenticator(mailUsername, mailPassword));
    }
    applyTransportSecurity(email, mailSSL, mailStartTLS);
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
      // Checked after ConnectException (a SocketException subclass) so a real ConnectException
      // still categorizes more specifically above -- this catches the plain SocketException a
      // *blocked* (rather than closed/unreachable) port typically produces, e.g. "Connection
      // reset" when a cloud platform's egress firewall RSTs outbound SMTP on port 25 (a common,
      // undocumented-until-you-hit-it default on several providers, including Azure).
      if (current instanceof SocketException) {
        return "connect";
      }
    }
    return "unknown";
  }
}
