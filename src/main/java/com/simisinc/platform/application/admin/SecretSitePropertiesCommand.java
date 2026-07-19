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

package com.simisinc.platform.application.admin;

import java.util.Set;

/**
 * The site properties whose values are secrets (passwords, API keys, tokens).
 * These are never rendered back to the browser and an empty form submission keeps the stored value.
 *
 * Only exact property names belong here; do not add prefix rules -- '.key' is a secret for
 * some services and a public publishable key for others (for example the Stripe and Square
 * browser keys, the captcha site key, and the Mapbox access token are sent to the browser
 * by design and must not be masked).
 *
 * @author elizabeth houser
 */
public class SecretSitePropertiesCommand {

  private static final Set<String> SECRET_PROPERTY_NAMES = Set.of(
      // Payment and fulfillment credentials
      "ecommerce.stripe.test.secret",
      "ecommerce.stripe.production.secret",
      "ecommerce.square.test.secret",
      "ecommerce.square.production.secret",
      "ecommerce.boxzooka.production.secret",
      "ecommerce.taxjar.apiKey",
      // Authentication and identity secrets
      "oauth.clientSecret",
      "mail.password",
      "captcha.google.secretkey",
      // Server-side integration keys and tokens
      "mailing-list.mailchimp.apiKey",
      "bi.superset.secret",
      "social.instagram.accessToken",
      "elearning.lrs.key",
      "elearning.lrs.secret",
      "elearning.lrs.authHeader",
      "elearning.moodle.token",
      "elearning.perls.clientId",
      "elearning.perls.secret",
      "conferencing.bbb.secret");

  private SecretSitePropertiesCommand() {
  }

  public static boolean isSecret(String propertyName) {
    return propertyName != null && SECRET_PROPERTY_NAMES.contains(propertyName);
  }

  public static Set<String> getSecretPropertyNames() {
    return SECRET_PROPERTY_NAMES;
  }
}
