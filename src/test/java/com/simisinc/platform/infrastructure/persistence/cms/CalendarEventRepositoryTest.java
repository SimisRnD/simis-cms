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

package com.simisinc.platform.infrastructure.persistence.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
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

import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link CalendarEventRepository} against a real PostgreSQL instance -- specifically the
 * issue #882 archived filter (mirroring MedicineRepository's archivedOnly shape) and the
 * update()/remove() paths the /admin/calendars bulk actions (archive/move/delete) drive.
 *
 * <p>
 * This is an integration test: it starts a throwaway PostgreSQL container (Testcontainers) and
 * exercises the repository through the real JDBC/HikariCP stack. It is skipped automatically when
 * Docker is not available, so it does not break the build on hosts without a Docker daemon.
 * </p>
 *
 * <p>
 * The schema below is a focused subset of the real {@code calendars}/{@code calendar_events} tables
 * (mirrored from NEW_10010__new_cms.sql): the PostGIS {@code geom} column and the
 * {@code created_by}/{@code modified_by} foreign keys to {@code users} are intentionally omitted --
 * nothing under test touches geom, and no {@code users} table exists in this throwaway schema.
 * </p>
 *
 * @author SimIS Inc.
 */
class CalendarEventRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping CalendarEventRepository integration test");

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
      // The DataSource is never initialized when Docker is unavailable
    }
    if (postgres != null) {
      postgres.stop();
    }
  }

  @BeforeEach
  void resetTables() {
    if (postgres == null || !postgres.isRunning()) {
      return;
    }
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE calendar_events RESTART IDENTITY CASCADE");
      statement.execute("TRUNCATE TABLE calendars RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset calendar tables", se);
    }
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }

  private static String resolveImage() {
    String image = System.getenv("TEST_POSTGRES_IMAGE");
    return (image != null && !image.isBlank()) ? image : DEFAULT_IMAGE;
  }

  private static void createSchema() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS calendar_events CASCADE");
      statement.execute("DROP TABLE IF EXISTS calendars CASCADE");
      statement.execute("CREATE TABLE calendars ("
          + "calendar_id BIGSERIAL PRIMARY KEY, "
          + "calendar_unique_id VARCHAR(255) UNIQUE NOT NULL, "
          + "name VARCHAR(255) NOT NULL, "
          + "description TEXT, "
          + "color VARCHAR(7), "
          + "created_by BIGINT NOT NULL, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified_by BIGINT NOT NULL, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "enabled BOOLEAN DEFAULT true, "
          + "event_count INTEGER DEFAULT 0)");
      statement.execute("CREATE TABLE calendar_events ("
          + "event_id BIGSERIAL PRIMARY KEY, "
          + "calendar_id BIGINT REFERENCES calendars(calendar_id) NOT NULL, "
          + "event_unique_id VARCHAR(255) NOT NULL, "
          + "title VARCHAR(255) NOT NULL, "
          + "body TEXT, "
          + "summary TEXT, "
          + "created_by BIGINT NOT NULL, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified_by BIGINT NOT NULL, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "published TIMESTAMP(3) DEFAULT NULL, "
          + "archived TIMESTAMP(3) DEFAULT NULL, "
          + "all_day BOOLEAN DEFAULT false, "
          + "start_date TIMESTAMP(3) NOT NULL, "
          + "end_date TIMESTAMP(3) NOT NULL, "
          + "details_url VARCHAR(255), "
          + "sign_up_url VARCHAR(255), "
          + "latitude FLOAT DEFAULT 0, "
          + "longitude FLOAT DEFAULT 0, "
          + "location_name VARCHAR(255), "
          + "street VARCHAR(100), "
          + "address_line_2 VARCHAR(100), "
          + "address_line_3 VARCHAR(100), "
          + "city VARCHAR(100), "
          + "state VARCHAR(100), "
          + "country VARCHAR(100), "
          + "postal_code VARCHAR(100), "
          + "county VARCHAR(100), "
          + "tsv TSVECTOR, "
          + "image_url VARCHAR(255), "
          + "video_url VARCHAR(255), "
          + "video_embed VARCHAR(512), "
          + "script_embed VARCHAR(512), "
          + "tags_list VARCHAR(255))");
      statement.execute("CREATE UNIQUE INDEX cal_events_unique_idx ON calendar_events(calendar_id, event_unique_id)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the calendar schema", se);
    }
  }

  private static Calendar addCalendar(String uniqueId) {
    Calendar calendar = new Calendar();
    calendar.setUniqueId(uniqueId);
    calendar.setName(uniqueId);
    calendar.setCreatedBy(1L);
    calendar.setModifiedBy(1L);
    calendar.setEnabled(true);
    return CalendarRepository.add(calendar);
  }

  private static CalendarEvent addEvent(long calendarId, String uniqueId, Timestamp published, Timestamp archived) {
    CalendarEvent event = new CalendarEvent();
    event.setCalendarId(calendarId);
    event.setUniqueId(uniqueId);
    event.setTitle(uniqueId);
    event.setCreatedBy(1L);
    event.setModifiedBy(1L);
    event.setStartDate(Timestamp.valueOf("2026-08-01 09:00:00"));
    event.setEndDate(Timestamp.valueOf("2026-08-01 10:00:00"));
    event.setPublished(published);
    event.setArchived(archived);
    return CalendarEventRepository.add(event);
  }

  private static List<String> uniqueIdsFor(CalendarEventSpecification specification) {
    List<CalendarEvent> results = CalendarEventRepository.findAll(specification, new DataConstraints());
    return results == null ? List.of() : results.stream().map(CalendarEvent::getUniqueId).toList();
  }

  // --- archived filter (issue #882) ---

  @Test
  void archivedOnlyTrueReturnsOnlyArchivedEvents() {
    Calendar calendar = addCalendar("cal-archived-only-true");
    addEvent(calendar.getId(), "live-event", Timestamp.valueOf("2026-01-01 00:00:00"), null);
    addEvent(calendar.getId(), "archived-event", Timestamp.valueOf("2026-01-01 00:00:00"),
        Timestamp.valueOf("2026-06-01 00:00:00"));

    CalendarEventSpecification specification = new CalendarEventSpecification();
    specification.setArchivedOnly(true);

    assertEquals(List.of("archived-event"), uniqueIdsFor(specification));
  }

  @Test
  void archivedOnlyFalseExcludesArchivedEvents() {
    Calendar calendar = addCalendar("cal-archived-only-false");
    addEvent(calendar.getId(), "live-event", null, null);
    addEvent(calendar.getId(), "archived-event", null, Timestamp.valueOf("2026-06-01 00:00:00"));

    CalendarEventSpecification specification = new CalendarEventSpecification();
    specification.setArchivedOnly(false);

    assertEquals(List.of("live-event"), uniqueIdsFor(specification));
  }

  @Test
  void undefinedArchivedOnlyReturnsEveryEventRegardlessOfArchivedState() {
    // Proves the new filter is purely additive: any caller that never touches archivedOnly (every
    // pre-#882 caller) keeps seeing archived rows exactly as before.
    Calendar calendar = addCalendar("cal-archived-undefined");
    addEvent(calendar.getId(), "live-event", null, null);
    addEvent(calendar.getId(), "archived-event", null, Timestamp.valueOf("2026-06-01 00:00:00"));

    List<String> uniqueIds = uniqueIdsFor(new CalendarEventSpecification());

    assertTrue(uniqueIds.contains("live-event"));
    assertTrue(uniqueIds.contains("archived-event"));
  }

  @Test
  void archivedFilterCombinesWithPublishedOnlyAsAnd() {
    // A publicly-visible query (publishedOnly=true) must still exclude an archived-but-published
    // event -- the two filters are independent dimensions, both applied.
    Calendar calendar = addCalendar("cal-archived-and-published");
    addEvent(calendar.getId(), "published-live", Timestamp.valueOf("2026-01-01 00:00:00"), null);
    addEvent(calendar.getId(), "published-archived", Timestamp.valueOf("2026-01-01 00:00:00"),
        Timestamp.valueOf("2026-06-01 00:00:00"));
    addEvent(calendar.getId(), "draft-live", null, null);

    CalendarEventSpecification specification = new CalendarEventSpecification();
    specification.setPublishedOnly(true);
    specification.setArchivedOnly(false);

    assertEquals(List.of("published-live"), uniqueIdsFor(specification));
  }

  @Test
  void findCountHonorsTheArchivedFilter() {
    Calendar calendar = addCalendar("cal-archived-count");
    addEvent(calendar.getId(), "live-1", null, null);
    addEvent(calendar.getId(), "live-2", null, null);
    addEvent(calendar.getId(), "archived-1", null, Timestamp.valueOf("2026-06-01 00:00:00"));

    CalendarEventSpecification archivedOnly = new CalendarEventSpecification();
    archivedOnly.setArchivedOnly(true);
    CalendarEventSpecification nonArchivedOnly = new CalendarEventSpecification();
    nonArchivedOnly.setArchivedOnly(false);

    assertEquals(1, CalendarEventRepository.findCount(archivedOnly));
    assertEquals(2, CalendarEventRepository.findCount(nonArchivedOnly));
  }

  private static Calendar addCalendar(String uniqueId, boolean enabled) {
    Calendar calendar = new Calendar();
    calendar.setUniqueId(uniqueId);
    calendar.setName(uniqueId);
    calendar.setCreatedBy(1L);
    calendar.setModifiedBy(1L);
    calendar.setEnabled(enabled);
    return CalendarRepository.add(calendar);
  }

  // --- calendarEnabledOnly: a calendar's "Online?" checkbox gates its events off public
  // list/feed surfaces (CalendarAjaxEvents, CalendarSearchResultsWidget, UpcomingCalendarEventsWidget) ---

  @Test
  void calendarEnabledOnlyTrueExcludesEventsFromADisabledCalendar() {
    Calendar onlineCalendar = addCalendar("cal-online", true);
    Calendar offlineCalendar = addCalendar("cal-offline", false);
    addEvent(onlineCalendar.getId(), "online-event", null, null);
    addEvent(offlineCalendar.getId(), "offline-event", null, null);

    CalendarEventSpecification specification = new CalendarEventSpecification();
    specification.setCalendarEnabledOnly(true);

    assertEquals(List.of("online-event"), uniqueIdsFor(specification));
  }

  @Test
  void calendarEnabledOnlyFalseByDefaultReturnsEventsFromEveryCalendar() {
    // Proves the new filter is purely additive: any caller that never touches
    // calendarEnabledOnly (every admin-side caller) keeps seeing every calendar's events exactly
    // as before, regardless of the calendar's "Online?" state.
    Calendar onlineCalendar = addCalendar("cal-online-default", true);
    Calendar offlineCalendar = addCalendar("cal-offline-default", false);
    addEvent(onlineCalendar.getId(), "online-event-2", null, null);
    addEvent(offlineCalendar.getId(), "offline-event-2", null, null);

    List<String> uniqueIds = uniqueIdsFor(new CalendarEventSpecification());

    assertTrue(uniqueIds.contains("online-event-2"));
    assertTrue(uniqueIds.contains("offline-event-2"));
  }

  @Test
  void findCountHonorsTheCalendarEnabledFilter() {
    Calendar onlineCalendar = addCalendar("cal-online-count", true);
    Calendar offlineCalendar = addCalendar("cal-offline-count", false);
    addEvent(onlineCalendar.getId(), "online-1", null, null);
    addEvent(onlineCalendar.getId(), "online-2", null, null);
    addEvent(offlineCalendar.getId(), "offline-1", null, null);

    CalendarEventSpecification enabledOnly = new CalendarEventSpecification();
    enabledOnly.setCalendarEnabledOnly(true);
    CalendarEventSpecification unfiltered = new CalendarEventSpecification();

    assertEquals(2, CalendarEventRepository.findCount(enabledOnly));
    assertEquals(3, CalendarEventRepository.findCount(unfiltered));
  }

  // --- author filter (issue #426, editorial calendar) ---

  @Test
  void createdByFilterReturnsOnlyEventsFromThatAuthor() {
    Calendar calendar = addCalendar("cal-created-by-filter");
    CalendarEvent event1 = new CalendarEvent();
    event1.setCalendarId(calendar.getId());
    event1.setUniqueId("from-author-1");
    event1.setTitle("from-author-1");
    event1.setCreatedBy(1L);
    event1.setModifiedBy(1L);
    event1.setStartDate(Timestamp.valueOf("2026-08-01 09:00:00"));
    event1.setEndDate(Timestamp.valueOf("2026-08-01 10:00:00"));
    CalendarEventRepository.add(event1);

    CalendarEvent event2 = new CalendarEvent();
    event2.setCalendarId(calendar.getId());
    event2.setUniqueId("from-author-2");
    event2.setTitle("from-author-2");
    event2.setCreatedBy(2L);
    event2.setModifiedBy(2L);
    event2.setStartDate(Timestamp.valueOf("2026-08-01 09:00:00"));
    event2.setEndDate(Timestamp.valueOf("2026-08-01 10:00:00"));
    CalendarEventRepository.add(event2);

    CalendarEventSpecification specification = new CalendarEventSpecification();
    specification.setCreatedBy(2L);

    assertEquals(List.of("from-author-2"), uniqueIdsFor(specification));
  }

  @Test
  void createdByUnsetByDefaultReturnsEventsFromEveryAuthor() {
    Calendar calendar = addCalendar("cal-created-by-undefined");
    addEvent(calendar.getId(), "live-event", null, null);

    assertEquals(List.of("live-event"), uniqueIdsFor(new CalendarEventSpecification()));
  }

  // --- countGroupedByCalendarId() (CalendarListWidget's N+1 fix) ---

  @Test
  void countGroupedByCalendarIdReturnsEachCalendarsOwnTotal() {
    Calendar calendarA = addCalendar("cal-grouped-count-a");
    Calendar calendarB = addCalendar("cal-grouped-count-b");
    addEvent(calendarA.getId(), "a-1", null, null);
    addEvent(calendarA.getId(), "a-2", Timestamp.valueOf("2026-01-01 00:00:00"), null);
    addEvent(calendarA.getId(), "a-3", null, Timestamp.valueOf("2026-06-01 00:00:00"));
    addEvent(calendarB.getId(), "b-1", null, null);

    Map<Long, Long> counts = CalendarEventRepository.countGroupedByCalendarId();

    // All 3 of calendar A's events count, regardless of published/archived state -- this mirrors
    // the old per-row findCount(specification-with-only-calendarId-set) behavior exactly, which
    // never filtered on published/archived either.
    assertEquals(3L, counts.get(calendarA.getId()));
    assertEquals(1L, counts.get(calendarB.getId()));
  }

  @Test
  void countGroupedByCalendarIdOmitsACalendarWithNoEvents() {
    Calendar withEvents = addCalendar("cal-grouped-count-with-events");
    Calendar withoutEvents = addCalendar("cal-grouped-count-without-events");
    addEvent(withEvents.getId(), "only-event", null, null);

    Map<Long, Long> counts = CalendarEventRepository.countGroupedByCalendarId();

    assertEquals(1L, counts.get(withEvents.getId()));
    assertFalse(counts.containsKey(withoutEvents.getId()), "a calendar with zero events must be absent, not present with a 0 value");
  }

  // --- update()/remove() paths the bulk actions drive ---

  @Test
  void updateCanSetTheArchivedTimestamp() {
    // Exercises CalendarEventListWidget#bulkArchiveAction's write path directly against a real row.
    Calendar calendar = addCalendar("cal-update-archive");
    CalendarEvent event = addEvent(calendar.getId(), "to-archive", null, null);
    assertNull(event.getArchived());

    Timestamp archivedAt = Timestamp.valueOf("2026-07-01 12:00:00");
    event.setArchived(archivedAt);
    CalendarEvent result = CalendarEventRepository.update(event);

    assertNotNull(result);
    CalendarEvent reloaded = CalendarEventRepository.findById(event.getId());
    assertEquals(archivedAt, reloaded.getArchived());
  }

  @Test
  void updateCanMoveAnEventToAnotherCalendar() {
    // Exercises CalendarEventListWidget#bulkMoveAction's write path directly against real rows.
    Calendar source = addCalendar("cal-move-source");
    Calendar destination = addCalendar("cal-move-destination");
    CalendarEvent event = addEvent(source.getId(), "to-move", null, null);

    event.setCalendarId(destination.getId());
    CalendarEvent result = CalendarEventRepository.update(event);

    assertNotNull(result);
    CalendarEvent reloaded = CalendarEventRepository.findById(event.getId());
    assertEquals(destination.getId(), reloaded.getCalendarId());

    CalendarEventSpecification destinationOnly = new CalendarEventSpecification();
    destinationOnly.setCalendarId(destination.getId());
    assertEquals(List.of("to-move"), uniqueIdsFor(destinationOnly));
  }

  @Test
  void removeDeletesOnlyTheTargetedEvent() {
    // Exercises CalendarEventListWidget#bulkDeleteAction's write path directly against real rows.
    Calendar calendar = addCalendar("cal-remove");
    CalendarEvent keep = addEvent(calendar.getId(), "keep-me", null, null);
    CalendarEvent remove = addEvent(calendar.getId(), "remove-me", null, null);

    boolean removed = CalendarEventRepository.remove(remove);

    assertTrue(removed);
    assertNull(CalendarEventRepository.findById(remove.getId()));
    assertNotNull(CalendarEventRepository.findById(keep.getId()));
  }
}
