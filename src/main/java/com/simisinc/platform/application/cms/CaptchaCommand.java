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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.IpAddressCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.http.HttpPostCommand;
import com.simisinc.platform.presentation.controller.SessionConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Generates and validates CAPTCHAs
 *
 * @author matt rajkowski
 * @created 8/14/18 9:30 AM
 */
public class CaptchaCommand {

  private static Log LOG = LogFactory.getLog(CaptchaCommand.class);

  private static final SecureRandom RANDOM = new SecureRandom();

  /** The value of captcha.service for Cloudflare Turnstile */
  private static final String TURNSTILE = "turnstile";
  /** The value of captcha.service for Google reCAPTCHA */
  private static final String GOOGLE = "google";
  /**
   * Google reCAPTCHA verified through the Enterprise assessment API rather than the legacy
   * siteverify endpoint. An internal mode, never a captcha.service value -- see usableGoogleMode.
   */
  private static final String GOOGLE_ENTERPRISE = "google-enterprise";

  /**
   * The configured captcha service, but only if it can actually run -- otherwise null.
   * <p>
   * Naming a service used to be enough to be trusted. If captcha.service said {@code turnstile}
   * or {@code google} and the keys behind it were blank, or if it held anything else at all,
   * validateRequest logged a warning and returned true: every submission accepted, on a control
   * whose entire job is to reject some of them. The widget still rendered, so the form looked
   * protected from the outside, and the only trace was a line in a log nobody reads (issue 1614).
   * </p>
   * <p>
   * captcha.service is a free-text site property, not a fixed list, so an unrecognised value is
   * not hypothetical -- {@code Google}, {@code recaptcha}, or a trailing space all reach this.
   * </p>
   * <p>
   * Returning null routes those cases to the drawn-image captcha, which the dispatch already fell
   * back to when nothing was configured at all. That is deliberately not the same as rejecting
   * every submission: a misconfigured site keeps a working form and a real challenge, rather than
   * trading a silent hole for a silent outage.
   * </p>
   */
  private static String usableService() {
    String service = LoadSitePropertyCommand.loadByName("captcha.service");
    if (StringUtils.isBlank(service)) {
      return null;
    }
    service = service.trim();
    if (TURNSTILE.equals(service)) {
      if (StringUtils.isBlank(LoadSitePropertyCommand.loadByName("captcha.turnstile.sitekey"))
          || StringUtils.isBlank(LoadSitePropertyCommand.loadByName("captcha.turnstile.secretkey"))) {
        LOG.warn("captcha.service is 'turnstile' but its keys are not set -- "
            + "using the drawn-image captcha instead");
        return null;
      }
      return TURNSTILE;
    }
    if (GOOGLE.equals(service)) {
      return usableGoogleMode();
    }
    LOG.warn("captcha.service is set to an unrecognized value -- "
        + "using the drawn-image captcha instead");
    return null;
  }

  /**
   * Which Google integration can actually run: Enterprise, legacy, or neither.
   *
   * <p>
   * Enterprise is inferred from a project id and an API key being present, deliberately rather
   * than from a fourth captcha.service value. That property is free text, and issue 1614 is what a
   * typo in it costs -- a site that meant "google" and wrote "Google" had no captcha at all. A
   * setting nobody types cannot be mistyped, and the two values Enterprise needs are ones a site
   * either has from the Google console or does not.
   * </p>
   *
   * <p>
   * The legacy path is checked second, so a site that has both configured uses Enterprise. That is
   * the right way round: a key issued by today's console cannot be verified by siteverify at all
   * (issue 1615), while an older key works either way.
   * </p>
   */
  private static String usableGoogleMode() {
    if (StringUtils.isBlank(LoadSitePropertyCommand.loadByName("captcha.google.sitekey"))) {
      LOG.warn("captcha.service is 'google' but no site key is set -- "
          + "using the drawn-image captcha instead");
      return null;
    }
    boolean hasProject = StringUtils.isNotBlank(LoadSitePropertyCommand.loadByName("captcha.google.projectid"));
    boolean hasApiKey = StringUtils.isNotBlank(LoadSitePropertyCommand.loadByName("captcha.google.apikey"));
    if (hasProject && hasApiKey) {
      return GOOGLE_ENTERPRISE;
    }
    if (hasProject || hasApiKey) {
      // Half-configured Enterprise. Saying so beats falling back to a legacy secret that may not
      // exist and reporting whatever siteverify makes of a key it cannot verify.
      LOG.warn("captcha.google.projectid and captcha.google.apikey must both be set to use the "
          + "reCAPTCHA Enterprise API; only one is present");
    }
    if (StringUtils.isNotBlank(LoadSitePropertyCommand.loadByName("captcha.google.secretkey"))) {
      return GOOGLE;
    }
    LOG.warn("captcha.service is 'google' but neither a secret key nor an Enterprise project and "
        + "API key are set -- using the drawn-image captcha instead");
    return null;
  }

