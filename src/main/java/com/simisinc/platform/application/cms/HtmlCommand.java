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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;

import java.util.ArrayList;

/**
 * HTML encoding and decoding functions
 *
 * @author matt rajkowski
 * @created 4/26/18 10:16 AM
 */
public class HtmlCommand {

  private static Log LOG = LogFactory.getLog(HtmlCommand.class);

  /**
   * Turns HTML content into readable text
   *
   * @param html content
   * @return text
   */
  public static String text(String html) {
    if (StringUtils.isBlank(html)) {
      return "";
    }
    return Jsoup.parse(html).text();
  }

  /**
   * Escapes values to output to HTML
   *
   * @param text the text to escape for HTML
   * @return
   */
  public static String toHtml(String text) {
    if (StringUtils.isBlank(text)) {
      return "";
    }
    return StringEscapeUtils.escapeHtml4(text);
  }

  public static String textToHtml(String text) {
    if (StringUtils.isBlank(text)) {
      return null;
    }
    Safelist safelist = Safelist.basic();
    Document dirty = Jsoup.parseBodyFragment(StringUtils.replace(text, "\r", "<br>"), "http://localhost:8080");
    Cleaner cleaner = new Cleaner(safelist);
    Document clean = cleaner.clean(dirty);

    Document.OutputSettings settings = clean.outputSettings();
    settings.prettyPrint(false);
    settings.escapeMode(Entities.EscapeMode.extended);
    settings.charset("ASCII");

    return clean.html();
  }

  /**
   * Simplifies and cleans user-submitted content against a safe list, to prevent XSS attacks
   *
   * @param contentHtml
   * @return
   */
  public static String cleanContent(String contentHtml) {
    // Validate the input
    if (StringUtils.isBlank(contentHtml)) {
      return contentHtml;
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("CONTENT RECEIVED: " + contentHtml);
    }

    // Strip off blank content at the end
    if (contentHtml.endsWith("<p>&nbsp;</p>")) {
      contentHtml = contentHtml.substring(0, contentHtml.lastIndexOf("<p>&nbsp;</p>"));
    }
    if (contentHtml.endsWith("<br /><br /></p>")) {
      contentHtml = contentHtml.substring(0, contentHtml.lastIndexOf("<br /><br /></p>")) + "</p>";
    }

    // Handle conventions used in TinyMCE for editing
    contentHtml = TinyMceCommand.updateContentFromEditor(contentHtml);

    if (LOG.isTraceEnabled()) {
      LOG.trace("CONTENT UPDATED FROM EDITOR: " + contentHtml);
    }

    // Content received:
    // <iframe src="//www.youtube.com/embed/3Ka7B3hCg08?rel=0" width="560" height="315"
    //         frameborder="0" allowfullscreen="allowfullscreen"></iframe>
    // <a class="button primary"...

    // <p><iframe src="//www.youtube.com/embed/GBcWPk4ohwM#action=share" width="560" height="314"
    // allowfullscreen="allowfullscreen"></iframe></p>

    Safelist safelist = Safelist.relaxed().preserveRelativeLinks(true);
    safelist.addAttributes("div", "class", "role", "aria-label", "data-orbit");
    safelist.addAttributes("span", "class", "style");
    safelist.addAttributes("img", "class");
    safelist.addAttributes("h1", "class", "style");
    safelist.addAttributes("h2", "class", "style");
    safelist.addAttributes("h3", "class", "style");
    safelist.addAttributes("h4", "class", "style");
    safelist.addAttributes("h5", "class", "style");
    safelist.addAttributes("h6", "class", "style");
    safelist.addAttributes("p", "class", "style");
    safelist.addAttributes("a", "id", "name", "class", "target");
    safelist.addAttributes("i", "class");
    safelist.addAttributes("ul", "class");
    safelist.addAttributes("li", "class");

    safelist.addTags("hr");
    safelist.addTags("iframe");
    safelist.addAttributes("iframe", "src", "width", "height", "allowfullscreen", "frameborder");
    // <video width="640" height="360" poster="/assets/img/1545053117079-105/AIMS-Video-Poster.jpg" controls autoplay="autoplay">
    //   <source src="http://simis.simisappstore.com/assets/view/20181214165905-1/AIMS%20Intubation.webm" type="video/webm; codecs=vp9,vorbis">
    //   <source src="http://simis.simisappstore.com/assets/view/20181214165905-1/AIMS%20Intubation.mp4" type="video/mp4">
    // </video>
    safelist.addTags("video");
    safelist.addAttributes("video", "src", "controls", "poster", "type", "width", "height", "autoplay");
    safelist.addTags("source");
    safelist.addAttributes("source", "src", "type");
    // style="border-collapse: collapse; width: 100%;"
    safelist.addTags("table");
    safelist.addAttributes("table", "style");
    safelist.addTags("th");
    safelist.addAttributes("th", "style");
    safelist.addTags("td");
    safelist.addAttributes("td", "style");

    Document dirty = Jsoup.parseBodyFragment(contentHtml, "http://localhost:8080");
    Cleaner cleaner = new Cleaner(safelist);
    Document clean = cleaner.clean(dirty);

    Document.OutputSettings settings = clean.outputSettings();
    settings.prettyPrint(false);
    settings.escapeMode(Entities.EscapeMode.extended);
    settings.charset("ASCII");

    try {
      // Use the above settings to manipulate the clean document
      removeUnallowedStyles(clean, "span");
      removeUnallowedStyles(clean, "p");
      removeUnallowedStyles(clean, "h1");
      removeUnallowedStyles(clean, "h2");
      removeUnallowedStyles(clean, "h3");
      removeUnallowedStyles(clean, "h4");
      removeUnallowedStyles(clean, "h5");
      removeUnallowedStyles(clean, "h6");
      removeEmptyEnclosingElements(clean, "span");
      removeEmptyEnclosingElements(clean, "div");
      handleVideoTags(clean);
      handleVideoEmbeds(clean);
    } catch (Exception e) {
      LOG.error("manipulate the clean document exception should not be here", e);
    }

    String cleanedContent = clean.html();

    // Use the html body
    if (cleanedContent.contains("<body>") && cleanedContent.contains("</body>")) {
      cleanedContent = cleanedContent.substring(cleanedContent.indexOf("<body>") + 6, cleanedContent.indexOf("</body>"));
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("CONTENT CLEANED: " + cleanedContent);
    }

    return cleanedContent;
  }

