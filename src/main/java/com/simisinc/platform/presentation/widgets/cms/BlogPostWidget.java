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

import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.UserCommand;
import com.simisinc.platform.application.cms.ContentImageSrcsetCommand;
import com.simisinc.platform.application.cms.ContentVideoEmbedCommand;
import com.simisinc.platform.application.cms.LoadBlogCommand;
import com.simisinc.platform.application.cms.LoadBlogPostCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/7/18 11:55 AM
 */
public class BlogPostWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/blog-post-details.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Preferences
    context.getRequest().setAttribute("showTitle", context.getPreferences().getOrDefault("showTitle", "true"));
    context.getRequest().setAttribute("showAuthor", context.getPreferences().getOrDefault("showAuthor", "true"));
    context.getRequest().setAttribute("showDate", context.getPreferences().getOrDefault("showDate", "true"));
    context.getRequest().setAttribute("link", context.getPreferences().get("link"));

    // Determine the blog
    Blog blog = retrieveValidatedBlogFromPreferences(context);
    if (blog == null) {
      return null;
    }
    context.getRequest().setAttribute("blog", blog);
    // "Back to List" used blog.link alone, so a reader who opened a 2021 post from page 4 was
    // returned to page 1 and had to paginate back (issue reported on the pilot). The listing page
    // and sort are recovered from the referrer rather than carried on the post's own URL, which
    // would put ?page=4 on every article link and into canonical/og:url for no reader benefit.
    context.getRequest().setAttribute("backToListUrl",
        buildBackToListUrl(blog.getLink(), context.getRequest().getHeader("Referer"),
            context.getRequest().getServerName()));

    // Determine the blog post
    BlogPost blogPost = retrieveValidatedBlogPostFromUrl(context, blog);
    if (blogPost == null) {
      return null;
    }
    context.getRequest().setAttribute("blogPost", blogPost);
    // A separate request attribute, not a mutation of blogPost.body itself -- the loaded object is
    // also read elsewhere (e.g. list widgets) expecting the raw, unprocessed value (issue #411 PR2).
    context.getRequest().setAttribute("blogPostBodyHtml", ContentVideoEmbedCommand.privacyEnhanceEmbeds(ContentImageSrcsetCommand.enhanceImageTags(blogPost.getBody())));

    // Set the HTML page title -- already includes the blog name, so the container must not
    // also append the WebPage's own title (e.g. a wildcard page like /news/*) on top of it
    context.setComposedPageTitle(blogPost.getTitle() + " - " + blog.getName());
    if (StringUtils.isNotBlank(blogPost.getSummary())) {
      context.setPageDescription(blogPost.getSummary());
    }
    if (StringUtils.isNotBlank(blogPost.getKeywords())) {
      context.setPageKeywords(blogPost.getKeywords());
    }
    // A blog post is an article, not a generic page -- PageServlet defaults every page that is not
    // an Item or Collection to "website", and it decides before this widget runs (issue #1355).
    context.setPageType("article");
    if (StringUtils.isNotBlank(blogPost.getImageUrl())) {
      context.setPageImageUrl(blogPost.getImageUrl());
    }

    // Set Article schema fields for JSON-LD (issue #403); a post that isn't actually published
    // yet (visible here only to admin/content-manager, see retrieveValidatedBlogPostFromUrl)
    // has nothing citable, so it gets no Article markup at all rather than a fabricated date
    if (blogPost.getPublished() != null) {
      context.setArticleHeadline(blogPost.getTitle());
      context.setArticlePublishedDate(blogPost.getPublished());
      context.setArticleModifiedDate(blogPost.getModified());
      User author = LoadUserCommand.loadUser(blogPost.getCreatedBy());
      if (author != null) {
        context.setArticleAuthorName(UserCommand.name(author));
      }
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public static Blog retrieveValidatedBlogFromPreferences(WidgetContext context) {
    String blogUniqueId = context.getPreferences().get("blogUniqueId");
    if (blogUniqueId == null) {
      return null;
    }
    Blog blog = LoadBlogCommand.loadBlogByUniqueId(blogUniqueId);
    if (blog == null) {
      return null;
    }
    if (!blog.getEnabled() &&
        !(context.hasRole("admin") || context.hasRole("content-manager"))) {
      return null;
    }
    return blog;
  }

  public static BlogPost retrieveValidatedBlogPostFromUrl(WidgetContext context, Blog blog) {
    String blogPostUniqueId = context.getUri().substring(context.getUri().lastIndexOf("/") + 1);
    BlogPost blogPost = LoadBlogPostCommand.loadBlogPostByUniqueId(blog.getId(), blogPostUniqueId);
    if (blogPost == null) {
      LOG.debug("Blog post not found: " + blog.getId() + " " + blogPostUniqueId);
      return null;
    }
    if (blogPost.getPublished() == null &&
        !(context.hasRole("admin") || context.hasRole("content-manager"))) {
      return null;
    }
    // Issue #427: an archived post must actually come offline for the public too, same as an
    // unpublished one -- BlogPostRepository.findByUniqueId doesn't go through createWhereStatement
    // (and so never considers the archived column at all), so this is the only place left to check.
    if (blogPost.getArchived() != null &&
        !(context.hasRole("admin") || context.hasRole("content-manager"))) {
      return null;
    }
    return blogPost;
  }

  public WidgetContext post(WidgetContext context) {
    // deletePost is submitted via a real POST (issue #358 moved state-changing admin actions
    // off GET query strings), so it arrives here rather than in action() below. Dispatch
    // through the same table action() uses for a GET caller.
    if ("deletePost".equals(context.getParameter("action"))) {
      return action(context);
    }
    return context;
  }

  public WidgetContext action(WidgetContext context) {
    // Permission is required
    if (!(context.hasRole("admin") || context.hasRole("content-manager"))) {
      return context;
    }
    // Find the blog record
    long blogPostId = context.getParameterAsLong("blogPostId");
    BlogPost blogPost = LoadBlogPostCommand.loadBlogPostById(blogPostId);
    if (blogPost == null) {
      context.setErrorMessage("The record was not found");
      return context;
    }
    Blog blog = LoadBlogCommand.loadBlogById(blogPost.getBlogId());
    // Execute the action
    context.setRedirect("/" + blog.getUniqueId());
    String action = context.getParameter("action");
    if ("deletePost".equals(action)) {
      return deletePost(context, blogPost);
    }
    return context;
  }

  private WidgetContext deletePost(WidgetContext context, BlogPost blogPost) {
    String targetId = String.valueOf(blogPost.getId());
    String targetLabel = blogPost.getTitle();
    // Attempt to delete the blog
    try {
      // remove() returns false on a swallowed DB failure rather than throwing, so branch on its result
      boolean removed = BlogPostRepository.remove(blogPost);
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.delete",
          removed ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE, "blog_post", targetId, targetLabel, null);
      context.setSuccessMessage("Post was deleted");
    } catch (Exception e) {
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.delete", AuditEventCommand.FAILURE,
          "blog_post", targetId, targetLabel, e.getMessage());
      context.setErrorMessage("The post could not be deleted: " + e.getMessage());
    }
    return context;
  }

  /**
   * Rebuilds the listing URL the reader came from, so "Back to List" returns them to the page and
   * sort they were on rather than to page 1.
   *
   * <p>
   * The referrer is treated strictly as untrusted input and is never echoed. It is used only to
   * answer "which listing page", and only when it is same-host and its path is exactly this blog's
   * listing. The returned URL is then rebuilt from the trusted blog link plus values that have each
   * been validated -- an integer page, and sort names matched against a conservative pattern -- so
   * nothing from the header can reach the markup verbatim.
   * </p>
   *
   * <p>
   * Referer is present for this case because PageServlet sends
   * {@code Referrer-Policy: strict-origin-when-cross-origin}, which still sends the full path on a
   * same-origin navigation. When it is absent, cross-origin, or points somewhere else -- a search
   * result, a bookmark, a link from another article -- the plain listing link is returned, which is
   * exactly today's behaviour.
   * </p>
   *
   * @return the blog's listing link, with a validated page/sort query appended when one is
   *         recoverable
   */
  static String buildBackToListUrl(String blogLink, String referer, String serverName) {
    if (StringUtils.isBlank(blogLink) || StringUtils.isBlank(referer)) {
      return blogLink;
    }
    URI uri;
    try {
      uri = new URI(referer);
    } catch (Exception e) {
      return blogLink;
    }
    if (!blogLink.equals(uri.getPath())) {
      return blogLink;
    }
    // A same-origin navigation reports the host we are serving; anything else is not a reader
    // arriving from this listing and must not steer where the button goes.
    if (uri.getHost() != null && serverName != null && !uri.getHost().equalsIgnoreCase(serverName)) {
      return blogLink;
    }
    String page = null;
    String sortBy = null;
    String sortOrder = null;
    String query = uri.getQuery();
    if (query != null) {
      for (String pair : query.split("&")) {
        int equals = pair.indexOf('=');
        if (equals < 1) {
          continue;
        }
        String name = pair.substring(0, equals);
        String value = pair.substring(equals + 1);
        if ("page".equals(name)) {
          page = value;
        } else if ("sortBy".equals(name)) {
          sortBy = value;
        } else if ("sortOrder".equals(name)) {
          sortOrder = value;
        }
      }
    }
    StringBuilder url = new StringBuilder(blogLink);
    // Page 1 is what the plain link already gives, so it earns no query string.
    if (isPageBeyondTheFirst(page)) {
      url.append("?page=").append(page);
    }
    if (isSimpleName(sortBy) && isSimpleName(sortOrder)) {
      url.append(url.indexOf("?") == -1 ? "?" : "&")
          .append("sortBy=").append(sortBy).append("&sortOrder=").append(sortOrder);
    }
    return url.toString();
  }

  private static boolean isPageBeyondTheFirst(String value) {
    if (StringUtils.isBlank(value)) {
      return false;
    }
    try {
      return Integer.parseInt(value) > 1;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /** Sort names are developer-defined words; anything else is not one and is dropped. */
  private static boolean isSimpleName(String value) {
    return value != null && value.length() <= 20 && value.matches("[A-Za-z]+");
  }
}
