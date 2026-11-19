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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.ecommerce.EcommerceCommand;

/**
 * Builds the Content-Security-Policy-Report-Only header used to test a stricter policy against real
 * traffic before enforcing it, and the parts of the enforced policy that depend on which features a
 * site has switched on: the image hosts Google Analytics adds, and the connect-src source list.
 *
 * <p>
 * Third-party integrations arrive as a &lt;script src&gt; and then call endpoints of their own:
 * Stripe's script talks to api.stripe.com, gtag builds google-analytics.com at runtime, Square
 * reaches pci-connect.squareup.com. None of those hosts appear in the platform's own code, and they
 * change when a vendor updates its SDK, with no commit here. So connect-src (#1430) was not written
 * from reading the source alone, but from three things together: an inventory of every call the
 * platform's templates and scripts make, each vendor's published CSP requirements, and three months
 * of report-only data from live traffic. See {@link #connectSourceList()}.
 * </p>
 *
 * <p>
 * Report-only stays useful once a directive is enforced. The browser evaluates the candidate policy,
 * refuses nothing, and posts a report for anything that would have been blocked -- which is how a
 * vendor's new endpoint shows up before anyone reports a broken page.
 * </p>
 *
 * <p>
 * The candidate is a site property rather than a constant, which is the opposite of how the enforced
 * policy is handled, and deliberately so: a report-only policy cannot break a page, so the usual
 * reason to keep CSP out of an administrator's hands does not apply -- while the ability to adjust
 * the candidate and watch what happens, without a release, is the entire point of an inventory.
 * Leaving it blank turns the whole mechanism off, which is how it ships.
 * </p>
 *
 * @author SimIS Inc.
 */
public class CspPolicyCommand {

  /** The candidate policy to test. Blank disables report-only entirely. */
  public static final String REPORT_ONLY_PROPERTY = "security.csp.reportOnly";

  /** Where violation reports are posted. Matches CspReportController's servlet mapping. */
  public static final String REPORT_PATH = "/csp-report";

  /** The name tying the Reporting-Endpoints header to the report-to directive. */
  public static final String REPORT_ENDPOINT_NAME = "csp-endpoint";

  /** Replaced with the request's script nonce, so a candidate can carry script-src. */
  public static final String NONCE_PLACEHOLDER = "{nonce}";

  /** Vimeo's oEmbed endpoint, which video.jsp calls from the browser for a Vimeo video's thumbnail. */
  static final String VIMEO_OEMBED = "https://vimeo.com/api/oembed.json";

  /**
   * What gtag.js calls: Google's documented connect-src list for Google Analytics, plus Google
   * Signals' host. See {@link #connectSourceList()} for what is left out.
   */
  static final List<String> GOOGLE_ANALYTICS_CONNECT_SOURCES = List.of(
      "https://www.googletagmanager.com",
      "https://*.google-analytics.com",
      "https://*.google.com",
      "https://*.g.doubleclick.net");

  /** What reCAPTCHA calls, per Google's reCAPTCHA FAQ. A path, so the rest of www.google.com stays closed. */
  static final String RECAPTCHA_CONNECT_SOURCE = "https://www.google.com/recaptcha/";

  /** What Stripe.js calls for the card field, per Stripe's integration security guide. */
  static final String STRIPE_CONNECT_SOURCE = "https://api.stripe.com";

  /** What Square's Web Payments SDK calls in production, per Square's CSP guide. */
  static final List<String> SQUARE_PRODUCTION_CONNECT_SOURCES = List.of(
      "https://web.squarecdn.com",
      "https://pci-connect.squareup.com",
      "https://o160250.ingest.sentry.io");

  /** The same for Square's sandbox, which the form loads whenever ecommerce.production is off. */
  static final List<String> SQUARE_SANDBOX_CONNECT_SOURCES = List.of(
      "https://sandbox.web.squarecdn.com",
      "https://pci-connect.squareupsandbox.com",
      "https://o160250.ingest.sentry.io");

  private CspPolicyCommand() {
    // Static utility, not instantiated
  }

