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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

/**
 * @author matt rajkowski
 * @created 5/3/2022 7:00 PM
 */
class DateCommandTest {

  @Test
  void isAfterNow() {
    Timestamp futureTime = new Timestamp(System.currentTimeMillis() + 10_000L);
    Assertions.assertTrue(DateCommand.isAfterNow(futureTime));
  }

  @Test
  void isBeforeNow() {
    Timestamp pastTime = new Timestamp(System.currentTimeMillis() - 10_000L);
    Assertions.assertTrue(DateCommand.isBeforeNow(pastTime));
  }

  @Test
  void isMinutesOld() {
    Timestamp pastTime = new Timestamp(System.currentTimeMillis() - (10 * 60 * 1_000L));
    Assertions.assertFalse(DateCommand.isMinutesOld(pastTime, 11));
    Assertions.assertTrue(DateCommand.isMinutesOld(pastTime, 9));
  }

  @Test
  void relative() {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    Assertions.assertEquals("just now", DateCommand.relative(now));

    Timestamp futureTime = new Timestamp(System.currentTimeMillis() + 10 * 60 * 1_000L);
    Assertions.assertEquals("within 10 minutes", DateCommand.relative(futureTime));

    Timestamp pastTime = new Timestamp(System.currentTimeMillis() - (10 * 60 * 1_000L));
    Assertions.assertEquals("10 minutes ago", DateCommand.relative(pastTime));
  }

  @Test
  void addDays() {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    Timestamp in10Days = DateCommand.addDays(now, 10);
    Assertions.assertEquals("within 10 days", DateCommand.relative(in10Days));
  }
}