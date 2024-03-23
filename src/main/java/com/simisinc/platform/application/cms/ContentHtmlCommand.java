/*
 * Copyright 2023 SimIS Inc. (https://www.simiscms.com)
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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.cms.BlogPostWidget;

/**
 * Methods for finding the HTML to be used in a Content-based widget
 *
 * @author matt rajkowski
 * @created 7/13/22 4:32 PM
 */
public class ContentHtmlCommand {

  private static Log LOG = LogFactory.getLog(ContentHtmlCommand.class);

  static String HTML_JSP = "/cms/content-html.jsp";

  public static String getHtmlFromPreferences(WidgetContext context) {

    String html = null;
    String uniqueId = context.getPreferences().get("uniqueId");

    if (uniqueId != null) {
      // Populate from dynamic values
      uniqueId = checkForBlogPreferences(context, uniqueId);
      context.getRequest().setAttribute("uniqueId", uniqueId);
      // Check for the content
      LOG.debug("Looking up content for uniqueId: " + uniqueId);
      Content content = LoadContentCommand.loadContentByUniqueId(uniqueId);
      if (content == null) {
        LOG.debug("Content not found for uniqueId: " + uniqueId);
      }
      if (content != null) {
        html = content.getContent();
        // Look for draft content
        if (context.hasRole("admin") || context.hasRole("content-manager")) {
          if (content.getDraftContent() != null) {
            LOG.debug("Setting draft content...");
            html = content.getDraftContent();
            context.getRequest().setAttribute("isDraft", "true");
          }
        }
      }
    }

    // Use the widget preferences
    if (html == null) {
      html = context.getPreferences().get("html");
    }

    // It's possible to have different content injected into this content
    html = embedInlineContent(context, html);

    // Display a button for admins to add content
    boolean hasEditorPermission = (context.hasRole("admin") || context.hasRole("content-manager"));
    if (uniqueId != null && html == null) {
      if (hasEditorPermission) {
        html = "<a class=\"button tiny radius primary\" href=\"" + context.getContextPath()
            + "/content-editor?uniqueId=" + uniqueId + "&returnPage=" + context.getUri() + "\"><i class=\""
            + FontCommand.fas() + " fa-edit\"></i> Add Content Here</a>";
        context.getRequest().setAttribute("contentHtml", html);
        context.setJsp(HTML_JSP);
        return null;
      }
    }

    return html;
  }

  public static String checkForBlogPreferences(WidgetContext context, String uniqueId) {
    if (uniqueId.contains("${blog.") || uniqueId.contains("${blogPost.")) {
      // Check blog
      Blog blog = BlogPostWidget.retrieveValidatedBlogFromPreferences(context);
      if (blog == null) {
        return null;
      }
      uniqueId = ReplaceBlogDynamicValuesCommand.replaceValues(blog, uniqueId);
      // Check blog post
      if (uniqueId != null && uniqueId.contains("${blogPost.")) {
        BlogPost blogPost = BlogPostWidget.retrieveValidatedBlogPostFromUrl(context, blog);
        if (blogPost == null) {
          return null;
        }
        uniqueId = ReplaceBlogPostDynamicValuesCommand.replaceValues(blogPost, uniqueId);
      }
    }
    return uniqueId;
  }

