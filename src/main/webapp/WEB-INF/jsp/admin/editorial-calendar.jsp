<%--
  ~ Copyright 2026 SimIS Inc.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>
<%@ page import="static com.simisinc.platform.ApplicationInfo.VERSION" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="authorList" class="java.util.ArrayList" scope="request"/>
<%-- Issue #426 (Bug B follow-up): a String "true"/"false" request attribute, not a raw EL boolean
     expression -- see EditorialCalendarWidget.java's canEditPagesAndPosts comment, and
     superset-embedded.jsp's hideChartTitle/hideChartControls for the established precedent of
     embedding a Java-computed boolean string straight into inline JS below. --%>
<jsp:useBean id="canEditPagesAndPosts" class="java.lang.String" scope="request"/>
<%-- Issue #426: editorial calendar -- reuses the same FullCalendar/moment bundle and CSS as the
     public full-calendar.jsp; the events themselves come from /json/editorialCalendar
     (EditorialCalendarAjax), not from this JSP. --%>
<link rel="stylesheet" href="${ctx}/css/platform-calendar.css?v=<%= VERSION %>" />
<script src="${ctx}/javascript/fullcalendar-6.1.10/moment-2.27.0.min.js"></script>
<script src="${ctx}/javascript/fullcalendar-6.1.10/index.global.min.js"></script>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>

<div class="callout primary radius">
  <h6>What this page shows</h6>
  <p>A read-only, aggregated planning view across the three content types that carry their own
    scheduling dates -- <strong>Web Pages</strong> (publish/expire dates), <strong>Blog
    Posts</strong> (start/end dates), and <strong>Calendar Events</strong> (their event date) --
    so an editor doesn't have to check three separate admin sections to see what's going out soon.
    It does <strong>not</strong> include Content blocks, Wiki pages, or Items: none of those carry
    a scheduling date of their own for this page to plot.</p>
  <p>Nothing can be created, edited, or published from here. Clicking an entry (where you have
    edit access to it -- see "Why some entries aren't clickable" below, if it applies to you)
    opens that content's own admin edit form, the same one
    <c:choose>
      <c:when test="${canEditPagesAndPosts == 'true'}">
        <a href="${ctx}/admin/web-pages">Web Pages</a>, <a href="${ctx}/admin/blog-posts">Blog
        Posts</a>, and
      </c:when>
      <c:otherwise>
        Web Pages, Blog Posts, and
      </c:otherwise>
    </c:choose>
    <a href="${ctx}/admin/calendars">Calendars</a> already link to.</p>
</div>

<%-- Issue #426 research finding: no legend existed anywhere on this page -- every entry was only
     self-describing individually, with nothing explaining the type icons or status colors to a
     first-time viewer. Reuses the exact TYPE_ICONS/STATUS_LABEL_CLASS/STATUS_ICONS pairings the
     script below renders with, and the same Foundation label classes/icons web-page-list.jsp and
     content-list.jsp already use for this same live/draft/scheduled/pending-review vocabulary, so
     this key matches what's actually on the calendar rather than describing it approximately. --%>
<div class="callout radius">
  <h6>Legend</h6>
  <div class="grid-x grid-margin-x ec-legend">
    <div class="cell small-6 medium-4 large-2"><i class="fa fa-fw fa-sticky-note" aria-hidden="true"></i> Page</div>
    <div class="cell small-6 medium-4 large-2"><i class="fa fa-fw fa-quote-right" aria-hidden="true"></i> Post</div>
    <div class="cell small-6 medium-4 large-2"><i class="fa fa-fw fa-calendar" aria-hidden="true"></i> Event</div>
    <div class="cell small-6 medium-4 large-2"><span class="label secondary radius"><i class="fa fa-clock" aria-hidden="true"></i> Scheduled</span></div>
    <div class="cell small-6 medium-4 large-2"><span class="label primary radius"><i class="fa fa-hourglass-half" aria-hidden="true"></i> Pending Review</span></div>
    <div class="cell small-6 medium-4 large-2"><span class="label warning radius">Draft</span></div>
    <div class="cell small-6 medium-4 large-2"><span class="label success radius">Published</span></div>
    <div class="cell small-6 medium-4 large-2"><span class="label alert radius"><i class="fa fa-hourglass-end" aria-hidden="true"></i> Expiring</span></div>
  </div>
  <p class="small" style="margin-top:8px; margin-bottom:0">
    <strong>Scheduled</strong> means the content is fully approved and ready -- it will go live
    automatically at its target date/time with no further action needed. <strong>Pending
    Review</strong> means a draft revision is still awaiting an approver's decision -- it will
    <strong>not</strong> go live on its own even after its target date passes; an approver has to
    act on it first -- the same governed submit/approve workflow Content blocks already use.
    <strong>Draft</strong> content has not yet been submitted for review at all.
  </p>
