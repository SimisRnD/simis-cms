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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.BlogTag;
import com.simisinc.platform.infrastructure.persistence.cms.BlogTagRepository;

/**
 * Verifies {@link SaveBlogTagCommand}'s validation and duplicate-name rules (issue #633).
 * Unlike {@code SaveTagCommand}'s #632 item-tag equivalent, this command must reject a duplicate
 * name on rename (not just on create), because {@code lookup_blog_post_tags} has no database-level
 * unique index on name -- only on (blog_id, tag_unique_id) -- so nothing else would catch it.
 *
 * @author SimIS Inc.
 */
class SaveBlogTagCommandTest {

  private static BlogTag tagBean(long blogId, String name, long createdBy) {
    BlogTag tag = new BlogTag();
    tag.setBlogId(blogId);
    tag.setName(name);
    tag.setCreatedBy(createdBy);
    return tag;
  }

  @Test
  void aMissingBlogIdIsRejected() {
    BlogTag bean = tagBean(-1, "Fiction", 1);
    assertThrows(DataException.class, () -> SaveBlogTagCommand.saveTag(bean));
  }

  @Test
  void aBlankNameIsRejected() {
    BlogTag bean = tagBean(5, "   ", 1);
    assertThrows(DataException.class, () -> SaveBlogTagCommand.saveTag(bean));
  }

  @Test
  void aMissingCreatedByIsRejected() {
    BlogTag bean = tagBean(5, "Fiction", -1);
    assertThrows(DataException.class, () -> SaveBlogTagCommand.saveTag(bean));
  }

  @Test
  void aDuplicateNameWithinTheSameBlogIsRejectedOnInsert() {
    BlogTag bean = tagBean(5, "Fiction", 1);
    BlogTag existingDuplicate = tagBean(5, "Fiction", 1);
    existingDuplicate.setId(42L); // a real, already-persisted record -- never -1
    try (MockedStatic<BlogTagRepository> repository = mockStatic(BlogTagRepository.class)) {
      repository.when(() -> BlogTagRepository.findByNameWithinBlog("Fiction", 5)).thenReturn(existingDuplicate);

      assertThrows(DataException.class, () -> SaveBlogTagCommand.saveTag(bean));
      repository.verify(() -> BlogTagRepository.save(any()), never());
    }
  }

  @Test
  void aNewTagWithAUniqueNameIsSaved() throws DataException {
    BlogTag bean = tagBean(5, "Fiction", 1);
    try (MockedStatic<BlogTagRepository> repository = mockStatic(BlogTagRepository.class)) {
      repository.when(() -> BlogTagRepository.findByNameWithinBlog("Fiction", 5)).thenReturn(null);
      repository.when(() -> BlogTagRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      BlogTag saved = SaveBlogTagCommand.saveTag(bean);

      assertEquals("Fiction", saved.getName());
      assertEquals(5, saved.getBlogId());
      assertEquals(1, saved.getCreatedBy());
      assertEquals("fiction", saved.getUniqueId());
    }
  }

  @Test
  void editingAMissingRecordThrows() {
    BlogTag bean = tagBean(5, "Fiction", 1);
    bean.setId(99L);
    try (MockedStatic<BlogTagRepository> repository = mockStatic(BlogTagRepository.class)) {
      repository.when(() -> BlogTagRepository.findById(99L)).thenReturn(null);

      assertThrows(DataException.class, () -> SaveBlogTagCommand.saveTag(bean));
      repository.verify(() -> BlogTagRepository.save(any()), never());
    }
  }

  @Test
  void editingAnExistingRecordWithoutChangingTheNameIsNotRejectedAsADuplicateOfItself() throws DataException {
    BlogTag existing = tagBean(10, "Fiction", 1);
    existing.setId(1L);
    existing.setUniqueId("fiction");

    BlogTag bean = tagBean(10, "Fiction", 1);
    bean.setId(1L);

    try (MockedStatic<BlogTagRepository> repository = mockStatic(BlogTagRepository.class)) {
      repository.when(() -> BlogTagRepository.findById(1L)).thenReturn(existing);
      repository.when(() -> BlogTagRepository.findByNameWithinBlog("Fiction", 10L)).thenReturn(existing);
      repository.when(() -> BlogTagRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      BlogTag saved = SaveBlogTagCommand.saveTag(bean);

      assertEquals("Fiction", saved.getName());
      assertEquals("fiction", saved.getUniqueId(), "an unchanged name must keep its existing uniqueId");
    }
  }

  @Test
  void renamingAnExistingTagToCollideWithAnotherTagsNameInTheSameBlogIsRejected() {
    // id=2 ("History") is being renamed to "Fiction", which id=1 already has in the same blog
    BlogTag existing = tagBean(10, "History", 1);
    existing.setId(2L);
    existing.setUniqueId("history");

    BlogTag anotherTag = tagBean(10, "Fiction", 1);
    anotherTag.setId(1L);
    anotherTag.setUniqueId("fiction");

    BlogTag bean = tagBean(10, "Fiction", 1);
    bean.setId(2L);

    try (MockedStatic<BlogTagRepository> repository = mockStatic(BlogTagRepository.class)) {
      repository.when(() -> BlogTagRepository.findById(2L)).thenReturn(existing);
      repository.when(() -> BlogTagRepository.findByNameWithinBlog("Fiction", 10L)).thenReturn(anotherTag);

      assertThrows(DataException.class, () -> SaveBlogTagCommand.saveTag(bean));
      repository.verify(() -> BlogTagRepository.save(any()), never());
    }
  }

  @Test
  void editingAnExistingRecordIgnoresATamperedBlogIdAndScopesChecksToTheRecordsOwnBlog() throws DataException {
    // The tag really belongs to blog 10; the submitted bean claims blog 99 (a tampered hidden field)
    BlogTag existing = tagBean(10, "Updates", 1);
    existing.setId(5L);
    existing.setUniqueId("updates");

    BlogTag bean = tagBean(99, "Updates", 1);
    bean.setId(5L);

    try (MockedStatic<BlogTagRepository> repository = mockStatic(BlogTagRepository.class)) {
      repository.when(() -> BlogTagRepository.findById(5L)).thenReturn(existing);
      repository.when(() -> BlogTagRepository.findByNameWithinBlog("Updates", 10L)).thenReturn(existing);
      repository.when(() -> BlogTagRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      BlogTag saved = SaveBlogTagCommand.saveTag(bean);

      assertEquals(10, saved.getBlogId(), "the tag's real existing blogId must win over a client-submitted value");
      repository.verify(() -> BlogTagRepository.findByNameWithinBlog("Updates", 99L), never());
    }
  }
}
