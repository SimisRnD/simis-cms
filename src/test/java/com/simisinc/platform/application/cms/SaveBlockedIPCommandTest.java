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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.BlockedIP;
import com.simisinc.platform.infrastructure.persistence.BlockedIPRepository;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * Covers the same three fixes as SaveAllowedIPCommandTest, applied to the Blocked IP side: trim
 * and case-normalization before validation/the duplicate check (fix 5), the cross-list conflict
 * warning when a new Blocked entry is already shadowed by an existing Allowed entry (fix 1), and
 * the stale-cache-on-edit bug that also existed in this class's save() (fix 3).
 */
class SaveBlockedIPCommandTest {

  @Test
  void trimsWhitespaceBeforeValidatingAndSaving() throws DataException {
    BlockedIP bean = new BlockedIP();
    bean.setIpAddress("  203.0.113.5  ");

    try (MockedStatic<BlockedIPRepository> blockedRepo = mockStatic(BlockedIPRepository.class);
        MockedStatic<LoadAllowedIPListCommand> loadAllowed = mockStatic(LoadAllowedIPListCommand.class);
        MockedStatic<LoadBlockedIPListCommand> loadBlocked = mockStatic(LoadBlockedIPListCommand.class)) {

      loadBlocked.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());
      loadAllowed.when(LoadAllowedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());
      blockedRepo.when(() -> BlockedIPRepository.save(any(BlockedIP.class))).thenAnswer(inv -> inv.getArgument(0));

      BlockedIP saved = SaveBlockedIPCommand.save(bean);

      Assertions.assertNotNull(saved);
      Assertions.assertEquals("203.0.113.5", saved.getIpAddress(),
          "leading/trailing whitespace should be trimmed before validation and storage, not just before the SQL write");
    }
  }

  @Test
  void caseInsensitiveDuplicateIsRejectedAndNothingIsPersisted() {
    BlockedIP bean = new BlockedIP();
    bean.setIpAddress("2001:db8::1");

    try (MockedStatic<BlockedIPRepository> blockedRepo = mockStatic(BlockedIPRepository.class);
        MockedStatic<LoadBlockedIPListCommand> loadBlocked = mockStatic(LoadBlockedIPListCommand.class)) {

      loadBlocked.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList)
          .thenReturn(new ArrayList<>(List.of("2001:DB8::1")));

      DataException thrown = Assertions.assertThrows(DataException.class, () -> SaveBlockedIPCommand.save(bean));
      Assertions.assertTrue(thrown.getMessage().toLowerCase().contains("already"),
          "the error should explain this is a duplicate of an existing entry");

      blockedRepo.verify(() -> BlockedIPRepository.save(any(BlockedIP.class)), never());
    }
  }

  @Test
  void savingABlockedEntryWarnsWhenItIsAlreadyShadowedByAnExistingAllowedEntry() throws DataException {
    BlockedIP bean = new BlockedIP();
    bean.setIpAddress("203.0.113.10");

    try (MockedStatic<BlockedIPRepository> blockedRepo = mockStatic(BlockedIPRepository.class);
        MockedStatic<LoadAllowedIPListCommand> loadAllowed = mockStatic(LoadAllowedIPListCommand.class);
        MockedStatic<LoadBlockedIPListCommand> loadBlocked = mockStatic(LoadBlockedIPListCommand.class)) {

      blockedRepo.when(() -> BlockedIPRepository.save(any(BlockedIP.class))).thenAnswer(inv -> inv.getArgument(0));
      loadBlocked.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());
      loadAllowed.when(LoadAllowedIPListCommand::retrieveCachedIpAddressList)
          .thenReturn(new ArrayList<>(List.of("203.0.113.0/24")));

      BlockedIP saved = SaveBlockedIPCommand.save(bean);

      Assertions.assertNotNull(saved, "the save itself must still succeed even though the block will not actually fire");
      String warning = SaveBlockedIPCommand.getLastConflictWarning();
      Assertions.assertNotNull(warning, "an existing, covering Allowed IP entry should produce a warning");
      Assertions.assertTrue(warning.contains("203.0.113.0/24"));
      Assertions.assertTrue(warning.toLowerCase().contains("allowed"));
      Assertions.assertTrue(warning.toLowerCase().contains("not"),
          "the warning should make clear the block will NOT actually take effect");
    }
  }

  @Test
  void noWarningWhenThereIsNoCrossListConflict() throws DataException {
    BlockedIP bean = new BlockedIP();
    bean.setIpAddress("198.51.100.1");

    try (MockedStatic<BlockedIPRepository> blockedRepo = mockStatic(BlockedIPRepository.class);
        MockedStatic<LoadAllowedIPListCommand> loadAllowed = mockStatic(LoadAllowedIPListCommand.class);
        MockedStatic<LoadBlockedIPListCommand> loadBlocked = mockStatic(LoadBlockedIPListCommand.class)) {

      blockedRepo.when(() -> BlockedIPRepository.save(any(BlockedIP.class))).thenAnswer(inv -> inv.getArgument(0));
      loadBlocked.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());
      loadAllowed.when(LoadAllowedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());

      SaveBlockedIPCommand.save(bean);

      Assertions.assertNull(SaveBlockedIPCommand.getLastConflictWarning());
    }
  }

  @Test
  void editingAnExistingRecordToADifferentAddressRemovesTheOldValueFromTheCache() throws DataException {
    BlockedIP existing = new BlockedIP();
    existing.setId(3L);
    existing.setIpAddress("203.0.113.0/24");

    BlockedIP bean = new BlockedIP();
    bean.setId(3L);
    bean.setIpAddress("203.0.113.5"); // narrowed from the /24 range to a single address

    try (MockedStatic<BlockedIPRepository> blockedRepo = mockStatic(BlockedIPRepository.class);
        MockedStatic<LoadAllowedIPListCommand> loadAllowed = mockStatic(LoadAllowedIPListCommand.class);
        MockedStatic<LoadBlockedIPListCommand> loadBlocked = mockStatic(LoadBlockedIPListCommand.class)) {

      blockedRepo.when(() -> BlockedIPRepository.findById(eq(3L))).thenReturn(existing);
      blockedRepo.when(() -> BlockedIPRepository.save(any(BlockedIP.class))).thenAnswer(inv -> inv.getArgument(0));
      loadBlocked.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList)
          .thenReturn(new ArrayList<>(List.of("203.0.113.0/24")));
      loadAllowed.when(LoadAllowedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());

      SaveBlockedIPCommand.save(bean);

      loadBlocked.verify(() -> LoadBlockedIPListCommand.removeIpFromCache("203.0.113.0/24"), times(1));
      loadBlocked.verify(() -> LoadBlockedIPListCommand.addIpToCache(
          argThat((BlockedIP ip) -> "203.0.113.5".equals(ip.getIpAddress()))), times(1));
    }
  }

  @Test
  void editingAnExistingRecordWithoutChangingItsAddressNeverRemovesAnythingFromTheCache() throws DataException {
    BlockedIP existing = new BlockedIP();
    existing.setId(4L);
    existing.setIpAddress("203.0.113.9");

    BlockedIP bean = new BlockedIP();
    bean.setId(4L);
    bean.setIpAddress("203.0.113.9");
    bean.setReason("just updating the reason");

    try (MockedStatic<BlockedIPRepository> blockedRepo = mockStatic(BlockedIPRepository.class);
        MockedStatic<LoadAllowedIPListCommand> loadAllowed = mockStatic(LoadAllowedIPListCommand.class);
        MockedStatic<LoadBlockedIPListCommand> loadBlocked = mockStatic(LoadBlockedIPListCommand.class)) {

      blockedRepo.when(() -> BlockedIPRepository.findById(eq(4L))).thenReturn(existing);
      blockedRepo.when(() -> BlockedIPRepository.save(any(BlockedIP.class))).thenAnswer(inv -> inv.getArgument(0));
      loadBlocked.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList)
          .thenReturn(new ArrayList<>(List.of("203.0.113.9")));
      loadAllowed.when(LoadAllowedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());

      SaveBlockedIPCommand.save(bean);

      loadBlocked.verify(() -> LoadBlockedIPListCommand.removeIpFromCache(anyString()), never());
      loadBlocked.verify(() -> LoadBlockedIPListCommand.addIpToCache(any(BlockedIP.class)), times(1));
    }
  }
}
