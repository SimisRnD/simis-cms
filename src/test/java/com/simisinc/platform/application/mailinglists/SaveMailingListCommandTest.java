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

package com.simisinc.platform.application.mailinglists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;

/**
 * saveMailingList() used to unconditionally overwrite createdBy on every save, including an edit
 * of an existing record -- so editing a mailing list's name silently reassigned its original
 * creator to whoever happened to be editing it that day. modifiedBy is correctly re-set on every
 * save; createdBy must be set once, only when the record is genuinely new.
 *
 * @author elizabeth houser
 */
class SaveMailingListCommandTest {

  @Test
  void newRecordGetsCreatedByFromTheSubmitter() throws DataException {
    MailingList bean = new MailingList();
    bean.setName("Newsletter");
    bean.setCreatedBy(42L); // the current submitter, per MailingListFormWidget.post()

    try (MockedStatic<MailingListRepository> repository = mockStatic(MailingListRepository.class)) {
      repository.when(() -> MailingListRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      SaveMailingListCommand.saveMailingList(bean);

      repository.verify(() -> MailingListRepository.save(argThat(saved -> saved.getCreatedBy() == 42L)));
    }
  }

  @Test
  void editingAnExistingRecordDoesNotChangeItsOriginalCreatedBy() throws DataException {
    MailingList existing = new MailingList();
    existing.setId(1L);
    existing.setCreatedBy(7L); // the original creator

    MailingList bean = new MailingList();
    bean.setId(1L);
    bean.setName("Newsletter Renamed");
    bean.setCreatedBy(42L); // a different user editing it today

    try (MockedStatic<MailingListRepository> repository = mockStatic(MailingListRepository.class)) {
      repository.when(() -> MailingListRepository.findById(1L)).thenReturn(existing);
      repository.when(() -> MailingListRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      SaveMailingListCommand.saveMailingList(bean);

      repository.verify(() -> MailingListRepository.save(argThat(saved -> saved.getCreatedBy() == 7L)));
    }
  }

  @Test
  void editingAnExistingRecordStillUpdatesModifiedBy() throws DataException {
    // modifiedBy is a different field with different, correct semantics -- must keep working
    MailingList existing = new MailingList();
    existing.setId(1L);
    existing.setCreatedBy(7L);
    existing.setModifiedBy(7L);

    MailingList bean = new MailingList();
    bean.setId(1L);
    bean.setName("Newsletter Renamed");
    bean.setCreatedBy(42L);

    try (MockedStatic<MailingListRepository> repository = mockStatic(MailingListRepository.class)) {
      repository.when(() -> MailingListRepository.findById(1L)).thenReturn(existing);
      repository.when(() -> MailingListRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      SaveMailingListCommand.saveMailingList(bean);

      repository.verify(() -> MailingListRepository.save(argThat(saved -> saved.getModifiedBy() == 42L)));
    }
  }

  @Test
  void editingAMissingRecordThrowsBeforeTouchingCreatedBy() {
    MailingList bean = new MailingList();
    bean.setId(99L);
    bean.setName("Ghost list");

    try (MockedStatic<MailingListRepository> repository = mockStatic(MailingListRepository.class)) {
      repository.when(() -> MailingListRepository.findById(99L)).thenReturn(null);

      assertThrows(DataException.class, () -> SaveMailingListCommand.saveMailingList(bean));

      repository.verify(() -> MailingListRepository.save(any()), org.mockito.Mockito.never());
    }
  }
}
