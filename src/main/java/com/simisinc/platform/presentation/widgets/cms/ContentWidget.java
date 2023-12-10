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

import com.simisinc.platform.application.cms.FontCommand;
import com.simisinc.platform.application.cms.LoadContentCommand;
import com.simisinc.platform.application.cms.ReplaceBlogDynamicValuesCommand;
import com.simisinc.platform.application.cms.ReplaceBlogPostDynamicValuesCommand;
import com.simisinc.platform.domain.model.cms.AccordionSection;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/6/18 9:26 PM
 */
public class ContentWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/content.jsp";
  static String HTML_JSP = "/cms/content-html.jsp";
  static String CARD_JSP = "/cms/content-card.jsp";
  static String GALLERY_JSP = "/cms/content-gallery.jsp";
  static String REVEAL_JSP = "/cms/content-reveal.jsp";
  static String ACCORDION_JSP = "/cms/content-accordion.jsp";
  static String CAROUSEL_JSP = "/cms/content-carousel.jsp";
  static String CARD_SLIDER_JSP = "/cms/content-card-slider.jsp";

  public WidgetContext execute(WidgetContext context) {

    String jsp = JSP;

    String view = context.getPreferences().get("view");

    if (context.hasRole("admin") || context.hasRole("content-manager")) {
      context.getRequest().setAttribute("showEditor", "true");
      context.getRequest().setAttribute("returnPage", context.getRequest().getRequestURI());
    }

    // Look for saved content
    String html = null;
    String uniqueId = context.getPreferences().get("uniqueId");
    if (uniqueId == null) {
      // @todo add note for this
      context.getRequest().removeAttribute("uniqueId");
    }
    if (uniqueId != null) {
      // Populate from dynamic values
      uniqueId = checkForBlogPreferences(context, uniqueId);
      context.getRequest().setAttribute("uniqueId", uniqueId);
      // Check for the content
      Content content = LoadContentCommand.loadContentByUniqueId(uniqueId);
      if (content != null) {
        html = content.getContent();
        // Look for draft content
        if (context.hasRole("admin") || context.hasRole("content-manager")) {
          if (content.getDraftContent() != null) {
            html = content.getDraftContent();
            context.getRequest().setAttribute("isDraft", "true");
          }
        }
      }
    }

    // Use the widget preferences
    if (html == null) {
      html = context.getPreferences().get("html");
      if (uniqueId == null) {
        // Directly output the html without an edit button
        jsp = HTML_JSP;
      }
    }

    // It's possible to have different content injected into this content
    html = embedInlineContent(context, html);

    // Display a button for admins to add content
    boolean hasEditorPermission = (context.hasRole("admin") || context.hasRole("content-manager"));
    boolean hasContent = true;
    if (uniqueId != null && html == null) {
      if (hasEditorPermission) {
        hasContent = false;
        html = "<a class=\"button tiny radius primary\" href=\"" + context.getContextPath() + "/content-editor?uniqueId=" + uniqueId + "&returnPage=" + context.getUri() + "\"><i class=\"" + FontCommand.fas() + " fa-edit\"></i> Add Content Here</a>";
      }
    }

    // Only show if there is content (or if an Admin wants to see it)
    if (html == null || html.length() == 0) {
      return null;
    }

    // Common attributes
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Preferences
    context.getRequest().setAttribute("videoBackgroundUrl", context.getPreferences().get("videoBackgroundUrl"));

    // Extra content can be appended to the final HTML
    StringBuilder extraHTMLContent = new StringBuilder();

    // Some views split the content into arrays
    if (hasContent) {
      if ("cards".equals(view) || "gallery".equals(view) || "reveal".equals(view) || "accordion".equals(view) || "carousel".equals(view) || "cardSlider".equals(view)) {

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

        // Determine the view
        if ("gallery".equals(view)) {
          context.getRequest().setAttribute("card1", cardList.get(0));
          context.setJsp(GALLERY_JSP);
        } else if ("reveal".equals(view)) {
          context.getRequest().setAttribute("revealClass", context.getPreferences().get("revealClass"));
          context.getRequest().setAttribute("size", context.getPreferences().get("size"));
          context.getRequest().setAttribute("attach", context.getPreferences().get("attach"));
          context.getRequest().setAttribute("animate", context.getPreferences().get("animate"));
          context.getRequest().setAttribute("useIcon", context.getPreferences().getOrDefault("useIcon", "false"));
          context.getRequest().setAttribute("card1", cardList.get(0));
          if (cardList.size() > 1) {
            context.getRequest().setAttribute("card2", cardList.get(1));
          }
          context.setJsp(REVEAL_JSP);
        } else if ("accordion".equals(view)) {
          // CSS class
          context.getRequest().setAttribute("accordionClass", context.getPreferences().get("class"));
          context.getRequest().setAttribute("innerAccordionClass", context.getPreferences().get("innerClass"));
          context.getRequest().setAttribute("expandTopLevel", context.getPreferences().getOrDefault("expandTopLevel", "false"));

          // Use the content itself to find the Accordion Label and Content; If the content begins with an <H1> then create a nested accordion
          List<AccordionSection> sectionList = new ArrayList<>();
          AccordionSection currentSection = null;

          for (String content : cardList) {
            // Check for a section
            if (LOG.isDebugEnabled()) {
              LOG.debug("Original Content: " + content);
            }
            if (content.trim().startsWith("<h1")) {
              int sectionStartIdx = content.indexOf(">", content.indexOf("<h1"));
              int sectionEndIdx = content.indexOf("</h1>", sectionStartIdx);
              if (sectionEndIdx > sectionStartIdx) {
                // Start a new section
                String sectionName = content.substring(sectionStartIdx + 1, sectionEndIdx);
                currentSection = new AccordionSection(sectionName);
                sectionList.add(currentSection);
              }
            }
            // Must have a section before adding data
            if (currentSection == null) {
              currentSection = new AccordionSection();
              sectionList.add(currentSection);
            }
            // Determine the label and content area
            // <p><span style="font-weight: 400;">&gt; The label</span></p>
            int labelStartIdx = content.indexOf(">&gt;") + 5;
            int labelEndIdx = content.indexOf("</", labelStartIdx);
            int tagEndIdx = content.indexOf("</p>", labelEndIdx) + 4;
            // Split the label and content into a new card
            String label = content.substring(labelStartIdx, labelEndIdx).trim();
            String card = content.substring(tagEndIdx);
            if (LOG.isDebugEnabled()) {
              LOG.debug("Found label: " + label);
              LOG.debug("Found card: " + card);
            }
            currentSection.getLabelsList().add(label);
            currentSection.getContentList().add(card);
          }
          context.getRequest().setAttribute("sectionList", sectionList);

          // Show the accordion
          context.setJsp(ACCORDION_JSP);

        } else if ("carousel".equals(view) || "cardSlider".equals(view)) {

          // Check the preferences
          context.getRequest().setAttribute("carouselSize", context.getPreferences().getOrDefault("carouselSize", "small"));
          context.getRequest().setAttribute("carouselClass", context.getPreferences().get("carouselClass"));
          context.getRequest().setAttribute("carouselTitle", context.getPreferences().get("carouselTitle"));
          context.getRequest().setAttribute("showControls", context.getPreferences().getOrDefault("showControls", "true"));
          context.getRequest().setAttribute("showLeftControl", context.getPreferences().getOrDefault("showLeftControl", "true"));
          context.getRequest().setAttribute("showRightControl", context.getPreferences().getOrDefault("showRightControl", "true"));
          context.getRequest().setAttribute("showBullets", context.getPreferences().getOrDefault("showBullets", "true"));

          // Determine any carousel data options
          StringBuilder dataOptions = new StringBuilder();
          String timerDelayValue = context.getPreferences().getOrDefault("timerDelay", "-1");
          int timerDelay = Integer.parseInt(timerDelayValue);
          if (timerDelay > 0) {
            dataOptions.append("data-timer-delay=\"").append(timerDelayValue).append("\"");
          }
          String pauseOnHoverValue = context.getPreferences().getOrDefault("pauseOnHover", "true");
          if ("false".equals(pauseOnHoverValue)) {
            if (dataOptions.length() > 0) {
              dataOptions.append(" ");
            }
            dataOptions.append("data-pause-on-hover=\"false\"");
          }
          if (dataOptions.length() > 0) {
            context.getRequest().setAttribute("dataOptions", dataOptions.toString());
          }

          // Determine how the content will be displayed (typically as a complete text block)
          String display = context.getPreferences().getOrDefault("display", "text");
          context.getRequest().setAttribute("display", display);
          if ("images".equals(display)) {
            // Use the content itself to extract image tag attributes
            List<String> updatedCardList = new ArrayList<>();
            for (String originalCard : cardList) {
              // Determine the image
              // <p><img src="/assets/img/20190826142844-128/Small%20Business.jpg" alt="" /></p>
              int imgAttributesStartIdx = originalCard.indexOf("<img ") + 5;
              int imgAttributesEndIdx = originalCard.indexOf(">", imgAttributesStartIdx);
              String attributes = originalCard.substring(imgAttributesStartIdx, imgAttributesEndIdx);
              if (attributes.endsWith("/")) {
                attributes = attributes.substring(0, attributes.length() - 1);
              }
              if (LOG.isDebugEnabled()) {
                LOG.debug("Found image attributes: " + attributes);
              }
              updatedCardList.add(attributes);
            }
            context.getRequest().setAttribute("cardList", updatedCardList);
          }

          if ("cardSlider".equals(view)) {
            context.setJsp(CARD_SLIDER_JSP);
          } else {
            context.setJsp(CAROUSEL_JSP);
          }

        } else {
          context.getRequest().setAttribute("gridMargin", context.getPreferences().getOrDefault("gridMargin", "false"));
          context.getRequest().setAttribute("extraHTMLContent", extraHTMLContent.toString());
          context.setJsp(CARD_JSP);
        }
        context.getRequest().setAttribute("cardClass", context.getPreferences().get("cardClass"));

        return context;
      }
    }

    // Use the final html
    context.getRequest().setAttribute("contentHtml", html);

    // Handle scripts and iframes
    if (html.contains("<script")) {
      context.getResponse().setHeader("X-XSS-Protection", "0");
    } else if (html.contains("<iframe")) {
      // Allow iframes (can limit later to certain applications)
      context.getResponse().setHeader("X-XSS-Protection", "0");
//        context.getResponse().setHeader("Content-Security-Policy", "script-src 'self' www.google-analytics.com ajax.googleapis.com;");
/*
        if (html.contains("youtube.com")) {
          context.getResponse().setHeader("Content-Security-Policy", "child-src 'self' *.youtube.com ;");
        }
        if (html.contains("vimeo.com")) {
          context.getResponse().setHeader("Content-Security-Policy", "default-src *.vimeo.com ;");
          context.getResponse().setHeader("Content-Security-Policy", "script-src *.vimeo.com *.vimeocdn.com *.newrelic.com *.nr-data.net ;");
          context.getResponse().setHeader("Content-Security-Policy", "style-src *.vimeocdn.com ;");
          context.getResponse().setHeader("Content-Security-Policy", "child-src 'self' *.vimeo.com *.vimeocdn.com ;");
        }
*/
    }

    context.setJsp(jsp);
    return context;
  }

  private void addCard(WidgetContext context, List<String> cardList, String html, StringBuilder extraHTMLContent) {

    // <a href="#reveal-some-unique-id">The Button Name</a>
    boolean addReveal = Boolean.parseBoolean(context.getPreferences().getOrDefault("addReveal", "false"));
    boolean setBackgroundImage = Boolean.parseBoolean(context.getPreferences().getOrDefault("setBackgroundImage", "false"));

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
      contentEditorLink = "<div class=\"platform-content-inline-editor\"><a class=\"hollow button secondary\" href=\"/content-editor?uniqueId=" + uniqueId + "&returnPage=" + returnPage + "\"><i class=\"" + FontCommand.far() + " fa-window-restore\"></i></a></div>";
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
      // https://foundation.zurb.com/sites/docs/motion-ui.html
      // motion-ui values: slide-in-right, slide-in-left, slide-in-down, slide-in-up, fade-in, scale-in-down
      String revealIn = context.getPreferences().getOrDefault("revealIn", "slide-in-left fast");
      String revealOut = context.getPreferences().getOrDefault("revealOut", "slide-out-left fast");
      String revealClass = context.getPreferences().get("revealClass");

      String textTag = "";
      if (!text.contains("<img")) {
        textTag = " reveal-button-content";
      }

      html = html.substring(0, tagStartIdx) +
          "<button class=\"reveal-button-text" + textTag + "\" data-toggle=\"modal" + data + "\">" + text + "</button>" +
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

  /**
   * Check for inline uniqueIds for complex html
   *
   * @param context
   * @param html
   * @return
   */
  private String embedInlineContent(WidgetContext context, String html) {
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
          embeddedHtml = "<a class=\"button tiny radius primary\" href=\"" + context.getContextPath() + "/content-editor?uniqueId=" + embeddedUniqueId + "&returnPage=" + context.getUri() + "\"><i class=\"" + FontCommand.fas() + " fa-edit\"></i> Add Content Here</a>";
        } else {
          embeddedHtml = "<div class=\"platform-content-inline-editor\">" +
              (hasDraftContent ? "<span class=\"label warning\">DRAFT</span>" : "") +
              "<a class=\"hollow button small secondary\" href=\"" + context.getContextPath() + "/content-editor?uniqueId=" + embeddedUniqueId + "&returnPage=" + context.getUri() + "\"><i class=\"" + FontCommand.fas() + " fa-edit\"></i></a>" +
              "</div>" +
              embeddedHtml;
        }
        context.getRequest().removeAttribute("showEditor");
      }
      //
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

  public WidgetContext action(WidgetContext context) {

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
    uniqueId = checkForBlogPreferences(context, uniqueId);

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

  private WidgetContext publishContent(WidgetContext context, Content content) {
    if (StringUtils.isNotBlank(content.getDraftContent())) {
      ContentRepository.publish(content);
    }
    return context;
  }

  private WidgetContext deleteContent(WidgetContext context, Content content) {
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

  private String checkForBlogPreferences(WidgetContext context, String uniqueId) {
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
}