  /** The built-in drawn-image challenge: compares the submitted text to the value held in session. */
  private static boolean validateDrawnImageCaptcha(WidgetContext context) {
    String checkValue = (String) context.getRequest().getSession().getAttribute(SessionConstants.CAPTCHA_TEXT);
    String captcha = context.getParameter("captcha");
    if (StringUtils.isBlank(checkValue) || StringUtils.isBlank(captcha)) {
      return false;
    }
    return (captcha.trim().equalsIgnoreCase(checkValue));
  }

  public static boolean validateRequest(WidgetContext context) {

    // A named service whose keys are usable, or null. Both this and populateWidgetAttributes read
    // it, which is the point: the page must not render a provider's widget that the check will not
    // then honour, or a visitor solves one challenge and is graded on another.
    String service = usableService();

    if (service == null) {
      return validateDrawnImageCaptcha(context);
    }

    if (TURNSTILE.equals(service)) {
      return validateTurnstileRequest(context);
    }

    if (GOOGLE_ENTERPRISE.equals(service)) {
      return validateEnterpriseRequest(context);
    }

    String siteKey = LoadSitePropertyCommand.loadByName("captcha.google.sitekey");
    String secretKey = LoadSitePropertyCommand.loadByName("captcha.google.secretkey");

    // Check for the required parameter
    String gResponse = context.getParameter("g-recaptcha-response");
    if (StringUtils.isBlank(gResponse)) {
      LOG.error("Request is missing g-recaptcha-response: " + context.getRequest().getRemoteAddr());
      return false;
    }

    // Send the value to Google for confirmation
    String url = "https://www.google.com/recaptcha/api/siteverify";
    Map<String, String> parameters = new HashMap<>();
    parameters.put("secret", secretKey);
    parameters.put("response", gResponse);
    // remoteip is defined by both providers as the address of the visitor making *this* request, so
    // it has to come from the request rather than from whatever address the session began at
    // (issue #1791). Neither provider returns an error code for a mismatched value, so a wrong one
    // is silently unreportable -- which is why this never surfaced.
    String remoteIpAddress = IpAddressCommand.forAction(context.getRequest(),
        context.getUserSession() != null ? context.getUserSession().getIpAddress() : null);
    if (StringUtils.isNotBlank(remoteIpAddress)) {
      parameters.put("remoteip", remoteIpAddress);
    }
    // executeWithResponse, not execute: a verification service reports a bad secret in the BODY of
    // a 4xx, and execute() drops the body of any non-2xx. Cloudflare answers a wrong Turnstile
    // secret with 400 and {"error-codes":["invalid-input-secret"]}, so this read "Remote content is
    // empty" and an operator could not tell a wrong secret from a network fault -- which is what
    // made an evening of Turnstile debugging produce nothing (issue 1616). Google returns 200 with
    // success:false for the same class of error, which is why only one of the two was ever
    // diagnosable. Parse the body whatever the status; the codes are in there either way.
    HttpPostCommand.HttpPostResult result = HttpPostCommand.executeWithResponse(url, parameters);
    if (result == null) {
      LOG.error("The verification request could not be sent to " + url);
      return false;
    }
    String remoteContent = result.getBody();
    if (StringUtils.isBlank(remoteContent)) {
      LOG.error("Verification returned HTTP " + result.getStatusCode() + " with no body from " + url);
      return false;
    }

    // {
    // "success": true|false,
    // "challenge_ts": timestamp, // timestamp of the challenge load (ISO format
    // yyyy-MM-dd'T'HH:mm:ssZZ)
    // "hostname": string, // the hostname of the site where the reCAPTCHA was
    // solved
    // "error-codes": [...] // optional
    // }
    if (LOG.isDebugEnabled()) {
      LOG.debug("REMOTE TEXT: " + remoteContent);
    }
    try {
      JsonNode json = JsonLoader.fromString(remoteContent);
      if (json.has("success")) {
        String success = json.get("success").asText();
        if ("true".equals(success)) {
          return true;
        }
      }
      LOG.error("reCAPTCHA rejected the response (HTTP " + result.getStatusCode() + "): "
          + describeRejection(json));
    } catch (Exception e) {
      LOG.error("validateRequest json error", e);
    }
    return false;
  }

