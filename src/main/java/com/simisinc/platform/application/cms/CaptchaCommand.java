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

  public static boolean validateRequest(WidgetContext context) {

    // Determine the service
    String service = LoadSitePropertyCommand.loadByName("captcha.service");

    // Use Cloudflare Turnstile if it's configured (checked first: a Turnstile-only install has
    // no reason to have captcha.google.sitekey set, so that value must not gate this branch)
    if ("turnstile".equals(service)) {
      return validateTurnstileRequest(context);
    }

    String siteKey = LoadSitePropertyCommand.loadByName("captcha.google.sitekey");

    // Use the default service
    if (StringUtils.isBlank(service) || StringUtils.isBlank(siteKey)) {
      String checkValue = (String) context.getRequest().getSession().getAttribute(SessionConstants.CAPTCHA_TEXT);
      String captcha = context.getParameter("captcha");
      if (StringUtils.isBlank(checkValue) || StringUtils.isBlank(captcha)) {
        return false;
      }
      return (captcha.trim().equalsIgnoreCase(checkValue));
    }

    String secretKey = LoadSitePropertyCommand.loadByName("captcha.google.secretkey");

    // Use Google Recaptcha if it's configured
    if (!"google".equals(service) || StringUtils.isBlank(siteKey) || StringUtils.isBlank(secretKey)) {
      LOG.warn("Google reCAPTCHA is not configured, so skipping check");
      return true;
    }

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
    String remoteContent = HttpPostCommand.execute(url, parameters);
    if (StringUtils.isBlank(remoteContent)) {
      LOG.error("Remote content is empty");
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
    } catch (Exception e) {
      LOG.error("validateRequest json error", e);
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
      LOG.warn("Cloudflare Turnstile is not configured, so skipping check");
      return true;
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
    String remoteContent = HttpPostCommand.execute(url, parameters);
    if (StringUtils.isBlank(remoteContent)) {
      LOG.error("Remote content is empty");
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
    String service = LoadSitePropertyCommand.loadByName("captcha.service");
    context.getRequest().setAttribute("captchaService", service);
    if ("turnstile".equals(service)) {
      context.getRequest().setAttribute("turnstileSiteKey", LoadSitePropertyCommand.loadByName("captcha.turnstile.sitekey"));
    } else if ("google".equals(service)) {
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
