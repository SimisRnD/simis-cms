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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.json.JsonCommand;
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
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Issue #426 (editorial calendar): given a [start, end) date range and optional type/author/status
 * filters, returns a unified JSON list of editorial calendar entries aggregated from web pages
 * (publishAt/expiresAt), blog posts (startDate/endDate -- BlogPost has no publishAt/expiresAt
 * columns, see {@link BlogPostSpecification}'s field comment), and calendar events (their own
 * startDate). Each entry carries a title, a type badge (Page/Post/Event), a status
 * (Scheduled/Draft/Published/Expiring), the date it's anchored to, and the admin edit-form URL for
 * that content -- reusing the exact edit-form URL patterns
 * {@code web-page-list.jsp}/{@code blog-post-list.jsp}/{@code calendar-event-list.jsp} already use,
 * not new ones.
 *
 * <p>Registered in json-services.xml at {@code /json/editorialCalendar} with
 * {@code role="admin,content-manager,community-manager"} -- the same role set /admin/calendars and
 * /admin/calendar-event use -- because this feed exposes admin-only data (draft pages, unpublished
 * posts, unpublished events) that must never reach an unprivileged caller. That page-level gate
 * (enforced by PageServlet before any widget/service class is dispatched, mirroring how every other
 * {@code <page>}/{@code <service>} role check in this codebase already works) is what actually
 * blocks an unauthorized request; {@link #isAuthorized(WidgetContext)} below is a defensive,
 * belt-and-suspenders re-check for the case this class is ever reused outside that page.</p>
 *
 * <p>Mirrors {@code com.simisinc.platform.presentation.widgets.calendar.CalendarAjax}'s manual
 * StringBuilder JSON assembly (no Jackson) and its start/end date-parsing (ISO8601 or a plain
 * {@code yyyy-MM-dd} fallback) exactly.</p>
 *
 * @author SimIS Inc.
 */
public class EditorialCalendarAjax extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public WidgetContext execute(WidgetContext context) {

    if (!isAuthorized(context)) {
      context.setJson("[]");
      return context;
    }

    String start = context.getParameter("start");
    String end = context.getParameter("end");
    if (StringUtils.isBlank(start) || StringUtils.isBlank(end)) {
      context.setJson("[]");
      return context;
    }

    Timestamp rangeStart;
    Timestamp rangeEnd;
    try {
      // ISO8601 date strings 2022-05-01T00:00:00-04:00, or a plain yyyy-MM-dd fallback -- matches
      // CalendarAjax.execute()'s parsing exactly, since FullCalendar's eventSources fetch appends
      // these same start/end parameter names and formats.
      Date startDate = start.contains("T") ? parseISO8601(start) : parseSimpleDateFormat(start);
      Date endDate = end.contains("T") ? parseISO8601(end) : parseSimpleDateFormat(end);
      rangeStart = new Timestamp(startDate.getTime());
      rangeEnd = new Timestamp(endDate.getTime());
    } catch (Exception e) {
      LOG.error("Date/time exception: " + e.getMessage(), e);
      context.setJson("[]");
      return context;
    }

    String typeFilter = StringUtils.trimToNull(context.getParameter("type"));
    long authorId = context.getParameterAsLong("authorId", -1);
    String statusFilter = StringUtils.trimToNull(context.getParameter("status"));

    Timestamp now = new Timestamp(System.currentTimeMillis());

    StringBuilder sb = new StringBuilder();
    if (typeFilter == null || "page".equalsIgnoreCase(typeFilter)) {
      addPages(rangeStart, rangeEnd, authorId, statusFilter, now, sb);
    }
    if (typeFilter == null || "post".equalsIgnoreCase(typeFilter)) {
      addPosts(rangeStart, rangeEnd, authorId, statusFilter, now, sb);
    }
    if (typeFilter == null || "event".equalsIgnoreCase(typeFilter)) {
      addEvents(rangeStart, rangeEnd, authorId, statusFilter, now, sb);
    }

    context.setJson("[" + sb + "]");
    return context;
  }

  /**
   * True when the current caller is allowed to see this admin-only feed. See the class-level
   * javadoc -- this is a defensive re-check, not the primary access control (that's the
   * {@code role=} attribute on this service's json-services.xml registration).
   */
  private static boolean isAuthorized(WidgetContext context) {
    return context.getUserSession() != null
        && (context.hasRole("admin") || context.hasRole("content-manager") || context.hasRole("community-manager"));
  }

  // --- Pages ---

  private static void addPages(Timestamp rangeStart, Timestamp rangeEnd, long authorId, String statusFilter,
      Timestamp now, StringBuilder sb) {
    WebPageSpecification specification = new WebPageSpecification();
    specification.setStartingDateRange(rangeStart);
    specification.setEndingDateRange(rangeEnd);
    // Archived pages are no longer editorially relevant -- exclude them from the calendar,
    // mirroring CalendarAjaxEvents' identical archived exclusion for the public calendar (#882).
    specification.setArchivedOnly(false);
    if (authorId > -1) {
      specification.setCreatedBy(authorId);
    }
    List<WebPage> pageList = WebPageRepository.findAll(specification, null);
    for (WebPage page : pageList) {
      String editUrl = "/admin/web-page?webPageId=" + page.getId() + "&returnPage=/admin/editorial-calendar";
      if (page.getPublishAt() != null && inRange(page.getPublishAt(), rangeStart, rangeEnd)) {
        appendEntry(sb, "page-" + page.getId() + "-publish", "Page", page.getTitle(), pageStatus(page, now),
            page.getPublishAt(), editUrl, statusFilter);
      }
      if (page.getExpiresAt() != null && inRange(page.getExpiresAt(), rangeStart, rangeEnd)) {
        appendEntry(sb, "page-" + page.getId() + "-expire", "Page", page.getTitle(), "Expiring",
            page.getExpiresAt(), editUrl, statusFilter);
      }
    }
  }

  /** Page -- a future publishAt takes precedence (it IS the scheduling mechanism, issue #371);
   * otherwise a page that has never gone live, or has a pending draft revision, reads as Draft. */
  private static String pageStatus(WebPage page, Timestamp now) {
    if (page.getPublishAt() != null && page.getPublishAt().after(now)) {
      return "Scheduled";
    }
    if (page.getDraft() || page.hasDraftContent()) {
      return "Draft";
    }
    return "Published";
  }

  // --- Blog posts ---

  private static void addPosts(Timestamp rangeStart, Timestamp rangeEnd, long authorId, String statusFilter,
      Timestamp now, StringBuilder sb) {
    BlogPostSpecification specification = new BlogPostSpecification();
    specification.setStartingDateRange(rangeStart);
    specification.setEndingDateRange(rangeEnd);
    specification.setArchivedOnly(false);
    if (authorId > -1) {
      specification.setCreatedBy(authorId);
    }
    List<BlogPost> postList = BlogPostRepository.findAll(specification, null);
    if (postList.isEmpty()) {
      return;
    }
    // Resolve each post's blog for the blog-editor edit URL (mirrors AdminBlogPostListWidget's
    // blogList lookup, keyed here for O(1) access per post).
    Map<Long, Blog> blogMap = new HashMap<>();
    for (Blog blog : BlogRepository.findAll()) {
      blogMap.put(blog.getId(), blog);
    }
    for (BlogPost post : postList) {
      Blog blog = blogMap.get(post.getBlogId());
      String blogUniqueId = blog != null ? blog.getUniqueId() : "";
      String editUrl = "/blog-editor?blogUniqueId=" + URLEncoder.encode(blogUniqueId, StandardCharsets.UTF_8)
          + "&blogPostId=" + post.getId() + "&returnPage=/admin/editorial-calendar";
      if (post.getStartDate() != null && inRange(post.getStartDate(), rangeStart, rangeEnd)) {
        appendEntry(sb, "post-" + post.getId() + "-publish", "Post", post.getTitle(), postStatus(post, now),
            post.getStartDate(), editUrl, statusFilter);
      }
      if (post.getEndDate() != null && inRange(post.getEndDate(), rangeStart, rangeEnd)) {
        appendEntry(sb, "post-" + post.getId() + "-expire", "Post", post.getTitle(), "Expiring",
            post.getEndDate(), editUrl, statusFilter);
      }
    }
  }

  /** Post -- a future startDate (the public-visibility gate, see BlogPostSpecification's field
   * comment) takes precedence; otherwise a post that has never been published reads as Draft. */
  private static String postStatus(BlogPost post, Timestamp now) {
    if (post.getStartDate() != null && post.getStartDate().after(now)) {
      return "Scheduled";
    }
    if (post.getPublished() == null) {
      return "Draft";
    }
    return "Published";
  }

  // --- Calendar events ---

  private static void addEvents(Timestamp rangeStart, Timestamp rangeEnd, long authorId, String statusFilter,
      Timestamp now, StringBuilder sb) {
    CalendarEventSpecification specification = new CalendarEventSpecification();
    specification.setStartingDateRange(rangeStart);
    specification.setEndingDateRange(rangeEnd);
    specification.setArchivedOnly(false);
    if (authorId > -1) {
      specification.setCreatedBy(authorId);
    }
    List<CalendarEvent> eventList = CalendarEventRepository.findAll(specification, null);
    for (CalendarEvent event : eventList) {
      // An event's start/end IS the event -- unlike pages/posts there's no separate
      // publish-schedule field to also emit an "Expiring" entry for.
      if (event.getStartDate() != null && inRange(event.getStartDate(), rangeStart, rangeEnd)) {
        String editUrl = "/admin/calendar-event?calendarEventId=" + event.getId() + "&returnPage=/admin/editorial-calendar";
        appendEntry(sb, "event-" + event.getId(), "Event", event.getTitle(), eventStatus(event, now),
            event.getStartDate(), editUrl, statusFilter);
      }
    }
  }

  /** Event -- an event that has never been published reads as Draft regardless of its date;
   * otherwise a future startDate reads as Scheduled. */
  private static String eventStatus(CalendarEvent event, Timestamp now) {
    if (event.getPublished() == null) {
      return "Draft";
    }
    if (event.getStartDate() != null && event.getStartDate().after(now)) {
      return "Scheduled";
    }
    return "Published";
  }

  // --- Shared helpers ---

  private static boolean inRange(Timestamp value, Timestamp rangeStart, Timestamp rangeEnd) {
    return !value.before(rangeStart) && value.before(rangeEnd);
  }

  private static void appendEntry(StringBuilder sb, String id, String type, String title, String status,
      Timestamp date, String editUrl, String statusFilter) {
    if (statusFilter != null && !statusFilter.equalsIgnoreCase(status)) {
      return;
    }
    if (sb.length() > 0) {
      sb.append(",");
    }
    sb.append("{");
    sb.append("\"id\":\"").append(JsonCommand.toJson(id)).append("\",");
    sb.append("\"type\":\"").append(type).append("\",");
    sb.append("\"title\":\"").append(JsonCommand.toJson(title)).append("\",");
    sb.append("\"status\":\"").append(status).append("\",");
    sb.append("\"date\":\"").append(new SimpleDateFormat("yyyy-MM-dd").format(date)).append("\",");
    sb.append("\"editUrl\":\"").append(JsonCommand.toJson(editUrl)).append("\"");
    sb.append("}");
  }

  private static Date parseISO8601(String value) {
    DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_DATE_TIME;
    OffsetDateTime offsetDateTime = OffsetDateTime.parse(value, timeFormatter);
    return Date.from(Instant.from(offsetDateTime));
  }

  private static Date parseSimpleDateFormat(String value) throws ParseException {
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm");
    return dateFormat.parse(value + (value.contains(" 00:00") ? "" : " 00:00"));
  }
}
