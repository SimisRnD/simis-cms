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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.login.StepUpAuthCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.cache.PublishEventCachePurgeHandler;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
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
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static String HTML_JSP = "/cms/content-html.jsp";

  /**
   * Renders a stored content string to display HTML according to its format stamp. This is the single
   * point where the content pipeline decides how a stored value becomes HTML:
   *
   * <ul>
   * <li>{@link DeltaContentCommand#LEGACY_HTML_FORMAT} (and any unrecognized value) is already HTML and
   * passes through unchanged -- the backward-compatible default, since legacy content is the vast
   * majority and was cleaned by {@code SaveContentCommand} on the way in.</li>
   * <li>{@link DeltaContentCommand#DELTA_FORMAT_VERSION} is visual-editor Quill Delta JSON, rendered
   * server-side through the allowlist in {@link DeltaContentCommand} -- never Quill's HTML-export path.</li>
   * </ul>
   *
   * <p>Null in, null out: callers already treat a null result as "no content" (e.g. the add-content
   * button), so that contract is preserved.
   */
  public static String toHtml(String content, int contentFormat) {
    if (content == null) {
      return null;
    }
    if (contentFormat == DeltaContentCommand.DELTA_FORMAT_VERSION) {
      return DeltaContentCommand.render(content);
    }
    return content;
  }

  public static String getHtmlFromPreferences(WidgetContext context) {

    String html = null;
    String uniqueId = context.getPreferences().get("uniqueId");

    if (uniqueId != null) {
      // Populate from dynamic values
      uniqueId = checkForBlogPreferences(context, uniqueId);
      context.getRequest().setAttribute("uniqueId", uniqueId);
      // Check for the content
      Content content = LoadContentCommand.loadContentByUniqueId(uniqueId);
      if (content != null) {
        html = toHtml(content.getContent(), content.getContentFormat());
        // Look for draft content
        if (EditorPermissionCommand.canEditContent(context.getUserSession())) {
          if (content.getDraftContent() != null) {
            html = toHtml(content.getDraftContent(), content.getDraftContentFormat());
            context.getRequest().setAttribute("isDraft", "true");
          }
          // Which review affordance to render, decided here rather than in a JSP expression
          context.getRequest().setAttribute("reviewOffer", ContentReviewCommand.offerFor(content,
              context.getUserId(), LoadSitePropertyCommand.loadByNameAsBoolean("content.review.required")));
        }
      }
    }

    // Use the widget preferences.
    // Sanitize this branch. Content loaded from the database above was cleaned by
    // SaveContentCommand on the way in, but a widget preference never passes through that path --
    // it comes straight from page-layout XML, which /admin/web-page-designer exposes to
    // content-manager. Without this, every widget that calls this method (content, cards,
    // carousel, gallery, reveal, slider, accordion) renders an unsanitized preference unescaped.
    if (html == null) {
      html = HtmlCommand.cleanContent(context.getPreferences().get("html"));
    }

    // It's possible to have different content injected into this content
    html = embedInlineContent(context, html);

    // Display a button for admins to add content
    boolean hasEditorPermission = EditorPermissionCommand.canEditContent(context.getUserSession());
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

    boolean hasEditorPermission = EditorPermissionCommand.canEditContent(context.getUserSession());
    boolean hasDraftContent = false;
    int endUniqueIdx;

    StringBuilder sb = new StringBuilder(html.substring(0, startUniqueIdx));
    while ((endUniqueIdx = html.indexOf("}", startUniqueIdx)) > -1) {
      String embeddedUniqueId = html.substring(startUniqueIdx + 11, endUniqueIdx).trim();
      String embeddedHtml = "";
      Content content = LoadContentCommand.loadContentByUniqueId(embeddedUniqueId);
      if (content != null) {
        embeddedHtml = toHtml(content.getContent(), content.getContentFormat());
        // Look for draft content
        if (hasEditorPermission) {
          if (content.getDraftContent() != null) {
            embeddedHtml = toHtml(content.getDraftContent(), content.getDraftContentFormat());
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
      // cardSize is rendered into a css width, so require a css length
      context.getRequest().setAttribute("cardSize",
          NumberCommand.filterCssLength(context.getPreferences().getOrDefault("cardSize", "200px"), "200px"));
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
    boolean hasEditorPermission = EditorPermissionCommand.canEditContent(context.getUserSession());
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
          toHtml(content.getContent(), content.getContentFormat()) + "\n" +
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
    if (!EditorPermissionCommand.canEditContent(context.getUserSession())) {
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

    // Determine the action. Governed publishing (Project #6, Phase 1) adds the review workflow; the
    // gate is enforced only when the site has opted in via content.review.required.
    // Note: "approve" is intentionally NOT handled here -- it requires step-up re-authentication
    // (see performContentApproval(), reached via ContentWidget.post()) and must only be reachable
    // through that gated path.
    String action = context.getParameter("action");
    boolean reviewRequired = LoadSitePropertyCommand.loadByNameAsBoolean("content.review.required");
    if ("publish".equals(action)) {
      return publishContent(context, content, reviewRequired);
    } else if ("submitForReview".equals(action)) {
      return submitForReview(context, content);
    } else if ("reject".equals(action)) {
      return rejectContent(context, content);
    } else if ("deleteContent".equals(action)) {
      return deleteContent(context, content);
    } else if ("saveDraft".equals(action)) {
      return saveDraft(context, content);
    }

    return context;
  }

  private static WidgetContext saveDraft(WidgetContext context, Content content) {
    String html = context.getParameter("html");
    if (StringUtils.isBlank(html)) {
      context.setJson("{\"success\":false,\"error\":\"No content provided\"}");
      return context;
    }
    try {
      Content savedContent = SaveContentCommand.saveSafeContent(content.getUniqueId(), html, context.getUserId(), false);
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.saveDraft", AuditEventCommand.SUCCESS,
          "content", String.valueOf(content.getId()), content.getUniqueId(), null);
      // The save above has already succeeded -- everything from here is a non-blocking, purely
      // additive author-facing notice (#258). It must never turn a successful save into a reported
      // failure, so the baseline response is set first and only replaced if the enrichment fully
      // succeeds.
      context.setJson("{\"success\":true}");
      try {
        String savedHtml = savedContent != null && savedContent.getDraftContent() != null
            ? savedContent.getDraftContent()
            : html;
        List<ContentAccessibilityCommand.Finding> findings = ContentAccessibilityCommand.check(savedHtml);
        if (!findings.isEmpty()) {
          Map<String, Object> response = new LinkedHashMap<>();
          response.put("success", true);
          response.put("a11yFindings", toA11yFindingList(findings));
          context.setJson(MAPPER.writeValueAsString(response));
        }
      } catch (Exception a11yException) {
        LOG.warn("Accessibility check failed for uniqueId " + content.getUniqueId(), a11yException);
      }
    } catch (Exception e) {
      LOG.error("saveDraft failed for uniqueId " + content.getUniqueId(), e);
      context.setJson("{\"success\":false,\"error\":\"Save failed\"}");
    }
    return context;
  }

  /** Converts a11y findings to plain maps for JSON serialization, in document order. */
  private static List<Map<String, String>> toA11yFindingList(List<ContentAccessibilityCommand.Finding> findings) {
    List<Map<String, String>> result = new ArrayList<>();
    for (ContentAccessibilityCommand.Finding finding : findings) {
      Map<String, String> map = new LinkedHashMap<>();
      map.put("rule", finding.getRule());
      map.put("criterion", finding.getCriterion());
      map.put("message", finding.getMessage());
      map.put("context", finding.getContext());
      result.add(map);
    }
    return result;
  }

  private static WidgetContext publishContent(WidgetContext context, Content content, boolean reviewRequired) {
    if (StringUtils.isBlank(content.getDraftContent())) {
      return context;
    }
    // The gate: with governed publishing on, an unapproved draft cannot be published directly -- the
    // only path to live is submit -> approve. With it off, this is the direct publish it always was.
    if (!ContentReviewCommand.mayPublish(content, reviewRequired)) {
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.publish", AuditEventCommand.FAILURE,
          "content", String.valueOf(content.getId()), content.getUniqueId(), "blocked: draft not approved for release");
      context.setErrorMessage("This content must be submitted for review and approved before it can be published");
      return context;
    }
    ContentRepository.publish(content);
    AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.publish", AuditEventCommand.SUCCESS,
        "content", String.valueOf(content.getId()), content.getUniqueId(), null);
    purgeCacheForCurrentPage(context);
    return context;
  }

  private static WidgetContext submitForReview(WidgetContext context, Content content) {
    try {
      ContentReviewCommand.submitForReview(content, context.getUserId());
      ContentRepository.save(content);
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.submit", AuditEventCommand.SUCCESS,
          "content", String.valueOf(content.getId()), content.getUniqueId(), null);
      context.setSuccessMessage("The content was submitted for review");
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
    }
    return context;
  }

  /**
   * Handles a POST-based content approval with step-up re-authentication.
   * Must be called after {@code execute()} has already set the JSP on the context so that a
   * step-up prompt can re-render the same page.
   */
  public static WidgetContext performContentApproval(WidgetContext context) {
    if (!EditorPermissionCommand.canEditContent(context.getUserSession())) {
      return context;
    }
    String uniqueId = context.getPreferences().get("uniqueId");
    if (uniqueId == null) {
      return context;
    }
    uniqueId = ContentHtmlCommand.checkForBlogPreferences(context, uniqueId);
    Content content = LoadContentCommand.loadContentByUniqueId(uniqueId);
    if (content == null) {
      return context;
    }
    String stepUpCredential = context.getParameter("stepUpCredential");
    if (!StepUpAuthCommand.isValid(context.getUserSession())) {
      if (StringUtils.isBlank(stepUpCredential)) {
        context.addSharedRequestValue("stepUpRequired", "true");
        return context;
      }
      User actingUser = LoadUserCommand.loadUser(context.getUserId());
      if (!StepUpAuthCommand.verify(context.getUserSession(), actingUser, stepUpCredential)) {
        context.setErrorMessage("Re-authentication failed. Enter your password or authenticator code.");
        context.addSharedRequestValue("stepUpRequired", "true");
        return context;
      }
    }
    return approveContent(context, content);
  }

  private static WidgetContext approveContent(WidgetContext context, Content content) {
    String releaseReference = context.getParameter("releaseReference");
    try {
      // approve() enforces separation of duties (the approver cannot be the submitter); approval then
      // promotes the draft to live and records the named approver + release authority in the audit trail.
      ContentReviewCommand.approve(content, context.getUserId(), releaseReference);
      ContentRepository.publish(content);
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.approve", AuditEventCommand.SUCCESS,
          "content", String.valueOf(content.getId()), content.getUniqueId(),
          StringUtils.isNotBlank(releaseReference) ? "release authority: " + releaseReference : null);
      purgeCacheForCurrentPage(context);
      context.setSuccessMessage("The content was approved and published");
    } catch (DataException e) {
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.approve", AuditEventCommand.FAILURE,
          "content", String.valueOf(content.getId()), content.getUniqueId(), e.getMessage());
      context.setErrorMessage(e.getMessage());
    }
    return context;
  }

  /**
   * Triggers an AFD cache purge (#420) for the page a Content publish/approve action was just
   * performed on. ContentWidget and its six siblings (gallery/slider/cards/accordion/reveal/carousel)
   * all submit their publish/approve actions back to {@code widgetContext.uri} -- see content.jsp's
   * publish/submit/approve/reject links and form, which all target {@code ${widgetContext.uri}} --
   * so the current request URI is exactly the live page whose rendered HTML just changed. This is the
   * same "the page this action happened on" convention ContentEditorWidget's own purge already uses
   * for its returnPage parameter.
   * <p>
   * A Content record can be embedded on more than one page (a shared uniqueId placed on multiple
   * pages, or nested via the {@code ${uniqueId:...}} inline-embed syntax in embedInlineContent()
   * above) -- only the page this action was invoked on is purged here. There is no reverse index
   * from a Content record to every page/widget instance that renders it (WebPageRepository has no
   * lookup by embedded content uniqueId), so enumerating every embed would require new indexing
   * infrastructure this codebase does not have; out of scope for this fix. Any other page embedding
   * the same content keeps serving its cached response until that page's own natural cache expiry
   * (max-age) or its own next publish/update, exactly as it already does today without this handler.
   * <p>
   * Never throws and never blocks the publish/approve that already succeeded by the time this runs
   * -- purgeUrls() (reached via onPageUpdated()) is fully self-contained try/catch, same as every
   * other call site.
   */
  private static void purgeCacheForCurrentPage(WidgetContext context) {
    WebPage webPage = LoadWebPageCommand.loadByLink(context.getUri());
    if (webPage != null) {
      PublishEventCachePurgeHandler.onPageUpdated(webPage);
    }
  }

  private static WidgetContext rejectContent(WidgetContext context, Content content) {
    try {
      ContentReviewCommand.reject(content, context.getUserId());
      ContentRepository.save(content);
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.reject", AuditEventCommand.SUCCESS,
          "content", String.valueOf(content.getId()), content.getUniqueId(), null);
      context.setSuccessMessage("The content was returned to the author");
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
    }
    return context;
  }

  private static WidgetContext deleteContent(WidgetContext context, Content content) {
    // Capture identity before the record is removed
    String targetId = String.valueOf(content.getId());
    String targetLabel = content.getUniqueId();
    // Attempt to delete the content (permission was verified in performWebAction)
    try {
      if (ContentRepository.remove(content)) {
        AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.delete", AuditEventCommand.SUCCESS,
            "content", targetId, targetLabel, null);
        context.setSuccessMessage("The content was deleted");
      } else {
        AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.delete", AuditEventCommand.FAILURE,
            "content", targetId, targetLabel, null);
        context.setErrorMessage("The content could not be deleted");
      }
    } catch (Exception e) {
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.delete", AuditEventCommand.FAILURE,
          "content", targetId, targetLabel, e.getMessage());
      context.setErrorMessage("The content could not be deleted: " + e.getMessage());
    }
    return context;
  }
}
