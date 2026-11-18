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

package com.simisinc.platform.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Keeps {@link UserRepository#removeAndReassign} from handing an account's own rows to the holding
 * account. It reassigns every reference to users it does not know to be part of the account, which
 * is right for created_by and the like, and wrong for a table that says what an account IS or MAY
 * DO: reassigning a membership or a grant gives it to the holding account. Those tables reference
 * users through a column named user_id, so each such column in the install schema has to be either
 * deleted with the account or listed here as deliberately reassigned.
 *
 * @author SimIS Inc.
 */
class UserReferenceClassificationTest {

  private static final Path INSTALL_DIR = Path.of("src/main/resources/database/install");

  /**
   * user_id columns that are reassigned, not deleted, and why. carts: a cart has line items and
   * may be tied to an order, so it is kept, owned by the holding account.
   */
  private static final Set<String> REASSIGNED_USER_ID_COLUMNS = Set.of("carts.user_id");

  private static final Pattern CREATE_TABLE = Pattern.compile(
      "CREATE TABLE (?:IF NOT EXISTS )?(\\w+)\\s*\\((.*?)\\);", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern USER_ID_REFERENCE = Pattern.compile(
      "^\\s*user_id\\s+(?:BIGINT|INTEGER|INT)\\b.*REFERENCES\\s+users\\s*\\(", Pattern.CASE_INSENSITIVE);

  @Test
  void everyUserIdReferenceIsDeletedWithTheAccountOrDeliberatelyReassigned() throws IOException {
    Set<String> unclassified = new TreeSet<>();
    for (String reference : userIdReferencesInTheInstallSchema()) {
      if (!UserRepository.ACCOUNT_REFERENCES.contains(reference) && !REASSIGNED_USER_ID_COLUMNS.contains(reference)) {
        unclassified.add(reference);
      }
    }
    assertEquals(Set.of(), unclassified,
        "Decide what deleting an account does to these: if a row is part of the account (a membership, a grant, a "
            + "token), add it to UserRepository.ACCOUNT_ROWS and ACCOUNT_REFERENCES; if it records something the "
            + "person did and should be kept, add it to REASSIGNED_USER_ID_COLUMNS with the reason");
  }

  @Test
  void everyTableTheDeleteClearsIsInTheSchema() throws IOException {
    Set<String> inSchema = userIdReferencesInTheInstallSchema();
    for (String reference : UserRepository.ACCOUNT_REFERENCES) {
      assertTrue(inSchema.contains(reference), reference + " is listed as part of the account but is not in the schema");
    }
  }

  private static Set<String> userIdReferencesInTheInstallSchema() throws IOException {
    Set<String> references = new TreeSet<>();
    try (Stream<Path> files = Files.list(INSTALL_DIR)) {
      for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".sql")).sorted()::iterator) {
        String sql = Files.readString(file, StandardCharsets.UTF_8).replaceAll("--[^\\n]*", "");
        Matcher table = CREATE_TABLE.matcher(sql);
        while (table.find()) {
          for (String line : table.group(2).split("\\n")) {
            if (USER_ID_REFERENCE.matcher(line).find()) {
              references.add(table.group(1).toLowerCase() + ".user_id");
            }
          }
        }
      }
    }
    assertTrue(references.size() >= 10, "the install schema parse found only " + references);
    return references;
  }
}
