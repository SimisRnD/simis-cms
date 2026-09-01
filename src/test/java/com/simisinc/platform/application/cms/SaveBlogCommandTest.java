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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;

/**
 * Issue #599 -- the blog-to-mailing-list association's validation and persistence.
 */
class SaveBlogCommandTest {

  private static Blog blogBean(String name, long mailingListId) {
    Blog bean = new Blog();
    bean.setName(name);
    bean.setCreatedBy(1L);
    bean.setModifiedBy(1L);
    bean.setMailingListId(mailingListId);
    return bean;
  }

  @Test
  void anOverLongNameIsRefusedWithTheLimitInTheMessage() {
    // issue #1740: blogs.name is VARCHAR(255) and nothing checked it, so the write reached Postgres
    // and the admin was told the system had failed and to try again
    Blog bean = blogBean("x".repeat(256), -1);

    try (MockedStatic<BlogRepository> blogRepo = mockStatic(BlogRepository.class)) {
      DataException exception = assertThrows(DataException.class, () -> SaveBlogCommand.saveBlog(bean));

      assertEquals("Please check the form and try again:\nA name can be up to 255 characters",
          exception.getMessage());
      blogRepo.verify(() -> BlogRepository.save(any()), org.mockito.Mockito.never());
    }
  }

  @Test
  void aNameExactlyAtTheLimitIsAccepted() throws DataException {
    Blog bean = blogBean("x".repeat(255), -1);

    try (MockedStatic<BlogRepository> blogRepo = mockStatic(BlogRepository.class)) {
      blogRepo.when(() -> BlogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      Blog saved = SaveBlogCommand.saveBlog(bean);

      assertEquals(255, saved.getName().length());
    }
  }

  @Test
  void savesWithNoMailingListAssociation() throws DataException {
    Blog bean = blogBean("News", -1);

    try (MockedStatic<BlogRepository> blogRepo = mockStatic(BlogRepository.class);
        MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      blogRepo.when(() -> BlogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      Blog saved = SaveBlogCommand.saveBlog(bean);

      assertEquals(-1, saved.getMailingListId());
      listRepo.verify(() -> MailingListRepository.findById(any(Long.class)), org.mockito.Mockito.never());
    }
  }

  @Test
  void savesWithAValidMailingListAssociation() throws DataException {
    Blog bean = blogBean("News", 5L);
    MailingList mailingList = new MailingList();
    mailingList.setId(5L);

    try (MockedStatic<BlogRepository> blogRepo = mockStatic(BlogRepository.class);
        MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      listRepo.when(() -> MailingListRepository.findById(5L)).thenReturn(mailingList);
      blogRepo.when(() -> BlogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      Blog saved = SaveBlogCommand.saveBlog(bean);

      assertEquals(5, saved.getMailingListId());
    }
  }

  @Test
  void rejectsAMailingListIdThatDoesNotExist() {
    Blog bean = blogBean("News", 999L);

    try (MockedStatic<BlogRepository> blogRepo = mockStatic(BlogRepository.class);
        MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      listRepo.when(() -> MailingListRepository.findById(999L)).thenReturn(null);

      DataException e = assertThrows(DataException.class, () -> SaveBlogCommand.saveBlog(bean));

      org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("mailing list could not be found"), e.getMessage());
      blogRepo.verify(() -> BlogRepository.save(any()), org.mockito.Mockito.never());
    }
  }

  @Test
  void updatingAnExistingBlogPreservesTheMailingListIdChange() throws DataException {
    Blog existing = blogBean("News", -1);
    existing.setId(3L);
    Blog updateBean = blogBean("News", 7L);
    updateBean.setId(3L);
    MailingList mailingList = new MailingList();
    mailingList.setId(7L);

    try (MockedStatic<BlogRepository> blogRepo = mockStatic(BlogRepository.class);
        MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      blogRepo.when(() -> BlogRepository.findById(3L)).thenReturn(existing);
      listRepo.when(() -> MailingListRepository.findById(7L)).thenReturn(mailingList);
      blogRepo.when(() -> BlogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      Blog saved = SaveBlogCommand.saveBlog(updateBean);

      assertEquals(7, saved.getMailingListId());
    }
  }
}
