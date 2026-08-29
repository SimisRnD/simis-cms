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
import com.github.fge.jackson.JsonLoader;
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
      if (StringUtils.isBlank(LoadSitePropertyCommand.loadByName("captcha.google.sitekey"))
          || StringUtils.isBlank(LoadSitePropertyCommand.loadByName("captcha.google.secretkey"))) {
        LOG.warn("captcha.service is 'google' but its keys are not set -- "
            + "using the drawn-image captcha instead");
        return null;
      }
      return GOOGLE;
    }
    LOG.warn("captcha.service is set to an unrecognized value -- "
        + "using the drawn-image captcha instead");
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
    if (context.getUserSession() != null && StringUtils.isNotBlank(context.getUserSession().getIpAddress())) {
      parameters.put("remoteip", context.getUserSession().getIpAddress());
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
  private static String describeRejection(JsonNode json) {
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
    if (json.has("hostname")) {
      detail.append(" (hostname: ").append(json.get("hostname").asText()).append(")");
    }
    return detail.toString();
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
    if (context.getUserSession() != null && StringUtils.isNotBlank(context.getUserSession().getIpAddress())) {
      parameters.put("remoteip", context.getUserSession().getIpAddress());
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
    } else if (GOOGLE.equals(service)) {
      context.getRequest().setAttribute("googleSiteKey", LoadSitePropertyCommand.loadByName("captcha.google.sitekey"));
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
