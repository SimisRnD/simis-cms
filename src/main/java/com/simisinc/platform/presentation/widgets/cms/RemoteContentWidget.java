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

package com.simisinc.platform.presentation.widgets.cms;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;

import com.github.benmanes.caffeine.cache.Cache;
import com.simisinc.platform.application.http.HttpGetCommand;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/4/18 9:45 AM
 */
public class RemoteContentWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  protected static Log LOG = LogFactory.getLog(RemoteContentWidget.class);

  static String JSP = "/cms/remote_content_wrapper.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Use the widget preferences
    String url = context.getPreferences().get("url");
    if (url == null) {
      return null;
    }
    boolean includeTags = Boolean.parseBoolean(context.getPreferences().getOrDefault("includeTags", "true"));

    // Check the cache
    Cache cache = CacheManager.getCache(CacheManager.CONTENT_REMOTE_URL_CACHE);
    String content = (String) cache.getIfPresent(url);
    if (content != null) {
      return useReturnType(context, content);
    }

    // Create a wrapper for images
    if (url.endsWith(".gif") || url.endsWith(".png") || url.endsWith(".jpg")) {
      content = "<img src=\"" + url + "\" />";
      cache.put(url, content);
      return useReturnType(context, content);
    }

    // Get the remote data, and cache it
    // @todo if this fails we don't want many more requests...
    try {
      long startRequestTime = System.currentTimeMillis();
      String remoteContent = HttpGetCommand.execute(url);
      if (StringUtils.isBlank(remoteContent)) {
        return null;
      }
      long endRequestTime = System.currentTimeMillis();
      long totalTime = endRequestTime - startRequestTime;
      LOG.info("Remote request: " + url + " " + totalTime + "ms");

      // Determine if the content can be returned as-is
      boolean doClean = Boolean.parseBoolean(context.getPreferences().getOrDefault("clean", "true"));
      if (!doClean) {
        // Trusted content, like a micro-service
        cache.put(url, remoteContent);
        return useReturnType(context, remoteContent);
      }

      // Get a portion of the content
      String start = context.getPreferences().get("startTag");
      String end = context.getPreferences().get("endTag");
      if (start != null && end != null) {
        boolean trimSuccess = false;
        int startIdx = remoteContent.indexOf(start);
        if (startIdx > -1) {
          int endIdx = remoteContent.indexOf(end, startIdx);
          if (endIdx > -1) {
            if (!includeTags) {
              startIdx = startIdx + start.length();
              endIdx = endIdx - end.length();
            }
            remoteContent = remoteContent.substring(startIdx, endIdx + end.length());
            trimSuccess = true;
          }
        }
        if (!trimSuccess) {
          LOG.warn("The content could not be trimmed for url: " + url);
          return null;
        }
      }

      // Clean the content
      Safelist safelist = Safelist.relaxed();
      safelist.addAttributes("span", "style");
      Document dirty = Jsoup.parseBodyFragment(remoteContent, "");
      Cleaner cleaner = new Cleaner(safelist);
      Document clean = cleaner.clean(dirty);

      // Make sure all elements target blank, add no follow
      Elements select = clean.select("a");
      for (Element e : select) {
        // baseUri will be used by absUrl
        //              String absUrl = e.absUrl("href");
        //              e.attr("href", absUrl);
        e.attr("target", "_blank");
        e.attr("rel", "nofollow");
      }

      // Allow HTML entities
      Document.OutputSettings settings = clean.outputSettings();
      settings.prettyPrint(false);
      settings.escapeMode(Entities.EscapeMode.extended);
      settings.charset("ASCII");
      content = clean.body().html();

      // Check for additional processing
      if ("true".equals(context.getPreferences().get("adjustTable"))) {
        content = StringUtils.replace(content, "<table>", "<table class=\"scroll\">");
      }

      cache.put(url, content);
      return useReturnType(context, content);
    } catch (Exception e) {
      LOG.warn("Could not get content from: " + url, e);
    }
    return null;
  }

  private WidgetContext useReturnType(WidgetContext context, String content) {
    // Determine if the content will be wrapped in a JSP
    String title = context.getPreferences().get("title");
    if (title != null) {
      // Wrap in a JSP
      context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
      context.getRequest().setAttribute("title", context.getPreferences().get("title"));
      context.getRequest().setAttribute("content", content);
      context.setJsp(JSP);
      return context;
    }

    // Output directly
    context.setHtml(content);
    return context;
  }
}