  /**
  * Check for inline uniqueIds for complex html
  *
  * @param context
  * @param html
  * @return
  */
  private static String embedInlineContent(WidgetContext context, String html) {
    if (html == null) {
      return null;
    }
    int startUniqueIdx = html.indexOf("${uniqueId:");
    if (startUniqueIdx == -1) {
      return html;
    }

    boolean hasEditorPermission = (context.hasRole("admin") || context.hasRole("content-manager"));
    boolean hasDraftContent = false;
    int endUniqueIdx;

    StringBuilder sb = new StringBuilder(html.substring(0, startUniqueIdx));
    while ((endUniqueIdx = html.indexOf("}", startUniqueIdx)) > -1) {
      String embeddedUniqueId = html.substring(startUniqueIdx + 11, endUniqueIdx).trim();
      String embeddedHtml = "";
      Content content = LoadContentCommand.loadContentByUniqueId(embeddedUniqueId);
      if (content != null) {
        embeddedHtml = content.getContent();
        // Look for draft content
        if (hasEditorPermission) {
          if (content.getDraftContent() != null) {
            embeddedHtml = content.getDraftContent();
            hasDraftContent = true;
          }
        }
      }
      // Embed an editor at the content point
      if (hasEditorPermission) {
        if (StringUtils.isBlank(embeddedHtml)) {
          embeddedHtml = "<a class=\"button tiny radius primary\" href=\"" + context.getContextPath()
              + "/content-editor?uniqueId=" + embeddedUniqueId + "&returnPage=" + context.getUri() + "\"><i class=\""
              + FontCommand.fas() + " fa-edit\"></i> Add Content Here</a>";
        } else {
          embeddedHtml = "<div class=\"platform-content-inline-editor\">" +
              (hasDraftContent ? "<span class=\"label warning\">DRAFT</span>" : "") +
              "<a class=\"hollow button small secondary\" href=\"" + context.getContextPath()
              + "/content-editor?uniqueId=" + embeddedUniqueId + "&returnPage=" + context.getUri() + "\"><i class=\""
              + FontCommand.fas() + " fa-edit\"></i></a>" +
              "</div>" +
              embeddedHtml;
        }
        // Turn off the general editor because the embedded ones will be used
        context.getRequest().setAttribute("showEditor", "false");
      }

      sb.append(embeddedHtml);
      startUniqueIdx = html.indexOf("${uniqueId:", startUniqueIdx + 1);
      if (startUniqueIdx > -1) {
        sb.append(html, endUniqueIdx + 1, startUniqueIdx);
      } else {
        sb.append(html.substring(endUniqueIdx + 1));
        break;
      }
    }
    //    if (hasDraftContent) {
    // @todo add the global publish button
    // <a class="hollow button small warning" href="${widgetContext.uri}?action=publish&widget=${widgetContext.uniqueId}&token=${userSession.formToken}" onclick="return confirm('Publish this content?');">DRAFT</a>
    // @todo update the publish routine to publish all embedded unique id's
    //    }
    return sb.toString();
  }

  public static List<String> extractCardsFromHtml(WidgetContext context, String html, StringBuilder extraHTMLContent) {
    // Determine if cards are set by number across, or stacked across by size
    String smallCardCount = context.getPreferences().get("smallCardCount");
    String mediumCardCount = context.getPreferences().get("mediumCardCount");
    String largeCardCount = context.getPreferences().get("largeCardCount");
    if (StringUtils.isNotBlank(smallCardCount)) {
      // Fit by number of items
      if (StringUtils.isBlank(mediumCardCount)) {
        mediumCardCount = smallCardCount;
      }
      if (StringUtils.isBlank(largeCardCount)) {
        largeCardCount = mediumCardCount;
      }
      context.getRequest().setAttribute("smallCardCount", smallCardCount);
      context.getRequest().setAttribute("mediumCardCount", mediumCardCount);
      context.getRequest().setAttribute("largeCardCount", largeCardCount);
    } else {
      // Stacked across by size
      context.getRequest().setAttribute("cardSize", context.getPreferences().getOrDefault("cardSize", "200px"));
    }

    // Standardize the content
    html = StringUtils.replaceIgnoreCase(html, "<hr />", "<hr>");
    html = StringUtils.replaceIgnoreCase(html, "<hr/>", "<hr>");

    // Remove starting <hr>
    if (html.startsWith("<hr>")) {
      html = html.substring(4);
    }

    // Remove ending <hr>
    if (html.endsWith("<hr>")) {
      html = html.substring(0, html.length() - 4);
    }

    // Find one or more cards
    List<String> cardList = new ArrayList<>();
    int currentIdx = 0;
    while (html.indexOf("<hr>", currentIdx) > -1) {
      int endIdx = html.indexOf("<hr>", currentIdx);
      addCard(context, cardList, html.substring(currentIdx, endIdx), extraHTMLContent);
      currentIdx = endIdx + 4;
    }
    // Make sure to get the last one (or the only one)
    addCard(context, cardList, html.substring(currentIdx), extraHTMLContent);
    context.getRequest().setAttribute("cardList", cardList);
    return cardList;
  }

