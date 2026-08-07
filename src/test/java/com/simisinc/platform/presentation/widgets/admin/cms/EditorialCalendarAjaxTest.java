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

package com.simisinc.platform.presentation.widgets.admin.cms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageSpecification;
import com.simisinc.platform.presentation.controller.DataConstants;

/**
 * Verifies {@link EditorialCalendarAjax}'s aggregation across pages/posts/events and its
 * Scheduled/Draft/Published/Expiring status derivation (issue #426). Mirrors
 * {@code CalendarEventAjaxTest}'s WidgetBase + mockStatic(Repository) pattern.
 *
 * @author SimIS Inc.
 */
class EditorialCalendarAjaxTest extends WidgetBase {

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

  private static Timestamp daysFromNow(int days) {
    return new Timestamp(System.currentTimeMillis() + Duration.ofDays(days).toMillis());
  }

  private void setDateRange(int startOffsetDays, int endOffsetDays) {
    Date start = new Date(System.currentTimeMillis() + Duration.ofDays(startOffsetDays).toMillis());
    Date end = new Date(System.currentTimeMillis() + Duration.ofDays(endOffsetDays).toMillis());
    addQueryParameter(widgetContext, "start", DATE_FORMAT.format(start));
    addQueryParameter(widgetContext, "end", DATE_FORMAT.format(end));
  }

  private void mockEmptyPostsAndEvents(MockedStatic<BlogPostRepository> posts, MockedStatic<CalendarEventRepository> events) {
    posts.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
    events.when(() -> CalendarEventRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
  }

  private void mockEmptyPagesAndEvents(MockedStatic<WebPageRepository> pages, MockedStatic<CalendarEventRepository> events) {
    pages.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
    events.when(() -> CalendarEventRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
  }

  private void mockEmptyPagesAndPosts(MockedStatic<WebPageRepository> pages, MockedStatic<BlogPostRepository> posts) {
    pages.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
    posts.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
  }

  /**
   * appendEntry()'s "date" field now goes through FormatDateCommand.getSiteZoneId(), which reads
   * "site.timezone" via LoadSitePropertyCommand -- otherwise requiring a real DB connection
   * through its Caffeine-backed cache. Mirrors ItemDateFacetCommandTest's identical helper. Only
   * needed by tests that emit at least one entry with a non-null date -- an entry filtered out by
   * statusFilter, or the undated feed (date is always null there), never reaches that code.
   */
  private static MockedStatic<LoadSitePropertyCommand> mockSiteTimezone() {
    MockedStatic<LoadSitePropertyCommand> mock = mockStatic(LoadSitePropertyCommand.class);
    mock.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
    return mock;
  }

  // --- role gate ---

  @Test
  void aCallerWithNoRelevantRoleGetsNoData() {
    // Default login() grants no roles. The real access control is json-services.xml's page-level
    // role gate (PageServlet blocks the request before this class is ever reached), but this
    // proves the class's own defensive re-check works too, in case it's ever reused elsewhere: no
    // repository should even be queried.
    setDateRange(0, 30);

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
      new EditorialCalendarAjax().execute(widgetContext);

      pages.verifyNoInteractions();
      posts.verifyNoInteractions();
      events.verifyNoInteractions();
    }

    Assertions.assertEquals("[]", widgetContext.getJson());
  }

  // --- pages ---

  @Test
  void pageWithOnlyAFuturePublishAtEmitsOneScheduledEntry() {
    setRoles(widgetContext, ADMIN);
    setDateRange(0, 30);

    WebPage page = new WebPage();
    page.setId(10L);
    page.setTitle("Announcing Q4");
    page.setDraft(false);
    page.setPublishAt(daysFromNow(5));

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockSiteTimezone()) {
      pages.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(List.of(page));
      mockEmptyPostsAndEvents(posts, events);

      new EditorialCalendarAjax().execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertTrue(json.contains("\"type\":\"Page\""), json);
    Assertions.assertTrue(json.contains("\"status\":\"Scheduled\""), json);
    Assertions.assertTrue(json.contains("Announcing Q4"), json);
    Assertions.assertFalse(json.contains("Expiring"), "no expiresAt was set, so no Expiring entry should be emitted: " + json);
  }

  @Test
  void pageWithOnlyAnExpiresAtInRangeEmitsOneExpiringEntry() {
    setRoles(widgetContext, ADMIN);
    setDateRange(0, 30);

    WebPage page = new WebPage();
    page.setId(11L);
    page.setTitle("Old Promo");
    page.setDraft(false);
    page.setExpiresAt(daysFromNow(10));

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockSiteTimezone()) {
      pages.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(List.of(page));
      mockEmptyPostsAndEvents(posts, events);

      new EditorialCalendarAjax().execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertTrue(json.contains("\"status\":\"Expiring\""), json);
    Assertions.assertFalse(json.contains("Scheduled"), "no publishAt was set, so no Scheduled entry should be emitted: " + json);
  }

  @Test
  void pageWithBothPublishAtAndExpiresAtInRangeEmitsTwoDistinctEntries() {
    setRoles(widgetContext, ADMIN);
    setDateRange(0, 30);

    WebPage page = new WebPage();
    page.setId(12L);
    page.setTitle("Campaign Page");
    page.setDraft(false);
    page.setPublishAt(daysFromNow(3));
    page.setExpiresAt(daysFromNow(20));

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockSiteTimezone()) {
      pages.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(List.of(page));
      mockEmptyPostsAndEvents(posts, events);

      new EditorialCalendarAjax().execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertTrue(json.contains("\"status\":\"Scheduled\""), json);
    Assertions.assertTrue(json.contains("\"status\":\"Expiring\""), json);
    Assertions.assertTrue(json.contains("\"id\":\"page-12-publish\""), json);
    Assertions.assertTrue(json.contains("\"id\":\"page-12-expire\""), json);
  }

  @Test
  void pageEditUrlUsesTheExistingAdminWebPageFormPattern() {
    setRoles(widgetContext, ADMIN);
    setDateRange(0, 30);

    WebPage page = new WebPage();
    page.setId(99L);
    page.setTitle("Some Page");
    page.setPublishAt(daysFromNow(5));

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockSiteTimezone()) {
      pages.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(List.of(page));
      mockEmptyPostsAndEvents(posts, events);

      new EditorialCalendarAjax().execute(widgetContext);
    }

    String json = widgetContext.getJson();
    // JsonCommand.toJson escapes "/" as "\/"
    Assertions.assertTrue(json.contains("\\/admin\\/web-page?webPageId=99&returnPage=\\/admin\\/editorial-calendar"), json);
  }

