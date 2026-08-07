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

package com.simisinc.platform.application.datasets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.sql.Timestamp;
import java.util.Calendar;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.items.SaveItemCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;

/**
 * Verifies that dataset-import field mappings for the date fields and assignedTo are
 * actually applied -- previously these were empty branches that silently dropped the
 * value. The guarantees under test: recognizable dates parse, an unrecognizable or
 * invalid date is skipped (never stored wrong), and assignedTo resolves a username to a
 * user id or is skipped rather than assigned to a bogus/wrong user.
 *
 * @author Liz Houser
 * @created 7/23/2026
 */
class SaveDatasetRowCommandTest {

  private static void assertYmd(Timestamp ts, int year, int month0Based, int day) {
    Assertions.assertNotNull(ts, "expected a parsed timestamp, not null");
    Calendar c = Calendar.getInstance();
    c.setTime(ts);
    Assertions.assertEquals(year, c.get(Calendar.YEAR));
    Assertions.assertEquals(month0Based, c.get(Calendar.MONTH));
    Assertions.assertEquals(day, c.get(Calendar.DAY_OF_MONTH));
  }

  @Test
  void parsesIsoDate() {
    assertYmd(SaveDatasetRowCommand.parseTimestamp("2026-01-15"), 2026, Calendar.JANUARY, 15);
  }

  @Test
  void parsesSpaceSeparatedDateTime() {
    Timestamp ts = SaveDatasetRowCommand.parseTimestamp("2026-01-15 14:30:00");
    assertYmd(ts, 2026, Calendar.JANUARY, 15);
    Calendar c = Calendar.getInstance();
    c.setTime(ts);
    Assertions.assertEquals(14, c.get(Calendar.HOUR_OF_DAY));
    Assertions.assertEquals(30, c.get(Calendar.MINUTE));
  }

  @Test
  void parsesUsSlashDate() {
    assertYmd(SaveDatasetRowCommand.parseTimestamp("01/15/2026"), 2026, Calendar.JANUARY, 15);
  }

  @Test
  void parsesIsoUtcInstant() {
    assertYmd(SaveDatasetRowCommand.parseTimestamp("2026-01-15T14:30:00Z"), 2026, Calendar.JANUARY, 15);
  }

  @Test
  void unparseableDateReturnsNullRatherThanWrongData() {
    Assertions.assertNull(SaveDatasetRowCommand.parseTimestamp("not a date"));
  }

  @Test
  void invalidCalendarDateRejectedByStrictParsing() {
    // Lenient parsing would roll "month 13, day 45" over into a real (wrong) date;
    // strict parsing must reject it so no nonsense date is imported.
    Assertions.assertNull(SaveDatasetRowCommand.parseTimestamp("2026-13-45"));
  }

  @Test
  void assignedToResolvesUsernameToUserId() {
    User user = new User();
    user.setId(42L);
    try (MockedStatic<UserRepository> repo = mockStatic(UserRepository.class)) {
      repo.when(() -> UserRepository.findByUsername(eq("jsmith"))).thenReturn(user);

      Assertions.assertEquals(42L, SaveDatasetRowCommand.resolveAssignedToUserId("jsmith"));
    }
  }

  @Test
  void assignedToUnknownUserReturnsMinusOneRatherThanBogusId() {
    try (MockedStatic<UserRepository> repo = mockStatic(UserRepository.class)) {
      repo.when(() -> UserRepository.findByUsername(any())).thenReturn(null);

      Assertions.assertEquals(-1L, SaveDatasetRowCommand.resolveAssignedToUserId("ghost"));
    }
  }

  /**
   * Regression test for a bug found reviewing issue #815's fix: a brand-new item (one the
   * dataset's unique-column lookup did not match to an existing row) goes straight from {@link
   * SaveDatasetRowCommand#saveRecord} to {@link SaveItemCommand#saveBatchItem} to {@code
   * ItemRepository.save}, bypassing {@code SaveItemCommand#saveItem}'s insert-only itemOrder
   * copy entirely. Without setting it explicitly first, the new item would persist at the
   * {@link Item} domain model's static default order (100) instead of appending after the
   * collection's existing items -- and a batch sync adding several new rows would collide all
   * of them at the same value.
   */
  @Test
  void saveRecordAppendsANewItemAtTheEndOfTheCollectionRatherThanTheDomainModelsStaticDefault() {
    Collection collection = new Collection();
    collection.setId(5L);

    Dataset dataset = new Dataset();
    dataset.setId(10L);
    dataset.setModifiedBy(1L);
    // No unique column configured, so the row is always treated as a new item rather than an
    // update to a previously-synced one.
    dataset.setColumnNames(new String[]{"Name"});
    dataset.setFieldTitles(new String[]{""});
    dataset.setFieldMappings(new String[]{"name"});
    dataset.setFieldOptions(new String[]{""});

    String[] row = new String[]{"Widget A"};

    try (MockedStatic<ItemRepository> itemRepository = mockStatic(ItemRepository.class);
        MockedStatic<SaveItemCommand> saveItemCommand = mockStatic(SaveItemCommand.class)) {
      // A collection that has already been reordered past the domain model's static default.
      itemRepository.when(() -> ItemRepository.getNextItemOrder(5L)).thenReturn(12);
      saveItemCommand.when(() -> SaveItemCommand.saveBatchItem(any(), any(Item.class))).thenReturn(true);

      boolean saved = SaveDatasetRowCommand.saveRecord(row, dataset, collection);

      Assertions.assertTrue(saved, "sanity check that the save itself was reported as successful");
      ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
      saveItemCommand.verify(() -> SaveItemCommand.saveBatchItem(any(), itemCaptor.capture()));
      Assertions.assertEquals(12, itemCaptor.getValue().getItemOrder(),
          "a newly synced item must append after the collection's existing items "
              + "(getNextItemOrder), not silently fall back to the domain model's static default");
    }
  }

