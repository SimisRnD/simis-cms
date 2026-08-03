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

package com.simisinc.platform.rest.services.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;

/**
 * Verifies {@link BlogPostService} matches {@code BlogPostWidget.retrieveValidatedBlogPostFromUrl}
 * exactly -- only {@code published != null} (with an admin/content-manager bypass), NOT the
 * list-page's stricter startDate/endDate window (issue #412).
 *
 * @author SimIS Inc.
 */
class BlogPostServiceTest {

  private ServiceContext contextFor(String blogUniqueId, String postUniqueId) {
    ServiceContext context = new ServiceContext();
    context.setPathParam(blogUniqueId);
    context.setPathParam2(postUniqueId);
    return context;
  }

  private ServiceContext adminContextFor(String blogUniqueId, String postUniqueId) {
    ServiceContext context = contextFor(blogUniqueId, postUniqueId);
    User admin = new User();
    admin.setId(1L);
    List<Role> roles = new ArrayList<>();
    roles.add(new Role("admin", "admin"));
    admin.setRoleList(roles);
    context.setUser(admin);
    return context;
  }

  private Blog enabledBlog(long id) {
    Blog blog = new Blog();
    blog.setId(id);
    blog.setEnabled(true);
    return blog;
  }

  private BlogPost publishedPost(String uniqueId) {
    BlogPost post = new BlogPost();
    post.setUniqueId(uniqueId);
    post.setTitle("A Post");
    post.setPublished(new Timestamp(System.currentTimeMillis() - 100_000));
    return post;
  }

  @Test
  void getReturns404WhenTheBlogDoesNotExist() {
    ServiceContext context = contextFor("missing-blog", "some-post");

    try (MockedStatic<BlogRepository> repo = mockStatic(BlogRepository.class)) {
      repo.when(() -> BlogRepository.findByUniqueId("missing-blog")).thenReturn(null);

      ServiceResponse response = new BlogPostService().get(context);

      assertEquals(404, response.getStatus());
    }
  }

  @Test
  void getReturns404WhenTheBlogIsDisabled() {
    ServiceContext context = contextFor("disabled-blog", "some-post");
    Blog disabled = enabledBlog(9L);
    disabled.setEnabled(false);

    try (MockedStatic<BlogRepository> repo = mockStatic(BlogRepository.class)) {
      repo.when(() -> BlogRepository.findByUniqueId("disabled-blog")).thenReturn(disabled);

      ServiceResponse response = new BlogPostService().get(context);

      assertEquals(404, response.getStatus());
    }
  }

  @Test
  void getReturns404WhenThePostDoesNotExistWithinThatBlog() {
    ServiceContext context = contextFor("news", "missing-post");
    Blog blog = enabledBlog(9L);

    try (MockedStatic<BlogRepository> blogRepo = mockStatic(BlogRepository.class);
        MockedStatic<BlogPostRepository> postRepo = mockStatic(BlogPostRepository.class)) {
      blogRepo.when(() -> BlogRepository.findByUniqueId("news")).thenReturn(blog);
      postRepo.when(() -> BlogPostRepository.findByUniqueId(9L, "missing-post")).thenReturn(null);

      ServiceResponse response = new BlogPostService().get(context);

      assertEquals(404, response.getStatus());
    }
  }

  @Test
  void getReturns404ForAnUnpublishedPost() {
    ServiceContext context = contextFor("news", "draft-post");
    Blog blog = enabledBlog(9L);
    BlogPost unpublished = new BlogPost();
    unpublished.setUniqueId("draft-post");
    unpublished.setPublished(null);

    try (MockedStatic<BlogRepository> blogRepo = mockStatic(BlogRepository.class);
        MockedStatic<BlogPostRepository> postRepo = mockStatic(BlogPostRepository.class)) {
      blogRepo.when(() -> BlogRepository.findByUniqueId("news")).thenReturn(blog);
      postRepo.when(() -> BlogPostRepository.findByUniqueId(9L, "draft-post")).thenReturn(unpublished);

      ServiceResponse response = new BlogPostService().get(context);

      assertEquals(404, response.getStatus());
    }
  }

  @Test
  void getReturns200ForAnUnpublishedPostWhenCallerIsAnAdmin() {
    // Mirrors BlogPostWidget.retrieveValidatedBlogPostFromUrl's admin/content-manager bypass.
    ServiceContext context = adminContextFor("news", "draft-post");
    Blog blog = enabledBlog(9L);
    BlogPost unpublished = new BlogPost();
    unpublished.setUniqueId("draft-post");
    unpublished.setPublished(null);

    try (MockedStatic<BlogRepository> blogRepo = mockStatic(BlogRepository.class);
        MockedStatic<BlogPostRepository> postRepo = mockStatic(BlogPostRepository.class)) {
      blogRepo.when(() -> BlogRepository.findByUniqueId("news")).thenReturn(blog);
      postRepo.when(() -> BlogPostRepository.findByUniqueId(9L, "draft-post")).thenReturn(unpublished);

      ServiceResponse response = new BlogPostService().get(context);

      assertEquals(200, response.getStatus());
    }
  }

  @Test
  void getReturns200ForAPublishedPostWhoseEndDateHasPassed() {
    // The real single-post live page (BlogPostWidget) never enforces endDate -- only the list
    // widgets do. A published post pulled out of listings via a past endDate must still resolve.
    ServiceContext context = contextFor("news", "expired-from-listings");
    Blog blog = enabledBlog(9L);
    BlogPost post = publishedPost("expired-from-listings");
    post.setEndDate(new Timestamp(System.currentTimeMillis() - 1000));

    try (MockedStatic<BlogRepository> blogRepo = mockStatic(BlogRepository.class);
        MockedStatic<BlogPostRepository> postRepo = mockStatic(BlogPostRepository.class)) {
      blogRepo.when(() -> BlogRepository.findByUniqueId("news")).thenReturn(blog);
      postRepo.when(() -> BlogPostRepository.findByUniqueId(9L, "expired-from-listings")).thenReturn(post);

      ServiceResponse response = new BlogPostService().get(context);

      assertEquals(200, response.getStatus());
      BlogPostResponse data = (BlogPostResponse) response.getData();
      assertEquals("expired-from-listings", data.getUniqueId());
    }
  }

  @Test
  void getReturns200ForAPublishedCurrentPost() {
    ServiceContext context = contextFor("news", "hello-world");
    Blog blog = enabledBlog(9L);
    BlogPost post = publishedPost("hello-world");

    try (MockedStatic<BlogRepository> blogRepo = mockStatic(BlogRepository.class);
        MockedStatic<BlogPostRepository> postRepo = mockStatic(BlogPostRepository.class)) {
      blogRepo.when(() -> BlogRepository.findByUniqueId("news")).thenReturn(blog);
      postRepo.when(() -> BlogPostRepository.findByUniqueId(9L, "hello-world")).thenReturn(post);

      ServiceResponse response = new BlogPostService().get(context);

      assertEquals(200, response.getStatus());
      BlogPostResponse data = (BlogPostResponse) response.getData();
      assertEquals("hello-world", data.getUniqueId());
      assertEquals("A Post", data.getTitle());
    }
  }
}
