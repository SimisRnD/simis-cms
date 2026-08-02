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
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
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
<table class="unstriped">
  <thead>
    <tr>
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
        <td colspan="5">No events match the current filters</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%@include file="../paging_control.jspf" %>
