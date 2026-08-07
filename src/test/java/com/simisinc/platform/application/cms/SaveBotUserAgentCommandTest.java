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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.BotUserAgent;
import com.simisinc.platform.infrastructure.persistence.BotUserAgentRepository;

/**
 * Matching is a raw substring test against every visitor's User-Agent header (see
 * SessionCommand.checkForBot()), so a short/generic signature has an outsized blast radius -- it can
 * misclassify a large fraction of real traffic as bot. Confirms save() rejects values shorter than
 * the shortest legitimate signature already shipped in the seed data ("Slurp", 5 characters -- see
 * NEW_10190__new_bot_list.sql), and confirms that updating an existing record's value corrects the
 * in-memory cache (removes the stale old value, adds the new one) rather than only ever appending to
 * it.
 */
class SaveBotUserAgentCommandTest {

  @Test
  void aValueShorterThanTheMinimumIsRejected() {
    BotUserAgent bean = new BotUserAgent();
    bean.setUserAgent("abcd"); // 4 characters -- one short of the 5-character minimum

    try (MockedStatic<BotUserAgentRepository> repository = mockStatic(BotUserAgentRepository.class);
        MockedStatic<LoadBotUserAgentListCommand> cache = mockStatic(LoadBotUserAgentListCommand.class)) {

      DataException thrown = Assertions.assertThrows(DataException.class, () -> SaveBotUserAgentCommand.save(bean));
      Assertions.assertTrue(thrown.getMessage().toLowerCase().contains("short"),
          "Expected a too-short/generic error message, got: " + thrown.getMessage());

      repository.verify(() -> BotUserAgentRepository.save(any(BotUserAgent.class)), never());
      cache.verify(() -> LoadBotUserAgentListCommand.addToCache(any(BotUserAgent.class)), never());
    }
  }

  @Test
  void aBlankValueIsStillRejectedWithTheOriginalMessage() {
    BotUserAgent bean = new BotUserAgent();
    bean.setUserAgent("   ");

    DataException thrown = Assertions.assertThrows(DataException.class, () -> SaveBotUserAgentCommand.save(bean));
    Assertions.assertTrue(thrown.getMessage().contains("required"));
  }

  @Test
  void aPaddedValueIsTrimmedBeforeTheMinimumLengthCheckAndBeforeSaving() throws Exception {
    // Guards the trimToNull() call itself, not just its net effect: "  ab  " is 6 raw characters
    // (clearing the 5-character minimum on its untrimmed length) but only 2 characters of real
    // content once trimmed -- if trimToNull() were reverted to using the raw value, this padded
    // string would wrongly slide past the length guard, and the untrimmed value would get cached
    // via LoadBotUserAgentListCommand.addToCache(), diverging from what the DB layer independently
    // trims into the stored row (ProcessBotListCSVFileCommand's CSV-import path never pre-trims,
    // so save() must be the one place this is guaranteed).
    BotUserAgent bean = new BotUserAgent();
    bean.setUserAgent("  ab  ");

    DataException thrown = Assertions.assertThrows(DataException.class, () -> SaveBotUserAgentCommand.save(bean));
    Assertions.assertTrue(thrown.getMessage().toLowerCase().contains("short"),
        "Expected a too-short/generic error message, got: " + thrown.getMessage());
  }

  @Test
  void aPaddedValueLongEnoughAfterTrimmingIsSavedTrimmed() throws Exception {
    BotUserAgent bean = new BotUserAgent();
    bean.setUserAgent("  Slurp  ");

    BotUserAgent saved = new BotUserAgent();
    saved.setId(9L);
    saved.setUserAgent("Slurp");

    try (MockedStatic<BotUserAgentRepository> repository = mockStatic(BotUserAgentRepository.class);
        MockedStatic<LoadBotUserAgentListCommand> cache = mockStatic(LoadBotUserAgentListCommand.class)) {
      repository.when(() -> BotUserAgentRepository.save(any(BotUserAgent.class))).thenReturn(saved);

      SaveBotUserAgentCommand.save(bean);

      ArgumentCaptor<BotUserAgent> captor = ArgumentCaptor.forClass(BotUserAgent.class);
      repository.verify(() -> BotUserAgentRepository.save(captor.capture()));
      Assertions.assertEquals("Slurp", captor.getValue().getUserAgent(),
          "the value passed to the repository must already be trimmed, not the padded raw input");
    }
  }

