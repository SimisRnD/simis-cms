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

import com.simisinc.platform.application.cms.ContentReviewCommand;
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
 * (Scheduled/Pending Review/Draft/Published/Expiring -- see {@link #pageStatus}/{@link
 * #postStatus} for why a page/post has one more status than an event), the date it's anchored to,
 * and the admin edit-form URL for that content -- reusing the exact edit-form URL patterns
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
 * <p>Issue #996: a page/post/event with no scheduling date set at all (neither field, for
 * whichever pair of fields that type uses) has no anchor date and can never appear in the
 * date-ranged feed above, under any range. Rather than forcing it onto a specific day, a request
 * with {@code undated=true} short-circuits {@link #execute(WidgetContext)} into a separate code
 * path ({@link #executeUndated(WidgetContext)}) that returns every such record instead, still
 * honoring the same type/authorId/status filters and the same {@link #isAuthorized(WidgetContext)}
 * gate. This stays a single query-param branch on the existing endpoint/class rather than a
 * second json-services.xml registration, since every entry still needs the exact same
 * per-type edit-URL and status logic this class already has -- a second class would either
 * duplicate that logic or need to call back into this one anyway. Each returned entry omits the
 * "date" key entirely (see {@link #appendEntry}) rather than inventing a placeholder value for a
 * field that, by definition, does not exist for this content -- callers must not assume "date" is
 * always present the way the date-ranged feed above guarantees it.</p>
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

    // issue #996: the "Drafts with no dates" feed -- see the class javadoc. Checked before the
    // start/end requirement below because this path needs no date range at all.
    if ("true".equalsIgnoreCase(StringUtils.trimToNull(context.getParameter("undated")))) {
      return executeUndated(context);
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
   * Issue #996: the "Drafts with no dates" feed -- see the class javadoc. Reuses the same
   * type/authorId/status filter params and the same per-type status/edit-URL logic as the
   * date-ranged {@link #execute(WidgetContext)} path, but queries each repository with
   * {@code undatedOnly} instead of a date range, and emits entries with no "date" key.
   */
  private WidgetContext executeUndated(WidgetContext context) {
    String typeFilter = StringUtils.trimToNull(context.getParameter("type"));
    long authorId = context.getParameterAsLong("authorId", -1);
    String statusFilter = StringUtils.trimToNull(context.getParameter("status"));

    Timestamp now = new Timestamp(System.currentTimeMillis());

    StringBuilder sb = new StringBuilder();
    if (typeFilter == null || "page".equalsIgnoreCase(typeFilter)) {
      addUndatedPages(authorId, statusFilter, now, sb);
    }
    if (typeFilter == null || "post".equalsIgnoreCase(typeFilter)) {
      addUndatedPosts(authorId, statusFilter, now, sb);
    }
    if (typeFilter == null || "event".equalsIgnoreCase(typeFilter)) {
      addUndatedEvents(authorId, statusFilter, now, sb);
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
      String editUrl = pageEditUrl(page);
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

  /**
   * Issue #996: the undated-only counterpart to {@link #addPages}. Unlike the date-ranged path,
   * a page can never emit more than one entry here (there's no "publish" vs "expire" moment to
   * split on when neither date is set), so the "Expiring" status this class otherwise derives
   * from a non-null expiresAt never applies -- {@link #pageStatus} covers every remaining case.
   */
  private static void addUndatedPages(long authorId, String statusFilter, Timestamp now, StringBuilder sb) {
    WebPageSpecification specification = new WebPageSpecification();
    specification.setUndatedOnly(true);
    specification.setArchivedOnly(false);
    if (authorId > -1) {
      specification.setCreatedBy(authorId);
    }
    List<WebPage> pageList = WebPageRepository.findAll(specification, null);
    for (WebPage page : pageList) {
      appendEntry(sb, "page-" + page.getId(), "Page", page.getTitle(), pageStatus(page, now),
          null, pageEditUrl(page), statusFilter);
    }
  }

  /**
   * Page status -- governed-review state is checked BEFORE the future-publishAt check (fixes a
   * bug found in the #426 research pass: the original order checked publishAt first, so a page
   * still mid governed review with a future publishAt read as "Scheduled", which misleadingly
   * implies it will go live on its own at that date. It will not -- {@link WebPageReviewWidget}
   * shows nothing promotes a submitted-but-unapproved draft to live without an approver acting
   * first. "Scheduled" is only correct once there is no pending draft in the way: PageServlet
   * (see its "Enforce publish schedule and expiry for non-editors" block) and
   * {@code ValidateApiAccessToWebPageCommand} both gate a page's public visibility on
   * {@code publishAt} for exactly this case, so a draft-free page with a future publishAt really
   * will go live automatically, unattended, right on schedule.
   *
   * <p>{@link WebPage#hasDraftContent()} (a non-blank {@code draftPageXml}) means a revision is
   * somewhere in the submit/approve pipeline -- {@link ContentReviewCommand#listStatusLabel} is
   * reused here for the exact same Draft/Pending Review/Approved vocabulary
   * {@code WebPageListWidget}/{@code WebPageReviewWidget} already show elsewhere in the admin,
   * rather than collapsing every one of those states into this class's own coarser "Draft" (the
   * fuller fix, option (b) in the issue notes: {@code WebPageListWidget} already calls this
   * exact method the exact same way -- {@code if (page.hasDraftContent()) { ... =
   * ContentReviewCommand.listStatusLabel(page); }} -- so this is genuine reuse, not a new
   * integration). {@code page.getDraft()} (the separate legacy "never went live at all" flag,
   * unrelated to the governed-review pipeline -- see {@code SaveWebPageCommand}, which never
   * touches draftPageXml/draftStatus) is checked next, preserving this class's original "Draft"
   * label for that case since {@link ContentReviewCommand#listStatusLabel} has nothing to say
   * about a page that was never enrolled in governed review to begin with.
   */
  private static String pageStatus(WebPage page, Timestamp now) {
    if (page.hasDraftContent()) {
      return ContentReviewCommand.listStatusLabel(page);
    }
    if (page.getDraft()) {
      return "Draft";
    }
    if (page.getPublishAt() != null && page.getPublishAt().after(now)) {
      return "Scheduled";
    }
    return "Published";
  }

  /** Reuses the exact admin-edit-form URL pattern {@code web-page-list.jsp} already uses. */
  private static String pageEditUrl(WebPage page) {
    return "/admin/web-page?webPageId=" + page.getId() + "&returnPage=/admin/editorial-calendar";
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
    Map<Long, Blog> blogMap = buildBlogMap();
    for (BlogPost post : postList) {
      String editUrl = postEditUrl(post, blogMap);
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

  /** Issue #996: the undated-only counterpart to {@link #addPosts}, mirroring
   * {@link #addUndatedPages}'s reasoning -- no split "publish" vs "expire" entry is possible when
   * neither date is set, so {@link #postStatus} alone determines the single entry emitted. */
  private static void addUndatedPosts(long authorId, String statusFilter, Timestamp now, StringBuilder sb) {
    BlogPostSpecification specification = new BlogPostSpecification();
    specification.setUndatedOnly(true);
    specification.setArchivedOnly(false);
    if (authorId > -1) {
      specification.setCreatedBy(authorId);
    }
    List<BlogPost> postList = BlogPostRepository.findAll(specification, null);
    if (postList.isEmpty()) {
      return;
    }
    Map<Long, Blog> blogMap = buildBlogMap();
    for (BlogPost post : postList) {
      appendEntry(sb, "post-" + post.getId(), "Post", post.getTitle(), postStatus(post, now),
          null, postEditUrl(post, blogMap), statusFilter);
    }
  }

  /**
   * Post status -- same governed-review-first ordering as {@link #pageStatus}, and for the same
   * reason: the original order checked a future startDate first, so a post still mid governed
   * review read as "Scheduled" even though {@link BlogPostReviewWidget} shows nothing publishes
   * it without an approver acting first. "Scheduled" is only correct once there is no pending
   * draft in the way -- a future startDate on an already-published post (published non-null) is
   * the genuine public-visibility gate ({@link BlogPostSpecification}'s field comment,
   * {@code startDateIsBeforeNow}), so that post really will become publicly visible unattended,
   * right on schedule.
   *
   * <p>{@link BlogPost#hasDraftContent()} (non-blank body, not yet published) means this post is
   * somewhere in the submit/approve pipeline -- {@link ContentReviewCommand#listStatusLabel} is
   * reused here exactly the way {@code AdminBlogPostListWidget} already calls it (
   * {@code if (blogPost.hasDraftContent()) { ... = ContentReviewCommand.listStatusLabel(...); }}),
   * for the same Draft/Pending Review/Approved vocabulary rather than this class's own coarser
   * "Draft". The {@code post.getPublished() == null} branch below only remains reachable for a
   * post with a blank body (no draft content to label) that was still somehow never published --
   * an edge case, but one {@link ContentReviewCommand#listStatusLabel} has nothing to say about
   * either, since it requires {@code hasDraftContent()} to report anything but "Live".
   */
  private static String postStatus(BlogPost post, Timestamp now) {
    if (post.hasDraftContent()) {
      return ContentReviewCommand.listStatusLabel(post);
    }
    if (post.getPublished() == null) {
      return "Draft";
    }
    if (post.getStartDate() != null && post.getStartDate().after(now)) {
      return "Scheduled";
    }
    return "Published";
  }

  /** Resolves every blog up front so each post's blog-editor edit URL is an O(1) lookup, mirroring
   * AdminBlogPostListWidget's blogList lookup. */
  private static Map<Long, Blog> buildBlogMap() {
    Map<Long, Blog> blogMap = new HashMap<>();
    for (Blog blog : BlogRepository.findAll()) {
      blogMap.put(blog.getId(), blog);
    }
    return blogMap;
  }

  private static String postEditUrl(BlogPost post, Map<Long, Blog> blogMap) {
    Blog blog = blogMap.get(post.getBlogId());
    String blogUniqueId = blog != null ? blog.getUniqueId() : "";
    return "/blog-editor?blogUniqueId=" + URLEncoder.encode(blogUniqueId, StandardCharsets.UTF_8)
        + "&blogPostId=" + post.getId() + "&returnPage=/admin/editorial-calendar";
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
      //
      // CalendarEventRepository's own query (see its createWhereStatement) fetches a row when
      // EITHER start_date OR end_date falls in [rangeStart, rangeEnd) -- a two-clause SQL OR --
      // so a multi-day event that started before this range but is still ongoing/ending inside it
      // (e.g. a week-long conference that started last month) is fetched here too. Anchoring only
      // on getStartDate(), as this used to, then silently dropped that event: its start date
      // isn't in the visible range for FullCalendar to plot an entry on, so inRange() below always
      // failed and no entry was ever appended for it, even though the DB query fetched it. Prefer
      // the start date when it's in range (the common case, unchanged from before); fall back to
      // the end date only when the start date itself isn't in range. That keeps this a single
      // calendar entry rather than mirroring addPages()/addPosts()'s two-separate-entries pattern
      // (id suffixes "-publish"/"-expire") -- those fit a page's or post's genuinely distinct
      // publish and expire moments, but an event's start/end are one continuous occurrence, not
      // two, and duplicating the pattern here would double-render every ordinary single-day event
      // (startDate == endDate, as most events are) into two identical same-day chips.
      Timestamp anchor = null;
      if (event.getStartDate() != null && inRange(event.getStartDate(), rangeStart, rangeEnd)) {
        anchor = event.getStartDate();
      } else if (event.getEndDate() != null && inRange(event.getEndDate(), rangeStart, rangeEnd)) {
        anchor = event.getEndDate();
      }
      if (anchor != null) {
        appendEntry(sb, "event-" + event.getId(), "Event", event.getTitle(), eventStatus(event, now),
            anchor, eventEditUrl(event), statusFilter);
      }
    }
  }

  /** Issue #996: the undated-only counterpart to {@link #addEvents}. An event only has one
   * scheduling field to begin with (startDate), so this is otherwise a direct mirror. */
  private static void addUndatedEvents(long authorId, String statusFilter, Timestamp now, StringBuilder sb) {
    CalendarEventSpecification specification = new CalendarEventSpecification();
    specification.setUndatedOnly(true);
    specification.setArchivedOnly(false);
    if (authorId > -1) {
      specification.setCreatedBy(authorId);
    }
    List<CalendarEvent> eventList = CalendarEventRepository.findAll(specification, null);
    for (CalendarEvent event : eventList) {
      appendEntry(sb, "event-" + event.getId(), "Event", event.getTitle(), eventStatus(event, now),
          null, eventEditUrl(event), statusFilter);
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

  private static String eventEditUrl(CalendarEvent event) {
    return "/admin/calendar-event?calendarEventId=" + event.getId() + "&returnPage=/admin/editorial-calendar";
  }

  // --- Shared helpers ---

  private static boolean inRange(Timestamp value, Timestamp rangeStart, Timestamp rangeEnd) {
    return !value.before(rangeStart) && value.before(rangeEnd);
  }

  /**
   * Appends one JSON entry to {@code sb}, or nothing at all if {@code statusFilter} is set and
   * doesn't match {@code status}. {@code date} is nullable (issue #996): the undated-drafts feed
   * has no date to anchor an entry to, so the "date" key is omitted entirely for those entries
   * rather than emitting a placeholder value for a field that, by definition, does not exist --
   * callers of the undated feed must not assume "date" is present the way the date-ranged feed
   * above guarantees it.
   */
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
    sb.append("\"status\":\"").append(status).append("\"");
    if (date != null) {
      // Site-timezone-aware (not the JVM's default zone) so an event near local midnight lands on
      // the same calendar day here as it does in FormatDateCommand's other siteZoneId-based
      // formatting -- otherwise a server running in, say, UTC could show an entry one day off from
      // the day an admin in the configured site timezone would expect.
      sb.append(",\"date\":\"").append(FormatDateCommand.formatIsoDate(date, FormatDateCommand.getSiteZoneId())).append("\"");
    }
    sb.append(",\"editUrl\":\"").append(JsonCommand.toJson(editUrl)).append("\"");
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
