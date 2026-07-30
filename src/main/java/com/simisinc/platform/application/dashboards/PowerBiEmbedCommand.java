/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.application.dashboards;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Validates a Power BI "Publish to web" embed URL before it's dropped into an iframe src.
 * <p>
 * Unlike Superset's guest-token flow or Metabase's static (signed) embedding, Power BI's publish-
 * to-web URLs are already complete, self-contained, and public by design -- Power BI itself signs
 * and hosts them, so there is no server-side secret to configure or token to generate here. This
 * command's only job is confirming the URL a page author configured is actually a Power BI embed
 * URL, not an arbitrary attacker-controlled address, before it's rendered.
 *
 * @author elizabeth houser
 */
public class PowerBiEmbedCommand {

  private static Log LOG = LogFactory.getLog(PowerBiEmbedCommand.class);

  private static final String REQUIRED_HOST = "app.powerbi.com";

  public static String validateEmbedUrl(String embedUrl) {
    if (StringUtils.isBlank(embedUrl)) {
      LOG.debug("Missing embedUrl");
      return null;
    }
    URI uri;
    try {
      uri = new URI(embedUrl.trim());
    } catch (URISyntaxException e) {
      LOG.warn("Power BI embedUrl is not a valid URL: " + embedUrl);
      return null;
    }
    if (!"https".equalsIgnoreCase(uri.getScheme()) || !REQUIRED_HOST.equalsIgnoreCase(uri.getHost())) {
      LOG.warn("Power BI embedUrl must be a https://" + REQUIRED_HOST + " address: " + embedUrl);
      return null;
    }
    return uri.toString();
  }
}