  private static void removeUnallowedStyles(Document document, String tagName) {
    ArrayList<String> unAllowedItems = new ArrayList<>();
    unAllowedItems.add("margin");
    unAllowedItems.add("padding");
    unAllowedItems.add("line-height");
    unAllowedItems.add("color");
    unAllowedItems.add("font-family");
    unAllowedItems.add("font-size");

    Elements e = document.getElementsByTag(tagName);
    if (e == null) {
      return;
    }
    for (Element element : e) {
      if (!element.hasAttr("style")) {
        continue;
      }
      String[] styles = element.attr("style").split(";");
      ArrayList<String> filteredItems = new ArrayList<>();
      for (String item : styles) {
        String key = (item.split(":"))[0].trim().toLowerCase();
        if (!unAllowedItems.contains(key)) {
          filteredItems.add(item);
        }
      }
      if (filteredItems.size() == 0) {
        element.removeAttr("style");
      } else {
        element.attr("style", StringUtils.join(filteredItems, ";"));
      }
    }
  }

  private static void handleVideoTags(Document document) {
    Elements e = document.getElementsByTag("video");
    if (e == null) {
      return;
    }
    for (Element element : e) {
      Element parent = element.parent();
      if (parent.nodeName().equals("div")) {
        parent.attr("class", "responsive-embed widescreen");
        continue;
      }
      Element div = document.createElement("div");
      div.attr("class", "responsive-embed widescreen");
      element.replaceWith(div);
      div.appendChild(element);
    }
  }

  private static void handleVideoEmbeds(Document document) {
    Elements e = document.getElementsByTag("iframe");
    if (e == null) {
      return;
    }
    for (Element element : e) {
      if (!element.hasAttr("src")) {
        continue;
      }
      if (!element.hasAttr("frameborder")) {
        element.attr("frameborder", "0");
      }
      Element parent = element.parent();
      if (parent.nodeName().equals("div")) {
        parent.attr("class", "responsive-embed widescreen");
        continue;
      }
      Element div = document.createElement("div");
      div.attr("class", "responsive-embed widescreen");
      element.replaceWith(div);
      div.appendChild(element);
    }
  }

  /*
  private static void allowCertainAllSpanStyles(Document document) {
    // Need to determine what is allowed...
    ArrayList<String> allowedItems = new ArrayList<String>();
    allowedItems.add("color");
    allowedItems.add("font-size");

    Elements e = document.getElementsByTag("span");
    for (Element element : e) {
      String[] styles = element.attr("style").split(";");
      ArrayList<String> filteredItems = new ArrayList<>();
      for (String item : styles) {
        String key = (item.split(":"))[0].trim().toLowerCase();
        if (allowedItems.contains(key)) {
          filteredItems.add(item);
        }
      }
      if (filteredItems.size() == 0) {
        element.removeAttr("style");
      } else {
        element.attr("style", StringUtils.join(filteredItems, ";"));
      }
    }
  }
  */

  private static void removeEmptyEnclosingElements(Document document, String tagName) {
    Elements e = document.getElementsByTag(tagName);
    if (e.isEmpty()) {
      LOG.debug("No enclosing elements for tag: " + tagName);
      return;
    }
    for (Element element : e) {
      if (element.html().length() == 0) {
//        LOG.debug("Removing empty element for tag: " + tagName);
        element.remove();
        // when the document has changed, re-run this pass
        removeEmptyEnclosingElements(document, tagName);
        break;
      }
      if (element.attributes().size() == 0 || element.attributes().asList().size() == 0) {
//        LOG.debug("Adding: " + element.html());
        element.before(element.html());
//        LOG.debug("Removing: " + element.outerHtml());
        element.remove();
        // when the document has changed, re-run this pass
        removeEmptyEnclosingElements(document, tagName);
        break;
      }
    }
  }
}
