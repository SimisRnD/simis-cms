/*
 * Copyright 2024 Matt Rajkowski
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

import java.math.BigDecimal;
import java.text.SimpleDateFormat;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.WebPage;

/**
 * Sitemap XML functions
 *
 * @author matt rajkowski
 * @created 2/13/2024 9:26 PM
 */
public class SitemapBuilderCommand {

  private static Log LOG = LogFactory.getLog(SitemapBuilderCommand.class);

  public static StringBuilder startSitemapXML() {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
    return xml;
  }

  public static void endSitemapXML(StringBuilder xml) {
    xml.append("</urlset>");
  }

  /**
   * Generate the xml for the given web page
   * @param xml
   * @param siteUrl
   * @param link
   * @param lastModified
   */
  public static void appendUrlXml(StringBuilder xml, String siteUrl, WebPage webPage, SimpleDateFormat sdf) {
    String link = webPage.getLink();
    String lastModified = sdf.format(webPage.getModified());
    String frequency = webPage.getSitemapChangeFrequency();
    BigDecimal priority = webPage.getSitemapPriority();

    // loc: This URL must begin with the protocol (such as http/s)
    // lastmod: (ISO 8601) 2022-07-24T14:53:29-0400
    // changefreq: (a hint) always, hourly, daily, weekly, monthly, yearly, never
    // priority: (0.0-1.0) The default priority of a page is 0.5
    xml.append("  <url>\n");
    xml.append("    <loc>").append(siteUrl + link).append("</loc>\n");
    if (StringUtils.isNotBlank(frequency)) {
      xml.append("    <changefreq>").append(frequency).append("</changefreq>\n");
    }
    if (StringUtils.isNotBlank(lastModified)) {
      if (StringUtils.isBlank(frequency) || "never".equals(frequency) || "yearly".equals(frequency)
          || "monthly".equals(frequency)) {
        xml.append("    <lastmod>").append(lastModified).append("</lastmod>\n");
      }
    }
    if (priority != null && priority.doubleValue() != 0.5) {
      xml.append("    <priority>").append(priority.doubleValue()).append("</priority>");
    }
    xml.append("  </url>\n");
  }
}
