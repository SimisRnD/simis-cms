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

package com.simisinc.platform.application.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.routines.UrlValidator;

import com.simisinc.platform.provided.net.ConnectAddressPin;

/**
 * Functions for working with http requests.
 *
 * <p>Use {@link #executeUserUrl(String)} (and the overloads) when the URL comes from
 * untrusted input (user data, admin-entered source URLs, or URLs derived from fetched
 * responses). Those methods run the SSRF guard automatically before connecting.
 *
 * <p>Use {@link #execute(String)} only for URLs that are constructed from fixed,
 * operator-controlled configuration (third-party API base URLs, OAuth endpoints, etc.)
 * that may legitimately resolve to internal addresses in some deployments.
 *
 * @author matt rajkowski
 * @created 2/7/2020 4:25 PM
 */
public class HttpGetCommand {

  private static Log LOG = LogFactory.getLog(HttpGetCommand.class);

  public static final int GET = 1;
  public static final int DELETE = 2;

  /**
   * True when {@link ConnectAddressPin} (issue #760's connect-time DNS pin resolver, built from
   * {@code ssrf-pin-resolver/} and normally supplied on the servlet container's shared
   * classpath -- {@code CATALINA_HOME/lib} for Tomcat) actually links on this JVM. This
   * project's own Docker image ({@code docker/app/Dockerfile}) adds it automatically. It is NOT
   * automatically present for any OTHER way of running this application -- notably, deploying
   * the plain {@code .war} to a self-managed servlet container, which is this project's own
   * README's documented deployment path and is not Docker-specific. Probed once at class-init
   * rather than per-call: whether the jar is on the classpath is a deployment-wide fact fixed
   * for the life of the JVM, not something that can change between two calls.
   */
  private static final boolean PIN_RESOLVER_AVAILABLE = isPinResolverAvailable();

  private static boolean isPinResolverAvailable() {
    try {
      // A real call, not just Class.forName: proves ConnectAddressPin actually LINKS (its own
      // dependencies resolve too), the same resolution executeUserUrl's set/clear calls below
      // depend on. Harmless: clear() is idempotent and nothing is pinned yet at class-init.
      ConnectAddressPin.clear();
      return true;
    } catch (Throwable t) {
      LOG.warn("SSRF connect-time DNS pinning (issue #760) is unavailable: "
          + "com.simisinc.platform.provided.net.ConnectAddressPin was not found on this JVM's "
          + "classpath. executeUserUrl(...) will still run the SSRF guard and fetch normally, "
          + "but without pinning the validated address, so the DNS-rebinding gap "
          + "RemoteUrlValidationCommand's javadoc describes is NOT closed in this deployment "
          + "(no worse than before issue #760, just not improved). This project's own Docker "
          + "image adds the required jar automatically; a servlet container run outside that "
          + "image must also place target/simis-cms-ssrf-pin-resolver.jar on the container's "
          + "shared classpath (CATALINA_HOME/lib for Tomcat) -- see ssrf-pin-resolver/README.md.",
          t);
      return false;
    }
  }

  /**
   * Validates that {@code url} is SSRF-safe, then fetches it. Returns null and logs a
   * warning if the guard rejects the URL. Use this for any URL derived from untrusted input.
   *
   * <p>Closes the DNS-rebinding gap {@code RemoteUrlValidationCommand} documents (issue #760):
   * the address(es) validated are pinned via {@link ConnectAddressPin} for the duration of the
   * actual connect below, so the JDK HttpClient's own re-resolution of {@code url}'s host
   * returns exactly those bytes rather than asking DNS again. The pin is scoped to this thread
   * and always cleared in {@code finally} -- Tomcat reuses worker threads across requests, so a
   * pin left set would otherwise leak into whatever that thread handles next. The request URI
   * itself is never rewritten to an IP literal, so TLS SNI and certificate hostname
   * verification see the original hostname unchanged.
   *
   * <p>Degrades gracefully -- guard still enforced, just not pinned -- rather than failing the
   * fetch outright, when {@link #PIN_RESOLVER_AVAILABLE} is false (see its javadoc).
   */
  public static String executeUserUrl(String url) {
    RemoteUrlValidationCommand.ValidationResult validation = RemoteUrlValidationCommand.validate(url);
    if (!validation.isAllowed()) {
      LOG.warn("Blocked an SSRF-unsafe user-supplied url: " + url);
      return null;
    }
    if (!PIN_RESOLVER_AVAILABLE) {
      return execute(url, GET);
    }
    ConnectAddressPin.set(validation.getHost(), validation.getAddresses());
    try {
      return execute(url, GET);
    } finally {
      ConnectAddressPin.clear();
    }
  }