  /**
   * The report-only policy for this request, or null when none is configured.
   *
   * @param cspNonce the per-request script nonce
   * @return a complete policy including its reporting directives, or null to send no header
   */
  public static String reportOnlyPolicy(String cspNonce) {
    String configured = LoadSitePropertyCommand.loadByName(REPORT_ONLY_PROPERTY);
    if (StringUtils.isBlank(configured)) {
      return null;
    }
    String policy = configured.trim();
    // A candidate that carries script-src needs this request's nonce, which the administrator
    // cannot know when typing the policy
    if (cspNonce != null) {
      policy = policy.replace(NONCE_PLACEHOLDER, cspNonce);
    }
    // Strip trailing semicolons so appending below never produces an empty directive
    while (policy.endsWith(";")) {
      policy = policy.substring(0, policy.length() - 1).trim();
    }
    if (policy.isEmpty()) {
      return null;
    }
    // Add the reporting directives when they are absent. Without one of these the browser evaluates
    // the policy and reports to nobody, which looks exactly like a policy that passes -- the single
    // most misleading way this feature could fail. Both are added because browsers disagree about
    // which they honor: report-uri is deprecated and still the one several of them use.
    if (!policy.contains("report-uri")) {
      policy = policy + "; report-uri " + REPORT_PATH;
    }
    if (!policy.contains("report-to")) {
      policy = policy + "; report-to " + REPORT_ENDPOINT_NAME;
    }
    return policy;
  }

  /**
   * The image hosts Google Analytics needs, for the enforced img-src, or an empty string when the
   * site does not load Google Analytics.
   *
   * <p>
   * gtag.js requests images as well as making fetch calls: Google Signals' audience pixel on
   * www.google.com (and on Google's country domains) and a pixel on www.googletagmanager.com. GA's
   * tag diagnostics reported both blocked by img-src. CSP cannot wildcard a top-level domain, so
   * {@code *.google.com} covers US visitors and requests to other country domains stay blocked.
   * </p>
   *
   * <p>
   * Only when Google Analytics is configured, under the same condition main.jsp uses to load
   * gtag.js. img-src exists to close the CSS exfiltration channel (#1430), and each host it admits
   * is somewhere an injected background-image could send a request. A site that does not use Google
   * Analytics gains nothing from widening it.
   * </p>
   *
   * @return the sources with a leading space, ready to append, or ""
   */
  public static String googleAnalyticsImageSources() {
    if (!loadsGoogleAnalytics()) {
      return "";
    }
    return " https://*.google.com https://*.googletagmanager.com";
  }

