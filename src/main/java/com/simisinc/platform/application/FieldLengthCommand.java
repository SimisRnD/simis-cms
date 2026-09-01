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

import org.apache.commons.lang3.StringUtils;

/**
 * Checks a user-entered value against the width of the column it is about to be written to.
 *
 * <p>Issue #1740: almost no Save*Command did this, so an over-length entry was not refused anywhere
 * on the way down. It reached Postgres, which rejected the write; the repository logged the
 * SQLException and returned null; and the form widget read that null as a system fault and told the
 * admin "Your information could not be saved due to a system error. Please try again." -- advice
 * that cannot work, because the same value fails identically every time. Nothing named the field,
 * the limit, or the fact that length was the problem.
 *
 * <p><b>Refuse, do not truncate.</b> Silently storing a shortened version of what someone typed is
 * its own bug, and worse on a field other records resolve by. Truncation stays correct for
 * machine-generated values with no user at the keyboard -- SessionRepository abbreviates user_agent
 * and referer, AuditLogRepository truncates its own metadata -- but a human filling in a form gets
 * told what the limit is.
 *
 * <p><b>The limit is the trimmed length.</b> Repositories trim before writing, so a value at exactly
 * the maximum followed by whitespace still fits. Measuring the raw string would refuse a save the
 * database would have accepted, which is easy to hit by pasting.
 *
 * <p>Limits are declared as constants on each command, next to the validation that uses them, and
 * carry an {@code @column table.column} comment. tools/check-column-length-limits.py compares every
 * declared constant against the width in the schema and fails the build when they disagree, so a
 * migration that narrows a column cannot leave behind a check that passes values the database then
 * rejects.
 *
 * @author SimIS Inc.
 */
public class FieldLengthCommand {

  private FieldLengthCommand() {
    // static helper
  }

  /**
   * Whether the value would be too long for its column once trimmed.
   *
   * @param value the submitted value, which may be null
   * @param maxLength the column width
   * @return true when the value cannot be stored
   */
  public static boolean exceedsLimit(String value, int maxLength) {
    return StringUtils.trimToEmpty(value).length() > maxLength;
  }

  /**
   * The message shown when a value is too long, phrased so the admin can act on it: which field,
   * and what the limit actually is.
   *
   * @param fieldLabel the field as the form labels it, e.g. "A name"
   * @param maxLength the column width
   */
  public static String tooLongMessage(String fieldLabel, int maxLength) {
    return fieldLabel + " can be up to " + maxLength + " characters";
  }

  /**
   * Appends the too-long message to a command's running validation message when the value does not
   * fit, using that command's own separator so the result reads as one sentence rather than two
   * messages run together.
   *
   * @param errorMessages the command's accumulating validation message
   * @param separator the separator this command already uses between messages
   * @param fieldLabel the field as the form labels it, e.g. "A name"
   * @param value the submitted value
   * @param maxLength the column width
   * @return true when a message was appended
   */
  public static boolean appendIfTooLong(StringBuilder errorMessages, String separator, String fieldLabel,
      String value, int maxLength) {
    if (!exceedsLimit(value, maxLength)) {
      return false;
    }
    if (errorMessages.length() > 0) {
      errorMessages.append(separator);
    }
    errorMessages.append(tooLongMessage(fieldLabel, maxLength));
    return true;
  }
}
