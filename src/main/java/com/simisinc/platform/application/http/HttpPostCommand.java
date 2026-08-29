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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.routines.UrlValidator;

import com.simisinc.platform.provided.net.ConnectAddressPin;

/**
 * Functions for working with http requests.
 *
 * <p>Use {@link #executeUserUrl(String, Map, String, int)} (and
 * {@link #executeUserUrlWithResponse(String, Map, String, int)}) when the URL comes from
 * untrusted input (user data, admin-entered destination URLs such as a webhook subscription's
 * target -- see issue #418 -- or URLs derived from fetched responses). Those methods run the
 * SSRF guard automatically before connecting, mirroring {@code HttpGetCommand#executeUserUrl}.
 *
 * <p>Use {@link #execute(String, Map)} (and its overloads) only for URLs that are constructed
 * from fixed, operator-controlled configuration (third-party API base URLs, OAuth endpoints,
 * etc.) that may legitimately resolve to internal addresses in some deployments.
 *
 * @author matt rajkowski
 * @created 7/9/2023 5:32 PM
 */
public class HttpPostCommand {

  private static Log LOG = LogFactory.getLog(HttpPostCommand.class);

  public static final int POST = 1;
  public static final int PATCH = 2;
  public static final int PUT = 3;

  /**
   * True when {@link ConnectAddressPin} (issue #760's connect-time DNS pin resolver) actually
   * links on this JVM. See {@code HttpGetCommand#PIN_RESOLVER_AVAILABLE} for the full rationale
   * -- this is the same probe, duplicated here because it must degrade independently per class
   * rather than depend on {@code HttpGetCommand} having been loaded first.
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
          + "classpath. executeUserUrl(...) will still run the SSRF guard and post normally, "
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

  /** The status code and body of an SSRF-guarded POST/PUT/PATCH -- see {@link #executeUserUrlWithResponse}. */
  public static final class HttpPostResult {
    private final int statusCode;
    private final String body;

    public HttpPostResult(int statusCode, String body) {
      this.statusCode = statusCode;
      this.body = body;
    }

    public int getStatusCode() {
      return statusCode;
    }

    public String getBody() {
      return body;
    }

    public boolean isSuccess() {
      return statusCode >= 200 && statusCode < 300;
    }
  }

  /**
   * Validates that {@code url} is SSRF-safe, then posts {@code data} to it. Returns null and
   * logs a warning if the guard rejects the URL. Use this for any URL derived from untrusted
   * input. Closes the DNS-rebinding gap the same way {@code HttpGetCommand#executeUserUrl} does
   * -- see that method's javadoc for the full rationale.
   *
   * @return the response body, or null if the guard rejected the url, the request could not be
   *         sent, or the response was a non-2xx status (same body-or-null contract as
   *         {@link #execute(String, Map, String, int)}) -- use
   *         {@link #executeUserUrlWithResponse(String, Map, String, int)} when the status code
   *         and body are both needed, e.g. for a non-2xx that still has a body worth recording.
   */
  public static String executeUserUrl(String url, Map<String, String> headers, String data, int httpMethod) {
    HttpResponse<String> response = sendRequestUserUrl(url, headers, data, httpMethod);
    if (response == null) {
      return null;
    }
    int status = response.statusCode();
    if (status < 200 || status >= 300) {
      // DEBUG here, WARN on the execute() path above, and the difference is deliberate. This
      // overload posts to a url someone else configured -- a subscriber's webhook endpoint being
      // down is routine and self-inflicted, so warning on every one would be noise in a log that
      // has to stay readable. The fixed endpoints execute() talks to are ours, and a non-2xx from
      // one is a misconfiguration worth surfacing. Callers here that need the body already have
      // executeUserUrlWithResponse, which is what webhook delivery uses.
      LOG.debug("Received status: " + status);
      return null;
    }
    String content = response.body();
    if (content == null || content.length() <= 0) {
      return null;
    }
    return content;
  }

  /**
   * Like {@link #executeUserUrl(String, Map, String, int)}, but returns the status code and body
   * together regardless of whether the status was 2xx -- needed by callers (e.g. webhook
   * delivery, issue #418) that must record what a non-2xx response actually said. Returns null
   * only when the SSRF guard rejected the url or the request could not be sent at all.
   */
  public static HttpPostResult executeUserUrlWithResponse(String url, Map<String, String> headers, String data,
      int httpMethod) {
    HttpResponse<String> response = sendRequestUserUrl(url, headers, data, httpMethod);
    if (response == null) {
      return null;
    }
    return new HttpPostResult(response.statusCode(), response.body());
  }

