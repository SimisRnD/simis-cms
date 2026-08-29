/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Atom feed for blog posts (issue #1182).
 */
class FeedServletTest {

  private static Map<String, String> siteProperties(boolean online, boolean feedEnabled) {
    Map<String, String> properties = new HashMap<>();
    properties.put("site.url", "https://example.org");
    properties.put("site.name", "Example");
    properties.put("site.online", String.valueOf(online));
    properties.put("site.feed.xml", String.valueOf(feedEnabled));
    return properties;
  }

  private static Blog blog(long id, String uniqueId, boolean enabled) {
    Blog blog = new Blog();
    blog.setId(id);
    blog.setUniqueId(uniqueId);
    blog.setName("News");
    blog.setEnabled(enabled);
    return blog;
  }

  private static BlogPost post(long blogId, String uniqueId, String title) {
    BlogPost post = new BlogPost();
    post.setBlogId(blogId);
    post.setUniqueId(uniqueId);
    post.setTitle(title);
    post.setModified(Timestamp.valueOf("2026-03-15 12:30:00"));
    post.setStartDate(Timestamp.valueOf("2026-03-14 09:00:00"));
    return post;
  }

  /** Runs doGet with the repositories mocked; returns the response body. */
  private String runDoGet(Map<String, String> properties, String pathInfo, List<BlogPost> posts,
      Blog blogByUniqueId, Blog blogById, HttpServletResponse response,
      ArgumentCaptor<BlogPostSpecification> specCaptor) throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getPathInfo()).thenReturn(pathInfo);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<BlogPostRepository> blogPostRepository = mockStatic(BlogPostRepository.class);
        MockedStatic<BlogRepository> blogRepository = mockStatic(BlogRepository.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(properties);
      blogRepository.when(() -> BlogRepository.findByUniqueId(any())).thenReturn(blogByUniqueId);
      blogRepository.when(() -> BlogRepository.findById(org.mockito.ArgumentMatchers.anyLong()))
          .thenReturn(blogById);
      if (specCaptor != null) {
        blogPostRepository.when(() -> BlogPostRepository.findAll(specCaptor.capture(), any())).thenReturn(posts);
      } else {
        blogPostRepository.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(posts);
      }

      new FeedServlet().doGet(request, response);
    }

    return body.toString();
  }

  private String runDoGetCapturingConstraints(List<BlogPost> posts, Blog blog,
      ArgumentCaptor<DataConstraints> constraintsCaptor) throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getPathInfo()).thenReturn(null);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<BlogPostRepository> blogPostRepository = mockStatic(BlogPostRepository.class);
        MockedStatic<BlogRepository> blogRepository = mockStatic(BlogRepository.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(siteProperties(true, true));
      blogRepository.when(() -> BlogRepository.findByUniqueId(any())).thenReturn(blog);
      blogRepository.when(() -> BlogRepository.findById(org.mockito.ArgumentMatchers.anyLong())).thenReturn(blog);
      blogPostRepository.when(() -> BlogPostRepository.findAll(any(), constraintsCaptor.capture()))
          .thenReturn(posts);
      new FeedServlet().doGet(request, response);
    }
    return body.toString();
  }

  private String runSiteWideFeed(Map<String, String> properties, List<BlogPost> posts, Blog blog)
      throws Exception {
    return runDoGet(properties, null, posts, blog, blog, mock(HttpServletResponse.class), null);
  }

  @Test
  void doGet404sWhenTheSiteIsNotOnline() throws Exception {
    // Same gate SitemapServlet applies: a site an admin has not taken online should not syndicate
    HttpServletResponse response = mock(HttpServletResponse.class);
    runDoGet(siteProperties(false, true), null, new ArrayList<>(), null, null, response, null);
    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  void doGet404sWhenTheFeedPropertyIsOff() throws Exception {
    // site.feed.xml defaults to false, so syndication stays opt-in
    HttpServletResponse response = mock(HttpServletResponse.class);
    runDoGet(siteProperties(true, false), null, new ArrayList<>(), null, null, response, null);
    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  void doGet404sWhenSiteUrlIsBlank() throws Exception {
    Map<String, String> properties = siteProperties(true, true);
    properties.remove("site.url");
    HttpServletResponse response = mock(HttpServletResponse.class);
    runDoGet(properties, null, new ArrayList<>(), null, null, response, null);
    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  void doGetEmitsAnAtomDocumentWithAnEntryPerPost() throws Exception {
    Blog blog = blog(1L, "news", true);
    String xml = runSiteWideFeed(siteProperties(true, true), List.of(post(1L, "first-post", "First Post")), blog);

    assertTrue(xml.contains("<feed xmlns=\"http://www.w3.org/2005/Atom\">"), xml);
    assertTrue(xml.contains("<title>First Post</title>"), xml);
    assertTrue(xml.contains("https://example.org/news/first-post"), xml);
    assertTrue(xml.contains("<link rel=\"self\" href=\"https://example.org/feed.xml\"/>"), xml);
  }

  @Test
  void doGetQueriesPostsWithTheSameVisibilityFiltersAsTheSitemap() throws Exception {
    // A feed that syndicates a post the site will not show is a content leak, not a convenience
    ArgumentCaptor<BlogPostSpecification> specCaptor = ArgumentCaptor.forClass(BlogPostSpecification.class);
    Blog blog = blog(1L, "news", true);

    runDoGet(siteProperties(true, true), null, List.of(post(1L, "first-post", "First Post")), blog, blog,
        mock(HttpServletResponse.class), specCaptor);

    BlogPostSpecification spec = specCaptor.getValue();
    assertEquals(DataConstants.TRUE, spec.getPublishedOnly(), "publishedOnly must be set");
    assertEquals(DataConstants.FALSE, spec.getArchivedOnly(), "archivedOnly must be false");
  }

  @Test
  void doGetLeavesOutPostsFlaggedToSkipTheFeed() throws Exception {
    // #1419: a post can opt out of syndication while staying published and searchable. Archiving
    // would hide it from the site too, which is a different editorial decision.
    ArgumentCaptor<BlogPostSpecification> specCaptor = ArgumentCaptor.forClass(BlogPostSpecification.class);
    Blog blog = blog(1L, "news", true);

    runDoGet(siteProperties(true, true), null, List.of(post(1L, "first-post", "First Post")), blog, blog,
        mock(HttpServletResponse.class), specCaptor);

    assertEquals(DataConstants.FALSE, specCaptor.getValue().getExcludedFromFeed(),
        "the feed must ask for posts that have not opted out");
  }

  @Test
  void doGetSkipsAPostWhoseBlogIsDisabled() throws Exception {
    // A disabled blog's posts are not public even when the post itself is published
    Blog disabled = blog(1L, "news", false);
    String xml = runSiteWideFeed(siteProperties(true, true), List.of(post(1L, "first-post", "First Post")), disabled);

    assertFalse(xml.contains("first-post"), xml);
  }

  @Test
  void doGet404sForAPerBlogFeedNamingAnUnknownBlog() throws Exception {
    // Quietly serving the site-wide feed instead would be worse than saying no
    HttpServletResponse response = mock(HttpServletResponse.class);
    runDoGet(siteProperties(true, true), "/missing.xml", new ArrayList<>(), null, null, response, null);
    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  void doGet404sForAPerBlogFeedNamingADisabledBlog() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    runDoGet(siteProperties(true, true), "/news.xml", new ArrayList<>(), blog(1L, "news", false), null,
        response, null);
    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  void doGetPerBlogFeedPointsSelfAtThePerBlogUrl() throws Exception {
    Blog blog = blog(1L, "news", true);
    String xml = runDoGet(siteProperties(true, true), "/news.xml",
        List.of(post(1L, "first-post", "First Post")), blog, blog, mock(HttpServletResponse.class), null);

    assertTrue(xml.contains("<link rel=\"self\" href=\"https://example.org/feed/news.xml\"/>"), xml);
    assertTrue(xml.contains("<title>Example - News</title>"), xml);
  }

  @Test
  void blogUniqueIdFromTolerantOfSuffixAndSlashes() {
    assertEquals("news", FeedServlet.blogUniqueIdFrom("/news.xml"));
    assertEquals("news", FeedServlet.blogUniqueIdFrom("/news"));
    assertEquals("news", FeedServlet.blogUniqueIdFrom("/news/"));
    assertEquals("news", FeedServlet.blogUniqueIdFrom("/news.XML"));
  }

  @Test
  void blogUniqueIdFromRejectsPathsThatNameNoSingleBlog() {
    assertNull(FeedServlet.blogUniqueIdFrom(null));
    assertNull(FeedServlet.blogUniqueIdFrom("/"));
    assertNull(FeedServlet.blogUniqueIdFrom(""));
    // A nested path is not a blog id
    assertNull(FeedServlet.blogUniqueIdFrom("/news/first-post"));
  }

  @Test
  void escapeXmlEscapesEveryXmlSignificantCharacter() {
    assertEquals("&amp;&lt;&gt;&quot;&apos;", FeedServlet.escapeXml("&<>\"'"));
    assertEquals("", FeedServlet.escapeXml(null));
  }

  @Test
  void summaryForPrefersTheCuratedSummary() {
    BlogPost post = post(1L, "p", "P");
    post.setSummary("A curated summary");
    post.setBody("<p>Body text</p>");
    assertEquals("A curated summary", FeedServlet.summaryFor(post));
  }

  @Test
  void summaryForFallsBackToTheBodyWithMarkupStripped() {
    // Atom <summary> is declared as text here, so raw HTML would be escaped into unreadable noise
    BlogPost post = post(1L, "p", "P");
    post.setBody("<p>Meet us at <strong>booth 412</strong></p>");
    assertEquals("Meet us at booth 412", FeedServlet.summaryFor(post));
  }

  @Test
  void summaryForReturnsNullWithNeitherSummaryNorBody() {
    assertNull(FeedServlet.summaryFor(post(1L, "p", "P")));
  }

  @Test
  void formatDateFallsBackToTheEpochRatherThanEmittingAnEmptyElement() {
    // Atom requires <updated>; an empty element would make the document invalid
    assertEquals("1970-01-01T00:00:00Z", FeedServlet.formatDate(null));
  }

  // --- curated link posts (#1420) ---------------------------------------------------------------

  @Test
  void doGetPointsRelAlternateAtTheSourceButKeepsThePermalinkAsTheId() throws Exception {
    // A curation feed's entry link has to be the article; a stub page makes the feed useless.
    // <id> must NOT follow it: Atom requires a permanent, unique identifier, and two posts citing
    // the same article would collide.
    BlogPost post = post(1L, "first-post", "First Post");
    post.setSourceUrl("https://example.org/some-article");

    String body = runSiteWideFeed(siteProperties(true, true), List.of(post), blog(1L, "news", true));

    assertTrue(body.contains("<link rel=\"alternate\" href=\"https://example.org/some-article\"/>"),
        "rel=alternate must be the source article: " + body);
    assertTrue(body.contains("<id>") && body.contains("/news/first-post</id>"),
        "the id must stay the post's own permalink: " + body);
    assertTrue(body.contains("<link rel=\"related\"") && body.contains("/news/first-post\"/>"),
        "the commentary page must stay reachable as rel=related: " + body);
  }

  @Test
  void doGetKeepsTheInternalLinkWhenNoSourceUrlIsSet() throws Exception {
    String body = runSiteWideFeed(siteProperties(true, true),
        List.of(post(1L, "first-post", "First Post")), blog(1L, "news", true));

    assertTrue(body.contains("<link rel=\"alternate\"") && body.contains("/news/first-post"),
        "an ordinary post still links to its own page: " + body);
    assertFalse(body.contains("rel=\"related\""), "no related link without a source url: " + body);
  }

  @Test
  void doGetRefusesAnUnsafeSourceUrlRatherThanEmittingIt() throws Exception {
    // Defense in depth: save-time validation rejects non-http(s), but a value arriving another way
    // must not reach the feed either.
    BlogPost post = post(1L, "first-post", "First Post");
    post.setSourceUrl("javascript:alert(1)");

    String body = runSiteWideFeed(siteProperties(true, true), List.of(post), blog(1L, "news", true));

    assertFalse(body.contains("javascript:"), "an active-scheme url must never be emitted: " + body);
    assertTrue(body.contains("/news/first-post"), "it falls back to the permalink: " + body);
  }

  // --- ordering ------------------------------------------------------------------------------
  // The feed caps at MAX_ENTRIES. Without an ORDER BY the database returned rows in arbitrary
  // order, so on a bulk-imported site the cap kept the oldest posts and nothing recent was ever
  // syndicated.

  @Test
  void doGetOrdersPostsNewestFirstSoTheEntryCapKeepsRecentOnes() throws Exception {
    ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);
    Blog blog = blog(1L, "news", true);

    runDoGetCapturingConstraints(List.of(post(1L, "first-post", "First Post")), blog, constraintsCaptor);

    DataConstraints constraints = constraintsCaptor.getValue();
    assertNotNull(constraints, "the feed must ask for an explicit order, not whatever the table returns");

    // Asserted on columnsToSortBy, not defaultColumnToSortBy. That distinction is the whole bug:
    // this test used to check the default setter had been called, which it had -- and the sort
    // still never reached the SQL, because BlogPostRepository#findAll overwrites that field with
    // "post_id" one line after receiving the constraints. Checking the call rather than the effect
    // is why the feed stayed in insertion order through a fix, a review and a green suite.
    String[] sortColumns = constraints.getColumnsToSortBy();
    assertNotNull(sortColumns, "the feed must set the application-facing sort, which the repository cannot overwrite");
    String sort = String.join(", ", sortColumns);
    assertTrue(sort.toUpperCase().contains("DESC"), "newest first, got: " + sort);
    // <published> falls back from startDate to published, so the ordering must key on the same value
    assertTrue(sort.contains("start_date") && sort.contains("published"),
        "ordering must match the date the feed actually publishes, got: " + sort);

    // And prove the repository cannot displace it. This is exactly what findAll does next.
    constraints.setDefaultColumnToSortBy("post_id");
    assertNotNull(constraints.getColumnsToSortBy(),
        "the repository's own default must not displace the feed's order");
    assertEquals(sort, String.join(", ", constraints.getColumnsToSortBy()),
        "the feed's order must survive the repository setting its default");
  }

  @Test
  void doGetDoesNotLimitTheQuerySoDisabledBlogPostsCannotUnderFillTheFeed() throws Exception {
    // The MAX_ENTRIES cap is applied after posts on a disabled blog are skipped; a SQL LIMIT at
    // the same number would quietly return fewer entries than the feed is meant to carry.
    ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);
    Blog blog = blog(1L, "news", true);

    runDoGetCapturingConstraints(List.of(post(1L, "first-post", "First Post")), blog, constraintsCaptor);

    assertTrue(constraintsCaptor.getValue().getPageSize() <= 0,
        "the feed must not apply a SQL row limit");
  }
}
