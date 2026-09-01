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

package com.simisinc.platform.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The length check shared by the Save*Commands (issue #1740).
 *
 * @author SimIS Inc.
 */
class FieldLengthCommandTest {

  @Test
  void aValueExactlyAtTheLimitFits() {
    // the column holds N, so N must save -- an off-by-one here refuses a legitimate entry
    assertFalse(FieldLengthCommand.exceedsLimit("x".repeat(100), 100));
  }

  @Test
  void oneCharacterOverTheLimitDoesNot() {
    assertTrue(FieldLengthCommand.exceedsLimit("x".repeat(101), 100));
  }

  @Test
  void trailingWhitespaceDoesNotPushAValueOverTheLimit() {
    // repositories trim before writing, so the stored length is the trimmed length -- measuring the
    // raw string would refuse a save the database would have accepted, which pasting easily produces
    assertFalse(FieldLengthCommand.exceedsLimit("x".repeat(100) + "     ", 100));
  }

  @Test
  void leadingWhitespaceIsTrimmedTheSameWay() {
    assertFalse(FieldLengthCommand.exceedsLimit("   " + "x".repeat(100), 100));
  }

  @Test
  void aNullValueIsNotTooLong() {
    // an absent optional field must not be reported as over-length
    assertFalse(FieldLengthCommand.exceedsLimit(null, 100));
  }

  @Test
  void theMessageNamesTheFieldAndTheActualLimit() {
    // the whole point of the issue: the admin was told "a system error" and to retry, with nothing
    // saying which field or what the limit was
    assertEquals("A name can be up to 100 characters",
        FieldLengthCommand.tooLongMessage("A name", 100));
  }

  @Test
  void appendingToAnEmptyMessageAddsNoSeparator() {
    StringBuilder errorMessages = new StringBuilder();

    assertTrue(FieldLengthCommand.appendIfTooLong(errorMessages, ", ", "A name", "x".repeat(101), 100));

    assertEquals("A name can be up to 100 characters", errorMessages.toString());
  }

  @Test
  void appendingToAnExistingMessageUsesTheCommandsOwnSeparator() {
    StringBuilder errorMessages = new StringBuilder("A unique name is required");

    FieldLengthCommand.appendIfTooLong(errorMessages, ", ", "A name", "x".repeat(101), 100);

    assertEquals("A unique name is required, A name can be up to 100 characters", errorMessages.toString());
  }

  @Test
  void aValueThatFitsAppendsNothingAndReportsSo() {
    StringBuilder errorMessages = new StringBuilder();

    assertFalse(FieldLengthCommand.appendIfTooLong(errorMessages, ", ", "A name", "Marketing", 100));

    assertEquals("", errorMessages.toString());
  }
}
