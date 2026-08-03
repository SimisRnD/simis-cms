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

package com.simisinc.platform.domain.model.cms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;

/**
 * Covers FileItem.isExpired()/isExpiringSoon() (issue #502) -- unlike WebPage's isScheduled()/
 * isExpiringSoon() (WebPage.java), which just test whether a timestamp is in the future with no
 * window, FileItem uses a fixed 30-day lead time for "expiring soon" and a separate isExpired()
 * for a date that has already passed. See the comment on FileItem.EXPIRING_SOON_WINDOW_MILLIS.
 */
class FileItemTest {

  private static final long ONE_DAY_MILLIS = 24L * 60 * 60 * 1000;

  @Test
  void aFileWithNoExpirationDateIsNotExpired() {
    assertFalse(new FileItem().isExpired());
  }

  @Test
  void aFileWithNoExpirationDateIsNotExpiringSoon() {
    assertFalse(new FileItem().isExpiringSoon());
  }

  @Test
  void aFileWithAPastExpirationDateIsExpired() {
    FileItem fileItem = new FileItem();
    fileItem.setExpirationDate(new Timestamp(System.currentTimeMillis() - 60_000));
    assertTrue(fileItem.isExpired());
  }

  @Test
  void aFileWithAFutureExpirationDateIsNotExpired() {
    FileItem fileItem = new FileItem();
    fileItem.setExpirationDate(new Timestamp(System.currentTimeMillis() + 60_000));
    assertFalse(fileItem.isExpired());
  }

  @Test
  void aFileWithAPastExpirationDateIsNotExpiringSoon() {
    FileItem fileItem = new FileItem();
    fileItem.setExpirationDate(new Timestamp(System.currentTimeMillis() - 60_000));
    assertFalse(fileItem.isExpiringSoon());
  }

  @Test
  void aFileExpiringWithinThirtyDaysIsExpiringSoon() {
    FileItem fileItem = new FileItem();
    fileItem.setExpirationDate(new Timestamp(System.currentTimeMillis() + (10 * ONE_DAY_MILLIS)));
    assertTrue(fileItem.isExpiringSoon());
  }

  @Test
  void aFileExpiringMoreThanThirtyDaysOutIsNotExpiringSoon() {
    FileItem fileItem = new FileItem();
    fileItem.setExpirationDate(new Timestamp(System.currentTimeMillis() + (60 * ONE_DAY_MILLIS)));
    assertFalse(fileItem.isExpiringSoon());
  }

  @Test
  void aFileExpiringSoonIsNotAlsoFlaggedAsExpired() {
    FileItem fileItem = new FileItem();
    fileItem.setExpirationDate(new Timestamp(System.currentTimeMillis() + (10 * ONE_DAY_MILLIS)));
    assertFalse(fileItem.isExpired());
  }
}
