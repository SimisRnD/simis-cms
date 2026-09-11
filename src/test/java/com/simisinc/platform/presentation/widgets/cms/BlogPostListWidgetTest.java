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

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.LoadBlogCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostSpecification;
import com.simisinc.platform.presentation.controller.DataConstants;
import com.simisinc.platform.presentation.controller.RequestConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.simisinc.platform.presentation.widgets.cms.BlogPostListWidget.JSP;
import static com.simisinc.platform.presentation.widgets.cms.BlogPostListWidget.PANEL_JSP;
import static com.simisinc.platform.presentation.widgets.cms.BlogPostListWidget.SHOWCASE_JSP;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

class BlogPostListWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // Set widget preferences
    preferences.put("blogUniqueId", "news");

    // Widgets can have parameters
    //widgetContext.getParameterMap().put("name", new String[]{"value"});

    // Blog
    Blog blog = new Blog();
    blog.setId(1L);
    blog.setUniqueId("news");
    blog.setName("News");
    blog.setEnabled(true);

    List<BlogPost> blogPostList = new ArrayList<>();
    for (long i = 1; i < 11; i++) {
      BlogPost blogPost = new BlogPost();
      blogPost.setId(i);
      blogPost.setBlogId(blog.getId());
      blogPost.setUniqueId("blog-post-" + i);
      blogPost.setTitle("This is blog post " + i);
      blogPostList.add(blogPost);
    }

    // Execute the widget
    try (MockedStatic<LoadBlogCommand> loadBlogCommandMockedStatic = mockStatic(LoadBlogCommand.class)) {
      try (MockedStatic<BlogPostRepository> blogPostRepositoryMockedStatic = mockStatic(BlogPostRepository.class)) {
        loadBlogCommandMockedStatic.when(() -> LoadBlogCommand.loadBlogByUniqueId(eq("news"))).thenReturn(blog);
        blogPostRepositoryMockedStatic.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(blogPostList);
        BlogPostListWidget widget = new BlogPostListWidget();
        widgetContext = widget.execute(widgetContext);
      }
    }

    DataConstraints constraints = (DataConstraints) widgetContext.getRequest().getAttribute(RequestConstants.RECORD_PAGING);
    Assertions.assertEquals(10, constraints.getPageSize());

    List<BlogPost> blogPostListRequest = (List) widgetContext.getRequest().getAttribute("blogPostList");
    Assertions.assertEquals(10, blogPostListRequest.size());

    Assertions.assertNotNull(widgetContext);
    Assertions.assertTrue(widgetContext.hasJsp());
    Assertions.assertEquals(JSP, widgetContext.getJsp());
  }

  @Test
  void executeSelectsThePanelJspForThePanelView() {
    preferences.put("blogUniqueId", "news");
    preferences.put("view", "panel");
    preferences.put("viewAllUrl", "/news");

    Blog blog = new Blog();
    blog.setId(1L);
    blog.setUniqueId("news");
    blog.setName("News");
    blog.setEnabled(true);

    List<BlogPost> blogPostList = new ArrayList<>();
    BlogPost blogPost = new BlogPost();
    blogPost.setId(1L);
    blogPost.setBlogId(blog.getId());
    blogPost.setUniqueId("blog-post-1");
    blogPost.setTitle("This is blog post 1");
    blogPostList.add(blogPost);

    try (MockedStatic<LoadBlogCommand> loadBlogCommandMockedStatic = mockStatic(LoadBlogCommand.class);
        MockedStatic<BlogPostRepository> blogPostRepositoryMockedStatic = mockStatic(BlogPostRepository.class)) {
      loadBlogCommandMockedStatic.when(() -> LoadBlogCommand.loadBlogByUniqueId(eq("news"))).thenReturn(blog);
      blogPostRepositoryMockedStatic.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(blogPostList);

      widgetContext = new BlogPostListWidget().execute(widgetContext);
    }

    Assertions.assertNotNull(widgetContext);
    Assertions.assertTrue(widgetContext.hasJsp());
    Assertions.assertEquals(PANEL_JSP, widgetContext.getJsp());
    Assertions.assertEquals("/news", widgetContext.getRequest().getAttribute("viewAllUrl"));
    Assertions.assertEquals("View all", widgetContext.getRequest().getAttribute("viewAllText"),
        "default view-all label when the preference is left unset");
  }

  @Test
  void executeExcludesArchivedPostsForAGuest() {
    // Issue #427: bulk Archive must actually take a post out of this public listing -- review
    // caught that this widget set publishedOnly/date-range filters for a guest but never
    // archivedOnly, so an archived post stayed fully visible here.
    preferences.put("blogUniqueId", "news");

    Blog blog = new Blog();
    blog.setId(1L);
    blog.setUniqueId("news");
    blog.setName("News");
    blog.setEnabled(true);

    try (MockedStatic<LoadBlogCommand> loadBlogCommandMockedStatic = mockStatic(LoadBlogCommand.class);
        MockedStatic<BlogPostRepository> blogPostRepositoryMockedStatic = mockStatic(BlogPostRepository.class)) {
      loadBlogCommandMockedStatic.when(() -> LoadBlogCommand.loadBlogByUniqueId(eq("news"))).thenReturn(blog);
      blogPostRepositoryMockedStatic.when(() -> BlogPostRepository.findAll(any(), any()))
          .thenReturn(new ArrayList<>());

      new BlogPostListWidget().execute(widgetContext);

      ArgumentCaptor<BlogPostSpecification> specCaptor = ArgumentCaptor.forClass(BlogPostSpecification.class);
      blogPostRepositoryMockedStatic.verify(() -> BlogPostRepository.findAll(specCaptor.capture(), any()));
      Assertions.assertEquals(DataConstants.FALSE, specCaptor.getValue().getArchivedOnly(),
          "a guest must never see archived posts in this listing");
    }
  }

  private static BlogPost post(long id, String title, String imageUrl) {
    BlogPost blogPost = new BlogPost();
    blogPost.setId(id);
    blogPost.setTitle(title);
    blogPost.setImageUrl(imageUrl);
    return blogPost;
  }

  /** The list-image map the widget builds for {@code view}, so tests read the same resolution. */
  private static Map<Long, String> urls(String view, BlogPost... posts) {
    Map<Long, String> map = new java.util.LinkedHashMap<>();
    for (BlogPost p : posts) {
      String url = BlogPostListWidget.listImageUrlFor(p, view);
      if (url != null) {
        map.put(p.getId(), url);
      }
    }
    return map;
  }

  private static Image image(long id, String altText) {
    Image image = new Image();
    image.setId(id);
    image.setAltText(altText);
    return image;
  }

  @Test
  void bannerAltTextPrefersTheLibrarysStoredDescription() {
    BlogPost blogPost = post(7L, "SimIS Wins the Workforce Innovation Award",
        "/assets/img/20161006120000-42/award.jpg");
    Map<Long, String> altText = BlogPostListWidget.resolveImageAltText(List.of(blogPost), urls("default", blogPost),
        Map.of(42L, image(42L, "Dr. Garcia accepting the award at a podium")));
    Assertions.assertEquals("Dr. Garcia accepting the award at a podium", altText.get(7L));
  }

  @Test
  void bannerAltTextFallsBackToThePostTitle() {
    BlogPost blogPost = post(7L, "SimIS Wins the Workforce Innovation Award",
        "/assets/img/20161006120000-42/award.jpg");
    // No stored alt text: the title is at least true and distinct per card. An empty alt is not an
    // option -- the banner sits inside the post link, which would leave that link unnamed.
    Map<Long, String> altText = BlogPostListWidget.resolveImageAltText(List.of(blogPost), urls("default", blogPost),
        Map.of(42L, image(42L, "   ")));
    Assertions.assertEquals("SimIS Wins the Workforce Innovation Award", altText.get(7L));
  }

  @Test
  void bannerAltTextFallsBackWhenTheImageRecordIsMissingEntirely() {
    BlogPost blogPost = post(7L, "A Post", "/assets/img/20161006120000-42/award.jpg");
    Assertions.assertEquals("A Post",
        BlogPostListWidget.resolveImageAltText(List.of(blogPost), urls("default", blogPost), Map.of()).get(7L));
  }

  @Test
  void postsWithNoBannerImageGetNoEntryAtAll() {
    Assertions.assertTrue(
        BlogPostListWidget.resolveImageAltText(List.of(post(7L, "A Post", null)),
            urls("default", post(7L, "A Post", null)), Map.of()).isEmpty());
  }

  private static BlogPost postWithBoth(long id, String banner, String share) {
    BlogPost blogPost = post(id, "SimIS Awarded Position on MDA's SHIELD IDIQ Contract", banner);
    blogPost.setShareImageUrl(share);
    return blogPost;
  }

  @Test
  void aCardViewShowsTheBannerEvenWhenAShareCardIsSet() {
    // The reported case: an editor set a square banner and a wide share card, and the news
    // listing -- a showcase view -- showed the wide one. The share card is for link previews and
    // the compact thumbnail; a card view shows the banner the editor authored for it.
    BlogPost blogPost = postWithBoth(89L,
        "/assets/img/20260910190704-338/shield-square.png",
        "/assets/img/20260910190511-337/shield-wide.png");
    for (String view : List.of("default", "showcase", "cards", "featured", "masonry")) {
      Assertions.assertEquals("/assets/img/20260910190704-338/shield-square.png",
          BlogPostListWidget.listImageUrlFor(blogPost, view),
          "the " + view + " view must show the banner, not the share card");
    }
  }

  @Test
  void theOverviewShowsTheShareCardWhenOneIsSet() {
    // The one view the share card is for: a fixed 1.91:1 thumbnail, which is the shape a share
    // card is authored in.
    BlogPost blogPost = postWithBoth(89L,
        "/assets/img/20260910190704-338/shield-square.png",
        "/assets/img/20260910190511-337/shield-wide.png");
    Assertions.assertEquals("/assets/img/20260910190511-337/shield-wide.png",
        BlogPostListWidget.listImageUrlFor(blogPost, "overview"));
  }

  @Test
  void theOverviewFallsBackToTheBannerWithoutAShareCard() {
    // A post that predates the field must render exactly as it did before it existed.
    BlogPost blogPost = post(7L, "A Post", "/assets/img/20161006120000-42/banner.jpg");
    Assertions.assertEquals("/assets/img/20161006120000-42/banner.jpg",
        BlogPostListWidget.listImageUrlFor(blogPost, "overview"));
  }

  @Test
  void altTextDescribesTheImageTheViewActuallyShows() {
    // The reason every value derives from one resolved map: the banner and the share card are
    // different pictures with different stored descriptions. Resolving alt text independently of
    // the image on screen is how a screen reader ends up describing the wrong one.
    BlogPost blogPost = postWithBoth(89L,
        "/assets/img/20260910190704-338/shield-square.png",
        "/assets/img/20260910190511-337/shield-wide.png");
    Map<Long, Image> images = Map.of(
        338L, image(338L, "Square SHIELD announcement graphic"),
        337L, image(337L, "Wide SHIELD announcement graphic"));
    Assertions.assertEquals("Square SHIELD announcement graphic",
        BlogPostListWidget.resolveImageAltText(List.of(blogPost), urls("showcase", blogPost), images).get(89L));
    Assertions.assertEquals("Wide SHIELD announcement graphic",
        BlogPostListWidget.resolveImageAltText(List.of(blogPost), urls("overview", blogPost), images).get(89L));
  }

  @Test
  void executeSelectsTheShowcaseJspAndStillResolvesTheCardCounts() {
    // The showcase view shares the cards view's preference handling rather than duplicating it, so
    // this pins both halves: that "showcase" reaches its own template, and that it still gets the
    // responsive card counts. If someone later splits the branch and forgets the counts, the JSP
    // renders "small-up-" with nothing after it and the grid silently collapses to one column.
    preferences.put("blogUniqueId", "news");
    preferences.put("view", "showcase");
    preferences.put("smallCardCount", "1");
    preferences.put("largeCardCount", "3");

    Blog blog = new Blog();
    blog.setId(1L);
    blog.setUniqueId("news");
    blog.setName("News");
    blog.setEnabled(true);

    List<BlogPost> blogPostList = new ArrayList<>();
    BlogPost blogPost = new BlogPost();
    blogPost.setId(1L);
    blogPost.setBlogId(blog.getId());
    blogPost.setUniqueId("blog-post-1");
    blogPost.setTitle("This is blog post 1");
    blogPostList.add(blogPost);

    try (MockedStatic<LoadBlogCommand> loadBlogCommandMockedStatic = mockStatic(LoadBlogCommand.class);
        MockedStatic<BlogPostRepository> blogPostRepositoryMockedStatic = mockStatic(BlogPostRepository.class)) {
      loadBlogCommandMockedStatic.when(() -> LoadBlogCommand.loadBlogByUniqueId(eq("news"))).thenReturn(blog);
      blogPostRepositoryMockedStatic.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(blogPostList);

      widgetContext = new BlogPostListWidget().execute(widgetContext);
    }

    Assertions.assertNotNull(widgetContext);
    Assertions.assertTrue(widgetContext.hasJsp());
    Assertions.assertEquals(SHOWCASE_JSP, widgetContext.getJsp());
    Assertions.assertEquals("1", widgetContext.getRequest().getAttribute("smallCardCount"));
    Assertions.assertEquals("1", widgetContext.getRequest().getAttribute("mediumCardCount"),
        "medium falls back to small when left unset");
    Assertions.assertEquals("3", widgetContext.getRequest().getAttribute("largeCardCount"));
  }
}
