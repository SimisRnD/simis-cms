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
<script nonce="${cspNonce}">
  (function () {
    'use strict';

    var typeFilterEl = document.getElementById('editorialCalendarTypeFilter');
    var authorFilterEl = document.getElementById('editorialCalendarAuthorFilter');
    var statusFilterEl = document.getElementById('editorialCalendarStatusFilter');
    var clearFiltersEl = document.getElementById('editorialCalendarClearFilters');
    var undatedListEl = document.getElementById('editorialCalendarUndatedList');

    var TYPE_ICONS = {
      page: 'fa-sticky-note',
      post: 'fa-quote-right',
      event: 'fa-calendar'
    };

    <%-- Foundation's label color classes (success/warning/secondary/alert) -- the same ones
         web-page-list.jsp uses for its live/draft/scheduled/expiring badges. Expiring gets the
         "alert" color (used nowhere else on this calendar) plus its own CSS rule below, so a
         piece of content nearing its expiration date reads as visually distinct even inside a
         packed month cell. --%>
    var STATUS_LABEL_CLASS = {
      Scheduled: 'secondary',
      Draft: 'warning',
      Published: 'success',
      Expiring: 'alert'
    };
    var STATUS_ICONS = {
      Scheduled: 'fa-clock',
      Expiring: 'fa-hourglass-end'
    };

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
      return {
        id: entry.id,
        title: entry.title,
        start: entry.date,
        allDay: true,
        extendedProps: {
          type: entry.type,
          status: entry.status,
          editUrl: entry.editUrl
        },
        classNames: ['ec-status-' + String(entry.status).toLowerCase()]
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
         calendar entry renders. --%>
    function renderUndatedEntry(entry) {
      var li = document.createElement('li');
      li.className = 'ec-undated-item';

      var link = document.createElement('a');
      link.className = 'ec-undated-link';
      link.href = (entry.editUrl && entry.editUrl.indexOf('/') === 0) ? ('${ctx}' + entry.editUrl) : '#';
      link.appendChild(buildEntryContent(entry.type, entry.title, entry.status));

      li.appendChild(link);
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
    function activateEvent(event) {
      var editUrl = event.extendedProps.editUrl;
      if (editUrl && editUrl.indexOf('/') === 0) {
        window.location.href = '${ctx}' + editUrl;
      }
    }

    // Makes each rendered entry keyboard-focusable and operable (issue #426 keyboard-access
    // follow-up): a plain Tab reaches it in DOM order, and Enter/Space opens its edit form the
    // same way a click does. aria-label carries the type/title/status that renderEventContent
    // otherwise only conveys visually plus a show-for-sr type prefix.
    function eventDidMount(arg) {
      var el = arg.el;
      var props = arg.event.extendedProps;
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

    function moveDayFocus(calendarEl, deltaDays) {
      if (!focusedDateISO) {
        return;
      }
      var current = new Date(focusedDateISO + 'T00:00:00');
      current.setDate(current.getDate() + deltaDays);
      var targetISO = toISODate(current);
      var targetCell = dayCell(calendarEl, targetISO);
      if (!targetCell) {
        // Off the edge of the currently rendered grid -- stop rather than silently paging to a
        // different month/week out from under the user.
        return;
      }
      var previousCell = dayCell(calendarEl, focusedDateISO);
      if (previousCell) {
        previousCell.setAttribute('tabindex', '-1');
      }
      focusedDateISO = targetISO;
      targetCell.setAttribute('tabindex', '0');
      targetCell.focus();
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
    });
  })();
</script>
