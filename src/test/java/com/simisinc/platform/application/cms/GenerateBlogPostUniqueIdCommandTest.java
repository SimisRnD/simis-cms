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

package com.simisinc.platform.application.cms;

import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.*;

/**
 * @author matt rajkowski
 * @created 5/3/2022 7:00 PM
 */
class GenerateBlogPostUniqueIdCommandTest {

  @Test
  void generateUniqueIdForNewBlogPost() {
    try (MockedStatic<BlogPostRepository> blogPostRepository = mockStatic(BlogPostRepository.class)) {
      blogPostRepository.when(() -> BlogPostRepository.findByUniqueId(anyLong(), anyString())).thenReturn(null);

      BlogPost blogPost = new BlogPost();
      blogPost.setTitle("SimIS CMS Has Been Updated With Tests");
      String uniqueId = GenerateBlogPostUniqueIdCommand.generateUniqueId(null, blogPost);
      Assertions.assertEquals("simis-cms-has-been-updated-with-tests", uniqueId);
    }
  }

  @Test
  void generateUniqueIdForUpdatedBlogPost() {
    try (MockedStatic<BlogPostRepository> blogPostRepository = mockStatic(BlogPostRepository.class)) {
      blogPostRepository.when(() -> BlogPostRepository.findByUniqueId(anyLong(), anyString())).thenReturn(null);

      BlogPost previousBlogPost = new BlogPost();
      previousBlogPost.setTitle("SimIS CMS Has Just Been Updated With Tests");
      BlogPost blogPost = new BlogPost();
      blogPost.setTitle("SimIS CMS Has Been Updated With Tests");
      String uniqueId = GenerateBlogPostUniqueIdCommand.generateUniqueId(previousBlogPost, blogPost);
      Assertions.assertEquals("simis-cms-has-been-updated-with-tests", uniqueId);
    }
  }

  @Test
  void anExistingPostKeepsItsUniqueIdWhenRenamed() {
    // This is the bug: renaming the title used to regenerate the slug from the new title,
    // silently changing the post's URL and breaking every link that pointed at the old one.
    BlogPost previousBlogPost = new BlogPost();
    previousBlogPost.setUniqueId("monthly-update");
    previousBlogPost.setTitle("Monthly Update");

    BlogPost renamed = new BlogPost();
    renamed.setTitle("Our Monthly Product Update for August");

    String uniqueId = GenerateBlogPostUniqueIdCommand.generateUniqueId(previousBlogPost, renamed);
    Assertions.assertEquals("monthly-update", uniqueId,
        "an existing post's URL must not change when only its title changes");
  }

  @Test
  void aNewPostWithEntityEncodedTitleGetsAReadableSlug() {
    // The reported defect, at the level the slug is actually generated: the entity name used to
    // survive into the URL as "whatandrsquos-in-americaandrsquos-code-...".
    try (MockedStatic<BlogPostRepository> blogPostRepository = mockStatic(BlogPostRepository.class)) {
      blogPostRepository.when(() -> BlogPostRepository.findByUniqueId(anyLong(), anyString())).thenReturn(null);

      BlogPost blogPost = new BlogPost();
      blogPost.setTitle("What&rsquo;s in America&rsquo;s Code");
      String uniqueId = GenerateBlogPostUniqueIdCommand.generateUniqueId(null, blogPost);
      Assertions.assertEquals("whats-in-americas-code", uniqueId);
    }
  }

  @Test
  void anExistingPostKeepsItsEntityEncodedSlugAfterThisFix() {
    // Existing posts are already published and indexed: the corrected slug must apply to new
    // posts only, never retroactively to a post that already has a URL.
    BlogPost previousBlogPost = new BlogPost();
    previousBlogPost.setUniqueId("whatandrsquos-in-americaandrsquos-code");
    previousBlogPost.setTitle("What&rsquo;s in America&rsquo;s Code");

    BlogPost edited = new BlogPost();
    edited.setTitle("What&rsquo;s in America&rsquo;s Code");

    String uniqueId = GenerateBlogPostUniqueIdCommand.generateUniqueId(previousBlogPost, edited);
    Assertions.assertEquals("whatandrsquos-in-americaandrsquos-code", uniqueId,
        "a published post's URL must not change as a side effect of fixing the slug generator");
  }

  @Test
  void generateUniqueIdForDuplicateBlogPost() {
    String existingUniqueId = "monthly-update";
    BlogPost existingPost = new BlogPost();
    existingPost.setUniqueId(existingUniqueId);

    try (MockedStatic<BlogPostRepository> blogPostRepository = mockStatic(BlogPostRepository.class)) {
      blogPostRepository.when(() -> BlogPostRepository.findByUniqueId(anyLong(), eq(existingUniqueId))).thenReturn(existingPost);

      BlogPost blogPost = new BlogPost();
      blogPost.setTitle("Monthly Update");
      String uniqueId = GenerateBlogPostUniqueIdCommand.generateUniqueId(null, blogPost);
      Assertions.assertEquals("monthly-update-2", uniqueId);
    }
  }

}