</div>

<c:if test="${canEditPagesAndPosts != 'true'}">
  <div class="callout radius">
    <p style="margin-bottom:0">
      <i class="fa fa-info-circle" aria-hidden="true"></i> <strong>Why some entries aren't
      clickable:</strong> Page and Post entries below are shown for awareness but aren't
      clickable for your role -- you don't have edit access to Web Pages or Blog Posts elsewhere
      in this admin either, so there's nothing to open. Event entries stay clickable, since you do
      have edit access to Calendar Events.
    </p>
  </div>
</c:if>

<%-- Filters -- re-fetch the currently-visible date range from /json/editorialCalendar whenever
     one changes (see the inline script below), rather than a page reload. --%>
<div class="grid-x grid-margin-x align-bottom" id="editorialCalendarFilters">
  <div class="cell small-12 medium-3">
    <label>Content Type
      <select id="editorialCalendarTypeFilter">
        <option value="">All Types</option>
        <option value="page">Page</option>
        <option value="post">Post</option>
        <option value="event">Event</option>
      </select>
    </label>
  </div>
  <div class="cell small-12 medium-3">
    <label>Author
      <select id="editorialCalendarAuthorFilter">
        <option value="">All Authors</option>
        <c:forEach items="${authorList}" var="author">
          <option value="${author.id}"><c:out value="${!empty author.fullName ? author.fullName : author.username}" /></option>
        </c:forEach>
      </select>
    </label>
  </div>
  <div class="cell small-12 medium-3">
    <label>Status
      <select id="editorialCalendarStatusFilter">
        <option value="">All Statuses</option>
        <option value="Scheduled">Scheduled</option>
        <option value="Draft">Draft</option>
        <%-- Issue #426 status-ordering fix: EditorialCalendarAjax.pageStatus()/postStatus() now
             route a page/post with a pending governed-review draft through
             ContentReviewCommand.listStatusLabel(), which can return "Pending Review" -- add it
             here so it's actually filterable, matching content-list.jsp's identical option value
             (must match ContentReviewCommand.LIST_STATUS_PENDING_REVIEW exactly). --%>
        <option value="Pending Review">Pending Review</option>
        <option value="Published">Published</option>
        <option value="Expiring">Expiring</option>
      </select>
    </label>
  </div>
  <div class="cell small-12 medium-3">
    <button type="button" id="editorialCalendarClearFilters" class="button secondary small radius expanded">Clear Filters</button>
  </div>
</div>
<%-- issue #996: the calendar grid can only ever plot content that has an anchor date -- a pure
     draft with neither scheduling field set is invisible on it under any date range. This sidebar
     cell surfaces that content instead, fetched separately from /json/editorialCalendar?undated=true
     (see the inline script below), reusing the same type/author/status filters above. --%>
<div class="grid-x grid-margin-x">
  <div class="cell small-12 large-8">
    <div id="editorialCalendar"></div>
  </div>
  <div class="cell small-12 large-4">
    <h6>Drafts with no dates</h6>
    <ul id="editorialCalendarUndatedList" class="no-bullet ec-undated-list"></ul>
  </div>
