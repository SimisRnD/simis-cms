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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;
import com.simisinc.platform.rest.controller.ServiceResponseCommand;

/**
 * Returns a single published blog post (issue #412).
 * <p>
 * Registered as {@code post/{blogUniqueId}/{postUniqueId}}, not {@code post/{postUniqueId}} as
 * originally specced -- {@link BlogPostRepository#findByUniqueId} is keyed by
 * {@code (blogId, postUniqueId)}, not a globally-unique post identifier, so the blog must be
 * resolved first regardless.
 * </p>
 *
 * @author SimIS Inc.
 */
public class BlogPostService {

  private static Log LOG = LogFactory.getLog(BlogPostService.class);

  // GET /post/{blogUniqueId}/{postUniqueId}
  public ServiceResponse get(ServiceContext context) {

    String blogUniqueId = context.getPathParam();
    String postUniqueId = context.getPathParam2();

    Blog blog = BlogRepository.findByUniqueId(blogUniqueId);
    if (blog == null || !blog.getEnabled()) {
      ServiceResponse response = new ServiceResponse(404);
      response.getError().put("title", "Blog was not found");
      return response;
    }

    BlogPost blogPost = BlogPostRepository.findByUniqueId(blog.getId(), postUniqueId);
    if (blogPost == null || !isVisible(blogPost, context)) {
      ServiceResponse response = new ServiceResponse(404);
      response.getError().put("title", "Post was not found");
      return response;
    }

    BlogPostResponse blogPostResponse = new BlogPostResponse(blogPost);

    ServiceResponse response = new ServiceResponse(200);
    ServiceResponseCommand.addMeta(response, "post");
    response.setData(blogPostResponse);
    return response;
  }

  // Mirrors BlogPostWidget.retrieveValidatedBlogPostFromUrl exactly -- the real single-post live
  // page only checks `published != null` (with an admin/content-manager bypass for unpublished
  // drafts); it does NOT enforce startDate/endDate the way the list-page widgets do. An earlier
  // version of this method incorrectly borrowed the list-page's stricter date-window filter,
  // which 404'd posts the live site actually serves (e.g. a live post whose endDate has passed --
  // a common way to pull it out of list/recent-posts widgets while keeping the permalink live).
  private static boolean isVisible(BlogPost blogPost, ServiceContext context) {
    if (blogPost.getPublished() != null) {
      return true;
    }
    return context.hasRole("admin") || context.hasRole("content-manager");
  }
}