  /**
   * The enforced connect-src source list: this site, plus what each feature the site has switched on
   * calls from the browser.
   *
   * <p>
   * connect-src governs where script can send things -- fetch, XMLHttpRequest, sendBeacon,
   * WebSocket, EventSource. With it absent and no default-src, script that got onto a page could post
   * whatever it read to any host. Every such call in the platform's own templates and scripts goes
   * to this site, except video.jsp's below; the rest of the list is what the vendors the platform
   * integrates document for their SDKs, added only when the site uses that vendor.
   * </p>
   *
   * <ul>
   * <li><b>blob:</b> -- TinyMCE reads an image back out of a blob: URL with fetch() when the image is
   * not in its own cache (uriToBlob). That reads memory the page already holds and sends nothing, so
   * admitting it gives up no protection, for the same reason img-src admits data:.</li>
   * <li><b>Vimeo's oEmbed endpoint</b> -- video.jsp fetches a Vimeo video's thumbnail from it. Always
   * present, like the video hosts in img-src: whether a page carries a Vimeo video is up to content,
   * not configuration, and a crawl of a site that has none would never show it. The exact path
   * rather than the host, because nothing else on vimeo.com is called.</li>
   * <li><b>Google Analytics</b> -- Google's documented connect-src list for the Google tag, plus
   * *.g.doubleclick.net, which Google Signals uses and which this site's reports showed
   * (stats.g.doubleclick.net). Google's country domains are left out, as they are from img-src: CSP
   * cannot wildcard a top-level domain, and those calls are Signals' advertising audience pings,
   * which analytics does not need. pagead2.googlesyndication.com, which Google lists for Ads
   * features, has never been reported here and is left out too.</li>
   * <li><b>reCAPTCHA</b> -- Google's reCAPTCHA FAQ lists https://www.google.com/recaptcha/. Turnstile
   * needs nothing here; Cloudflare's guide asks only for 'self', and only for pre-clearance.</li>
   * <li><b>Stripe or Square</b> -- the SDK behind the payment form's card field, chosen the way
   * PaymentFormWidget chooses it. Stripe's guide lists api.stripe.com for Stripe.js
   * (maps.googleapis.com is for the Address Element, which the form does not use). Square's lists
   * its CDN, its PCI endpoint and the error-reporting host its SDK sends to, with separate hosts for
   * the sandbox.</li>
   * </ul>
   *
   * <p>
   * Unlike frame-src there is no setting for more hosts. frame-src needs one because authored content
   * embeds iframes from vendors the platform has never heard of. Content cannot run script -- script-src
   * admits only this site and the request's nonce -- so the only code that can make a cross-origin
   * call is code the platform ships, and a feature that needs a new host adds it here. The marketing
   * tags under analytics settings are the exception that list does not cover: Google Tag Manager
   * cannot load under the enforced script-src at all (its loader inserts gtm.js without the nonce),
   * and Simpli.fi and BrandCDN publish no list of the hosts their scripts call.
   * </p>
   *
   * @return the sources, space separated, without the directive name
   */
  public static String connectSourceList() {
    Set<String> sources = new LinkedHashSet<>();
    sources.add("'self'");
    sources.add("blob:");
    sources.add(VIMEO_OEMBED);
    if (loadsGoogleAnalytics()) {
      sources.addAll(GOOGLE_ANALYTICS_CONNECT_SOURCES);
    }
    if (usesGoogleCaptcha()) {
      sources.add(RECAPTCHA_CONNECT_SOURCE);
    }
    String paymentProcessor = LoadSitePropertyCommand.loadByName("ecommerce.paymentProcessor");
    if ("Stripe".equalsIgnoreCase(paymentProcessor)) {
      sources.add(STRIPE_CONNECT_SOURCE);
    } else if ("Square".equalsIgnoreCase(paymentProcessor)) {
      sources.addAll(EcommerceCommand.isProductionEnabled()
          ? SQUARE_PRODUCTION_CONNECT_SOURCES
          : SQUARE_SANDBOX_CONNECT_SOURCES);
    }
    return String.join(" ", sources);
  }

  /** The condition main.jsp loads gtag.js under. */
  private static boolean loadsGoogleAnalytics() {
    return "google".equals(LoadSitePropertyCommand.loadByName("analytics.service"))
        && StringUtils.isNotBlank(LoadSitePropertyCommand.loadByName("analytics.google.key"));
  }

  /**
   * Whether a form can render reCAPTCHA: the rule AllowedIframeHostCommand uses for reCAPTCHA's
   * frame, which covers every case CaptchaCommand renders it in. Turnstile wins when it is selected.
   */
  private static boolean usesGoogleCaptcha() {
    String captchaService = LoadSitePropertyCommand.loadByName("captcha.service");
    return !"turnstile".equals(captchaService)
        && StringUtils.isNotBlank(captchaService)
        && StringUtils.isNotBlank(LoadSitePropertyCommand.loadByName("captcha.google.sitekey"));
  }

  /** The Reporting-Endpoints header value that gives report-to its destination. */
  public static String reportingEndpointsHeader() {
    return REPORT_ENDPOINT_NAME + "=\"" + REPORT_PATH + "\"";
  }

  /** True when violation reports should be accepted, i.e. a candidate policy is configured. */
  public static boolean isReportingEnabled() {
    return StringUtils.isNotBlank(LoadSitePropertyCommand.loadByName(REPORT_ONLY_PROPERTY));
  }
}