  public static void addCard(WidgetContext context, List<String> cardList, String html,
      StringBuilder extraHTMLContent) {

    // <a href="#reveal-some-unique-id">The Button Name</a>
    boolean addReveal = Boolean.parseBoolean(context.getPreferences().getOrDefault("addReveal", "false"));
    boolean setBackgroundImage = Boolean
        .parseBoolean(context.getPreferences().getOrDefault("setBackgroundImage", "false"));

    if (setBackgroundImage) {
      // Strip out the image, create the updated HTML, then continue
      // <div class="image-card">
      //   <p><img src="/assets/img/20200519200325-142/Something.jpg" alt="Something"></p>
      //   <div>
      //     <h3>The title</h3>
      //     <p><button class="reveal-button-text" data-toggle="modalreveal-something" aria-controls="modalreveal-something" aria-haspopup="true" tabindex="0"><img src="/assets/img/20191001153112-134/Something-Else.png" alt="" width="200" height="34"></button></p>
      //     <div class="platform-content-inline-editor"><a class="hollow button tiny secondary" href="/content-editor?uniqueId=something&amp;returnPage=/somewhere"><i class="fas fa-edit"></i></a></div><p></p>
      //   </div>
      // </div>
      int imgStartIdx = html.indexOf("<p><img");
      if (imgStartIdx > -1) {
        int imgEndIdx = html.indexOf("></p>", imgStartIdx);
        if (imgEndIdx > -1) {
          html = "<div class=\"image-card\">" +
              html.substring(0, imgEndIdx + "></p>".length()) +
              "<div>" +
              html.substring(imgEndIdx + "></p>".length()) +
              "</div>" +
              "</div>";
        }
      }
    }

    // Add as-is since a reveal is not expected
    if (!addReveal) {
      cardList.add(html);
      return;
    }

    // Preference is set, but this block does not have the required revel href
    int startIdx = html.indexOf("href=\"#reveal-");
    if (startIdx == -1) {
      cardList.add(html);
      return;
    }

    // Determine the reveal values
    int tagStartIdx = html.substring(0, startIdx).lastIndexOf("<a");
    int textEndIdx = html.indexOf("</a>", startIdx);
    int tagEndIdx = textEndIdx + 4;
    int linkStartIdx = html.indexOf("#reveal-", startIdx) + 1;
    int uniqueIdStartIdx = linkStartIdx + 7;
    int uniqueIdEndIdx = html.indexOf("\"", uniqueIdStartIdx);
    int textStartIdx = html.indexOf(">", uniqueIdEndIdx) + 1;

    if (LOG.isDebugEnabled()) {
      LOG.debug("addCard tagStartIdx: " + tagStartIdx);
      LOG.debug("addCard textEndIdx: " + textEndIdx);
      LOG.debug("addCard tagEndIdx: " + tagEndIdx);
      LOG.debug("addCard linkStartIdx: " + linkStartIdx);
      LOG.debug("addCard uniqueIdStartIdx: " + uniqueIdStartIdx);
      LOG.debug("addCard uniqueIdEndIdx: " + uniqueIdEndIdx);
      LOG.debug("addCard textStartIdx: " + textStartIdx);
    }

    String text = html.substring(textStartIdx, textEndIdx);
    String data = html.substring(linkStartIdx, uniqueIdEndIdx);
    String uniqueId = html.substring(uniqueIdStartIdx, uniqueIdEndIdx);

    if (LOG.isDebugEnabled()) {
      LOG.debug("addCard text: " + text);
      LOG.debug("addCard data: " + data);
      LOG.debug("addCard uniqueId: " + uniqueId);
    }

    // Determine editing, links, settings
    boolean hasEditorPermission = (context.hasRole("admin") || context.hasRole("content-manager"));
    String returnPage = context.getUri();
    String contentEditorLink = "";
    if (hasEditorPermission) {
      contentEditorLink = "<div class=\"platform-content-inline-editor\"><a class=\"hollow button secondary\" href=\"/content-editor?uniqueId="
          + uniqueId + "&returnPage=" + returnPage + "\"><i class=\"" + FontCommand.far()
          + " fa-window-restore\"></i></a></div>";
    }

    // Rewrite the content
    Content content = LoadContentCommand.loadContentByUniqueId(uniqueId);
    if (content == null) {
      // Rewrite the HTML to edit the revealed content
      html = html.substring(0, tagStartIdx) +
          text +
          contentEditorLink +
          html.substring(tagEndIdx);
    } else {
      // Rewrite the HTML to reveal the new content
      // https://get.foundation/sites/docs/motion-ui.html
      // motion-ui values: slide-in-right, slide-in-left, slide-in-down, slide-in-up, fade-in, scale-in-down
      String revealIn = context.getPreferences().getOrDefault("revealIn", "slide-in-left fast");
      String revealOut = context.getPreferences().getOrDefault("revealOut", "slide-out-left fast");
      String revealClass = context.getPreferences().get("revealClass");

      String textTag = "";
      if (!text.contains("<img")) {
        textTag = " reveal-button-content";
      }

      html = html.substring(0, tagStartIdx) +
          "<button class=\"reveal-button-text" + textTag + "\" data-toggle=\"modal" + data + "\">" + text + "</button>"
          +
          contentEditorLink +
          html.substring(tagEndIdx);
      // Append the revealed content
      String reveal = "<div class=\"reveal\" id=\"modal" + data + "\"\n" +
          "data-reveal\n" +
          "data-animation-in=\"" + revealIn + "\"\n" +
          "data-animation-out=\"" + revealOut + "\"\n" +
          //"data-h-offset=\"0\"\n" +
          //          "data-multiple-opened=\"true\"\n" +
          (StringUtils.isNotBlank(revealClass) ? "data-additional-overlay-classes=\"" + revealClass + "\"\n" : "") +
          "data-close-on-click=\"true\">\n" +
          content.getContent() + "\n" +
          "<button class=\"close-button\" data-close aria-label=\"Close reveal\" type=\"button\">\n" +
          "<span aria-hidden=\"true\"><i class=\"" + FontCommand.fal() + " fa-circle-xmark\"></i></span>\n" +
          "</button>\n" +
          "</div>";
      extraHTMLContent.append(reveal);
    }
    cardList.add(html);
  }