  private static HttpResponse<String> sendRequestUserUrl(String url, Map<String, String> headers, String data,
      int httpMethod) {
    RemoteUrlValidationCommand.ValidationResult validation = RemoteUrlValidationCommand.validate(url);
    if (!validation.isAllowed()) {
      LOG.warn("Blocked an SSRF-unsafe user-supplied url: " + url);
      return null;
    }
    if (!PIN_RESOLVER_AVAILABLE) {
      return sendRequest(url, headers, data, httpMethod);
    }
    ConnectAddressPin.set(validation.getHost(), validation.getAddresses());
    try {
      return sendRequest(url, headers, data, httpMethod);
    } finally {
      ConnectAddressPin.clear();
    }
  }

  public static String execute(String url, Map<String, String> parameters) {
    return execute(url, parameters, POST);
  }

  public static String execute(String url, Map<String, String> headers, Map<String, String> parameters) {
    return execute(url, headers, parameters, POST);
  }

  public static String execute(String url, Map<String, String> headers, String data) {
    return execute(url, headers, data, POST);
  }

  public static String execute(String url, Map<String, String> parameters, int httpMethod) {
    return execute(url, null, parameters, httpMethod);
  }

  public static String execute(String url, Map<String, String> headers, Map<String, String> parameters,
      int httpMethod) {
    return execute(url, withFormContentType(headers), getFormDataAsString(parameters), httpMethod);
  }

  /**
   * Declares the encoding this class just applied, unless the caller already said something.
   *
   * <p>
   * Both overloads that call {@code getFormDataAsString} must route through this. They are the only
   * two places a Map becomes a form body, and they are the only two that can know it -- which is
   * exactly how this was missed once: issue 1616 added executeWithResponse as a second encoding
   * entry point and moved the captcha onto it, and the fix for issue 1624 then landed on the
   * overload the captcha had stopped calling. The header was declared on a path nothing used while
   * Turnstile kept answering 415.
   * </p>
   *
   * <p>
   * The parameters overloads turn a Map into an {@code a=1&b=2} body and then sent it with no
   * {@code Content-Type} at all, because Java's HttpClient adds none. Whether that works is up to
   * the remote: Google's reCAPTCHA siteverify accepts it, and Cloudflare's Turnstile siteverify
   * answers {@code 415 Unsupported Media Type} with "This API expects Content-Type to be
   * application/json, application/x-www-form-urlencoded, or multipart/form-data".
   * </p>
   *
   * <p>
   * So Turnstile verification could never have succeeded, on any secret -- the request was rejected
   * before the credentials were read (issue 1624). Two callers had already found this the hard way
   * and set the header themselves ({@code OAuthHttpCommand}, {@code PERLSAccessTokenCommand}),
   * which is the tell: the default was missing, and each caller was paying for it separately.
   * </p>
   *
   * <p>
   * A caller's own Content-Type always wins -- MailChimp posts JSON through the string overload and
   * must not be second-guessed -- and the caller's map is copied rather than mutated.
   * </p>
   */
  private static Map<String, String> withFormContentType(Map<String, String> headers) {
    if (headers != null) {
      for (String name : headers.keySet()) {
        if ("content-type".equalsIgnoreCase(name)) {
          return headers;
        }
      }
    }
    Map<String, String> withType = new HashMap<>();
    if (headers != null) {
      withType.putAll(headers);
    }
    withType.put("Content-Type", "application/x-www-form-urlencoded");
    return withType;
  }