  @Test
  void theShortestRealSeedValueIsAccepted() throws Exception {
    // "Slurp" (Yahoo), 5 characters -- the shortest signature already shipped in
    // NEW_10190__new_bot_list.sql; the minimum-length guard must not reject it
    BotUserAgent bean = new BotUserAgent();
    bean.setUserAgent("Slurp");

    BotUserAgent saved = new BotUserAgent();
    saved.setId(9L);
    saved.setUserAgent("Slurp");

    try (MockedStatic<BotUserAgentRepository> repository = mockStatic(BotUserAgentRepository.class);
        MockedStatic<LoadBotUserAgentListCommand> cache = mockStatic(LoadBotUserAgentListCommand.class)) {
      repository.when(() -> BotUserAgentRepository.save(any(BotUserAgent.class))).thenReturn(saved);

      BotUserAgent result = SaveBotUserAgentCommand.save(bean);

      Assertions.assertNotNull(result);
      Assertions.assertEquals("Slurp", result.getUserAgent());
    }
  }

  @Test
  void updatingAnExistingRecordsValueRemovesTheStaleValueFromCache() throws Exception {
    BotUserAgent existing = new BotUserAgent();
    existing.setId(5L);
    existing.setUserAgent("OldSignature");

    BotUserAgent bean = new BotUserAgent();
    bean.setId(5L);
    bean.setUserAgent("NewSignature");

    BotUserAgent saved = new BotUserAgent();
    saved.setId(5L);
    saved.setUserAgent("NewSignature");

    try (MockedStatic<BotUserAgentRepository> repository = mockStatic(BotUserAgentRepository.class);
        MockedStatic<LoadBotUserAgentListCommand> cache = mockStatic(LoadBotUserAgentListCommand.class)) {
      repository.when(() -> BotUserAgentRepository.findById(5L)).thenReturn(existing);
      repository.when(() -> BotUserAgentRepository.save(any(BotUserAgent.class))).thenReturn(saved);

      SaveBotUserAgentCommand.save(bean);

      // The stale pre-update value must be removed -- otherwise addToCache() only ever appends,
      // leaving the old (now-incorrect) substring live in SessionCommand.checkForBot() indefinitely
      ArgumentCaptor<BotUserAgent> removedCaptor = ArgumentCaptor.forClass(BotUserAgent.class);
      cache.verify(() -> LoadBotUserAgentListCommand.removeFromCache(removedCaptor.capture()));
      Assertions.assertEquals("OldSignature", removedCaptor.getValue().getUserAgent());

      ArgumentCaptor<BotUserAgent> addedCaptor = ArgumentCaptor.forClass(BotUserAgent.class);
      cache.verify(() -> LoadBotUserAgentListCommand.addToCache(addedCaptor.capture()));
      Assertions.assertEquals("NewSignature", addedCaptor.getValue().getUserAgent());
    }
  }

  @Test
  void updatingAnExistingRecordWithoutChangingItsValueDoesNotRemoveAnythingFromCache() throws Exception {
    BotUserAgent existing = new BotUserAgent();
    existing.setId(5L);
    existing.setUserAgent("SameSignature");

    BotUserAgent bean = new BotUserAgent();
    bean.setId(5L);
    bean.setUserAgent("SameSignature");
    bean.setLabel("Updated label only");

    BotUserAgent saved = new BotUserAgent();
    saved.setId(5L);
    saved.setUserAgent("SameSignature");

    try (MockedStatic<BotUserAgentRepository> repository = mockStatic(BotUserAgentRepository.class);
        MockedStatic<LoadBotUserAgentListCommand> cache = mockStatic(LoadBotUserAgentListCommand.class)) {
      repository.when(() -> BotUserAgentRepository.findById(5L)).thenReturn(existing);
      repository.when(() -> BotUserAgentRepository.save(any(BotUserAgent.class))).thenReturn(saved);

      SaveBotUserAgentCommand.save(bean);

      cache.verify(() -> LoadBotUserAgentListCommand.removeFromCache(any(BotUserAgent.class)), never());
      cache.verify(() -> LoadBotUserAgentListCommand.addToCache(any(BotUserAgent.class)), times(1));
    }
  }
}
