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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.FormDefinition;
import com.simisinc.platform.infrastructure.persistence.cms.FormDefinitionRepository;

/**
 * Verifies {@link SaveFormDefinitionCommand}, including the "Email submissions to" validation
 * added so a malformed address can't be saved -- previously anything typed there was persisted
 * as-is, and a typo meant EmailTask's send would either fail silently in the background job queue
 * or (for a valid-looking-but-wrong address) never fail at all.
 */
class SaveFormDefinitionCommandTest {

  private static FormDefinition definition(long id, String name, String emailTo) {
    FormDefinition formDefinition = new FormDefinition();
    formDefinition.setId(id);
    formDefinition.setName(name);
    formDefinition.setEmailTo(emailTo);
    formDefinition.setModifiedBy(1L);
    formDefinition.setCreatedBy(1L);
    return formDefinition;
  }

  @Test
  void aBlankEmailToIsAccepted() throws DataException {
    FormDefinition bean = definition(-1L, "Contact Us", null);

    try (MockedStatic<FormDefinitionRepository> repository = mockStatic(FormDefinitionRepository.class)) {
      repository.when(() -> FormDefinitionRepository.findByUniqueId(any())).thenReturn(null);
      repository.when(() -> FormDefinitionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      FormDefinition saved = SaveFormDefinitionCommand.saveFormDefinition(bean);

      assertNull(saved.getEmailTo());
    }
  }

  @Test
  void aSingleValidEmailToIsAccepted() throws DataException {
    FormDefinition bean = definition(-1L, "Contact Us", "sales@simis.com");

    try (MockedStatic<FormDefinitionRepository> repository = mockStatic(FormDefinitionRepository.class)) {
      repository.when(() -> FormDefinitionRepository.findByUniqueId(any())).thenReturn(null);
      repository.when(() -> FormDefinitionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      FormDefinition saved = SaveFormDefinitionCommand.saveFormDefinition(bean);

      assertEquals("sales@simis.com", saved.getEmailTo());
    }
  }

  @Test
  void multipleCommaSeparatedValidAddressesAreAccepted() throws DataException {
    FormDefinition bean = definition(-1L, "Contact Us", "sales@simis.com, technical@simis.com");

    try (MockedStatic<FormDefinitionRepository> repository = mockStatic(FormDefinitionRepository.class)) {
      repository.when(() -> FormDefinitionRepository.findByUniqueId(any())).thenReturn(null);
      repository.when(() -> FormDefinitionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      FormDefinition saved = SaveFormDefinitionCommand.saveFormDefinition(bean);

      assertEquals("sales@simis.com, technical@simis.com", saved.getEmailTo());
    }
  }

  @Test
  void aSyntacticallyInvalidEmailToIsRejected() {
    FormDefinition bean = definition(-1L, "Contact Us", "not-an-email");

    try (MockedStatic<FormDefinitionRepository> repository = mockStatic(FormDefinitionRepository.class)) {
      DataException e = assertThrows(DataException.class, () -> SaveFormDefinitionCommand.saveFormDefinition(bean));

      assertTrue(e.getMessage().contains("not-an-email"));
      repository.verify(() -> FormDefinitionRepository.save(any()), never());
    }
  }

  @Test
  void oneInvalidAddressAmongMultipleValidOnesIsRejected() {
    FormDefinition bean = definition(-1L, "Contact Us", "sales@simis.com, not-an-email");

    try (MockedStatic<FormDefinitionRepository> repository = mockStatic(FormDefinitionRepository.class)) {
      DataException e = assertThrows(DataException.class, () -> SaveFormDefinitionCommand.saveFormDefinition(bean));

      assertTrue(e.getMessage().contains("not-an-email"));
      repository.verify(() -> FormDefinitionRepository.save(any()), never());
    }
  }

  @Test
  void extraCommasAndWhitespaceAreToleratedWithoutFalselyRejecting() throws DataException {
    FormDefinition bean = definition(-1L, "Contact Us", " sales@simis.com ,, technical@simis.com ,");

    try (MockedStatic<FormDefinitionRepository> repository = mockStatic(FormDefinitionRepository.class)) {
      repository.when(() -> FormDefinitionRepository.findByUniqueId(any())).thenReturn(null);
      repository.when(() -> FormDefinitionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      FormDefinition saved = SaveFormDefinitionCommand.saveFormDefinition(bean);

      assertEquals(" sales@simis.com ,, technical@simis.com ,", saved.getEmailTo());
    }
  }

  @Test
  void aBlankNameIsRejected() {
    FormDefinition bean = definition(-1L, "", null);

    try (MockedStatic<FormDefinitionRepository> repository = mockStatic(FormDefinitionRepository.class)) {
      assertThrows(DataException.class, () -> SaveFormDefinitionCommand.saveFormDefinition(bean));
      repository.verify(() -> FormDefinitionRepository.save(any()), never());
    }
  }
}