  /**
   * Posts form parameters and returns the status code alongside the body, rather than discarding
   * the body of a non-2xx response.
   * <p>
   * The {@code execute} overloads return the body or null, which is the right contract when a
   * failed call has nothing to say. It is the wrong one when the remote reports the failure
   * <em>in</em> the body of a 4xx -- the explanation is then thrown away one layer below the code
   * that needs it. Cloudflare's Turnstile verification does exactly that: a wrong secret comes back
   * as {@code 400} with {@code {"error-codes":["invalid-input-secret"]}}, so
   * {@code CaptchaCommand} could only report "Remote content is empty" and an operator had no way
   * to tell a bad secret from a network fault (issue 1616). Google returns 200 with
   * {@code success:false} for the same class of error, which is why the reCAPTCHA path was
   * diagnosable and the Turnstile path was not.
   * </p>
   * <p>
   * This mirrors {@link #executeUserUrlWithResponse}, which already exists for the same reason on
   * the SSRF-guarded path -- see its javadoc, "a non-2xx that still has a body worth recording".
   * That variant is for untrusted URLs; this one is for the fixed endpoints an integration talks to.
   * </p>
   *
   * @return the status and body, or null if the request could not be sent at all
   */
  public static HttpPostResult executeWithResponse(String url, Map<String, String> parameters) {
    return executeWithResponse(url, withFormContentType(null), getFormDataAsString(parameters), POST);
  }

  /** @see #executeWithResponse(String, Map) */
  public static HttpPostResult executeWithResponse(String url, Map<String, String> headers, String data,
      int httpMethod) {
    HttpResponse<String> response = sendRequest(url, headers, data, httpMethod);
    if (response == null) {
      return null;
    }
    return new HttpPostResult(response.statusCode(), response.body());
  }

  public static String execute(String url, Map<String, String> headers, String data, int httpMethod) {
    HttpResponse<String> response = sendRequest(url, headers, data, httpMethod);
    if (response == null) {
      return null;
    }
    int status = response.statusCode();
    if (status < 200 || status >= 300) {
      // WARN, not DEBUG: this is the branch that silently drops a remote's explanation of its own
      // failure, and a caller using this overload has no other way to learn the status. Callers
      // that need the body of a non-2xx should use executeWithResponse (issue 1616).
      LOG.warn("Received status " + status + " from " + url + " -- response body discarded");
      return null;
    }
    String content = response.body();
    if (content == null || content.length() <= 0) {
      return null;
    }
    return content;
  }

  /**
   * Like execute(), but returns the raw HTTP status code instead of the response body. Some
   * endpoints (e.g. MailChimp's campaign send/schedule actions) return 2xx with an empty body on
   * success -- indistinguishable from a real failure via the body-returning overloads above, which
   * treat "no content" and "request failed" the same way. Returns -1 if the url is blank/invalid or
   * the request could not be sent at all (caller should treat that as failure, same as a bad status).
   */
  public static int executeForStatusCode(String url, Map<String, String> headers, String data, int httpMethod) {
    HttpResponse<String> response = sendRequest(url, headers, data, httpMethod);
    return response != null ? response.statusCode() : -1;
  }

  private static HttpResponse<String> sendRequest(String url, Map<String, String> headers, String data,
      int httpMethod) {
    // Validate the url
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

    try {
      LOG.debug("Posting to: " + url);
      // Build the request
      HttpRequest.Builder builder = HttpRequest.newBuilder();
      builder.uri(URI.create(url));
      builder.timeout(Duration.ofSeconds(20));
      if (headers != null) {
        for (Map.Entry<String, String> set : headers.entrySet()) {
          String name = set.getKey();
          String value = set.getValue();
          builder.setHeader(name, value);
        }
      }
      HttpRequest request = null;

      if (PATCH == httpMethod) {
        request = builder.method("PATCH", HttpRequest.BodyPublishers.ofString(data)).build();
      } else if (PUT == httpMethod) {
        request = builder.PUT(HttpRequest.BodyPublishers.ofString(data)).build();
      } else {
        request = builder.POST(HttpRequest.BodyPublishers.ofString(data)).build();
      }

      // Create the HTTP client
      HttpClient client = HttpClient.newBuilder().build();
      return HttpRetryCommand.send(client, request, HttpResponse.BodyHandlers.ofString());
    } catch (Exception e) {
      LOG.error("Http client exception", e);
      return null;
    }
  }

  private static String getFormDataAsString(Map<String, String> formData) {
    StringBuilder formBodyBuilder = new StringBuilder();
    for (Map.Entry<String, String> singleEntry : formData.entrySet()) {
      if (formBodyBuilder.length() > 0) {
        formBodyBuilder.append("&");
      }
      formBodyBuilder.append(URLEncoder.encode(singleEntry.getKey(), StandardCharsets.UTF_8));
      formBodyBuilder.append("=");
      formBodyBuilder.append(URLEncoder.encode(singleEntry.getValue(), StandardCharsets.UTF_8));
    }
    return formBodyBuilder.toString();
  }
}