  /**
   * A rejection, in the terms the vendor used.
   *
   * <p>
   * Both providers return an {@code error-codes} array naming exactly why a token was refused --
   * {@code invalid-input-secret} for a mismatched key pair, {@code timeout-or-duplicate} for a token
   * already spent or past its lifetime, {@code invalid-input-response} for one that never came from
   * this widget. Reading only {@code success} threw that away, leaving a failed submission with no
   * log line at all: the two error paths above this one log, this one did not, so the most common
   * failure was the only silent one. A site owner then sees "the form could not be validated" and
   * has nothing anywhere to say which of those it was.
   * </p>
   *
   * <p>
   * The hostname is included because a mismatch there is a common cause and is not always reported
   * as an error code.
   * </p>
   */
  /**
   * A one-line explanation of why a verification service said no.
   *
   * <p>
   * Package-private so it can be tested directly. The value it returns only ever reaches a log
   * line, so nothing observable distinguishes a good description from a useless one at the call
   * site, and "the codes were parsed" is exactly the kind of thing that passes a test while telling
   * an operator nothing (see issue 1604 for the same shape of mistake in a different file).
   * </p>
   *
   * <p>
   * {@code messages} is read because Cloudflare puts the human-readable half of the answer there
   * while {@code error-codes} carries only a slug. On a 415 the codes said "bad-request" and the
   * message said "This API expects Content-Type to be application/json,
   * application/x-www-form-urlencoded, or multipart/form-data" -- which named the defect outright
   * (issue 1624). Google does not send the field, so this costs nothing there.
   * </p>
   */
  static String describeRejection(JsonNode json) {
    StringBuilder detail = new StringBuilder();
    if (json.has("error-codes") && json.get("error-codes").isArray()) {
      for (JsonNode code : json.get("error-codes")) {
        if (detail.length() > 0) {
          detail.append(", ");
        }
        detail.append(code.asText());
      }
    }
    if (detail.length() == 0) {
      detail.append("no error codes returned");
    }
    if (json.has("messages") && json.get("messages").isArray()) {
      for (JsonNode message : json.get("messages")) {
        String text = message.asText();
        if (StringUtils.isNotBlank(text)) {
          detail.append(" -- ").append(text);
        }
      }
    }
    if (json.has("hostname")) {
      detail.append(" (hostname: ").append(json.get("hostname").asText()).append(")");
    }
    return detail.toString();
  }