  public static String execute(String url) {
    return execute(url, GET);
  }

  public static String execute(String url, Map<String, String> headers) {
    return execute(url, headers, GET);
  }

  public static String execute(String url, int httpMethod) {
    return execute(url, null, httpMethod);
  }

  public static String execute(String url, Map<String, String> headers, int httpMethod) {
    // Validate the parameters
    if (StringUtils.isBlank(url)) {
      LOG.debug("No url");
      return null;
    }
    String[] schemes = { "http", "https" };
    UrlValidator urlValidator = new UrlValidator(schemes);
    if (!urlValidator.isValid(url)) {
      LOG.debug("Invalid url: " + url);
      return null;
    }

    // Download as a string
    try {
      LOG.debug("Requesting from: " + redactSecretQueryParams(url));

      // Build the request
      HttpRequest.Builder builder = HttpRequest.newBuilder();
      builder.uri(URI.create(url));
      if (DELETE == httpMethod) {
        builder.DELETE();
      } else {
        builder.GET();
      }
      builder.timeout(Duration.ofSeconds(20));
      if (headers != null) {
        for (Map.Entry<String, String> set : headers.entrySet()) {
          String name = set.getKey();
          String value = set.getValue();
          builder.setHeader(name, value);
        }
      }
      HttpRequest request = builder.build();

      // Send the request and handle the response
      HttpClient client = HttpClient.newHttpClient();
      var response = HttpRetryCommand.send(client, request, HttpResponse.BodyHandlers.ofString());
      if (response == null) {
        LOG.debug("No response");
        return null;
      }

      // Check the status code
      int status = response.statusCode();
      if (status < 200 || status >= 300) {
        LOG.debug("Received status: " + status);
        return null;
      }

      // Verify the content
      String content = response.body();
      if (content == null || content.length() <= 0) {
        return null;
      }
      return content;
    } catch (Exception e) {
      LOG.error("Http client exception", e);
      return null;
    }
  }

  /**
   * This is a shared utility called with whatever URL a caller builds, and callers routinely
   * embed a credential as a query parameter (e.g. an OAuth/webservice token, or a third-party
   * API's access_token/api_key) rather than a header. This class has no way to know which
   * parameter name a given caller considers secret, so redact by a broad name-based heuristic
   * rather than trying to enumerate every caller's convention -- over-redacting an
   * unrelated-but-similarly-named param is a harmless debug-log readability cost; under-redacting
   * a real credential is not.
   */
  static String redactSecretQueryParams(String url) {
    int queryStart = url.indexOf('?');
    if (queryStart < 0 || queryStart == url.length() - 1) {
      return url;
    }
    String base = url.substring(0, queryStart + 1);
    String query = url.substring(queryStart + 1);
    StringBuilder redacted = new StringBuilder(base);
    String[] pairs = query.split("&");
    for (int i = 0; i < pairs.length; i++) {
      if (i > 0) {
        redacted.append('&');
      }
      String pair = pairs[i];
      int eq = pair.indexOf('=');
      if (eq < 0) {
        redacted.append(pair);
        continue;
      }
      String name = pair.substring(0, eq);
      String lowerName = name.toLowerCase();
      if (lowerName.contains("token") || lowerName.contains("key") || lowerName.contains("secret")
          || lowerName.contains("password") || lowerName.contains("pwd") || lowerName.contains("auth")) {
        redacted.append(name).append("=REDACTED");
      } else {
        redacted.append(pair);
      }
    }
    return redacted.toString();
  }
}
