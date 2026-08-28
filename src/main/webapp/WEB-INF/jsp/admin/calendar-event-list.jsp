<%--
  ~ Copyright 2022 SimIS Inc.
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
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="calendarEventList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="calendarList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<p class="help-text">
  <i class="fa fa-info-circle"></i> Every event here belongs to exactly one calendar, and there's
  no general "add event" button on this list -- open the calendar it belongs to (from the Calendars
  table above) and use that calendar's own "Add Event" button. <strong>No recurring events:</strong>
  each row is a single independent event; there's no way to schedule something like "every Monday"
  automatically. Dates below are shown in the site's configured timezone (Site Properties &gt;
  <code>site.timezone</code>).
</p>
<%-- Filters (GET so the criteria live in the URL and paging preserves them) --%>
<form method="get" autocomplete="off" class="margin-bottom-10">
  <div class="grid-x grid-margin-x">
    <div class="cell medium-3">
      <label>Search
        <input type="text" name="q" placeholder="event title" value="<c:out value='${q}'/>">
      </label>
    </div>
    <div class="cell medium-3">
      <label>Calendar
        <select name="calendarId">
          <option value="">All</option>
          <c:forEach items="${calendarList}" var="calendar">
            <option value="${calendar.id}" <c:if test="${calendarId == calendar.id}">selected</c:if>><c:out value="${calendar.name}" /></option>
          </c:forEach>
        </select>
      </label>
    </div>
    <div class="cell medium-2">
      <label>Status
        <select name="status">
          <option value="">All</option>
          <option value="published" <c:if test="${status == 'published'}">selected</c:if>>Published</option>
          <option value="draft" <c:if test="${status == 'draft'}">selected</c:if>>Draft</option>
          <%-- Archived events are excluded from every other option above by default (issue #882);
               this is the only way to see them in the admin list. --%>
          <option value="archived" <c:if test="${status == 'archived'}">selected</c:if>>Archived</option>
        </select>
      </label>
    </div>
    <div class="cell medium-2">
      <label>From
        <input type="date" name="fromDate" value="<c:out value='${fromDate}'/>">
      </label>
    </div>
    <div class="cell medium-2">
      <label>To
        <input type="date" name="toDate" value="<c:out value='${toDate}'/>">
      </label>
    </div>
  </div>
  <button type="submit" class="button small primary radius"><i class="fa fa-filter"></i> Filter</button>
  <a href="${widgetContext.uri}" class="button small secondary radius">Clear</a>
</form>
<div id="bulkActionsBar" class="callout radius" style="display:none;padding:10px 15px;margin-bottom:10px;">
  <span id="bulkSelectedCount"></span>
  <button type="button" class="button tiny radius" id="bulkArchiveBtn">Archive</button>
  <button type="button" class="button tiny radius" id="bulkMoveBtn">Move</button>
  <button type="button" class="button tiny alert radius" id="bulkDeleteBtn">Delete</button>
</div>
<table class="unstriped">
  <thead>
    <tr>
      <th width="24"><input type="checkbox" id="selectAllEvents" aria-label="Select all events on this page"></th>
      <th>Title</th>
      <th width="160" class="text-center">Date</th>
      <th width="160">Calendar</th>
      <th width="100" class="text-center">Status</th>
      <th width="80" class="text-center">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${calendarEventList}" var="event">
      <c:set var="eventCalendar" value="${null}" />
      <c:forEach items="${calendarList}" var="calendar">
        <c:if test="${calendar.id == event.calendarId}"><c:set var="eventCalendar" value="${calendar}" /></c:if>
      </c:forEach>
      <tr>
        <td><input type="checkbox" class="eventRowCheckbox" value="${event.id}" data-title="${fn:escapeXml(event.title)}" aria-label="Select ${fn:escapeXml(event.title)}"></td>
        <td>
          <a href="${ctx}/admin/calendar-event?calendarEventId=${event.id}&returnPage=/admin/calendars"><c:out value="${event.title}" /></a>
        </td>
        <td class="text-center"><fmt:formatDate pattern="yyyy-MM-dd" value="${event.startDate}" /></td>
        <td>
          <c:if test="${!empty eventCalendar}">
            <c:if test="${!empty eventCalendar.color}"><small style="padding-right: 10px;border:1px solid #000;background-color:<c:out value="${eventCalendar.color}" />">&nbsp;</small></c:if>
            <c:out value="${eventCalendar.name}" />
          </c:if>
        </td>
        <td class="text-center">
          <c:choose>
            <c:when test="${!empty event.archived}">
              <span class="label secondary radius">Archived</span>
            </c:when>
            <c:when test="${!empty event.published}">
              <span class="label success radius">Published</span>
            </c:when>
            <c:otherwise>
              <span class="label radius">Draft</span>
            </c:otherwise>
          </c:choose>
        </td>
        <td class="text-center">
          <a href="${ctx}/admin/calendar-event?calendarEventId=${event.id}&returnPage=/admin/calendars"><i class="fa fa-edit"></i></a>
        </td>
      </tr>
    </c:forEach>
    <c:if test="${empty calendarEventList}">
      <tr>
        <td colspan="6">No events match the current filters</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%@include file="../paging_control.jspf" %>
<%-- Bulk action reveal modals -- selection is scoped to the events currently checked on this page
     (see the JS below); each is populated at open time with the live selection, not just a count.
     Mirrors users-list.jsp's bulk reveal modals (issue #882/PR #731 pattern) and image-browser.jsp's
     bulkDelete convention. --%>
<div class="reveal" id="bulkArchiveReveal" role="dialog" aria-modal="true" aria-labelledby="bulkArchiveRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkArchiveRevealTitle">Archive <span id="bulkArchiveCount">0</span> Event(s)</h4>
  <p class="help-text">Archived events are removed from the public calendar and hidden from this list by default. They can still be found with the Archived status filter.</p>
  <ul id="bulkArchiveList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkArchive"/>
    <input type="submit" class="button radius" value="Archive Events"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="bulkMoveReveal" role="dialog" aria-modal="true" aria-labelledby="bulkMoveRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkMoveRevealTitle">Move <span id="bulkMoveCount">0</span> Event(s)</h4>
  <ul id="bulkMoveList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkMove"/>
    <label for="bulkMoveCalendarId">Destination calendar <span class="required">*</span>
      <select id="bulkMoveCalendarId" name="calendarId" required>
        <c:forEach items="${calendarList}" var="calendar">
          <option value="${calendar.id}"><c:out value="${calendar.name}" /></option>
        </c:forEach>
      </select>
    </label>
    <input type="submit" class="button radius" value="Move Events"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="bulkDeleteReveal" role="dialog" aria-modal="true" aria-labelledby="bulkDeleteRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkDeleteRevealTitle">Delete <span id="bulkDeleteCount">0</span> Event(s)</h4>
  <p class="help-text">This permanently removes the selected events. This cannot be undone.</p>
  <ul id="bulkDeleteList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkDelete"/>
    <input type="submit" class="button alert radius" value="Delete Events"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<script nonce="${cspNonce}">
  (function () {
    var $selectAll = $('#selectAllEvents');
    var $rows = $('.eventRowCheckbox');
    var $bar = $('#bulkActionsBar');
    var $count = $('#bulkSelectedCount');

    function selected() {
      return $rows.filter(':checked');
    }

    function refresh() {
      var n = selected().length;
      $count.text(n + (n === 1 ? ' event selected  ' : ' events selected  '));
      $bar.toggle(n > 0);
      $selectAll.prop('indeterminate', n > 0 && n < $rows.length);
      $selectAll.prop('checked', n > 0 && n === $rows.length);
    }

    // Populates one bulk modal's hidden eventId fields and visible title list from the currently
    // checked rows, so the admin sees exactly what is about to be affected before confirming.
    function populateBulkModal(revealId, listId) {
      var $reveal = $('#' + revealId);
      var $form = $reveal.find('form');
      var $list = $('#' + listId);
      $form.find('input[name="eventId"]').remove();
      $list.empty();
      selected().each(function () {
        var $checkbox = $(this);
        $form.append($('<input type="hidden" name="eventId">').val($checkbox.val()));
        $list.append($('<li>').text($checkbox.data('title')));
      });
      $('#' + revealId + 'Count').text(selected().length);
      $reveal.foundation('open');
    }

    $selectAll.on('change', function () {
      $rows.prop('checked', this.checked);
      refresh();
    });
    $rows.on('change', refresh);

    $('#bulkArchiveBtn').on('click', function () { populateBulkModal('bulkArchiveReveal', 'bulkArchiveList'); });
    $('#bulkMoveBtn').on('click', function () { populateBulkModal('bulkMoveReveal', 'bulkMoveList'); });
    $('#bulkDeleteBtn').on('click', function () { populateBulkModal('bulkDeleteReveal', 'bulkDeleteList'); });

    refresh();
  })();
</script>