  /**
   * Validates a token through the reCAPTCHA Enterprise assessment API.
   *
   * <p>
   * A key issued by Google's current console cannot be verified by the legacy {@code siteverify}
   * endpoint at all -- not with its secret, and not with the "legacy secret key" the console offers
   * for third-party integrations, which covers checkbox keys only. Verified against the live
   * service on a policy-based key: {@code invalid-input-response} every time (issue 1615). So this
   * is the only route by which a site can use a key created today.
   * </p>
   *
   * <p>
   * Unlike siteverify, the response carries a score. The legacy paths read only {@code success} and
   * throw the score away, which means a threshold set in Google's console governs whether the
   * BROWSER shows a challenge and has no effect on what the server accepts. Here the score is read.
   * When {@code captcha.google.scorethreshold} is set it is enforced; when it is not, the score is
   * logged rather than silently ignored, so an operator can see what real traffic scores before
   * choosing a number to reject people on.
   * </p>
   *
   * https://cloud.google.com/recaptcha/docs/create-assessment-website
   */
  private static boolean validateEnterpriseRequest(WidgetContext context) {

    String siteKey = LoadSitePropertyCommand.loadByName("captcha.google.sitekey");
    String projectId = LoadSitePropertyCommand.loadByName("captcha.google.projectid");
    String apiKey = LoadSitePropertyCommand.loadByName("captcha.google.apikey");
    if (StringUtils.isBlank(siteKey) || StringUtils.isBlank(projectId) || StringUtils.isBlank(apiKey)) {
      // Unreachable: usableGoogleMode established all three before dispatching here. Kept as a
      // guard, and it fails CLOSED -- see issue 1614 for what the other choice costs.
      LOG.error("validateEnterpriseRequest reached without usable settings -- rejecting");
      return false;
    }

    String gResponse = context.getParameter("g-recaptcha-response");
    if (StringUtils.isBlank(gResponse)) {
      LOG.error("Request is missing g-recaptcha-response: " + context.getRequest().getRemoteAddr());
      return false;
    }

    // The API key travels in the query string because that is the form Google's API takes. Every
    // url this platform logs is redacted first (HttpPostCommand#redactUrl), so it cannot reach a
    // log line from here -- including at DEBUG, which is the level someone turns on while chasing
    // exactly this kind of failure.
    String url = "https://recaptchaenterprise.googleapis.com/v1/projects/" + projectId
        + "/assessments?key=" + apiKey;

    String body;
    try {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode event = mapper.createObjectNode();
      // Built through Jackson rather than concatenated: the token is request input, and a quote in
      // it would otherwise rewrite the document being posted.
      event.put("token", gResponse);
      event.put("siteKey", siteKey);
      event.put("expectedAction", "submit");
      ObjectNode root = mapper.createObjectNode();
      root.set("event", event);
      body = mapper.writeValueAsString(root);
    } catch (Exception e) {
      LOG.error("Could not build the assessment request", e);
      return false;
    }

    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");

    HttpPostCommand.HttpPostResult result = HttpPostCommand.executeWithResponse(url, headers, body,
        HttpPostCommand.POST);
    if (result == null) {
      LOG.error("The assessment request could not be sent to the reCAPTCHA Enterprise API");
      return false;
    }
    String remoteContent = result.getBody();
    if (StringUtils.isBlank(remoteContent)) {
      LOG.error("The reCAPTCHA Enterprise API returned HTTP " + result.getStatusCode() + " with no body");
      return false;
    }

    try {
      JsonNode json = JsonLoader.fromString(remoteContent);

      // A 4xx here is an API problem rather than a visitor problem -- an unenabled API, a
      // restricted key, an exhausted quota. Naming it matters: the same shape of failure spent a
      // day looking like a bad secret (issue 1616).
      if (!result.isSuccess()) {
        String message = json.has("error") && json.get("error").has("message")
            ? json.get("error").get("message").asText()
            : "no message returned";
        LOG.error("The reCAPTCHA Enterprise API rejected the request (HTTP " + result.getStatusCode()
            + "): " + message);
        return false;
      }

      JsonNode tokenProperties = json.get("tokenProperties");
      if (tokenProperties == null || !tokenProperties.path("valid").asBoolean(false)) {
        String reason = tokenProperties != null && tokenProperties.has("invalidReason")
            ? tokenProperties.get("invalidReason").asText()
            : "no reason returned";
        LOG.error("reCAPTCHA Enterprise rejected the token: " + reason);
        return false;
      }

      double score = json.path("riskAnalysis").path("score").asDouble(-1d);
      String threshold = LoadSitePropertyCommand.loadByName("captcha.google.scorethreshold");
      if (StringUtils.isNotBlank(threshold)) {
        try {
          double floor = Double.parseDouble(threshold.trim());
          if (score >= 0d && score < floor) {
            LOG.warn("reCAPTCHA Enterprise scored " + score + ", below the configured threshold "
                + floor + " -- rejecting");
            return false;
          }
        } catch (NumberFormatException e) {
          // A threshold nobody can parse must not quietly become no threshold at all.
          LOG.error("captcha.google.scorethreshold is not a number: " + threshold + " -- rejecting");
          return false;
        }
      } else if (score >= 0d) {
        LOG.info("reCAPTCHA Enterprise scored " + score
            + "; set captcha.google.scorethreshold to reject below a value");
      }
      return true;
    } catch (Exception e) {
      LOG.error("validateEnterpriseRequest json error", e);
    }
    return false;
  }

