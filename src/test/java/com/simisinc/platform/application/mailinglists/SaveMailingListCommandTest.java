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
 * <p>
 * It also validated only name (#1734), while the admin form marks Title required too and
 * <code>mailing_lists.title</code> is NOT NULL -- which permits <code>''</code>. So a list could be
 * saved with a blank title, and title is what nearly every surface displays. Every bean below
 * therefore sets a title: without one the save now fails validation before reaching the createdBy
 * behaviour these tests are about.
 *
 * @author elizabeth houser
 */
class SaveMailingListCommandTest {

  @Test
  void newRecordGetsCreatedByFromTheSubmitter() throws DataException {
    MailingList bean = new MailingList();
    bean.setName("Newsletter");
    bean.setTitle("Newsletter");
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
    bean.setTitle("Newsletter Renamed");
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
    bean.setTitle("Newsletter Renamed");
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
    // set so the save fails on the missing record, not on validation -- otherwise this passes for
    // the wrong reason
    bean.setTitle("Ghost list");

    try (MockedStatic<MailingListRepository> repository = mockStatic(MailingListRepository.class)) {
      repository.when(() -> MailingListRepository.findById(99L)).thenReturn(null);

      DataException exception = assertThrows(DataException.class,
          () -> SaveMailingListCommand.saveMailingList(bean));

      assertEquals("The existing record could not be found", exception.getMessage());
      repository.verify(() -> MailingListRepository.save(any()), org.mockito.Mockito.never());
    }
  }

  /**
   * The admin form marks Title required, and every surface that shows a mailing list shows its
   * title -- but nothing enforced it, and mailing_lists.title being NOT NULL does not help because
   * NOT NULL permits ''.
   */
  @Test
  void aBlankTitleIsRejectedRatherThanSaved() {
    MailingList bean = new MailingList();
    bean.setName("Newsletter");
    bean.setTitle("");

    try (MockedStatic<MailingListRepository> repository = mockStatic(MailingListRepository.class)) {
      DataException exception = assertThrows(DataException.class,
          () -> SaveMailingListCommand.saveMailingList(bean));

      assertEquals("Please check the form and try again:\nA title is required", exception.getMessage());
      repository.verify(() -> MailingListRepository.save(any()), org.mockito.Mockito.never());
    }
  }

  @Test
  void aWhitespaceOnlyTitleIsRejectedToo() {
    // a title of spaces satisfies both the NOT NULL column and the input's HTML required attribute,
    // and still renders as an empty link and an unnamed checkbox
    MailingList bean = new MailingList();
    bean.setName("Newsletter");
    bean.setTitle("   ");

    try (MockedStatic<MailingListRepository> repository = mockStatic(MailingListRepository.class)) {
      assertThrows(DataException.class, () -> SaveMailingListCommand.saveMailingList(bean));

      repository.verify(() -> MailingListRepository.save(any()), org.mockito.Mockito.never());
    }
  }

  @Test
  void aMissingTitleIsRejectedOnAnEditToo() {
    // clearing the title of a list that already has one must be refused the same way -- the widget
    // populates the bean from the submitted form only, so an omitted field arrives as null
    MailingList existing = new MailingList();
    existing.setId(1L);
    existing.setName("Newsletter");
    existing.setTitle("Our Newsletter");

    MailingList bean = new MailingList();
    bean.setId(1L);
    bean.setName("Newsletter");

    try (MockedStatic<MailingListRepository> repository = mockStatic(MailingListRepository.class)) {
      // stubbed so the record is found either way -- the message below is then the title's, and
      // stays the title's if validation ever moves after the lookup
      repository.when(() -> MailingListRepository.findById(1L)).thenReturn(existing);

      DataException exception = assertThrows(DataException.class,
          () -> SaveMailingListCommand.saveMailingList(bean));

      assertEquals("Please check the form and try again:\nA title is required", exception.getMessage());
      repository.verify(() -> MailingListRepository.save(any()), org.mockito.Mockito.never());
    }
  }

  @Test
  void aBlankNameAndTitleReportBothInOneMessage() {
    // the errorMessages pattern this command already uses joins with "; " -- appending without a
    // separator would read "A name is requiredA title is required"
    MailingList bean = new MailingList();

    try (MockedStatic<MailingListRepository> repository = mockStatic(MailingListRepository.class)) {
      DataException exception = assertThrows(DataException.class,
          () -> SaveMailingListCommand.saveMailingList(bean));

      assertEquals("Please check the form and try again:\nA name is required; A title is required",
          exception.getMessage());
      repository.verify(() -> MailingListRepository.save(any()), org.mockito.Mockito.never());
    }
  }
}