  public static WidgetContext performWebAction(WidgetContext context) {
    // Permission is required
    if (!(context.hasRole("admin") || context.hasRole("content-manager"))) {
      LOG.warn("No permission found");
      return context;
    }

    // Find the content record
    String uniqueId = context.getPreferences().get("uniqueId");
    if (uniqueId == null) {
      LOG.warn("No uniqueId found");
      return context;
    }

    // Check for dynamic values
    uniqueId = ContentHtmlCommand.checkForBlogPreferences(context, uniqueId);

    // Check the content
    Content content = LoadContentCommand.loadContentByUniqueId(uniqueId);
    if (content == null) {
      LOG.warn("No content found");
      return context;
    }

    // Determine the action
    String action = context.getParameter("action");
    if ("publish".equals(action)) {
      return publishContent(context, content);
    } else if ("deleteContent".equals(action)) {
      return deleteContent(context, content);
    }

    return context;
  }

  private static WidgetContext publishContent(WidgetContext context, Content content) {
    if (StringUtils.isNotBlank(content.getDraftContent())) {
      ContentRepository.publish(content);
    }
    return context;
  }

  private static WidgetContext deleteContent(WidgetContext context, Content content) {
    // Attempt to delete the content
    try {
      LOG.warn("Content delete is not implemented");
      // @todo
      //      ContentRepository.remove(content);
    } catch (Exception e) {
      context.setErrorMessage("The content could not be deleted: " + e.getMessage());
    }
    return context;
  }
}
