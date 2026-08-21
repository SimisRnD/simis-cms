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
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/tlds/group-functions.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="calendarList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="calendarEventCount" class="java.util.HashMap" scope="request"/>
<c:if test="${userSession.hasRole('admin')}">
<script nonce="${cspNonce}">
  function deleteCalendar(calendarId) {
    if (!confirm("Are you sure you want to delete this calendar and all of its events?")) {
      return;
    }
    postAction('${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&id=' + calendarId);
  }
</script>
</c:if>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<a class="button small radius primary" href="${ctx}/admin/calendar?returnPage=/admin/calendars">Add a Calendar <i class="fa fa-arrow-circle-right"></i></a>
<%@include file="../page_messages.jspf" %>
<p>
  A <strong>Calendar</strong> is a named, colored container for events -- purely an internal
  organizing concept, the same way a Blog groups posts. There's no public page listing every
  calendar; instead, a page author wires one specific calendar into a page by its
  <strong>Unique Id</strong> (below), set as the <code>calendarUniqueId</code> preference on a
  <code>calendar</code>, <code>upcomingCalendarEvents</code>, or <code>calendarSearchResults</code>
  widget.
</p>
<p class="help-text">
  <i class="fa fa-info-circle"></i> <strong>No recurring events.</strong> Every event is a single,
  independent record -- there's no built-in way to schedule something like "every Monday"
  automatically on any calendar. If you need that, you'll be creating and maintaining each
  occurrence by hand.
</p>
<h5>Two ways to view and edit events</h5>
<p>
  This page shows a compact list-and-small-calendar view. A calendar can also be placed on a live
  page using the site's full interactive calendar widget, which adds a click-to-create/edit modal
  directly on the calendar grid. Both are safe to edit from interchangeably -- editing an event here
  or through that modal now saves the same complete set of fields either way.
</p>
<h5>Troubleshooting</h5>
<ul>
  <li><strong>Events aren't showing up on a public page.</strong> The most common cause is a
    mismatched <code>calendarUniqueId</code> -- the value on the page's widget preference must
    exactly match a calendar's Unique Id below, character for character.</li>
  <li><strong># of events shows 0.</strong> Normal for a freshly-created calendar, or one whose
    events were all deleted or moved elsewhere -- not an error.</li>
</ul>
<h5>Timezone</h5>
<p>
  Event dates and times display in the site's configured timezone (<strong>Site Properties &gt;
  site.timezone</strong>), not your browser's or the server's local time.
</p>
<h5>As this grows</h5>
<p>
  Calendars themselves are typically few -- one per audience or purpose (events, holidays, etc.) --
  so this table rarely grows large. Individual <strong>events</strong> are where volume shows up;
  see the Events list below for search, filtering, pagination, and archiving as an individual
  calendar's event count grows. Calendar and event data lives in the same PostgreSQL database as the
  rest of the site, so deploying to a new environment needs no special provisioning beyond normal
  database backup/restore. The interactive calendar UI (FullCalendar) is a vendored JavaScript
  library bundled with the site, not a separate runtime dependency.
</p>
<table class="unstriped">
  <thead>
    <tr>
      <th width="75%">Name</th>
      <th width="25%">Unique Id</th>
      <th width="100" class="text-center"># of events</th>
      <th width="100" class="text-center">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${calendarList}" var="calendar">
      <tr>
        <td>
          <c:if test="${!empty calendar.color}"><small style="padding-right: 10px;border:1px solid #000;background-color:<c:out value="${calendar.color}" />">&nbsp;</small></c:if>
          <c:out value="${calendar.name}" />
          <c:if test="${!calendar.enabled}"><span class="label warning">offline</span></c:if>
          <c:if test="${!empty calendar.description}">
            <br /><small class="subheader"><c:out value="${calendar.description}" /></small>
          </c:if>
        </td>
        <td>
          <small><c:out value="${calendar.uniqueId}" /></small>
        </td>
        <td class="text-center">
          <%-- countGroupedByCalendarId() omits a calendar entirely when it has zero events (rather
               than returning an explicit 0), so a missing entry must default to 0 here. --%>
          <fmt:formatNumber value="${empty calendarEventCount[calendar.id] ? 0 : calendarEventCount[calendar.id]}" />
        </td>
        <td class="text-center">
          <a href="${ctx}/admin/calendar?calendarId=${calendar.id}&returnPage=/admin/calendars"><i class="${font:fas()} fa-edit"></i></a>
          <c:if test="${userSession.hasRole('admin')}">
            <a href="#" data-js-call="deleteCalendar" data-js-arg1="${calendar.id}"><i class="fa fa-remove"></i></a>
          </c:if>
        </td>
      </tr>
    </c:forEach>
    <c:if test="${empty calendarList}">
      <tr>
        <td colspan="4">No calendars were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
