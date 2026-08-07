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
import com.simisinc.platform.domain.model.AllowedIP;
import com.simisinc.platform.infrastructure.persistence.AllowedIPRepository;

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
 * Covers: trimming/case-normalization before validation and the duplicate check (fix 5), the
 * cross-list conflict warning when an Allowed entry shadows an existing Blocked entry (fix 1),
 * and the stale-cache-on-edit bug where narrowing an existing record used to leave the old,
 * broader cached value live (fix 3).
 */
class SaveAllowedIPCommandTest {

  @Test
  void trimsWhitespaceBeforeValidatingAndSaving() throws DataException {
    AllowedIP bean = new AllowedIP();
    bean.setIpAddress("  203.0.113.5  ");

    try (MockedStatic<AllowedIPRepository> allowedRepo = mockStatic(AllowedIPRepository.class);
        MockedStatic<LoadAllowedIPListCommand> loadAllowed = mockStatic(LoadAllowedIPListCommand.class);
        MockedStatic<LoadBlockedIPListCommand> loadBlocked = mockStatic(LoadBlockedIPListCommand.class)) {

      loadAllowed.when(LoadAllowedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());
      loadBlocked.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());
      allowedRepo.when(() -> AllowedIPRepository.save(any(AllowedIP.class))).thenAnswer(inv -> inv.getArgument(0));

      AllowedIP saved = SaveAllowedIPCommand.save(bean);

      Assertions.assertNotNull(saved);
      Assertions.assertEquals("203.0.113.5", saved.getIpAddress(),
          "leading/trailing whitespace should be trimmed before validation and storage, not just before the SQL write");
    }
  }

  @Test
  void whitespaceAroundAnInvalidValueIsStillRejected() {
    AllowedIP bean = new AllowedIP();
    bean.setIpAddress("   not-an-ip   ");

    DataException thrown = Assertions.assertThrows(DataException.class, () -> SaveAllowedIPCommand.save(bean));
    Assertions.assertTrue(thrown.getMessage().contains("valid IPv4 or IPv6"));
  }

  @Test
  void caseInsensitiveDuplicateIsRejectedAndNothingIsPersisted() {
    AllowedIP bean = new AllowedIP();
    bean.setIpAddress("2001:db8::1");

    try (MockedStatic<AllowedIPRepository> allowedRepo = mockStatic(AllowedIPRepository.class);
        MockedStatic<LoadAllowedIPListCommand> loadAllowed = mockStatic(LoadAllowedIPListCommand.class)) {

      loadAllowed.when(LoadAllowedIPListCommand::retrieveCachedIpAddressList)
          .thenReturn(new ArrayList<>(List.of("2001:DB8::1")));

      DataException thrown = Assertions.assertThrows(DataException.class, () -> SaveAllowedIPCommand.save(bean));
      Assertions.assertTrue(thrown.getMessage().toLowerCase().contains("already"),
          "the error should explain this is a duplicate of an existing entry");

      allowedRepo.verify(() -> AllowedIPRepository.save(any(AllowedIP.class)), never());
    }
  }

  @Test
  void editingARecordWithoutChangingItsAddressIsNotFlaggedAsADuplicateOfItself() throws DataException {
    AllowedIP existing = new AllowedIP();
    existing.setId(7L);
    existing.setIpAddress("2001:db8::1");

    AllowedIP bean = new AllowedIP();
    bean.setId(7L);
    bean.setIpAddress("2001:DB8::1"); // same address, different case -- e.g. only the reason changed
    bean.setReason("updated reason");

    try (MockedStatic<AllowedIPRepository> allowedRepo = mockStatic(AllowedIPRepository.class);
        MockedStatic<LoadAllowedIPListCommand> loadAllowed = mockStatic(LoadAllowedIPListCommand.class);
        MockedStatic<LoadBlockedIPListCommand> loadBlocked = mockStatic(LoadBlockedIPListCommand.class)) {

      allowedRepo.when(() -> AllowedIPRepository.findById(eq(7L))).thenReturn(existing);
      allowedRepo.when(() -> AllowedIPRepository.save(any(AllowedIP.class))).thenAnswer(inv -> inv.getArgument(0));
      loadAllowed.when(LoadAllowedIPListCommand::retrieveCachedIpAddressList)
          .thenReturn(new ArrayList<>(List.of("2001:db8::1")));
      loadBlocked.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());

      AllowedIP saved = SaveAllowedIPCommand.save(bean);
      Assertions.assertEquals("2001:DB8::1", saved.getIpAddress());
    }
  }

  @Test
  void savingAnAllowedEntryWarnsWhenItShadowsAnExistingBlockedEntry() throws DataException {
    AllowedIP bean = new AllowedIP();
    bean.setIpAddress("203.0.113.10");

    try (MockedStatic<AllowedIPRepository> allowedRepo = mockStatic(AllowedIPRepository.class);
        MockedStatic<LoadAllowedIPListCommand> loadAllowed = mockStatic(LoadAllowedIPListCommand.class);
        MockedStatic<LoadBlockedIPListCommand> loadBlocked = mockStatic(LoadBlockedIPListCommand.class)) {

      allowedRepo.when(() -> AllowedIPRepository.save(any(AllowedIP.class))).thenAnswer(inv -> inv.getArgument(0));
      loadAllowed.when(LoadAllowedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());
      loadBlocked.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList)
          .thenReturn(new ArrayList<>(List.of("203.0.113.0/24")));

      AllowedIP saved = SaveAllowedIPCommand.save(bean);

      Assertions.assertNotNull(saved, "the save itself must still succeed -- allow-over-block is intentional");
      String warning = SaveAllowedIPCommand.getLastConflictWarning();
      Assertions.assertNotNull(warning, "a covering Blocked IP entry should produce a warning");
      Assertions.assertTrue(warning.contains("203.0.113.0/24"));
      Assertions.assertTrue(warning.toLowerCase().contains("blocked"));
    }
  }

  @Test
  void noWarningWhenThereIsNoCrossListConflict() throws DataException {
    AllowedIP bean = new AllowedIP();
    bean.setIpAddress("198.51.100.1");

    try (MockedStatic<AllowedIPRepository> allowedRepo = mockStatic(AllowedIPRepository.class);
        MockedStatic<LoadAllowedIPListCommand> loadAllowed = mockStatic(LoadAllowedIPListCommand.class);
        MockedStatic<LoadBlockedIPListCommand> loadBlocked = mockStatic(LoadBlockedIPListCommand.class)) {

      allowedRepo.when(() -> AllowedIPRepository.save(any(AllowedIP.class))).thenAnswer(inv -> inv.getArgument(0));
      loadAllowed.when(LoadAllowedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());
      loadBlocked.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());

      SaveAllowedIPCommand.save(bean);

      Assertions.assertNull(SaveAllowedIPCommand.getLastConflictWarning());
    }
  }

  @Test
  void aConflictWarningDoesNotLeakIntoASubsequentUnrelatedSaveOnTheSameThread() throws DataException {
    try (MockedStatic<AllowedIPRepository> allowedRepo = mockStatic(AllowedIPRepository.class);
        MockedStatic<LoadAllowedIPListCommand> loadAllowed = mockStatic(LoadAllowedIPListCommand.class);
        MockedStatic<LoadBlockedIPListCommand> loadBlocked = mockStatic(LoadBlockedIPListCommand.class)) {

      allowedRepo.when(() -> AllowedIPRepository.save(any(AllowedIP.class))).thenAnswer(inv -> inv.getArgument(0));
      loadAllowed.when(LoadAllowedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());

      // First save conflicts with an existing Blocked entry
      loadBlocked.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList)
          .thenReturn(new ArrayList<>(List.of("203.0.113.10")));
      AllowedIP conflicting = new AllowedIP();
      conflicting.setIpAddress("203.0.113.10");
      SaveAllowedIPCommand.save(conflicting);
      Assertions.assertNotNull(SaveAllowedIPCommand.getLastConflictWarning());

      // A second save on the same worker thread has nothing to warn about
      loadBlocked.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());
      AllowedIP clean = new AllowedIP();
      clean.setIpAddress("198.51.100.7");
      SaveAllowedIPCommand.save(clean);

      Assertions.assertNull(SaveAllowedIPCommand.getLastConflictWarning(),
          "a warning left over from an earlier save on a reused thread must not attach itself to a later, unrelated save");
    }
  }

  @Test
  void editingAnExistingRecordToANarrowerAddressRemovesTheOldValueFromTheCache() throws DataException {
    AllowedIP existing = new AllowedIP();
    existing.setId(3L);
    existing.setIpAddress("203.0.113.0/24");

    AllowedIP bean = new AllowedIP();
    bean.setId(3L);
    bean.setIpAddress("203.0.113.5"); // narrowed from the /24 range to a single address

    try (MockedStatic<AllowedIPRepository> allowedRepo = mockStatic(AllowedIPRepository.class);
        MockedStatic<LoadAllowedIPListCommand> loadAllowed = mockStatic(LoadAllowedIPListCommand.class);
        MockedStatic<LoadBlockedIPListCommand> loadBlocked = mockStatic(LoadBlockedIPListCommand.class)) {

      allowedRepo.when(() -> AllowedIPRepository.findById(eq(3L))).thenReturn(existing);
      allowedRepo.when(() -> AllowedIPRepository.save(any(AllowedIP.class))).thenAnswer(inv -> inv.getArgument(0));
      loadAllowed.when(LoadAllowedIPListCommand::retrieveCachedIpAddressList)
          .thenReturn(new ArrayList<>(List.of("203.0.113.0/24")));
      loadBlocked.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());

      SaveAllowedIPCommand.save(bean);

      loadAllowed.verify(() -> LoadAllowedIPListCommand.removeIpFromCache("203.0.113.0/24"), times(1));
      loadAllowed.verify(() -> LoadAllowedIPListCommand.addIpToCache(
          argThat((AllowedIP ip) -> "203.0.113.5".equals(ip.getIpAddress()))), times(1));
    }
  }

  @Test
  void editingAnExistingRecordWithoutChangingItsAddressNeverRemovesAnythingFromTheCache() throws DataException {
    AllowedIP existing = new AllowedIP();
    existing.setId(4L);
    existing.setIpAddress("203.0.113.9");

    AllowedIP bean = new AllowedIP();
    bean.setId(4L);
    bean.setIpAddress("203.0.113.9");
    bean.setReason("just updating the reason");

    try (MockedStatic<AllowedIPRepository> allowedRepo = mockStatic(AllowedIPRepository.class);
        MockedStatic<LoadAllowedIPListCommand> loadAllowed = mockStatic(LoadAllowedIPListCommand.class);
        MockedStatic<LoadBlockedIPListCommand> loadBlocked = mockStatic(LoadBlockedIPListCommand.class)) {

      allowedRepo.when(() -> AllowedIPRepository.findById(eq(4L))).thenReturn(existing);
      allowedRepo.when(() -> AllowedIPRepository.save(any(AllowedIP.class))).thenAnswer(inv -> inv.getArgument(0));
      loadAllowed.when(LoadAllowedIPListCommand::retrieveCachedIpAddressList)
          .thenReturn(new ArrayList<>(List.of("203.0.113.9")));
      loadBlocked.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());

      SaveAllowedIPCommand.save(bean);

      loadAllowed.verify(() -> LoadAllowedIPListCommand.removeIpFromCache(anyString()), never());
      loadAllowed.verify(() -> LoadAllowedIPListCommand.addIpToCache(any(AllowedIP.class)), times(1));
    }
  }

  @Test
  void findCoveringEntryDetectsOverlapRegardlessOfWhichSideIsTheRange() {
    List<String> range = List.of("203.0.113.0/24");

    // A single address inside an existing CIDR range
    Assertions.assertEquals("203.0.113.0/24", SaveAllowedIPCommand.findCoveringEntry("203.0.113.42", range));

    // A CIDR range that contains an existing single address
    Assertions.assertEquals("203.0.113.42",
        SaveAllowedIPCommand.findCoveringEntry("203.0.113.0/24", List.of("203.0.113.42")));

    // An exact match
    Assertions.assertEquals("198.51.100.1",
        SaveAllowedIPCommand.findCoveringEntry("198.51.100.1", List.of("198.51.100.1")));

    // No overlap
    Assertions.assertNull(SaveAllowedIPCommand.findCoveringEntry("198.51.100.1", range));

    // Different address families never match
    Assertions.assertNull(SaveAllowedIPCommand.findCoveringEntry("2001:db8::1", range));
  }
}