  /**
   * Validates a Cloudflare Turnstile response, mirroring the Google reCAPTCHA branch above:
   * same secret+response POST shape, same {"success": true|false} response shape.
   * https://developers.cloudflare.com/turnstile/get-started/server-side-validation/
   */
  private static boolean validateTurnstileRequest(WidgetContext context) {

    String siteKey = LoadSitePropertyCommand.loadByName("captcha.turnstile.sitekey");
    String secretKey = LoadSitePropertyCommand.loadByName("captcha.turnstile.secretkey");
    if (StringUtils.isBlank(siteKey) || StringUtils.isBlank(secretKey)) {
      // Unreachable: usableService() has already established both keys before dispatching here.
      // Kept as a guard, and it fails CLOSED -- this is the shape that made issue 1614, where a
      // blank secret meant every submission was accepted by the control meant to reject them. A
      // caller reaching this has bypassed the dispatch, which is not a state to trust.
      LOG.error("validateTurnstileRequest reached without usable keys -- rejecting");
      return false;
    }

    // Check for the required parameter (Turnstile's widget submits this field name)
    String turnstileResponse = context.getParameter("cf-turnstile-response");
    if (StringUtils.isBlank(turnstileResponse)) {
      LOG.error("Request is missing cf-turnstile-response: " + context.getRequest().getRemoteAddr());
      return false;
    }

    // Send the value to Cloudflare for confirmation
    String url = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    Map<String, String> parameters = new HashMap<>();
    parameters.put("secret", secretKey);
    parameters.put("response", turnstileResponse);
    // remoteip is defined by both providers as the address of the visitor making *this* request, so
    // it has to come from the request rather than from whatever address the session began at
    // (issue #1791). Neither provider returns an error code for a mismatched value, so a wrong one
    // is silently unreportable -- which is why this never surfaced.
    String remoteIpAddress = IpAddressCommand.forAction(context.getRequest(),
        context.getUserSession() != null ? context.getUserSession().getIpAddress() : null);
    if (StringUtils.isNotBlank(remoteIpAddress)) {
      parameters.put("remoteip", remoteIpAddress);
    }
    // executeWithResponse, not execute: a verification service reports a bad secret in the BODY of
    // a 4xx, and execute() drops the body of any non-2xx. Cloudflare answers a wrong Turnstile
    // secret with 400 and {"error-codes":["invalid-input-secret"]}, so this read "Remote content is
    // empty" and an operator could not tell a wrong secret from a network fault -- which is what
    // made an evening of Turnstile debugging produce nothing (issue 1616). Google returns 200 with
    // success:false for the same class of error, which is why only one of the two was ever
    // diagnosable. Parse the body whatever the status; the codes are in there either way.
    HttpPostCommand.HttpPostResult result = HttpPostCommand.executeWithResponse(url, parameters);
    if (result == null) {
      LOG.error("The verification request could not be sent to " + url);
      return false;
    }
    String remoteContent = result.getBody();
    if (StringUtils.isBlank(remoteContent)) {
      LOG.error("Verification returned HTTP " + result.getStatusCode() + " with no body from " + url);
      return false;
    }

    // {
    // "success": true|false,
    // "challenge_ts": timestamp,
    // "hostname": string,
    // "error-codes": [...],
    // "action": string,
    // "cdata": string
    // }
    if (LOG.isDebugEnabled()) {
      LOG.debug("REMOTE TEXT: " + remoteContent);
    }
    try {
      JsonNode json = JsonLoader.fromString(remoteContent);
      if (json.has("success")) {
        String success = json.get("success").asText();
        if ("true".equals(success)) {
          return true;
        }
      }
      LOG.error("Turnstile rejected the response (HTTP " + result.getStatusCode() + "): "
          + describeRejection(json));
    } catch (Exception e) {
      LOG.error("validateTurnstileRequest json error", e);
    }
    return false;
  }