  @Test
  void aDraftPageWithNoScheduleDatesIsNeverEmitted() {
    // A draft page with no publishAt/expiresAt has nothing to anchor a calendar entry to.
    setRoles(widgetContext, ADMIN);
    setDateRange(0, 30);

    WebPage page = new WebPage();
    page.setId(13L);
    page.setTitle("Untouched Draft");
    page.setDraft(true);

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
      pages.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(List.of(page));
      mockEmptyPostsAndEvents(posts, events);

      new EditorialCalendarAjax().execute(widgetContext);
    }

    Assertions.assertEquals("[]", widgetContext.getJson());
  }

  // --- posts ---

  @Test
  void postWithAFutureStartDateEmitsAScheduledEntryWithABlogEditorUrl() {
    setRoles(widgetContext, ADMIN);
    setDateRange(0, 30);

    Blog blog = new Blog();
    blog.setId(5L);
    blog.setUniqueId("news");

    BlogPost post = new BlogPost();
    post.setId(20L);
    post.setBlogId(5L);
    post.setTitle("Upcoming Feature");
    post.setStartDate(daysFromNow(7));
    // #426 status-ordering fix: "Scheduled" now requires the post to have actually been
    // published (see postStatus()) -- a future startDate on a post that was never published
    // (published == null) is the exact ordering bug this fix corrects (see
    // postWithNoBodyAndNeverPublishedStillShowsDraftNotScheduled below), so this genuinely
    // "already published, still gated by a future startDate" scenario needs published set too.
    post.setPublished(daysFromNow(-1));

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<BlogRepository> blogs = mockStatic(BlogRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockSiteTimezone()) {
      mockEmptyPagesAndEvents(pages, events);
      posts.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(List.of(post));
      blogs.when(BlogRepository::findAll).thenReturn(List.of(blog));

      new EditorialCalendarAjax().execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertTrue(json.contains("\"type\":\"Post\""), json);
    Assertions.assertTrue(json.contains("\"status\":\"Scheduled\""), json);
    Assertions.assertTrue(json.contains("\\/blog-editor?blogUniqueId=news&blogPostId=20&returnPage=\\/admin\\/editorial-calendar"), json);
  }

  @Test
  void anUnpublishedPostWithAPastStartDateIsADraft() {
    setRoles(widgetContext, ADMIN);
    setDateRange(-30, 0);

    Blog blog = new Blog();
    blog.setId(5L);
    blog.setUniqueId("news");

    BlogPost post = new BlogPost();
    post.setId(21L);
    post.setBlogId(5L);
    post.setTitle("Never Published");
    post.setStartDate(daysFromNow(-5));
    post.setPublished(null);

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<BlogRepository> blogs = mockStatic(BlogRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockSiteTimezone()) {
      mockEmptyPagesAndEvents(pages, events);
      posts.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(List.of(post));
      blogs.when(BlogRepository::findAll).thenReturn(List.of(blog));

      new EditorialCalendarAjax().execute(widgetContext);
    }

    Assertions.assertTrue(widgetContext.getJson().contains("\"status\":\"Draft\""), widgetContext.getJson());
  }

  @Test
  void aPublishedPostWithAPastStartDateIsPublished() {
    setRoles(widgetContext, ADMIN);
    setDateRange(-30, 0);

    Blog blog = new Blog();
    blog.setId(5L);
    blog.setUniqueId("news");

    BlogPost post = new BlogPost();
    post.setId(22L);
    post.setBlogId(5L);
    post.setTitle("Already Live");
    post.setStartDate(daysFromNow(-5));
    post.setPublished(daysFromNow(-5));

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<BlogRepository> blogs = mockStatic(BlogRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockSiteTimezone()) {
      mockEmptyPagesAndEvents(pages, events);
      posts.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(List.of(post));
      blogs.when(BlogRepository::findAll).thenReturn(List.of(blog));

      new EditorialCalendarAjax().execute(widgetContext);
    }

    Assertions.assertTrue(widgetContext.getJson().contains("\"status\":\"Published\""), widgetContext.getJson());
  }

  // --- events ---

  @Test
  void aPublishedEventWithAFutureStartDateEmitsAScheduledEntryWithACalendarEventFormUrl() {
    setRoles(widgetContext, ADMIN);
    setDateRange(0, 30);

    CalendarEvent event = new CalendarEvent();
    event.setId(30L);
    event.setTitle("Quarterly Town Hall");
    event.setStartDate(daysFromNow(6));
    event.setEndDate(daysFromNow(6));
    event.setPublished(daysFromNow(-1));

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockSiteTimezone()) {
      mockEmptyPagesAndPosts(pages, posts);
      events.when(() -> CalendarEventRepository.findAll(any(), any())).thenReturn(List.of(event));

      new EditorialCalendarAjax().execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertTrue(json.contains("\"type\":\"Event\""), json);
    Assertions.assertTrue(json.contains("\"status\":\"Scheduled\""), json);
    Assertions.assertTrue(json.contains("\\/admin\\/calendar-event?calendarEventId=30&returnPage=\\/admin\\/editorial-calendar"), json);
  }

  @Test
  void anUnpublishedEventIsADraftRegardlessOfItsDate() {
    setRoles(widgetContext, ADMIN);
    setDateRange(0, 30);

    CalendarEvent event = new CalendarEvent();
    event.setId(31L);
    event.setTitle("Not Yet Published");
    event.setStartDate(daysFromNow(6));
    event.setEndDate(daysFromNow(6));
    event.setPublished(null);

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockSiteTimezone()) {
      mockEmptyPagesAndPosts(pages, posts);
      events.when(() -> CalendarEventRepository.findAll(any(), any())).thenReturn(List.of(event));

      new EditorialCalendarAjax().execute(widgetContext);
    }

    Assertions.assertTrue(widgetContext.getJson().contains("\"status\":\"Draft\""), widgetContext.getJson());
  }

  // --- status-ordering fix (#426 research pass): a page/post mid governed review must never
  // read as "Scheduled" just because it also has a future publishAt/startDate -- see
  // EditorialCalendarAjax.pageStatus()/postStatus(). eventStatus() already checked Draft before
  // Scheduled and needed no fix; there's no equivalent test for it here for that reason. ---

  @Test
  void pagePendingReviewWithAFuturePublishAtDoesNotShowScheduled() {
    setRoles(widgetContext, ADMIN);
    setDateRange(0, 30);

    WebPage page = new WebPage();
    page.setId(60L);
    page.setTitle("Mid Review With A Future Date");
    page.setDraft(false);
    // hasDraftContent() true (non-blank draftPageXml) + submitted, not yet approved: exactly the
    // state that used to read as "Scheduled" because the future-publishAt check ran first.
    page.setDraftPageXml("<xml>staged edit</xml>");
    page.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    page.setSubmittedBy(5L);
    page.setPublishAt(daysFromNow(5));

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockSiteTimezone()) {
      pages.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(List.of(page));
      mockEmptyPostsAndEvents(posts, events);

      new EditorialCalendarAjax().execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertTrue(json.contains("\"status\":\"Pending Review\""), json);
    Assertions.assertFalse(json.contains("\"status\":\"Scheduled\""),
        "a page still mid governed review must not read as Scheduled, even with a future publishAt: " + json);
  }

  @Test
  void pageWithAnUnsubmittedDraftAndAFuturePublishAtShowsDraftNotScheduled() {
    // hasDraftContent() true but draftStatus null/never-submitted: ContentReviewCommand.listStatusLabel
    // reports this as "Draft" (being edited, not yet sent for review), not "Pending Review".
    setRoles(widgetContext, ADMIN);
    setDateRange(0, 30);

    WebPage page = new WebPage();
    page.setId(61L);
    page.setTitle("Still Being Edited");
    page.setDraft(false);
    page.setDraftPageXml("<xml>work in progress</xml>");
    page.setPublishAt(daysFromNow(5));

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockSiteTimezone()) {
      pages.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(List.of(page));
      mockEmptyPostsAndEvents(posts, events);

      new EditorialCalendarAjax().execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertTrue(json.contains("\"status\":\"Draft\""), json);
    Assertions.assertFalse(json.contains("\"status\":\"Scheduled\""), json);
  }

  @Test
  void postPendingReviewWithAFutureStartDateDoesNotShowScheduled() {
    setRoles(widgetContext, ADMIN);
    setDateRange(0, 30);

    Blog blog = new Blog();
    blog.setId(5L);
    blog.setUniqueId("news");

    BlogPost post = new BlogPost();
    post.setId(62L);
    post.setBlogId(5L);
    post.setTitle("Mid Review Post With A Future Date");
    // hasDraftContent() true (non-blank body, published still null) + submitted, not yet
    // approved: exactly the state that used to read as "Scheduled" because the future-startDate
    // check ran first.
    post.setBody("Draft body text");
    post.setPublished(null);
    post.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    post.setSubmittedBy(5L);
    post.setStartDate(daysFromNow(5));

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<BlogRepository> blogs = mockStatic(BlogRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockSiteTimezone()) {
      mockEmptyPagesAndEvents(pages, events);
      posts.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(List.of(post));
      blogs.when(BlogRepository::findAll).thenReturn(List.of(blog));

      new EditorialCalendarAjax().execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertTrue(json.contains("\"status\":\"Pending Review\""), json);
    Assertions.assertFalse(json.contains("\"status\":\"Scheduled\""),
        "a post still mid governed review must not read as Scheduled, even with a future startDate: " + json);
  }

  @Test
  void postWithNoBodyAndNeverPublishedStillShowsDraftNotScheduled() {
    // hasDraftContent() requires a non-blank body -- a blank-body post with a future startDate
    // (an edge case: a post record created but never actually written) falls through to the
    // post.getPublished() == null check instead, which still correctly reads as Draft, not
    // Scheduled.
    setRoles(widgetContext, ADMIN);
    setDateRange(0, 30);

    Blog blog = new Blog();
    blog.setId(5L);
    blog.setUniqueId("news");

    BlogPost post = new BlogPost();
    post.setId(63L);
    post.setBlogId(5L);
    post.setTitle("Empty Shell Post");
    post.setPublished(null);
    post.setStartDate(daysFromNow(5));

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<BlogRepository> blogs = mockStatic(BlogRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockSiteTimezone()) {
      mockEmptyPagesAndEvents(pages, events);
      posts.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(List.of(post));
      blogs.when(BlogRepository::findAll).thenReturn(List.of(blog));

      new EditorialCalendarAjax().execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertTrue(json.contains("\"status\":\"Draft\""), json);
    Assertions.assertFalse(json.contains("\"status\":\"Scheduled\""), json);
  }

  // --- multi-day calendar events (Bug A, #426 research pass): CalendarEventRepository's own
  // query fetches an event when EITHER start_date OR end_date falls in the requested range (a
  // two-clause SQL OR); addEvents() used to anchor only on getStartDate(), silently dropping a
  // multi-day event that started before the range but is still ongoing/ending inside it. ---

  @Test
  void multiDayEventStartingBeforeTheRangeButEndingInsideItStillAppears() {
    setRoles(widgetContext, ADMIN);
    setDateRange(0, 30);

    CalendarEvent event = new CalendarEvent();
    event.setId(70L);
    event.setTitle("Week-Long Conference");
    // Started 10 days ago -- before setDateRange(0, 30)'s window -- but still running, ending 5
    // days from now, inside the window.
    event.setStartDate(daysFromNow(-10));
    event.setEndDate(daysFromNow(5));
    event.setPublished(daysFromNow(-20));

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockSiteTimezone()) {
      mockEmptyPagesAndPosts(pages, posts);
      events.when(() -> CalendarEventRepository.findAll(any(), any())).thenReturn(List.of(event));

      new EditorialCalendarAjax().execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertTrue(json.contains("\"type\":\"Event\""), json);
    Assertions.assertTrue(json.contains("Week-Long Conference"), json);
    Assertions.assertTrue(json.contains("\"date\":\"" + DATE_FORMAT.format(event.getEndDate()) + "\""),
        "a multi-day event that started before the range must be anchored on its in-range end date: " + json);
  }

  @Test
  void singleDayEventEmitsExactlyOneEntryNotTwo() {
    // Guards the design decision in addEvents(): an ordinary single-day event (startDate ==
    // endDate) must still emit exactly one calendar entry, not two identical same-day chips.
    setRoles(widgetContext, ADMIN);
    setDateRange(0, 30);

    CalendarEvent event = new CalendarEvent();
    event.setId(71L);
    event.setTitle("Single Day Standup");
    event.setStartDate(daysFromNow(6));
    event.setEndDate(daysFromNow(6));
    event.setPublished(daysFromNow(-1));

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockSiteTimezone()) {
      mockEmptyPagesAndPosts(pages, posts);
      events.when(() -> CalendarEventRepository.findAll(any(), any())).thenReturn(List.of(event));

      new EditorialCalendarAjax().execute(widgetContext);
    }

    String json = widgetContext.getJson();
    int entryCount = json.split("\"id\":\"event-71\"", -1).length - 1;
    Assertions.assertEquals(1, entryCount, "expected exactly one entry for a single-day event: " + json);
  }

  @Test
  void eventEndingBeforeTheRangeStartsIsStillExcluded() {
    // Sanity check on the new start-or-end logic: an event that is entirely in the past (both
    // dates before the requested range) must not appear just because the fallback now also checks
    // getEndDate().
    setRoles(widgetContext, ADMIN);
    setDateRange(0, 30);

    CalendarEvent event = new CalendarEvent();
    event.setId(72L);
    event.setTitle("Long Over");
    event.setStartDate(daysFromNow(-20));
    event.setEndDate(daysFromNow(-15));
    event.setPublished(daysFromNow(-30));

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
      mockEmptyPagesAndPosts(pages, posts);
      events.when(() -> CalendarEventRepository.findAll(any(), any())).thenReturn(List.of(event));

      new EditorialCalendarAjax().execute(widgetContext);
    }

    Assertions.assertEquals("[]", widgetContext.getJson());
  }

  // --- filters ---

  @Test
  void typeFilterOfPageSkipsPostsAndEvents() {
    setRoles(widgetContext, ADMIN);
    setDateRange(0, 30);
    addQueryParameter(widgetContext, "type", "page");

    WebPage page = new WebPage();
    page.setId(40L);
    page.setTitle("Only This Page");
    page.setPublishAt(daysFromNow(5));

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockSiteTimezone()) {
      pages.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(List.of(page));

      new EditorialCalendarAjax().execute(widgetContext);

      posts.verifyNoInteractions();
      events.verifyNoInteractions();
    }

    Assertions.assertTrue(widgetContext.getJson().contains("Only This Page"), widgetContext.getJson());
  }

  @Test
  void statusFilterOfDraftExcludesAScheduledEntry() {
    setRoles(widgetContext, ADMIN);
    setDateRange(0, 30);
    addQueryParameter(widgetContext, "status", "Draft");

    WebPage page = new WebPage();
    page.setId(41L);
    page.setTitle("Scheduled Not Draft");
    page.setPublishAt(daysFromNow(5));

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
      pages.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(List.of(page));
      mockEmptyPostsAndEvents(posts, events);

      new EditorialCalendarAjax().execute(widgetContext);
    }

    Assertions.assertEquals("[]", widgetContext.getJson());
  }

  @Test
  void authorIdFilterIsPassedThroughToEverySpecification() {
    setRoles(widgetContext, ADMIN);
    setDateRange(0, 30);
    addQueryParameter(widgetContext, "authorId", "7");

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
      mockEmptyPagesAndPosts(pages, posts);
      events.when(() -> CalendarEventRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      new EditorialCalendarAjax().execute(widgetContext);

      pages.verify(() -> WebPageRepository.findAll(argThat((WebPageSpecification s) -> s.getCreatedBy() == 7L), any()));
      posts.verify(() -> BlogPostRepository.findAll(argThat((BlogPostSpecification s) -> s.getCreatedBy() == 7L), any()));
      events.verify(() -> CalendarEventRepository.findAll(argThat((CalendarEventSpecification s) -> s.getCreatedBy() == 7L), any()));
    }
  }

  @Test
  void missingStartOrEndParametersReturnEmptyJsonWithoutQueryingAnyRepository() {
    setRoles(widgetContext, ADMIN);

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
      new EditorialCalendarAjax().execute(widgetContext);

      pages.verifyNoInteractions();
      posts.verifyNoInteractions();
      events.verifyNoInteractions();
    }

    Assertions.assertEquals("[]", widgetContext.getJson());
  }

  // --- undated drafts (issue #996) ---

  @Test
  void undatedFeedRequiresAuthorization() {
    // Mirrors aCallerWithNoRelevantRoleGetsNoData -- the role gate applies to the undated=true
    // path exactly like the date-ranged path.
    addQueryParameter(widgetContext, "undated", "true");

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
      new EditorialCalendarAjax().execute(widgetContext);

      pages.verifyNoInteractions();
      posts.verifyNoInteractions();
      events.verifyNoInteractions();
    }

    Assertions.assertEquals("[]", widgetContext.getJson());
  }

  @Test
  void aPageWithNoDatesAppearsInTheUndatedFeedWithNoDateKey() {
    // No setDateRange()/start/end at all -- proves the undated path doesn't need (or get
    // short-circuited by) the missing-start/end guard the date-ranged path has.
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "undated", "true");

    WebPage page = new WebPage();
    page.setId(50L);
    page.setTitle("Someday Maybe");
    page.setDraft(true);

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
      pages.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(List.of(page));
      mockEmptyPostsAndEvents(posts, events);

      new EditorialCalendarAjax().execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertTrue(json.contains("\"type\":\"Page\""), json);
    Assertions.assertTrue(json.contains("\"status\":\"Draft\""), json);
    Assertions.assertTrue(json.contains("Someday Maybe"), json);
    Assertions.assertFalse(json.contains("\"date\":"), "an undated entry must not carry a date key: " + json);
  }

  @Test
  void aPostWithNoDatesAppearsInTheUndatedFeed() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "undated", "true");

    Blog blog = new Blog();
    blog.setId(5L);
    blog.setUniqueId("news");

    BlogPost post = new BlogPost();
    post.setId(51L);
    post.setBlogId(5L);
    post.setTitle("Unscheduled Draft Post");

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<BlogRepository> blogs = mockStatic(BlogRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
      mockEmptyPagesAndEvents(pages, events);
      posts.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(List.of(post));
      blogs.when(BlogRepository::findAll).thenReturn(List.of(blog));

      new EditorialCalendarAjax().execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertTrue(json.contains("\"type\":\"Post\""), json);
    Assertions.assertTrue(json.contains("\"status\":\"Draft\""), json);
    Assertions.assertTrue(json.contains("\\/blog-editor?blogUniqueId=news&blogPostId=51&returnPage=\\/admin\\/editorial-calendar"), json);
    Assertions.assertFalse(json.contains("\"date\":"), json);
  }

  @Test
  void anEventWithNoDatesAppearsInTheUndatedFeed() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "undated", "true");

    CalendarEvent event = new CalendarEvent();
    event.setId(52L);
    event.setTitle("Not Yet Scheduled");

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
      mockEmptyPagesAndPosts(pages, posts);
      events.when(() -> CalendarEventRepository.findAll(any(), any())).thenReturn(List.of(event));

      new EditorialCalendarAjax().execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertTrue(json.contains("\"type\":\"Event\""), json);
    Assertions.assertTrue(json.contains("\"status\":\"Draft\""), json);
    Assertions.assertTrue(json.contains("\\/admin\\/calendar-event?calendarEventId=52&returnPage=\\/admin\\/editorial-calendar"), json);
    Assertions.assertFalse(json.contains("\"date\":"), json);
  }

  @Test
  void typeFilterOfPageSkipsPostsAndEventsInTheUndatedFeed() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "undated", "true");
    addQueryParameter(widgetContext, "type", "page");

    WebPage page = new WebPage();
    page.setId(53L);
    page.setTitle("Only This Undated Page");
    page.setDraft(true);

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
      pages.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(List.of(page));

      new EditorialCalendarAjax().execute(widgetContext);

      posts.verifyNoInteractions();
      events.verifyNoInteractions();
    }

    Assertions.assertTrue(widgetContext.getJson().contains("Only This Undated Page"), widgetContext.getJson());
  }

  @Test
  void statusFilterExcludesAnUndatedEntryThatDoesNotMatch() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "undated", "true");
    addQueryParameter(widgetContext, "status", "Published");

    WebPage page = new WebPage();
    page.setId(54L);
    page.setTitle("Draft Not Published");
    page.setDraft(true);

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
      pages.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(List.of(page));
      mockEmptyPostsAndEvents(posts, events);

      new EditorialCalendarAjax().execute(widgetContext);
    }

    Assertions.assertEquals("[]", widgetContext.getJson());
  }

  @Test
  void authorIdFilterIsPassedThroughToEveryUndatedSpecification() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "undated", "true");
    addQueryParameter(widgetContext, "authorId", "7");

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
      mockEmptyPagesAndPosts(pages, posts);
      events.when(() -> CalendarEventRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      new EditorialCalendarAjax().execute(widgetContext);

      pages.verify(() -> WebPageRepository.findAll(
          argThat((WebPageSpecification s) -> s.isUndatedOnly() && s.getCreatedBy() == 7L), any()));
      posts.verify(() -> BlogPostRepository.findAll(
          argThat((BlogPostSpecification s) -> s.isUndatedOnly() && s.getCreatedBy() == 7L), any()));
      events.verify(() -> CalendarEventRepository.findAll(
          argThat((CalendarEventSpecification s) -> s.isUndatedOnly() && s.getCreatedBy() == 7L), any()));
    }
  }

  @Test
  void undatedQueriesExcludeArchivedContent() {
    // Issue #996's coverage requirement: archived undated content must not leak into the "Drafts
    // with no dates" feed, mirroring the date-ranged path's identical archivedOnly(false) call.
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "undated", "true");

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class);
        MockedStatic<BlogPostRepository> posts = mockStatic(BlogPostRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
      mockEmptyPagesAndPosts(pages, posts);
      events.when(() -> CalendarEventRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      new EditorialCalendarAjax().execute(widgetContext);

      pages.verify(() -> WebPageRepository.findAll(
          argThat((WebPageSpecification s) -> s.getArchivedOnly() == DataConstants.FALSE), any()));
      posts.verify(() -> BlogPostRepository.findAll(
          argThat((BlogPostSpecification s) -> s.getArchivedOnly() == DataConstants.FALSE), any()));
      events.verify(() -> CalendarEventRepository.findAll(
          argThat((CalendarEventSpecification s) -> s.getArchivedOnly() == DataConstants.FALSE), any()));
    }
  }
}
