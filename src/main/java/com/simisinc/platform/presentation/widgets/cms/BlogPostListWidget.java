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

import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.application.cms.ImageCommand;
import com.simisinc.platform.application.cms.NumberCommand;

import com.simisinc.platform.application.cms.LoadBlogCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.domain.model.cms.ImageVariant;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ImageVariantRepository;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/7/18 11:55 AM
 */
public class BlogPostListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/blog-post-list.jsp";
  static String OVERVIEW_JSP = "/cms/blog-post-list-overview.jsp";
  static String TITLES_JSP = "/cms/blog-post-list-titles.jsp";
  static String PANEL_JSP = "/cms/blog-post-list-panel.jsp";
  static String CARDS_JSP = "/cms/blog-post-list-cards.jsp";
  static String FEATURED_JSP = "/cms/blog-post-list-featured.jsp";
  static String MASONRY_JSP = "/cms/blog-post-list-masonry.jsp";
  static String SHOWCASE_JSP = "/cms/blog-post-list-showcase.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("showPaging", context.getPreferences().getOrDefault("showPaging", "true"));

    // Preferences
    context.getRequest().setAttribute("showSort", context.getPreferences().getOrDefault("showSort", "false"));
    context.getRequest().setAttribute("showAuthor", context.getPreferences().getOrDefault("showAuthor", "true"));
    context.getRequest().setAttribute("showDate", context.getPreferences().getOrDefault("showDate", "true"));
    context.getRequest().setAttribute("addDateToTitle", context.getPreferences().getOrDefault("addDateToTitle", "false"));
    context.getRequest().setAttribute("showTags", context.getPreferences().getOrDefault("showTags", "true"));
    context.getRequest().setAttribute("showImage", context.getPreferences().getOrDefault("showImage", "true"));
    context.getRequest().setAttribute("showSummary", context.getPreferences().getOrDefault("showSummary", "true"));
    context.getRequest().setAttribute("readMoreText", context.getPreferences().getOrDefault("readMoreText", "Read More"));

    // Determine the blog
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
    context.getRequest().setAttribute("blog", blog);

    // Check for a type: recent
    String type = context.getPreferences().get("type");

    // Check for the view
    String view = context.getPreferences().getOrDefault("view", "default");

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "10"));
    if ("masonry".equals(view)) {
      limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "50"));
    }
    int page = context.getParameterAsInt("page", 1);
    if ("recent".equals(type)) {
      page = 1;
    }
    int itemsPerPage = context.getParameterAsInt("items", limit);

    // Determine the sorting, use values for the request that are separate from the database values
    String sortByValue = context.getParameter("sortBy", "date");
    String sortOrderValue = context.getParameter("sortOrder", "newest");
    String pagingUri = "";
    if (!"date".equals(sortByValue) || !"newest".equals(sortOrderValue)) {
      pagingUri =
          "&sortBy=" + UrlCommand.encodeUri(sortByValue) +
              "&sortOrder=" + UrlCommand.encodeUri(sortOrderValue);
    }
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING_URI, pagingUri);
    context.getRequest().setAttribute(RequestConstants.RECORD_SORT_BY, sortByValue);
    context.getRequest().setAttribute(RequestConstants.RECORD_SORT_ORDER, sortOrderValue);

    // Set the constraints
    String columnToSortBy = "start_date";
    if ("category".equals(sortByValue)) {
      // @todo can this be sorted on?
    }
    String columnSortOrder = "desc";
    if ("oldest".equals(sortOrderValue)) {
      columnSortOrder = "asc";
    } else if ("newest".equals(sortOrderValue)) {
      columnSortOrder = "desc";
    }
    DataConstraints constraints = new DataConstraints(page, itemsPerPage, columnToSortBy, columnSortOrder);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Determine criteria
    BlogPostSpecification blogPostSpecification = new BlogPostSpecification();
    blogPostSpecification.setBlogId(blog.getId());
    if (!(context.hasRole("admin") || context.hasRole("content-manager"))) {
      blogPostSpecification.setPublishedOnly(true);
      blogPostSpecification.setStartDateIsBeforeNow(true);
      blogPostSpecification.setIsWithinEndDate(true);
      // Issue #427: an archived post must actually disappear from this public listing -- mirrors
      // the UpcomingCalendarEventsWidget/CalendarSearchResultsWidget exclusion added for #882.
      blogPostSpecification.setArchivedOnly(false);
    }

    // Load the blog posts
    List<BlogPost> blogPostList = BlogPostRepository.findAll(blogPostSpecification, constraints);
    context.getRequest().setAttribute("blogPostList", blogPostList);

    // Batch-fetch existing image variants for every post's image in one query (issue #411 PR2) --
    // avoids one findByImageId call per row in the JSP loop, mirroring blogPostReviewStatusMap
    // immediately below.
    List<Long> blogPostImageIds = new ArrayList<>();
    for (BlogPost blogPost : blogPostList) {
      Long imageId = ImageCommand.parseImageId(blogPost.getImageUrl());
      if (imageId != null) {
        blogPostImageIds.add(imageId);
      }
    }
    Map<Long, List<ImageVariant>> imageVariantsByImageId = ImageVariantRepository.findByImageIds(blogPostImageIds);
    context.getRequest().setAttribute("imageVariantsByImageId", imageVariantsByImageId);
    // The image records themselves, in one more query for the whole page rather than one per row.
    // Two things come out of them: the originals' widths, so srcset can offer the full-size file as
    // a candidate rather than topping out at a thumbnail (issue #1370), and the author's alt text
    // (issue #1372).
    Map<Long, Image> imagesByImageId = ImageRepository.findByIds(blogPostImageIds);
    Map<Long, Integer> imageWidthsByImageId = new LinkedHashMap<>();
    for (Image image : imagesByImageId.values()) {
      if (image.getWidth() > 0) {
        imageWidthsByImageId.put(image.getId(), image.getWidth());
      }
    }
    context.getRequest().setAttribute("imageWidthsByImageId", imageWidthsByImageId);
    // Resolved per post rather than per image, because the fallback needs the post: an image can be
    // reused across posts, and the title is what distinguishes the cards when the library has
    // nothing stored. Keyed by post id so the JSPs read one value and make no decision of their own.
    context.getRequest().setAttribute("blogPostImageAltText", resolveImageAltText(blogPostList, imagesByImageId));

    // Governed publish workflow status per post (#407, phase 2), keyed by post id -- only shown to
    // admin/content-manager viewers (the same audience already shown unpublished posts here at all,
    // see the specification.setPublishedOnly() gate above), and only for posts with a pending draft
    // -- mirrors WebPageListWidget's identical webPageReviewStatusMap pattern.
    Map<Long, String> blogPostReviewStatusMap = new HashMap<>();
    if (context.hasRole("admin") || context.hasRole("content-manager")) {
      for (BlogPost blogPost : blogPostList) {
        if (blogPost.hasDraftContent()) {
          blogPostReviewStatusMap.put(blogPost.getId(), ContentReviewCommand.listStatusLabel(blogPost));
        }
      }
    }
    context.getRequest().setAttribute("blogPostReviewStatusMap", blogPostReviewStatusMap);

    // See if an empty widget can be shown
    if (blogPostList.isEmpty()) {
      if (!"true".equals(context.getPreferences().getOrDefault("showWhenEmpty", "true"))) {
        return null;
      }
    }

    // Show the editor
    if ("overview".equals(view)) {
      context.getRequest().setAttribute("showReadMore", context.getPreferences().getOrDefault("showReadMore", "false"));
      context.setJsp(OVERVIEW_JSP);
    } else if ("titles".equals(view)) {
      context.getRequest().setAttribute("showBullets", context.getPreferences().getOrDefault("showBullets", "false"));
      context.setJsp(TITLES_JSP);
    } else if ("panel".equals(view)) {
      // A compact, static list intended for a sidebar/rail placement (e.g. a "Latest News" panel) --
      // no slider, no auto-advancing motion, so there is nothing here that needs a pause control or
      // a prefers-reduced-motion check. Every item is a plain server-rendered link, which also keeps
      // it fully crawlable -- unlike carousel slide state, a search engine sees every entry.
      context.getRequest().setAttribute("viewAllUrl", context.getPreferences().get("viewAllUrl"));
      context.getRequest().setAttribute("viewAllText", context.getPreferences().getOrDefault("viewAllText", "View all"));
      context.setJsp(PANEL_JSP);
    } else if ("cards".equals(view) || "showcase".equals(view)) {
      // "showcase" is the same grid as "cards" -- same card counts, same responsive breakpoints --
      // and differs only in its template and styling, so it shares this preference handling rather
      // than duplicating it and drifting.

      // Determine the number of cards to use across
      String smallCardCount = context.getPreferences().getOrDefault("smallCardCount", "3");
      String mediumCardCount = context.getPreferences().get("mediumCardCount");
      String largeCardCount = context.getPreferences().get("largeCardCount");
      if (StringUtils.isBlank(mediumCardCount)) {
        mediumCardCount = smallCardCount;
      }
      if (StringUtils.isBlank(largeCardCount)) {
        largeCardCount = mediumCardCount;
      }
      // These are rendered into the slider's javascript config, so require plain integers
      context.getRequest().setAttribute("smallCardCount", NumberCommand.filterPositiveInteger(smallCardCount, "3"));
      context.getRequest().setAttribute("mediumCardCount", NumberCommand.filterPositiveInteger(mediumCardCount, "3"));
      context.getRequest().setAttribute("largeCardCount", NumberCommand.filterPositiveInteger(largeCardCount, "3"));
      context.getRequest().setAttribute("cardClass", context.getPreferences().get("cardClass"));

      context.setJsp("showcase".equals(view) ? SHOWCASE_JSP : CARDS_JSP);
    } else if ("masonry".equals(view)) {
      context.setJsp(MASONRY_JSP);
    } else if ("featured".equals(view)) {
      context.setJsp(FEATURED_JSP);
    } else {
      context.setJsp(JSP);
    }
    return context;
  }

  /**
   * Resolves the alt text for each post's banner image.
   *
   * <p>
   * The banner is inside the post's own link, so an empty alt is not an option here: it would leave
   * that link with no accessible name at all (WCAG 2.4.4), which is a worse failure than a generic
   * description. The post title is therefore the floor, and it is at least true and distinct per
   * card -- unlike the {@code "Blog post banner image"} literal these views used to hardcode, which
   * was announced identically on every card on the page (issue #1372).
   * </p>
   *
   * @return alt text keyed by blog post id, for every post that has a banner image
   */
  static Map<Long, String> resolveImageAltText(List<BlogPost> blogPostList,
      Map<Long, Image> imagesByImageId) {
    Map<Long, String> altTextByPostId = new LinkedHashMap<>();
    for (BlogPost blogPost : blogPostList) {
      if (StringUtils.isBlank(blogPost.getImageUrl())) {
        continue;
      }
      Long imageId = ImageCommand.parseImageId(blogPost.getImageUrl());
      Image image = (imageId != null ? imagesByImageId.get(imageId) : null);
      String storedAltText = (image != null ? StringUtils.trimToNull(image.getAltText()) : null);
      altTextByPostId.put(blogPost.getId(), storedAltText != null ? storedAltText : blogPost.getTitle());
    }
    return altTextByPostId;
  }
}
