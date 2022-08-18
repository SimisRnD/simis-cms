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

package com.simisinc.platform.presentation.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.granule.utils.HttpHeaders;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageSpecification;

/**
 * Serves sitemap.xml
 *
 * @author matt rajkowski
 * @created 7/24/2022 1:58 PM
 */

public class SitemapXmlServlet extends HttpServlet {

  private static final long serialVersionUID = -371092409070142705L;
  private static Log LOG = LogFactory.getLog(SitemapXmlServlet.class);

  public void init(ServletConfig config) throws ServletException {
    LOG.info("SitemapXmlServlet starting up...");
  }

  public void destroy() {

  }

  public void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

    // Determine the URL to use
    String siteUrl = LoadSitePropertyCommand.loadByName("site.url");
    if (StringUtils.isBlank(siteUrl) ||
        (!siteUrl.startsWith("http://") && !siteUrl.startsWith("https://"))) {
      LOG.warn("site.url must be configured");
      response.setStatus(404);
      return;
    }

    // Determine if the site is public
    boolean siteIsOnline = LoadSitePropertyCommand.loadByNameAsBoolean("site.online");
    if (!siteIsOnline) {
      LOG.warn("Denying request for sitemap.xml, site.online is false");
      response.setStatus(404);
      return;
    }

    // Determine if the sitemap.xml is enabled, but allow an alternate 'review' path
    String requestURI = request.getRequestURI();
    if (!requestURI.endsWith("/sitemap-review.xml")) {
      boolean sitemapXmlEnabled = LoadSitePropertyCommand.loadByNameAsBoolean("site.sitemap.xml");
      if (!sitemapXmlEnabled) {
        LOG.warn("Denying request for sitemap.xml, site.sitemap.xml is false, but /sitemap-review.xml is enabled");
        response.setStatus(404);
        return;
      }
    }

    // Use ISO 6801 date formatter
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZ");

    // Open the XML
    long mostRecentTimestamp = 0L;
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

    // @todo Use the main menu first

    // Find the web pages
    WebPageSpecification specification = new WebPageSpecification();
    specification.setDraft(false);
    specification.setInSitemap(true);
    specification.setHasRedirect(false);

    List<WebPage> webPageList = WebPageRepository.findAll(specification, null);
    if (webPageList == null || webPageList.isEmpty()) {
      LOG.debug("No web pages found for the sitemap");
      response.setStatus(404);
      return;
    }

    // Add each web page
    for (WebPage webPage : webPageList) {
      LOG.debug("Found: " + webPage.getLink());
      if (webPage.getModified().getTime() > mostRecentTimestamp) {
        mostRecentTimestamp = webPage.getModified().getTime();
      }
      // @todo if web page contains something dynamic... can use frequency instead of date
      // Ex. blog, calendar, collection
      appendUrlXml(xml, siteUrl, webPage.getLink(), sdf.format(webPage.getModified()),
          webPage.getSitemapChangeFrequency(), webPage.getSitemapPriority());
    }

    // @todo Public Blog posts

    // @todo Public Collections

    // Public Items

    // Close the XML
    xml.append("</urlset>");

    // Check for a last-modified header and return 304 if possible
    if (mostRecentTimestamp > 0) {
      long headerValue = request.getDateHeader("If-Modified-Since");
      if (mostRecentTimestamp <= headerValue + 1000) {
        LOG.debug("Sitemap not modified, use cache");
        response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        return;
      }
    }

    // Set headers and send the content
    LOG.debug("Sending sitemap.xml");
    response.setHeader("Content-Type", "text/xml; charset=utf-8");
    if (mostRecentTimestamp > 0) {
      response.setDateHeader("Last-Modified", mostRecentTimestamp);
    } else {
      HttpHeaders.setCacheExpireDate(response, 6048000);
    }
    LOG.debug("XML original length: " + xml.length());
    if (gzipSupported(request)) {
      try (OutputStream os = response.getOutputStream()) {
        byte[] gzipCss = gzip(xml.toString());
        response.setHeader("ETag", DigestUtils.md5Hex(gzipCss));
        response.setHeader("Content-Encoding", "gzip");
        os.write(gzipCss);
        os.flush();
        LOG.debug("Sent as gzip... gzip length: " + gzipCss.length);
      }
    } else {
      response.setHeader("ETag", DigestUtils.md5Hex(xml.toString()));
      response.getWriter().print(xml.toString());
    }
  }

  /**
   * Generate the xml for the given URL
   * @param xml
   * @param siteUrl
   * @param link
   * @param lastModified
   */
  private static void appendUrlXml(StringBuilder xml, String siteUrl, String link, String lastModified,
      String frequency,
      BigDecimal priority) {
    // loc: This URL must begin with the protocol (such as http/s)
    // lastmod: (ISO 8601) 2022-07-24T14:53:29-0400
    // changefreq: (a hint) always, hourly, daily, weekly, monthly, yearly, never
    // priority: (0.0-1.0) The default priority of a page is 0.5
    xml.append("<url>\n");
    xml.append("<loc>").append(siteUrl + link).append("</loc>\n");
    if (StringUtils.isNotBlank(frequency)) {
      xml.append("<changefreq>").append(frequency).append("</changefreq>\n");
    }
    if (StringUtils.isNotBlank(lastModified)) {
      if (StringUtils.isBlank(frequency) || "never".equals(frequency) || "yearly".equals(frequency)
          || "monthly".equals(frequency)) {
        xml.append("<lastmod>").append(lastModified).append("</lastmod>\n");
      }
    }
    if (priority != null && priority.doubleValue() != 0.5) {
      xml.append("<priority>").append(priority.doubleValue()).append("</priority>");
    }
    xml.append("</url>\n");
  }

  private static boolean gzipSupported(HttpServletRequest request) {
    String acceptEncoding = request.getHeader("Accept-Encoding");
    if (acceptEncoding == null || !acceptEncoding.contains("gzip")) {
      return false;
    }
    String userAgent = request.getHeader("user-agent");
    if (userAgent == null) {
      return false;
    }
    if (userAgent.contains("MSIE 6") && !userAgent.contains("SV1")) {
      return false;
    }
    return true;
  }

  private static byte[] gzip(String text) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (
        GZIPOutputStream gzipStream = new GZIPOutputStream(bos)) {
      gzipStream.write(text.getBytes("UTF-8"));
      gzipStream.flush();
    }
    return bos.toByteArray();
  }
}
