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

package com.simisinc.platform.infrastructure.persistence.mailinglists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies the distinct-subscriber count, trend, and deliverability-classification-breakdown
 * methods (issue #562) against a real PostgreSQL instance. Minimal schema replicated from
 * {@code NEW_10070__new_mailing_lists.sql} -- emails, mailing_lists, mailing_list_members only,
 * without the users-table foreign keys (nullable, not needed for these tests). The emails table
 * additionally includes the validation_status/validated_at columns added by #574's
 * UPGRADE_20260728.2000__email_classification.sql.
 *
 * @author SimIS Inc.
 * @created 7/28/2026
 */
class MailingListMemberRepositoryQueryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping mailing list metrics integration test");

    postgres = new GenericContainer<>(DockerImageName.parse(resolveImage()))
        .withEnv("POSTGRES_USER", DB_USER)
        .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
        .withEnv("POSTGRES_DB", DB_NAME)
        .withExposedPorts(POSTGRES_PORT)
        .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2)
            .withStartupTimeout(Duration.ofSeconds(120)));
    try {
      postgres.start();
    } catch (Throwable t) {
      Assumptions.abort("Unable to start PostgreSQL test container: " + t.getMessage());
    }

    String jdbcUrl = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(POSTGRES_PORT)
        + "/" + DB_NAME;
    Properties properties = new Properties();
    properties.setProperty("jdbcUrl", jdbcUrl);
    properties.setProperty("username", DB_USER);
    properties.setProperty("password", DB_PASSWORD);
    DataSource.init(properties);

    createSchema();
  }

  @AfterAll
  static void stopDatabase() {
    try {
      DataSource.shutdown();
    } catch (Exception e) {
      // Never initialized when Docker is unavailable
    }
    if (postgres != null) {
      postgres.stop();
    }
  }

  @BeforeEach
  void clearTables() throws SQLException {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping mailing list metrics integration test");
    try (Connection connection = DB.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE mailing_list_members, mailing_lists, emails RESTART IDENTITY CASCADE");
    }
  }

  @Test
  void countDistinctSubscribersDoesNotDoubleCountAPersonOnMultipleLists() throws SQLException {
    long listA = seedList("List A");
    long listB = seedList("List B");
    long alice = seedEmail("alice@example.com");
    long bob = seedEmail("bob@example.com");

    seedMembership(listA, alice, true, null);
    seedMembership(listB, alice, true, null); // alice is on both lists -- must count once
    seedMembership(listA, bob, true, null);

    assertEquals(2, MailingListMemberRepository.countDistinctSubscribers(), "alice + bob, not 3 membership rows");
  }

  @Test
  void countActiveSubscribersExcludesInvalidatedMembers() throws SQLException {
    long list = seedList("List A");
    seedMembership(list, seedEmail("active@example.com"), true, null);
    seedMembership(list, seedEmail("invalid@example.com"), false, "2026-07-01 00:00:00");

    assertEquals(1, MailingListMemberRepository.countActiveSubscribers());
  }

  @Test
  void countUnsubscribedCountsDistinctPeopleNotRows() throws SQLException {
    long listA = seedList("List A");
    long listB = seedList("List B");
    long dana = seedEmail("dana@example.com");

    // dana unsubscribed from both lists -- still counts once
    seedMembership(listA, dana, false, "2026-07-01 00:00:00");
    seedMembership(listB, dana, false, "2026-07-02 00:00:00");

    assertEquals(1, MailingListMemberRepository.countUnsubscribed());
  }

  @Test
  void aPersonCanBeBothActiveAndUnsubscribedAtOnceOnDifferentLists() throws SQLException {
    long listA = seedList("List A");
    long listB = seedList("List B");
    long erin = seedEmail("erin@example.com");

    seedMembership(listA, erin, true, null); // still active here
    seedMembership(listB, erin, false, "2026-07-01 00:00:00"); // unsubscribed here

    assertEquals(1, MailingListMemberRepository.countActiveSubscribers());
    assertEquals(1, MailingListMemberRepository.countUnsubscribed());
  }

  @Test
  void findMonthlySubscriptionsZeroFillsAndOrdersOldestToNewest() throws SQLException {
    long list = seedList("List A");
    seedMembership(list, seedEmail("new@example.com"), true, null); // created "now" by default

    List<StatisticsData> series = MailingListMemberRepository.findMonthlySubscriptions(3);

    assertEquals(4, series.size(), "3 months plus the current month, inclusive");
    assertNotNull(series.get(series.size() - 1).getValue());
    assertTrue(series.get(0).getLabel().compareTo(series.get(series.size() - 1).getLabel()) < 0,
        "expected oldest to newest: " + series);
  }

  @Test
  void findClassificationBreakdownGroupsDistinctSubscribersByStatus() throws SQLException {
    long list = seedList("List A");
    long valid = seedEmail("valid@example.com");
    long invalid = seedEmail("invalid-addr@example.com");
    long neverChecked = seedEmail("unchecked@example.com");
    classifyEmail(valid, "valid", "2026-07-28 00:00:00");
    classifyEmail(invalid, "invalid", "2026-07-28 00:00:00");
    // neverChecked is left with validation_status/validated_at both NULL

    seedMembership(list, valid, true, null);
    seedMembership(list, invalid, true, null);
    seedMembership(list, neverChecked, true, null);

    List<StatisticsData> breakdown = MailingListMemberRepository.findClassificationBreakdown();

    Map<String, String> byStatus = new HashMap<>();
    for (StatisticsData data : breakdown) {
      byStatus.put(data.getLabel(), data.getValue());
    }
    assertEquals("1", byStatus.get("valid"));
    assertEquals("1", byStatus.get("invalid"));
    assertEquals("1", byStatus.get("unclassified"),
        "a never-validated subscriber must fall into 'unclassified', not be omitted: " + breakdown);
  }

  @Test
  void findClassificationBreakdownCountsDistinctPeopleNotMemberships() throws SQLException {
    long listA = seedList("List A");
    long listB = seedList("List B");
    long frank = seedEmail("frank@example.com");
    classifyEmail(frank, "valid", "2026-07-28 00:00:00");

    seedMembership(listA, frank, true, null);
    seedMembership(listB, frank, true, null); // frank is on both lists -- must count once

    List<StatisticsData> breakdown = MailingListMemberRepository.findClassificationBreakdown();

    assertEquals(1, breakdown.size(), "expected a single 'valid' group: " + breakdown);
    assertEquals("valid", breakdown.get(0).getLabel());
    assertEquals("1", breakdown.get(0).getValue());
  }

  @Test
  void findClassificationBreakdownExcludesNonSubscriberEmails() throws SQLException {
    long list = seedList("List A");
    long subscriber = seedEmail("subscriber@example.com");
    long customerOnly = seedEmail("customer-only@example.com"); // e.g. an ecommerce customer, never subscribed
    classifyEmail(subscriber, "valid", "2026-07-28 00:00:00");
    classifyEmail(customerOnly, "invalid", "2026-07-28 00:00:00");

    seedMembership(list, subscriber, true, null);
    // customerOnly is intentionally never added to mailing_list_members

    List<StatisticsData> breakdown = MailingListMemberRepository.findClassificationBreakdown();

    assertEquals(1, breakdown.size(), "a non-subscriber address must not appear in a mailing-list breakdown: " + breakdown);
    assertEquals("valid", breakdown.get(0).getLabel());
  }

  @Test
  void addEmailToListReportsCreatedForABrandNewMembership() throws SQLException {
    long listId = seedList("List A");
    long emailId = seedEmail("new@example.com");
    MailingList mailingList = new MailingList();
    mailingList.setId(listId);
    Email email = new Email();
    email.setId(emailId);

    MailingListMemberRepository.AddToListResult result = MailingListMemberRepository.addEmailToList(email,
        mailingList);

    assertTrue(result.isCreated(), "issue #452 -- must report created=true for a brand-new membership");
    assertTrue(!result.wasPreviouslyUnsubscribed(), "there was no prior row at all, so it was never unsubscribed");
    assertNotNull(result.getMember());
    assertEquals("new@example.com", result.getMember().getEmailAddress());
    assertTrue(result.getMember().getIsValid());
  }

  @Test
  void addEmailToListReportsNotCreatedForAReactivatedMembership() throws SQLException {
    long listId = seedList("List A");
    long emailId = seedEmail("returning@example.com");
    seedMembership(listId, emailId, false, "2026-07-01 00:00:00"); // previously unsubscribed
    MailingList mailingList = new MailingList();
    mailingList.setId(listId);
    Email email = new Email();
    email.setId(emailId);

    MailingListMemberRepository.AddToListResult result = MailingListMemberRepository.addEmailToList(email,
        mailingList);

    assertTrue(!result.isCreated(), "issue #452 -- re-adding an existing (list, email) pair must report created=false");
    assertTrue(result.wasPreviouslyUnsubscribed(),
        "issue #452 -- a genuine reactivation must be reported so the caller can fire the right event");
    assertNotNull(result.getMember());
    assertNull(result.getMember().getUnsubscribed(), "reactivating must clear the prior unsubscribe timestamp");
    assertTrue(result.getMember().getIsValid());
  }

  @Test
  void addEmailToListDoesNotReportAnAlreadyActiveMemberAsReactivated() throws SQLException {
    long listId = seedList("List A");
    long emailId = seedEmail("already-subscribed@example.com");
    seedMembership(listId, emailId, true, null); // already active, never unsubscribed
    MailingList mailingList = new MailingList();
    mailingList.setId(listId);
    Email email = new Email();
    email.setId(emailId);

    MailingListMemberRepository.AddToListResult result = MailingListMemberRepository.addEmailToList(email,
        mailingList);

    assertTrue(!result.isCreated(), "the row already existed");
    assertTrue(!result.wasPreviouslyUnsubscribed(),
        "issue #452 -- re-adding an already-active member is a no-op, not a reactivation; the caller must not "
            + "fire a misleading \"resubscribed\" event for someone who never left");
  }

  @Test
  void addEmailToListDoesNotReactivateAQuarantinedMember() throws SQLException {
    long listId = seedList("List A");
    long emailId = seedEmail("spamtrap@example.com");
    seedQuarantinedMembership(listId, emailId, "spamtrap"); // e.g. quarantineFlaggedMembers() (issue #564)
    MailingList mailingList = new MailingList();
    mailingList.setId(listId);
    Email email = new Email();
    email.setId(emailId);

    MailingListMemberRepository.AddToListResult result = MailingListMemberRepository.addEmailToList(email,
        mailingList);

    assertTrue(!result.isCreated(), "the row already existed");
    assertNotNull(result.getMember());
    assertTrue(!result.getMember().getIsValid(),
        "a quarantined address must not be silently reactivated by a resubscribe -- it must go through "
            + "deliberate admin review first");
    assertNotNull(result.getMember().getQuarantined(),
        "the quarantine timestamp must not be cleared by a resubscribe");
    assertEquals("spamtrap", result.getMember().getQuarantineReason(),
        "the quarantine reason must not be cleared by a resubscribe");
  }

  @Test
  void findLastClassifiedAtReturnsNullWhenNoSubscriberHasBeenClassified() throws SQLException {
    long list = seedList("List A");
    seedMembership(list, seedEmail("unchecked@example.com"), true, null);

    assertNull(MailingListMemberRepository.findLastClassifiedAt());
  }

  @Test
  void findLastClassifiedAtReturnsTheMostRecentSubscriberValidationAndIgnoresNonSubscribers() throws SQLException {
    long list = seedList("List A");
    long older = seedEmail("older@example.com");
    long newer = seedEmail("newer@example.com");
    long nonSubscriberButNewer = seedEmail("customer-only@example.com");
    classifyEmail(older, "valid", "2026-07-01 00:00:00");
    classifyEmail(newer, "valid", "2026-07-15 00:00:00");
    classifyEmail(nonSubscriberButNewer, "valid", "2026-07-27 00:00:00"); // newest overall, but not a subscriber

    seedMembership(list, older, true, null);
    seedMembership(list, newer, true, null);
    // nonSubscriberButNewer is intentionally never added to mailing_list_members

    Timestamp lastClassifiedAt = MailingListMemberRepository.findLastClassifiedAt();

    assertNotNull(lastClassifiedAt);
    assertEquals(Timestamp.valueOf("2026-07-15 00:00:00"), lastClassifiedAt,
        "must reflect the most recent SUBSCRIBER validation, not the unrelated non-subscriber's later one");
  }

  private long seedList(String name) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO mailing_lists (name, title) VALUES ('" + name + "', '" + name + "')");
      try (var rs = statement.executeQuery("SELECT list_id FROM mailing_lists WHERE name = '" + name + "'")) {
        rs.next();
        return rs.getLong(1);
      }
    }
  }

  private long seedEmail(String email) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO emails (email) VALUES ('" + email + "')");
      try (var rs = statement.executeQuery("SELECT email_id FROM emails WHERE email = '" + email + "'")) {
        rs.next();
        return rs.getLong(1);
      }
    }
  }

  private void classifyEmail(long emailId, String validationStatus, String validatedAt) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("UPDATE emails SET validation_status = '" + validationStatus + "', validated_at = '"
          + validatedAt + "' WHERE email_id = " + emailId);
    }
  }

  private void seedMembership(long listId, long emailId, boolean isValid, String unsubscribed) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      String unsubscribedSql = unsubscribed == null ? "NULL" : "'" + unsubscribed + "'";
      statement.execute("INSERT INTO mailing_list_members (list_id, email_id, is_valid, unsubscribed) VALUES ("
          + listId + ", " + emailId + ", " + isValid + ", " + unsubscribedSql + ")");
    }
  }

  /** A membership already archived by {@code quarantineFlaggedMembers()} -- is_valid=false, quarantined
   *  timestamp set, quarantine_reason set, and (as that job does) unsubscribed left NULL since quarantine
   *  is a distinct reason a membership stopped being active, not a person choosing to leave. */
  private void seedQuarantinedMembership(long listId, long emailId, String quarantineReason) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO mailing_list_members "
          + "(list_id, email_id, is_valid, unsubscribed, quarantined, quarantine_reason) VALUES ("
          + listId + ", " + emailId + ", false, NULL, CURRENT_TIMESTAMP, '" + quarantineReason + "')");
    }
  }

  private static void createSchema() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE emails ("
          + "email_id BIGSERIAL PRIMARY KEY, "
          + "email VARCHAR(255) UNIQUE NOT NULL, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "first_name VARCHAR(100), "
          + "last_name VARCHAR(100), "
          + "organization VARCHAR(200), "
          + "ip_address VARCHAR(45), "
          + "validation_status VARCHAR(20), "
          + "validation_sub_status VARCHAR(50), "
          + "validated_at TIMESTAMP(3))");
      statement.execute("CREATE TABLE mailing_lists ("
          + "list_id BIGSERIAL PRIMARY KEY, "
          + "list_order INTEGER DEFAULT 100, "
          + "name VARCHAR(200) NOT NULL, "
          + "title VARCHAR(200) NOT NULL, "
          + "member_count INTEGER DEFAULT 0, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
      statement.execute("CREATE TABLE mailing_list_members ("
          + "member_id BIGSERIAL PRIMARY KEY, "
          + "list_id BIGINT REFERENCES mailing_lists(list_id), "
          + "email_id BIGINT REFERENCES emails(email_id), "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "created_by BIGINT DEFAULT -1, "
          + "modified TIMESTAMP(3), "
          + "modified_by BIGINT DEFAULT -1, "
          + "last_emailed TIMESTAMP(3), "
          + "unsubscribed TIMESTAMP(3), "
          + "unsubscribed_by BIGINT DEFAULT -1, "
          + "unsubscribe_reason VARCHAR(100), "
          + "unsubscribe_token VARCHAR(255), "
          + "is_valid BOOLEAN DEFAULT true, "
          + "quarantined TIMESTAMP(3), "
          + "quarantine_reason VARCHAR(50))");
      statement.execute("CREATE UNIQUE INDEX mail_lis_mem_uniq_idx ON mailing_list_members(list_id, email_id)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the mailing list metrics test schema", se);
    }
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (RuntimeException | LinkageError e) {
      return false;
    }
  }

  private static String resolveImage() {
    String image = System.getenv("TEST_POSTGRES_IMAGE");
    return (image != null && !image.isBlank()) ? image : DEFAULT_IMAGE;
  }
}
