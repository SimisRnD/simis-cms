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

package com.simisinc.platform.application.cms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;

/**
 * The fields an admin types or pastes on the blog editor (issue #1740).
 *
 * <p>The #1740 sweep guarded the other editors and missed this one. A keyword list a little over
 * 255 characters -- eight or nine descriptive phrases -- reached Postgres, was refused, and came
 * back as "Your information could not be saved due to a system error. Please try again." Retrying
 * cannot help, and nothing said the keywords were the problem.
 *
 * @author elizabeth houser
 */
class SaveBlogPostCommandLengthTest {

  private static String of(int length) {
    return "a".repeat(length);
  }

  private static String urlOf(int length) {
    String prefix = "https://example.com/";
    return prefix + "a".repeat(length - prefix.length());
  }

  private static BlogPost post() {
    BlogPost bean = new BlogPost();
    bean.setBlogId(1L);
    bean.setCreatedBy(42L);
    bean.setModifiedBy(42L);
    bean.setTitle("A post");
    bean.setBody("<p>The post</p>");
    return bean;
  }

  private static void assertRefused(BlogPost bean, String expected) {
    try (MockedStatic<BlogPostRepository> repository = mockStatic(BlogPostRepository.class)) {
      DataException exception = assertThrows(DataException.class,
          () -> SaveBlogPostCommand.saveBlogPost(bean));
      assertTrue(exception.getMessage().contains(expected),
          "the message has to name the field and the limit, not just say something went wrong: "
              + exception.getMessage());
      repository.verify(() -> BlogPostRepository.save(any()), never());
    }
  }

  private static void assertSaves(BlogPost bean) throws DataException {
    // cleanContent reads the iframe allowlist from site properties, which a unit test has no
    // database for; the body is not what these tests are about
    try (MockedStatic<BlogPostRepository> repository = mockStatic(BlogPostRepository.class);
        MockedStatic<HtmlCommand> html = mockStatic(HtmlCommand.class)) {
      html.when(() -> HtmlCommand.cleanContent(anyString())).thenAnswer(i -> i.getArgument(0));
      repository.when(() -> BlogPostRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      SaveBlogPostCommand.saveBlogPost(bean);
      repository.verify(() -> BlogPostRepository.save(any()));
    }
  }

  @Test
  void overLongKeywordsAreRefusedByName() {
    BlogPost bean = post();
    bean.setKeywords(of(256));
    assertRefused(bean, "Keywords can be up to 255 characters");
  }

  @Test
  void anOverLongTitleIsRefusedByName() {
    BlogPost bean = post();
    bean.setTitle(of(256));
    assertRefused(bean, "A title can be up to 255 characters");
  }

  @Test
  void anOverLongSourceLinkIsRefusedByName() {
    BlogPost bean = post();
    bean.setSourceUrl(urlOf(513));
    assertRefused(bean, "The source article link can be up to 512 characters");
  }

  @Test
  void overLongImageUrlsAreRefusedByName() {
    BlogPost bean = post();
    bean.setImageUrl(of(256));
    bean.setShareImageUrl(of(256));
    assertRefused(bean, "A banner image URL can be up to 255 characters; "
        + "A share image URL can be up to 255 characters");
  }

  @Test
  void everyFieldAtItsLimitSaves() throws DataException {
    // an off-by-one here would refuse a legitimate entry, which is the same failure with a nicer
    // message
    BlogPost bean = post();
    bean.setTitle(of(255));
    bean.setKeywords(of(255));
    bean.setSourceUrl(urlOf(512));
    bean.setImageUrl(of(255));
    bean.setShareImageUrl(of(255));
    assertSaves(bean);
  }

  @Test
  void atTheLimitWithTrailingSpaceStillSaves() throws DataException {
    // pasting easily adds whitespace, and BlogPostRepository trims before writing, so refusing this
    // would refuse a save the column would have accepted
    BlogPost bean = post();
    bean.setKeywords(of(255) + "   ");
    assertSaves(bean);
  }

  @Test
  void aBlankTitleReportsRequiredRatherThanTooLong() {
    BlogPost bean = post();
    bean.setTitle("   ");
    assertRefused(bean, "A title is required");
  }

  @Test
  void aSourceLinkThatIsNotAUrlIsNotAlsoReportedAsTooLong() {
    BlogPost bean = post();
    bean.setSourceUrl("javascript:" + of(600));
    try (MockedStatic<BlogPostRepository> repository = mockStatic(BlogPostRepository.class)) {
      DataException exception = assertThrows(DataException.class,
          () -> SaveBlogPostCommand.saveBlogPost(bean));
      assertTrue(exception.getMessage().contains("must be a full http:// or https:// address"),
          exception.getMessage());
      assertFalse(exception.getMessage().contains("can be up to"), exception.getMessage());
    }
  }
}