</div>

<h5>What to monitor</h5>
<div class="callout radius">
  <p>The date-range feed behind the calendar grid, and the "Drafts with no dates" feed on the
    right, are both <strong>unbounded -- no pagination</strong> -- by design, since this is a
    calendar view, not a paged list: a month either shows everything scheduled in it or it isn't a
    useful calendar. On a very large, long-running site with thousands of scheduled or
    perpetually-drafted records, a very wide date range (e.g. a full year in one view) or a
    heavily-used "Drafts with no dates" sidebar could mean a large response. This isn't a bug, just
    something worth knowing if this page ever feels slow to load on a big site.</p>
</div>

<h5>For Azure</h5>
<div class="callout radius">
  <p>This page reads from the same Postgres tables (<code>web_pages</code>,
    <code>blog_posts</code>, <code>calendar_events</code>) as the rest of the app, through the
    same connection pool -- there's nothing here that needs separate provisioning on Azure
    Database for PostgreSQL. Unlike Cache Management or the Job Queue, this page keeps no
    per-instance state and reads nothing from memory that could differ across Azure App Service
    instances -- every request is a fresh, consistent query.</p>
</div>
<script nonce="${cspNonce}">
  (function () {
    'use strict';

    var typeFilterEl = document.getElementById('editorialCalendarTypeFilter');
    var authorFilterEl = document.getElementById('editorialCalendarAuthorFilter');
    var statusFilterEl = document.getElementById('editorialCalendarStatusFilter');
    var clearFiltersEl = document.getElementById('editorialCalendarClearFilters');
    var undatedListEl = document.getElementById('editorialCalendarUndatedList');

    <%-- Issue #426 Bug B: see EditorialCalendarWidget.java's canEditPagesAndPosts comment. A pure
         community-manager (no admin/content-manager) can see every Page/Post entry here but has
         no edit access to /admin/web-page or /blog-editor -- both 404 for that role -- so those
         entries must not render as clickable/keyboard-activatable links for that viewer. Event
         entries are unaffected: /admin/calendar-event already includes community-manager. --%>
    var CAN_EDIT_PAGES_AND_POSTS = ${canEditPagesAndPosts};

    function isInteractive(type) {
      return CAN_EDIT_PAGES_AND_POSTS || String(type).toLowerCase() === 'event';
    }

    var TYPE_ICONS = {
      page: 'fa-sticky-note',
      post: 'fa-quote-right',
      event: 'fa-calendar'
    };

    <%-- Foundation's label color classes (success/warning/secondary/alert/primary) -- the same
         ones web-page-list.jsp/content-list.jsp use for this same live/draft/scheduled/
         pending-review vocabulary. Expiring gets the "alert" color (used nowhere else on this
         calendar) plus its own CSS rule below, so a piece of content nearing its expiration date
         reads as visually distinct even inside a packed month cell. Pending Review reuses
         "primary" -- unused elsewhere on this calendar -- so it never reads as the same color as
         Draft, which is exactly the ordering bug this status now fixes (see
         EditorialCalendarAjax.pageStatus()/postStatus()). --%>
    var STATUS_LABEL_CLASS = {
      Scheduled: 'secondary',
      'Pending Review': 'primary',
      Draft: 'warning',
      Published: 'success',
      Expiring: 'alert'
    };
    var STATUS_ICONS = {
      Scheduled: 'fa-clock',
      'Pending Review': 'fa-hourglass-half',
      Expiring: 'fa-hourglass-end'
    };

    <%-- A status like "Pending Review" contains a space, which is not a single valid CSS class
         token -- classList.add() throws on a string containing whitespace, and even where it
         doesn't throw, a single array entry with an embedded space risks being read as two
         separate tokens. Collapsing whitespace to a hyphen keeps this a single well-formed class
         for every status, present and future. --%>
    function statusClassSuffix(status) {
      return String(status).toLowerCase().replace(/\s+/g, '-');
    }

    function buildEventUrl(fetchInfo) {
      var params = new URLSearchParams();
      params.set('start', fetchInfo.startStr);
      params.set('end', fetchInfo.endStr);
      if (typeFilterEl.value) {
        params.set('type', typeFilterEl.value);
      }
      if (authorFilterEl.value) {
        params.set('authorId', authorFilterEl.value);
      }
      if (statusFilterEl.value) {
        params.set('status', statusFilterEl.value);
      }
      return '${ctx}/json/editorialCalendar?' + params.toString();
    }

    <%-- issue #996: the "Drafts with no dates" sidebar's feed -- same endpoint/filters as
         buildEventUrl above, minus start/end (this feed has no date range) plus undated=true,
         which short-circuits EditorialCalendarAjax into the undated-only code path. --%>
    function buildUndatedUrl() {
      var params = new URLSearchParams();
      params.set('undated', 'true');
      if (typeFilterEl.value) {
        params.set('type', typeFilterEl.value);
      }
      if (authorFilterEl.value) {
        params.set('authorId', authorFilterEl.value);
      }
      if (statusFilterEl.value) {
        params.set('status', statusFilterEl.value);
      }
      return '${ctx}/json/editorialCalendar?' + params.toString();
    }

    function mapEntryToEvent(entry) {
      var interactive = isInteractive(entry.type);
      var classNames = ['ec-status-' + statusClassSuffix(entry.status)];
      if (!interactive) {
        classNames.push('ec-noninteractive');
      }
      return {
        id: entry.id,
        title: entry.title,
        start: entry.date,
        allDay: true,
        extendedProps: {
          type: entry.type,
          status: entry.status,
          editUrl: entry.editUrl,
          interactive: interactive
        },
        classNames: classNames
      };
    }

    <%-- Builds the icon + title + status-badge content shared by a calendar-grid entry
         (renderEventContent below) and a "Drafts with no dates" list entry (renderUndatedEntry
         below) -- same markup, same TYPE_ICONS/STATUS_LABEL_CLASS/STATUS_ICONS lookups, so the
         undated list reads as visually consistent with the calendar it sits beside. --%>
    function buildEntryContent(type, title, status) {
      var wrapper = document.createElement('div');
      wrapper.className = 'ec-event';

      var typeIcon = document.createElement('i');
      typeIcon.className = 'fa fa-fw ' + (TYPE_ICONS[String(type).toLowerCase()] || 'fa-file');
      typeIcon.setAttribute('aria-hidden', 'true');
      wrapper.appendChild(typeIcon);

      var srType = document.createElement('span');
      srType.className = 'show-for-sr';
      srType.textContent = type + ': ';
      wrapper.appendChild(srType);

      var titleEl = document.createElement('span');
      titleEl.className = 'ec-event-title';
      titleEl.textContent = title;
      wrapper.appendChild(titleEl);

      var statusEl = document.createElement('span');
      statusEl.className = 'label ec-status-badge ' + (STATUS_LABEL_CLASS[status] || 'secondary');
      var statusIconClass = STATUS_ICONS[status];
      if (statusIconClass) {
        var statusIcon = document.createElement('i');
        statusIcon.className = 'fa ' + statusIconClass;
        statusIcon.setAttribute('aria-hidden', 'true');
        statusEl.appendChild(statusIcon);
        statusEl.appendChild(document.createTextNode(' '));
      }
      statusEl.appendChild(document.createTextNode(status));
      wrapper.appendChild(statusEl);

      return wrapper;
    }

    function renderEventContent(arg) {
      var props = arg.event.extendedProps;
      return {domNodes: [buildEntryContent(props.type, arg.event.title, props.status)]};
    }

    <%-- issue #996: one "Drafts with no dates" list item -- a plain <a href> (natively focusable
         and keyboard-operable, unlike the calendar grid's custom-rendered entries, which needed
         the manual tabindex/keydown handling below) wrapping the same icon/title/badge content a
         calendar entry renders.
         Issue #426 Bug B: when the current viewer has no edit access to this entry's content type
         (a community-manager-only viewer looking at a Page/Post -- see isInteractive above), this
         renders as a plain, non-focusable wrapper instead of an <a href> -- still showing the
         title/status for awareness (the calendar's content is the same for every role; only the
         available actions differ), but with no implied "you can open this" affordance and no
         keyboard stop. --%>
    function renderUndatedEntry(entry) {
      var li = document.createElement('li');
      li.className = 'ec-undated-item';

      var interactive = isInteractive(entry.type);
      var wrapper = document.createElement(interactive ? 'a' : 'div');
      wrapper.className = interactive ? 'ec-undated-link' : 'ec-undated-static';
      if (interactive) {
        wrapper.href = (entry.editUrl && entry.editUrl.indexOf('/') === 0) ? ('${ctx}' + entry.editUrl) : '#';
      }
      wrapper.appendChild(buildEntryContent(entry.type, entry.title, entry.status));

      li.appendChild(wrapper);
      return li;
    }

    function renderUndatedList(entries) {
      undatedListEl.innerHTML = '';
      if (!entries || entries.length === 0) {
        var emptyEl = document.createElement('li');
        emptyEl.className = 'ec-undated-empty';
        emptyEl.textContent = 'Nothing here';
        undatedListEl.appendChild(emptyEl);
        return;
      }
      entries.forEach(function (entry) {
        undatedListEl.appendChild(renderUndatedEntry(entry));
      });
    }

    function refreshUndatedList() {
      fetch(buildUndatedUrl(), {credentials: 'same-origin'})
          .then(function (response) {
            if (!response.ok) {
              throw new Error('Request failed: ' + response.status);
            }
            return response.json();
          })
          .then(renderUndatedList)
          .catch(function () {
            undatedListEl.innerHTML = '';
            var errorEl = document.createElement('li');
            errorEl.className = 'ec-undated-empty';
            errorEl.textContent = 'Could not load drafts';
            undatedListEl.appendChild(errorEl);
          });
    }

    // Opens the edit form for a calendar entry -- shared by mouse click (eventClick) and keyboard
    // activation (Enter/Space on a focused entry, see eventDidMount below).
    //
    // Issue #426 Bug B: this is the real gate, not just eventDidMount's tabindex/keydown omission
    // below -- FullCalendar's own eventClick fires for every rendered entry regardless of what
    // eventDidMount did or didn't add to it, so a non-interactive entry (a community-manager-only
    // viewer looking at a Page/Post -- see isInteractive above) must also be refused here, or a
    // mouse click would still navigate to an edit form this viewer has no access to (a 404 at
    // /admin/web-page or /blog-editor).
    function activateEvent(event) {
      if (!event.extendedProps.interactive) {
        return;
      }
      var editUrl = event.extendedProps.editUrl;
      if (editUrl && editUrl.indexOf('/') === 0) {
        window.location.href = '${ctx}' + editUrl;
      }
    }

    // Makes each rendered entry keyboard-focusable and operable (issue #426 keyboard-access
    // follow-up): a plain Tab reaches it in DOM order, and Enter/Space opens its edit form the
    // same way a click does. aria-label carries the type/title/status that renderEventContent
    // otherwise only conveys visually plus a show-for-sr type prefix.
    //
    // Issue #426 Bug B: a non-interactive entry (see activateEvent above) gets none of this -- no
    // tabindex, no role="button", no keydown handler -- since there is nothing for it to do when
    // activated. It stays a plain, inert node showing its title/status for awareness, matching the
    // issue's own design that every role sees the same content, just not necessarily the same
    // actions.
    function eventDidMount(arg) {
      var el = arg.el;
      var props = arg.event.extendedProps;
      if (!props.interactive) {
        return;
      }
      el.setAttribute('tabindex', '0');
      el.setAttribute('role', 'button');
      el.setAttribute('aria-label', props.type + ': ' + arg.event.title + ' (' + props.status + ')');
      el.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ' ' || e.key === 'Spacebar') {
          e.preventDefault();
          activateEvent(arg.event);
        }
      });
    }

    // --- Day-cell keyboard navigation (issue #426) ---
    // A roving-tabindex date grid (the same pattern used by ARIA APG's date-picker grid): exactly
    // one rendered day cell is Tab-reachable at a time (tabindex=0); ArrowLeft/Right/Up/Down move
    // that single stop to an adjacent *rendered* date and move focus there, instead of paging the
    // whole month/week the way the toolbar's prev/next buttons do. FullCalendar stamps every
    // day-grid cell with data-date="yyyy-mm-dd" already, including the all-day row that every
    // entry on this calendar renders into (mapEntryToEvent always sets allDay:true), so this same
    // logic covers both the month and week views without extra view-specific handling.
    var focusedDateISO = null;

    function pad2(n) {
      return n < 10 ? '0' + n : String(n);
    }

    function toISODate(date) {
      return date.getFullYear() + '-' + pad2(date.getMonth() + 1) + '-' + pad2(date.getDate());
    }

    function dayCell(calendarEl, iso) {
      return calendarEl.querySelector('.fc-daygrid-day[data-date="' + iso + '"]');
    }

    function applyRovingTabindex(calendarEl) {
      var cells = calendarEl.querySelectorAll('.fc-daygrid-day[data-date]');
      if (cells.length === 0) {
        return;
      }
      var isoList = [];
      cells.forEach(function (cell) {
        isoList.push(cell.getAttribute('data-date'));
      });
      if (!focusedDateISO || isoList.indexOf(focusedDateISO) === -1) {
        var todayISO = toISODate(new Date());
        focusedDateISO = isoList.indexOf(todayISO) !== -1 ? todayISO : isoList.sort()[0];
      }
      cells.forEach(function (cell) {
        var iso = cell.getAttribute('data-date');
        cell.setAttribute('tabindex', iso === focusedDateISO ? '0' : '-1');
        if (!cell.hasAttribute('aria-label')) {
          cell.setAttribute('aria-label', new Date(iso + 'T00:00:00').toDateString());
        }
      });
    }

    // Shared by moveDayFocus (keyboard) and the click handler below (issue #994): moves the
    // single roving-tabindex stop to targetISO and hands DOM focus to it, if that date is
    // actually part of the currently rendered grid.
    function focusDayCell(calendarEl, targetISO) {
      var targetCell = dayCell(calendarEl, targetISO);
      if (!targetCell) {
        // Off the edge of the currently rendered grid -- stop rather than silently paging to a
        // different month/week out from under the user.
        return;
      }
      var previousCell = focusedDateISO ? dayCell(calendarEl, focusedDateISO) : null;
      if (previousCell && previousCell !== targetCell) {
        previousCell.setAttribute('tabindex', '-1');
      }
      focusedDateISO = targetISO;
      targetCell.setAttribute('tabindex', '0');
      targetCell.focus();
    }

    function moveDayFocus(calendarEl, deltaDays) {
      if (!focusedDateISO) {
        return;
      }
      var current = new Date(focusedDateISO + 'T00:00:00');
      current.setDate(current.getDate() + deltaDays);
      focusDayCell(calendarEl, toISODate(current));
    }

    document.addEventListener('DOMContentLoaded', function () {
      var calendarEl = document.getElementById('editorialCalendar');
      var calendar = new FullCalendar.Calendar(calendarEl, {
        initialView: 'dayGridMonth',
        aspectRatio: 1.8,
        dayMaxEvents: true,
        headerToolbar: {
          start: 'title',
          center: '',
          end: 'dayGridMonth,timeGridWeek today prev,next'
        },
        buttonText: {
          today: 'Today',
          month: 'Month',
          week: 'Week'
        },
        eventContent: renderEventContent,
        eventDidMount: eventDidMount,
        eventClick: function (info) {
          activateEvent(info.event);
        },
        datesSet: function () {
          applyRovingTabindex(calendarEl);
        },
        events: function (fetchInfo, successCallback, failureCallback) {
          fetch(buildEventUrl(fetchInfo), {credentials: 'same-origin'})
              .then(function (response) {
                if (!response.ok) {
                  throw new Error('Request failed: ' + response.status);
                }
                return response.json();
              })
              .then(function (entries) {
                successCallback(entries.map(mapEntryToEvent));
              })
              .catch(function (err) {
                failureCallback(err);
              });
        }
      });
      calendar.render();

      <%-- issue #996: fetch the undated list once up front, and again on the same filter
           change/clear events that already refetch the calendar grid, so both stay in sync. --%>
      refreshUndatedList();

      [typeFilterEl, authorFilterEl, statusFilterEl].forEach(function (el) {
        el.addEventListener('change', function () {
          calendar.refetchEvents();
          refreshUndatedList();
        });
      });
      clearFiltersEl.addEventListener('click', function () {
        typeFilterEl.value = '';
        authorFilterEl.value = '';
        statusFilterEl.value = '';
        calendar.refetchEvents();
        refreshUndatedList();
      });

      <%-- Custom keyboard navigation (issue #426): FullCalendar has no built-in per-date keyboard
           navigation (the vendored bundle sets no tabindex/role anywhere), so it's wired here by
           hand as a roving-tabindex date grid -- see applyRovingTabindex/moveDayFocus above.
           Scoped to calendarEl (not document), so it only fires once focus is actually inside the
           calendar grid; the filter dropdowns above are siblings of calendarEl, not descendants,
           so their own arrow-key behavior (moving between <select> options) is unaffected. --%>
      calendarEl.addEventListener('keydown', function (e) {
        if (e.altKey || e.ctrlKey || e.metaKey || e.shiftKey) {
          return;
        }
        // Only steer between dates when a date cell itself is focused -- not when the keydown
        // bubbled up from a focused event inside it, which has its own Enter/Space handling above
        // and shouldn't have its focus stolen out from under an arrow key press.
        if (!e.target || !e.target.matches || !e.target.matches('.fc-daygrid-day[data-date]')) {
          return;
        }
        switch (e.key) {
          case 'ArrowLeft':
            e.preventDefault();
            moveDayFocus(calendarEl, -1);
            break;
          case 'ArrowRight':
            e.preventDefault();
            moveDayFocus(calendarEl, 1);
            break;
          case 'ArrowUp':
            e.preventDefault();
            moveDayFocus(calendarEl, -7);
            break;
          case 'ArrowDown':
            e.preventDefault();
            moveDayFocus(calendarEl, 7);
            break;
          default:
            break;
        }
      });

      <%-- Day-cell click-to-focus (issue #994): the FullCalendar.Calendar init above has no
           dateClick/select callback, and FullCalendar's own mousedown handling on day-grid cells
           suppresses the browser's normal click-to-focus behavior -- so clicking a day cell never
           updated focusedDateISO, never flipped its tabindex to 0, and never received DOM focus,
           leaving the arrow keys above with nothing to move from until the user tabbed to a cell
           instead. Delegated the same way as the keydown listener (a plain listener on calendarEl
           matched against the day-cell selector) rather than FullCalendar's own click callbacks,
           to stay consistent with this file's hand-rolled roving-tabindex approach. closest() is
           used (not e.target.matches, unlike the keydown check above) so a click that lands on an
           event chip inside a cell -- which eventClick/activateEvent already separately opens --
           still resolves to its containing day cell and moves day-cell focus there too; nothing
           here calls preventDefault or stops propagation, so eventClick keeps firing normally for
           chip clicks. --%>
      calendarEl.addEventListener('click', function (e) {
        var cell = e.target && e.target.closest && e.target.closest('.fc-daygrid-day[data-date]');
        if (!cell) {
          return;
        }
        focusDayCell(calendarEl, cell.getAttribute('data-date'));
      });
    });
  })();
</script>
