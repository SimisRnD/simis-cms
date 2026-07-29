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
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.cms.LoadBlogCommand;
import com.simisinc.platform.application.cms.LoadBlogPostCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import java.sql.Timestamp;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

/**
 * deletePostViaPostCallsRepositoryAndAudits guards a real regression: the blog post's delete button submits
 * via a real HTTP POST (issue #358 moved state-changing admin actions off GET query strings), so
 * WebContainerContext routes the request to post(), not action() below -- action()'s "deletePost" dispatch
 * (and its admin/content-manager role check) was correct but unreachable, and this widget had no post()
 * override at all, so the request silently no-opped (redirect back to the same page, no error, no repository
 * call). This test calls post() directly, the same method a real request now reaches, so it fails if that
 * dispatch gap reopens.
 */
class BlogPostWidgetTest extends WidgetBase {

  @Test
  void deletePostViaPostCallsRepositoryAndAudits() throws Exception {
    setRoles(widgetContext, CONTENT_MANAGER);

    Blog blog = new Blog();
    blog.setId(2L);
    blog.setUniqueId("news");

    BlogPost blogPost = new BlogPost();
    blogPost.setId(9L);
    blogPost.setBlogId(2L);
    blogPost.setTitle("Launch Announcement");

    addQueryParameter(widgetContext, "blogPostId", "9");
    addQueryParameter(widgetContext, "action", "deletePost");

    try (MockedStatic<LoadBlogPostCommand> loadBlogPost = mockStatic(LoadBlogPostCommand.class);
        MockedStatic<LoadBlogCommand> loadBlog = mockStatic(LoadBlogCommand.class);
        MockedStatic<BlogPostRepository> blogPostRepository = mockStatic(BlogPostRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadBlogPost.when(() -> LoadBlogPostCommand.loadBlogPostById(anyLong())).thenReturn(blogPost);
      loadBlog.when(() -> LoadBlogCommand.loadBlogById(anyLong())).thenReturn(blog);
      blogPostRepository.when(() -> BlogPostRepository.remove(blogPost)).thenReturn(true);

      WidgetContext result = new BlogPostWidget().post(widgetContext);

      blogPostRepository.verify(() -> BlogPostRepository.remove(blogPost), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONTENT), eq("content.delete"),
          eq(AuditEventCommand.SUCCESS), eq("blog_post"), eq("9"), eq("Launch Announcement"), any()), times(1));
      Assertions.assertEquals("Post was deleted", result.getSuccessMessage());
    }
  }

  @Test
  void executeSetsArticleSchemaFieldsForAPublishedPost() throws Exception {
    Mockito.when(request.getRequestURI()).thenReturn("/news/launch-announcement");
    preferences.put("blogUniqueId", "news");

    Blog blog = new Blog();
    blog.setId(2L);
    blog.setUniqueId("news");
    blog.setName("News");
    blog.setEnabled(true);

    BlogPost blogPost = new BlogPost();
    blogPost.setId(9L);
    blogPost.setBlogId(2L);
    blogPost.setTitle("Launch Announcement");
    blogPost.setCreatedBy(7L);
    blogPost.setPublished(Timestamp.valueOf("2026-07-01 09:00:00"));
    blogPost.setModified(Timestamp.valueOf("2026-07-15 14:30:00"));

    User author = new User();
    author.setId(7L);
    author.setNickname("Jane Author");

    try (MockedStatic<LoadBlogCommand> loadBlog = mockStatic(LoadBlogCommand.class);
        MockedStatic<LoadBlogPostCommand> loadBlogPost = mockStatic(LoadBlogPostCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class)) {
      loadBlog.when(() -> LoadBlogCommand.loadBlogByUniqueId("news")).thenReturn(blog);
      loadBlogPost.when(() -> LoadBlogPostCommand.loadBlogPostByUniqueId(2L, "launch-announcement")).thenReturn(blogPost);
      loadUser.when(() -> LoadUserCommand.loadUser(7L)).thenReturn(author);

      new BlogPostWidget().execute(widgetContext);
    }

    Assertions.assertEquals("Launch Announcement", widgetContext.getArticleHeadline());
    Assertions.assertEquals(blogPost.getPublished(), widgetContext.getArticlePublishedDate());
    Assertions.assertEquals(blogPost.getModified(), widgetContext.getArticleModifiedDate());
    Assertions.assertEquals("Jane Author", widgetContext.getArticleAuthorName());
  }

  @Test
  void executeDoesNotSetArticleSchemaFieldsForAnUnpublishedPost() throws Exception {
    setRoles(widgetContext, CONTENT_MANAGER);
    Mockito.when(request.getRequestURI()).thenReturn("/news/draft-post");
    preferences.put("blogUniqueId", "news");

    Blog blog = new Blog();
    blog.setId(2L);
    blog.setUniqueId("news");
    blog.setName("News");
    blog.setEnabled(true);

    BlogPost blogPost = new BlogPost();
    blogPost.setId(10L);
    blogPost.setBlogId(2L);
    blogPost.setTitle("Draft Post");
    blogPost.setCreatedBy(7L);
    // published is intentionally left null -- this post is still a draft

    try (MockedStatic<LoadBlogCommand> loadBlog = mockStatic(LoadBlogCommand.class);
        MockedStatic<LoadBlogPostCommand> loadBlogPost = mockStatic(LoadBlogPostCommand.class)) {
      loadBlog.when(() -> LoadBlogCommand.loadBlogByUniqueId("news")).thenReturn(blog);
      loadBlogPost.when(() -> LoadBlogPostCommand.loadBlogPostByUniqueId(2L, "draft-post")).thenReturn(blogPost);

      new BlogPostWidget().execute(widgetContext);
    }

    Assertions.assertNull(widgetContext.getArticleHeadline());
    Assertions.assertNull(widgetContext.getArticlePublishedDate());
    Assertions.assertNull(widgetContext.getArticleModifiedDate());
    Assertions.assertNull(widgetContext.getArticleAuthorName());
  }
}
