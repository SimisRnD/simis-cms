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

package com.simisinc.platform.domain.events.cms;

import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/11/2022 10:30 PM
 */
class FormSubmittedEventTest {

  @Test
  void checkEvent() {
    FormData formData = new FormData();
    formData.setId(1L);

    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findById(anyLong())).thenReturn(formData);

      FormSubmittedEvent event = new FormSubmittedEvent(formData, "example@example.com");
      Assertions.assertEquals(formData.getId(), event.getFormId());
      Assertions.assertTrue(event.getOccurred() <= System.currentTimeMillis());
      Assertions.assertEquals(FormSubmittedEvent.ID, event.getDomainEventType());
    }
  }
}