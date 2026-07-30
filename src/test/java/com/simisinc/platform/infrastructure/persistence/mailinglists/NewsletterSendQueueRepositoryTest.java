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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.mailinglists.NewsletterSendCommand;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListHistory;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;
import com.simisinc.platform.domain.model.mailinglists.MailingListSent;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies the newsletter send-queue repository pieces (issue #600) against a real PostgreSQL
 * instance -- claiming a batch, retry-cap behavior, token generation/lookup, and the anonymous
 * unsubscribe write. Minimal schema replicated from NEW_10070__new_mailing_lists.sql plus
 * UPGRADE_20260729.1004__newsletter_send_queue.sql, without the blog_posts FK (not needed here).
 *
 * @author SimIS Inc.
 */
class NewsletterSendQueueRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping newsletter send-queue integration test");

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
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping newsletter send-queue integration test");
    try (Connection connection = DB.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute(
          "TRUNCATE TABLE mailing_list_sent, mailing_list_history, mailing_list_members, mailing_lists, emails RESTART IDENTITY CASCADE");
    }
  }

  @Test
  void historyRoundTripsSubjectAndBlogPostId() {
    long listId = seedList("News");
    MailingListHistory history = new MailingListHistory();
    history.setListId(listId);
    history.setService("smtp");
    history.setEmailCount(3);
    history.setSubject("A Post Title");
    history.setBlogPostId(42L);

    MailingListHistory saved = withConnection(connection -> {
      try {
        return MailingListHistoryRepository.add(connection, history);
      } catch (SQLException se) {
        throw new RuntimeException(se);
      }
    });

    assertNotNull(saved);
    MailingListHistory found = MailingListHistoryRepository.findById(saved.getId());
    assertEquals("A Post Title", found.getSubject());
    assertEquals(42L, found.getBlogPostId());
    assertEquals(3, found.getEmailCount());
  }

  @Test
  void sendBlogPostNotificationCreatesABatchAndOneQueuedRowPerActiveMember() throws DataException {
    long listId = seedList("News");
    seedMembership(listId, seedEmail("active1@example.com"), true, null, null);
    seedMembership(listId, seedEmail("active2@example.com"), true, null, null);
    seedMembership(listId, seedEmail("unsub@example.com"), true, "2026-07-01 00:00:00", null);

    MailingList mailingList = MailingListRepository.findById(listId);
    BlogPost blogPost = new BlogPost();
    blogPost.setId(99L);
    blogPost.setTitle("A New Post");

    int queuedCount = NewsletterSendCommand.sendBlogPostNotification(mailingList, blogPost, 1L);

    assertEquals(2, queuedCount, "only active, non-unsubscribed members should be queued");
    List<MailingListSent> claimed = MailingListSentRepository.claimBatch(10);
    assertEquals(2, claimed.size());
  }

  @Test
  void sendBlogPostNotificationReturnsZeroForAListWithNoActiveMembers() throws DataException {
    long listId = seedList("Empty List");
    MailingList mailingList = MailingListRepository.findById(listId);
    BlogPost blogPost = new BlogPost();
    blogPost.setId(99L);
    blogPost.setTitle("A New Post");

    int queuedCount = NewsletterSendCommand.sendBlogPostNotification(mailingList, blogPost, 1L);

    assertEquals(0, queuedCount);
    assertTrue(MailingListSentRepository.claimBatch(10).isEmpty());
  }

  @Test
  void enqueueCreatesOneQueuedRowPerEmail() {
    long listId = seedList("News");
    long historyId = seedHistory(listId);
    long email1 = seedEmail("a@example.com");
    long email2 = seedEmail("b@example.com");

    withConnectionVoid(connection -> {
      try {
        MailingListSentRepository.enqueue(connection, historyId, listId, List.of(email1, email2));
      } catch (SQLException se) {
        throw new RuntimeException(se);
      }
    });

    List<MailingListSent> claimed = MailingListSentRepository.claimBatch(10);
    assertEquals(2, claimed.size());
    assertTrue(claimed.stream().allMatch(item -> MailingListSent.PROCESSING.equals(item.getStatus())),
        "claiming should flip queued rows to processing");
  }

  @Test
  void claimBatchOnlyClaimsUpToTheRequestedSize() {
    long listId = seedList("News");
    long historyId = seedHistory(listId);
    List<Long> emailIds = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      emailIds.add(seedEmail("member" + i + "@example.com"));
    }
    withConnectionVoid(connection -> {
      try {
        MailingListSentRepository.enqueue(connection, historyId, listId, emailIds);
      } catch (SQLException se) {
        throw new RuntimeException(se);
      }
    });

    List<MailingListSent> firstBatch = MailingListSentRepository.claimBatch(2);
    assertEquals(2, firstBatch.size());

    // The other 3 remain queued, not claimed
    List<MailingListSent> secondBatch = MailingListSentRepository.claimBatch(10);
    assertEquals(3, secondBatch.size());
  }

  @Test
  void markFailedOrRequeueGoesBackToQueuedUnderTheAttemptCap() {
    long listId = seedList("News");
    long historyId = seedHistory(listId);
    long emailId = seedEmail("a@example.com");
    withConnectionVoid(connection -> {
      try {
        MailingListSentRepository.enqueue(connection, historyId, listId, List.of(emailId));
      } catch (SQLException se) {
        throw new RuntimeException(se);
      }
    });

    MailingListSent item = MailingListSentRepository.claimBatch(1).get(0);
    MailingListSentRepository.markFailedOrRequeue(item, "SMTP timeout", 3);

    List<MailingListSent> requeued = MailingListSentRepository.claimBatch(1);
    assertEquals(1, requeued.size(), "should be claimable again, not permanently failed");
    assertEquals(1, requeued.get(0).getAttemptCount());
  }

  @Test
  void markFailedOrRequeuePermanentlyFailsAtTheAttemptCap() {
    long listId = seedList("News");
    long historyId = seedHistory(listId);
    long emailId = seedEmail("a@example.com");
    withConnectionVoid(connection -> {
      try {
        MailingListSentRepository.enqueue(connection, historyId, listId, List.of(emailId));
      } catch (SQLException se) {
        throw new RuntimeException(se);
      }
    });

    MailingListSent item = MailingListSentRepository.claimBatch(1).get(0);
    item.setAttemptCount(2); // simulate this being the 3rd attempt
    MailingListSentRepository.markFailedOrRequeue(item, "SMTP timeout", 3);

    assertTrue(MailingListSentRepository.claimBatch(1).isEmpty(), "must not be claimable once permanently failed");
  }

  @Test
  void findActiveMembersForListGeneratesATokenOncePerMember() {
    long listId = seedList("News");
    long emailId = seedEmail("a@example.com");
    seedMembership(listId, emailId, true, null, null);

    List<MailingListMember> firstLookup = MailingListMemberRepository.findActiveMembersForList(listId);
    assertEquals(1, firstLookup.size());
    String token = firstLookup.get(0).getUnsubscribeToken();
    assertNotNull(token);
    assertEquals("a@example.com", firstLookup.get(0).getEmailAddress());

    List<MailingListMember> secondLookup = MailingListMemberRepository.findActiveMembersForList(listId);
    assertEquals(token, secondLookup.get(0).getUnsubscribeToken(), "the same token should be reused, not regenerated");
  }

  @Test
  void findActiveMembersForListExcludesUnsubscribedAndInvalidMembers() {
    long listId = seedList("News");
    seedMembership(listId, seedEmail("active@example.com"), true, null, null);
    seedMembership(listId, seedEmail("unsub@example.com"), true, "2026-07-01 00:00:00", null);
    seedMembership(listId, seedEmail("invalid@example.com"), false, null, null);

    List<MailingListMember> members = MailingListMemberRepository.findActiveMembersForList(listId);
    assertEquals(1, members.size());
    assertEquals("active@example.com", members.get(0).getEmailAddress());
  }

  @Test
  void findByUnsubscribeTokenFindsTheRightMember() {
    long listId = seedList("News");
    long emailId = seedEmail("a@example.com");
    seedMembership(listId, emailId, true, null, "tok-123");

    MailingListMember member = MailingListMemberRepository.findByUnsubscribeToken("tok-123");
    assertNotNull(member);
    assertEquals("a@example.com", member.getEmailAddress());
  }

  @Test
  void findByUnsubscribeTokenReturnsNullForAnUnknownToken() {
    assertNull(MailingListMemberRepository.findByUnsubscribeToken("does-not-exist"));
  }

  @Test
  void unsubscribeByTokenClearsTheTokenSoItIsSingleUse() {
    long listId = seedList("News");
    long emailId = seedEmail("a@example.com");
    seedMembership(listId, emailId, true, null, "tok-123");
    MailingListMember member = MailingListMemberRepository.findByUnsubscribeToken("tok-123");

    MailingListMemberRepository.unsubscribeByToken(member);

    assertNull(MailingListMemberRepository.findByUnsubscribeToken("tok-123"), "token must be cleared after use");
    MailingListMember reloaded = MailingListMemberRepository.findByListAndEmail(listId, emailId);
    assertNotNull(reloaded.getUnsubscribed());
    assertFalse(reloaded.getIsValid());
  }

  @Test
  void findByListAndEmailGeneratesATokenIfNoneExistsYet() {
    long listId = seedList("News");
    long emailId = seedEmail("a@example.com");
    seedMembership(listId, emailId, true, null, null);

    MailingListMember member = MailingListMemberRepository.findByListAndEmail(listId, emailId);

    assertNotNull(member.getUnsubscribeToken());
  }

  private long seedList(String name) {
    return withConnection(connection -> {
      try (Statement statement = connection.createStatement()) {
        statement.execute("INSERT INTO mailing_lists (name, title) VALUES ('" + name + "', '" + name + "')");
        try (var rs = statement.executeQuery("SELECT list_id FROM mailing_lists WHERE name = '" + name + "'")) {
          rs.next();
          return rs.getLong(1);
        }
      } catch (SQLException se) {
        throw new RuntimeException(se);
      }
    });
  }

  private long seedHistory(long listId) {
    return withConnection(connection -> {
      try (Statement statement = connection.createStatement()) {
        statement.execute("INSERT INTO mailing_list_history (list_id, service, blog_post_id) VALUES ("
            + listId + ", 'smtp', 1)");
        try (var rs = statement.executeQuery("SELECT history_id FROM mailing_list_history WHERE list_id = " + listId)) {
          rs.next();
          return rs.getLong(1);
        }
      } catch (SQLException se) {
        throw new RuntimeException(se);
      }
    });
  }

  private long seedEmail(String email) {
    return withConnection(connection -> {
      try (Statement statement = connection.createStatement()) {
        statement.execute("INSERT INTO emails (email) VALUES ('" + email + "')");
        try (var rs = statement.executeQuery("SELECT email_id FROM emails WHERE email = '" + email + "'")) {
          rs.next();
          return rs.getLong(1);
        }
      } catch (SQLException se) {
        throw new RuntimeException(se);
      }
    });
  }

  private void seedMembership(long listId, long emailId, boolean isValid, String unsubscribed, String unsubscribeToken) {
    withConnectionVoid(connection -> {
      try (Statement statement = connection.createStatement()) {
        String unsubscribedSql = unsubscribed == null ? "NULL" : "'" + unsubscribed + "'";
        String tokenSql = unsubscribeToken == null ? "NULL" : "'" + unsubscribeToken + "'";
        statement.execute("INSERT INTO mailing_list_members (list_id, email_id, is_valid, unsubscribed, unsubscribe_token) VALUES ("
            + listId + ", " + emailId + ", " + isValid + ", " + unsubscribedSql + ", " + tokenSql + ")");
      } catch (SQLException se) {
        throw new RuntimeException(se);
      }
    });
  }

  private interface ConnectionFunction<T> {
    T apply(Connection connection);
  }

  private static <T> T withConnection(ConnectionFunction<T> function) {
    try (Connection connection = DB.getConnection()) {
      return function.apply(connection);
    } catch (SQLException se) {
      throw new RuntimeException(se);
    }
  }

  private interface ConnectionConsumer {
    void accept(Connection connection);
  }

  private static void withConnectionVoid(ConnectionConsumer consumer) {
    try (Connection connection = DB.getConnection()) {
      consumer.accept(connection);
    } catch (SQLException se) {
      throw new RuntimeException(se);
    }
  }

  private static void createSchema() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      // Empty on purpose: NewsletterSendCommand.sendBlogPostNotification checks
      // MailChimpCommand.isEnabled(), which reads this table via LoadSitePropertyCommand. With no
      // rows, "mailing-list.service" resolves to null, isEnabled() is false, and the send falls
      // through to the SMTP path this test suite actually exercises.
      statement.execute("CREATE TABLE site_properties ("
          + "property_id BIGSERIAL PRIMARY KEY, "
          + "property_order INTEGER DEFAULT 100, "
          + "property_name VARCHAR(50) UNIQUE NOT NULL, "
          + "property_value TEXT NOT NULL)");
      statement.execute("CREATE TABLE emails ("
          + "email_id BIGSERIAL PRIMARY KEY, "
          + "email VARCHAR(255) UNIQUE NOT NULL, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
      statement.execute("CREATE TABLE mailing_lists ("
          + "list_id BIGSERIAL PRIMARY KEY, "
          + "list_order INTEGER DEFAULT 100, "
          + "name VARCHAR(200) NOT NULL, "
          + "title VARCHAR(200) NOT NULL, "
          + "description TEXT, "
          + "member_count INTEGER DEFAULT 0, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "created_by BIGINT, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified_by BIGINT, "
          + "last_emailed TIMESTAMP(3), "
          + "show_online BOOLEAN DEFAULT false, "
          + "enabled BOOLEAN DEFAULT true)");
      statement.execute("CREATE TABLE mailing_list_members ("
          + "member_id BIGSERIAL PRIMARY KEY, "
          + "list_id BIGINT REFERENCES mailing_lists(list_id), "
          + "email_id BIGINT REFERENCES emails(email_id), "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "created_by BIGINT, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified_by BIGINT, "
          + "last_emailed TIMESTAMP(3), "
          + "unsubscribed TIMESTAMP(3), "
          + "unsubscribed_by BIGINT, "
          + "unsubscribe_reason VARCHAR(100), "
          + "is_valid BOOLEAN DEFAULT true, "
          + "unsubscribe_token VARCHAR(255))");
      statement.execute("CREATE UNIQUE INDEX mail_lis_mem_uniq_idx ON mailing_list_members(list_id, email_id)");
      statement.execute("CREATE UNIQUE INDEX mail_lis_mem_unsub_tok_idx ON mailing_list_members(unsubscribe_token)");
      statement.execute("CREATE TABLE mailing_list_history ("
          + "history_id BIGSERIAL PRIMARY KEY, "
          + "list_id BIGINT REFERENCES mailing_lists(list_id), "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "created_by BIGINT, "
          + "service VARCHAR(20), "
          + "email_count INTEGER DEFAULT 0, "
          + "subject VARCHAR(255), "
          + "blog_post_id BIGINT, "
          + "mailchimp_campaign_id VARCHAR(50))");
      statement.execute("CREATE TABLE mailing_list_sent ("
          + "item_id BIGSERIAL PRIMARY KEY, "
          + "email_id BIGINT REFERENCES emails(email_id), "
          + "list_id BIGINT REFERENCES mailing_lists(list_id), "
          + "history_id BIGINT REFERENCES mailing_list_history(history_id), "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "status VARCHAR(20) DEFAULT 'queued', "
          + "attempt_count INTEGER DEFAULT 0, "
          + "error_message VARCHAR(500), "
          + "claimed_at TIMESTAMP(3), "
          + "modified TIMESTAMP(3))");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the newsletter send-queue test schema", se);
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
