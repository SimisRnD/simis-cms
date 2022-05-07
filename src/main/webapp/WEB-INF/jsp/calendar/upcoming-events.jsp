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
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="calendarEventList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="showMonthName" class="java.lang.String" scope="request"/>
<%@include file="../page_messages.jspf" %>
<div class="platform-calendar-list-container">
<c:if test="${!empty title}">
  <div class="platform-calendar-title text-center">
    <h3><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h3>
  </div>
</c:if>
<c:if test="${empty calendarEventList}">
  <p>No events were found</p>
</c:if>
<c:set var="lastMonth" scope="request" value="---"/>
<c:set var="lastDay" scope="request" value="---"/>
<c:forEach items="${calendarEventList}" var="calendarEvent">
  <%-- Show the month header--%>
  <c:set var="thisMonth" scope="request"><fmt:formatDate pattern="MMMM yyyy" value="${calendarEvent.startDate}" /></c:set>
  <c:if test="${lastMonth ne thisMonth}">
    <c:set var="lastMonth" scope="request" value="${thisMonth}"/>
    <c:if test="${showMonthName eq 'true'}">
    <div class="platform-calendar-month text-center"><h2><c:out value="${thisMonth}" /></h2></div>
    </c:if>
  </c:if>
  <%-- Show the day --%>
  <c:set var="thisDay" scope="request"><fmt:formatDate pattern="MMMM d, yyyy" value="${calendarEvent.startDate}" /></c:set>
  <c:if test="${lastDay ne thisDay}">
    <c:set var="lastDay" scope="request" value="${thisDay}"/>
    <div class="platform-calendar-month-separator">
      <span class="platform-calendar-month-separator-label">${thisDay}</span>
    </div>
  </c:if>
  <div class="platform-calendar-event-block">
    <c:choose>
      <c:when test="${!empty calendarLink}">
        <h4><a href="${ctx}${calendarLink}"><c:out value="${calendarEvent.title}" /></a></h4>
      </c:when>
      <c:otherwise>
        <h4><a href="${ctx}/calendar-event/${calendarEvent.uniqueId}?returnPage=${widgetContext.uri}"><c:out value="${calendarEvent.title}" /></a></h4>
      </c:otherwise>
    </c:choose>
    <c:set var="startDateTime" scope="request"><fmt:formatDate pattern="MMMM d, yyyy" value="${calendarEvent.startDate}" /></c:set>
    <c:set var="endDateTime" scope="request"><fmt:formatDate pattern="MMMM d, yyyy" value="${calendarEvent.endDate}" /></c:set>
    <c:set var="startDate" scope="request"><fmt:formatDate pattern="MMMM d" value="${calendarEvent.startDate}" /></c:set>
    <c:set var="endDate" scope="request"><fmt:formatDate pattern="MMMM d" value="${calendarEvent.endDate}" /></c:set>
    <c:set var="startYear" scope="request"><fmt:formatDate pattern="yyyy" value="${calendarEvent.startDate}" /></c:set>
    <c:set var="endYear" scope="request"><fmt:formatDate pattern="yyyy" value="${calendarEvent.endDate}" /></c:set>
    <c:set var="startTime" scope="request"><fmt:formatDate pattern="h:mm a" value="${calendarEvent.startDate}" /></c:set>
    <c:set var="endTime" scope="request"><fmt:formatDate pattern="h:mm a" value="${calendarEvent.endDate}" /></c:set>
    <c:choose>
      <c:when test="${calendarEvent.allDay}">
        <c:if test="${startDateTime ne endDateTime}">
          <p class="platform-calendar-event-date">
            <i class="fa fa-calendar-o fa-fw"></i>
            <fmt:formatDate pattern="MMMM d, yyyy" value="${calendarEvent.startDate}" />
            <c:if test="${startDateTime ne endDateTime}">
              -
              <fmt:formatDate pattern="MMMM d, yyyy" value="${calendarEvent.endDate}" />
            </c:if>
          </p>
        </c:if>
      </c:when>
      <c:otherwise>
        <p class="platform-calendar-event-date">
          <c:choose>
            <c:when test="${startDateTime eq endDateTime}">
              <i class="fa fa-clock-o fa-fw"></i>
              <fmt:formatDate pattern="h:mm a" value="${calendarEvent.startDate}" />
              <c:if test="${startTime ne endTime}">
                - <fmt:formatDate pattern="h:mm a" value="${calendarEvent.endDate}" />
              </c:if>
            </c:when>
            <c:otherwise>
              <i class="fa fa-calendar-o fa-fw"></i>
              <fmt:formatDate pattern="MMMM d, h:mm a" value="${calendarEvent.startDate}" />
              -
              <c:choose>
                <c:when test="${startYear ne endYear}">
                  <fmt:formatDate pattern="MMMM d, yyyy h:mm a" value="${calendarEvent.endDate}" />
                </c:when>
                <c:otherwise>
                  <fmt:formatDate pattern="MMMM d, h:mm a" value="${calendarEvent.endDate}" />
                </c:otherwise>
              </c:choose>
            </c:otherwise>
          </c:choose>
        </p>
      </c:otherwise>
    </c:choose>
    <c:if test="${!empty calendarEvent.location}">
      <p class="platform-calendar-event-location"><i class="fa fa-map-marker fa-fw"></i> <c:out value="${calendarEvent.location}" /></p>
    </c:if>
    <c:if test="${!empty calendarEvent.summary}">
      <p class="platform-calendar-event-summary"><i class="fa fa-fw"></i> <c:out value="${calendarEvent.summary}" /></p>
    </c:if>
  </div>
</c:forEach>
</div>