  /**
   * Regression test for the "skipDuplicates" field option being a no-op during a real sync:
   * {@link SaveDatasetRowCommand#constructItem} used to call {@code isSkipped(options, value)},
   * the 2-arg overload that always checks against a null map/-1 column id and so can never
   * detect a repeat. A real sync feeds rows into {@link SaveDatasetRowCommand#saveRecord} one at
   * a time (from a streaming parser or a per-row loop), so nothing carried a seen-value map
   * forward from one call to the next -- unlike Preview (LoadCSVRowsCommand, LoadJsonCommand,
   * LoadTSVRowsCommand), which already builds exactly that kind of map for a single load. This
   * proves the second occurrence of a value in a "skipDuplicates" column is now dropped during a
   * real sync, matching what Preview already reports.
   */
  @Test
  void skipDuplicatesOptionDropsARepeatedValueAcrossSeparateSaveRecordCalls() {
    Collection collection = new Collection();
    collection.setId(5L);

    Dataset dataset = new Dataset();
    dataset.setId(99L);
    dataset.setModifiedBy(1L);
    dataset.setColumnNames(new String[] { "Name" });
    dataset.setFieldTitles(new String[] { "" });
    dataset.setFieldMappings(new String[] { "name" });
    dataset.setFieldOptions(new String[] { "skipDuplicates" });

    String[] firstRow = new String[] { "Widget A" };
    String[] duplicateRow = new String[] { "Widget A" };

    try (MockedStatic<ItemRepository> itemRepository = mockStatic(ItemRepository.class);
        MockedStatic<SaveItemCommand> saveItemCommand = mockStatic(SaveItemCommand.class)) {
      itemRepository.when(() -> ItemRepository.getNextItemOrder(5L)).thenReturn(1);
      saveItemCommand.when(() -> SaveItemCommand.saveBatchItem(any(), any(Item.class))).thenReturn(true);

      boolean firstSaved = SaveDatasetRowCommand.saveRecord(firstRow, dataset, collection);
      boolean secondSaved = SaveDatasetRowCommand.saveRecord(duplicateRow, dataset, collection);

      Assertions.assertTrue(firstSaved, "the first occurrence of a value is never a duplicate");
      Assertions.assertTrue(secondSaved,
          "a row skipped on purpose still reports success -- it isn't a save failure");
      saveItemCommand.verify(() -> SaveItemCommand.saveBatchItem(any(), any(Item.class)), times(1));
    } finally {
      SaveDatasetRowCommand.clearDuplicateTracking(dataset);
    }
  }

  /**
   * Regression test for {@link SaveDatasetRowCommand#clearDuplicateTracking}: without it, a
   * dataset's "skipDuplicates" state from one sync would still be present the next time the
   * same dataset is synced, incorrectly treating that later run's first row as a repeat of a
   * value from a prior run. {@link com.simisinc.platform.application.datasets.DatasetFileCommand}
   * calls this once a dataset's file has been fully converted.
   */
  @Test
  void clearDuplicateTrackingResetsSkipDuplicatesStateForALaterSync() {
    Collection collection = new Collection();
    collection.setId(6L);

    Dataset dataset = new Dataset();
    dataset.setId(100L);
    dataset.setModifiedBy(1L);
    dataset.setColumnNames(new String[] { "Name" });
    dataset.setFieldTitles(new String[] { "" });
    dataset.setFieldMappings(new String[] { "name" });
    dataset.setFieldOptions(new String[] { "skipDuplicates" });

    String[] row = new String[] { "Widget B" };

    try (MockedStatic<ItemRepository> itemRepository = mockStatic(ItemRepository.class);
        MockedStatic<SaveItemCommand> saveItemCommand = mockStatic(SaveItemCommand.class)) {
      itemRepository.when(() -> ItemRepository.getNextItemOrder(6L)).thenReturn(1);
      saveItemCommand.when(() -> SaveItemCommand.saveBatchItem(any(), any(Item.class))).thenReturn(true);

      SaveDatasetRowCommand.saveRecord(row, dataset, collection);
      SaveDatasetRowCommand.clearDuplicateTracking(dataset);
      SaveDatasetRowCommand.saveRecord(row, dataset, collection);

      // Without the reset, the second sync's row would incorrectly look like a repeat of the
      // previous sync's row and get skipped instead of saved
      saveItemCommand.verify(() -> SaveItemCommand.saveBatchItem(any(), any(Item.class)), times(2));
    } finally {
      SaveDatasetRowCommand.clearDuplicateTracking(dataset);
    }
  }
}
