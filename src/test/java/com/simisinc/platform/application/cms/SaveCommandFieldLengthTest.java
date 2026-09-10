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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.FormDefinition;
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.infrastructure.persistence.cms.FormDefinitionRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WikiRepository;

/**
 * The next slice of issue #1740, for the CMS commands.
 *
 * <p>Before this, an over-length entry was refused by nobody on the way down. It reached Postgres,
 * the repository logged the SQLException and returned null, and the widget read that null as
 * "Your information could not be saved due to a system error. Please try again." -- advice that
 * cannot work, since the same value fails identically every time, and which never named the field
 * or the limit.
 *
 * <p>Each command is checked at three points rather than one: over the limit is refused with a
 * message that names the field and the number, exactly at the limit saves (an off-by-one here
 * would reject a legitimate entry, which is the same failure wearing a nicer message), and a value
 * at the limit with trailing whitespace saves too, because the repositories trim before writing
 * and measuring the raw string would refuse a value the database would have accepted.
 *
 * @author elizabeth houser
 */
class SaveCommandFieldLengthTest {

  private static String of(int length) {
    return "a".repeat(length);
  }

  @Nested
  class SubFolderName {

    @Test
    void overTheLimitIsRefusedByName() {
      SubFolder bean = new SubFolder();
      bean.setName(of(256));
      bean.setFolderId(1L);
      bean.setCreatedBy(42L);

      try (MockedStatic<SubFolderRepository> repository = mockStatic(SubFolderRepository.class)) {
        DataException exception = assertThrows(DataException.class,
            () -> SaveSubFolderCommand.saveSubFolder(bean));
        assertTrue(exception.getMessage().contains("A name can be up to 255 characters"),
            "the message has to name the field and the limit, not just say something went wrong: "
                + exception.getMessage());
        repository.verify(() -> SubFolderRepository.save(any()), never());
      }
    }

    @Test
    void exactlyAtTheLimitSaves() throws DataException {
      SubFolder bean = new SubFolder();
      bean.setName(of(255));
      bean.setFolderId(1L);
      bean.setCreatedBy(42L);

      try (MockedStatic<SubFolderRepository> repository = mockStatic(SubFolderRepository.class)) {
        repository.when(() -> SubFolderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        SaveSubFolderCommand.saveSubFolder(bean);
        repository.verify(() -> SubFolderRepository.save(any()));
      }
    }

    @Test
    void atTheLimitWithTrailingSpaceStillSaves() throws DataException {
      // 255 characters plus whitespace is easy to produce by pasting, and the stored value is
      // trimmed, so refusing it would refuse a save the column would have accepted
      SubFolder bean = new SubFolder();
      bean.setName(of(255) + "   ");
      bean.setFolderId(1L);
      bean.setCreatedBy(42L);

      try (MockedStatic<SubFolderRepository> repository = mockStatic(SubFolderRepository.class)) {
        repository.when(() -> SubFolderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        SaveSubFolderCommand.saveSubFolder(bean);
        repository.verify(() -> SubFolderRepository.save(any()));
      }
    }
  }

  @Nested
  class WikiName {

    @Test
    void overTheLimitIsRefusedByName() {
      Wiki bean = new Wiki();
      bean.setName(of(256));
      bean.setCreatedBy(42L);

      try (MockedStatic<WikiRepository> repository = mockStatic(WikiRepository.class)) {
        DataException exception = assertThrows(DataException.class,
            () -> SaveWikiCommand.saveWiki(bean));
        assertTrue(exception.getMessage().contains("A name can be up to 255 characters"),
            exception.getMessage());
        repository.verify(() -> WikiRepository.save(any()), never());
      }
    }

    @Test
    void aBlankNameStillReportsRequiredRatherThanTooLong() {
      // the name arm is an if/else: blank must not fall through to the length message
      Wiki bean = new Wiki();
      bean.setName("   ");
      bean.setCreatedBy(42L);

      try (MockedStatic<WikiRepository> repository = mockStatic(WikiRepository.class)) {
        DataException exception = assertThrows(DataException.class,
            () -> SaveWikiCommand.saveWiki(bean));
        assertTrue(exception.getMessage().contains("A name is required"), exception.getMessage());
        assertTrue(!exception.getMessage().contains("can be up to"), exception.getMessage());
      }
    }
  }

  @Nested
  class FormDefinitionFields {

    @Test
    void theButtonNameIsTheNarrowestFieldAndIsRefusedAtOneHundredAndOne() {
      FormDefinition bean = new FormDefinition();
      bean.setName("Contact Us");
      bean.setButtonName(of(101));
      bean.setModifiedBy(42L);

      try (MockedStatic<FormDefinitionRepository> repository =
          mockStatic(FormDefinitionRepository.class)) {
        DataException exception = assertThrows(DataException.class,
            () -> SaveFormDefinitionCommand.saveFormDefinition(bean));
        assertTrue(exception.getMessage().contains("A button name can be up to 100 characters"),
            exception.getMessage());
        repository.verify(() -> FormDefinitionRepository.save(any()), never());
      }
    }

    @Test
    void anOptionalFieldIsCheckedEvenThoughItMayBeBlank() {
      // title is optional, so it is checked unconditionally rather than in a required-field else
      FormDefinition bean = new FormDefinition();
      bean.setName("Contact Us");
      bean.setTitle(of(256));
      bean.setModifiedBy(42L);

      try (MockedStatic<FormDefinitionRepository> repository =
          mockStatic(FormDefinitionRepository.class)) {
        DataException exception = assertThrows(DataException.class,
            () -> SaveFormDefinitionCommand.saveFormDefinition(bean));
        assertTrue(exception.getMessage().contains("A title can be up to 255 characters"),
            exception.getMessage());
      }
    }

    @Test
    void aBlankOptionalFieldIsNotReportedAsTooLong() throws DataException {
      FormDefinition bean = new FormDefinition();
      bean.setName("Contact Us");
      bean.setModifiedBy(42L);

      try (MockedStatic<FormDefinitionRepository> repository =
          mockStatic(FormDefinitionRepository.class)) {
        repository.when(() -> FormDefinitionRepository.save(any()))
            .thenAnswer(i -> i.getArgument(0));
        SaveFormDefinitionCommand.saveFormDefinition(bean);
        repository.verify(() -> FormDefinitionRepository.save(any()));
      }
    }
  }

  @Nested
  class MessageWording {

    @Test
    void namesTheLimitSoTheAdminCanAct() {
      // the whole point of #1740: the old failure said "a system error, please try again", which
      // was both wrong and unactionable
      assertEquals("A name can be up to 255 characters",
          com.simisinc.platform.application.FieldLengthCommand.tooLongMessage("A name", 255));
    }
  }
}
