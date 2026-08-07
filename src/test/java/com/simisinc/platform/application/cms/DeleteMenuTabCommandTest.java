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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.infrastructure.persistence.cms.MenuTabRepository;

/**
 * Verifies {@link DeleteMenuTabCommand}'s validation, in particular that the Home tab guard
 * actually fires for a real, existing Home tab (its previous {@code menuTabBean.getId() == -1}
 * condition could never be true alongside a tab that was actually found by id, making the guard
 * a no-op).
 *
 * @author SimIS Inc.
 */
class DeleteMenuTabCommandTest {

  @Test
  void aNullMenuTabIsRejected() {
    assertThrows(DataException.class, () -> DeleteMenuTabCommand.deleteMenuTab(null));
  }

  @Test
  void aMenuTabWithNoIdIsRejected() {
    MenuTab bean = new MenuTab();
    assertThrows(DataException.class, () -> DeleteMenuTabCommand.deleteMenuTab(bean));
  }

  @Test
  void anExistingHomeTabCannotBeDeleted() {
    MenuTab bean = new MenuTab();
    bean.setId(1L);
    bean.setLink("/");
    assertThrows(DataException.class, () -> DeleteMenuTabCommand.deleteMenuTab(bean));
  }

  @Test
  void aNonHomeTabIsRemovedViaTheRepository() throws DataException {
    MenuTab bean = new MenuTab();
    bean.setId(7L);
    bean.setLink("/solutions");

    try (MockedStatic<MenuTabRepository> repository = mockStatic(MenuTabRepository.class)) {
      repository.when(() -> MenuTabRepository.remove(bean)).thenReturn(true);

      assertTrue(DeleteMenuTabCommand.deleteMenuTab(bean));

      repository.verify(() -> MenuTabRepository.remove(bean));
    }
  }
}