  /**
   * Sets the request attributes a widget's JSP needs to render the configured CAPTCHA challenge
   * (Google reCAPTCHA, Cloudflare Turnstile, or -- when neither is configured -- the drawn-image
   * fallback). Callers keep their own useCaptcha widget-preference check exactly as before and
   * only call this when that preference is true; this method does not re-check it.
   *
   * Always sets useCaptcha=true and captchaService (the raw captcha.service value, so JSPs don't
   * need to look it up themselves). Sets googleSiteKey or turnstileSiteKey only when captcha.service
   * names that provider -- keeping the client-rendered widget in sync with which provider
   * validateRequest() will actually check the response against.
   */
  public static void populateWidgetAttributes(WidgetContext context) {
    context.getRequest().setAttribute("useCaptcha", "true");
    // usableService(), not the raw property: a service whose keys are missing is verified with the
    // drawn-image captcha, so its widget must not be the one rendered. Reading the raw value here
    // would put a Turnstile box on the page while validateRequest graded a drawn-image answer, and
    // every submission would fail for a reason nothing on screen explains.
    String service = usableService();
    context.getRequest().setAttribute("captchaService", service);
    if (TURNSTILE.equals(service)) {
      context.getRequest().setAttribute("turnstileSiteKey", LoadSitePropertyCommand.loadByName("captcha.turnstile.sitekey"));
    } else if (GOOGLE.equals(service) || GOOGLE_ENTERPRISE.equals(service)) {
      context.getRequest().setAttribute("googleSiteKey", LoadSitePropertyCommand.loadByName("captcha.google.sitekey"));
      // Enterprise keys are driven by enterprise.js, not api.js -- the two are different script
      // families and a key from one is not rendered by the other. The JSPs branch on this rather
      // than on captchaService, so the markup and the check agree the same way they do for
      // Turnstile (issue 1614).
      if (GOOGLE_ENTERPRISE.equals(service)) {
        context.getRequest().setAttribute("googleEnterprise", "true");
      }
    }
  }

  /**
   * Generates a PNG image of text 180 pixels wide, 40 pixels high with white
   * background.
   * https://github.com/javalite/javalite/blob/master/activeweb/src/main/java/org/javalite/activeweb/Captcha.java
   *
   * @param text expects string size eight (8) or less characters.
   * @return byte array that is a PNG image generated with text displayed.
   */
  public static void generateImage(String text, OutputStream out) throws Exception {

    int width = (22 * text.length() + text.length());
    int w = width, h = 40;

    BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    g.setColor(Color.white);
    g.fillRect(0, 0, w, h);

    g.setFont(new Font("Serif", Font.PLAIN, 26));
    g.setColor(Color.blue);
    int start = 10;
    byte[] bytes = text.getBytes();
    for (int i = 0; i < bytes.length; i++) {
      g.setColor(new Color(RANDOM.nextInt(255), RANDOM.nextInt(255), RANDOM.nextInt(255)));
      g.drawString(new String(new byte[] { bytes[i] }), start + (i * 20), (int) (Math.random() * 20 + 20));
    }

    g.setColor(Color.white);
    for (int i = 0; i < text.length(); i++) {
      g.drawOval((int) (Math.random() * width), (int) (Math.random() * 10), 30, 30);
    }

    Stroke oldStroke = g.getStroke();
    g.setStroke(new BasicStroke(1.5f));
    g.setColor(Color.lightGray);
    g.drawRect(0, 0, w, h - 1);
    g.setStroke(oldStroke);
    g.dispose();

    ImageIO.write(image, "png", out);
  }

}
