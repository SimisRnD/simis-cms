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
<%@ page import="static com.simisinc.platform.ApplicationInfo.VERSION" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="calendar" class="com.simisinc.platform.domain.model.cms.Calendar" scope="request"/>
<jsp:useBean id="calendarEvent" class="com.simisinc.platform.domain.model.cms.CalendarEvent" scope="request"/>
<%@include file="../page_messages.jspf" %>
<script src="${ctx}/javascript/add-to-calendar-0.1.0/add-to-calendar.js?v=<%= VERSION %>"></script>
<link rel="stylesheet" id="add-to-calendar-css" href="${ctx}/javascript/add-to-calendar-0.1.0/add-to-calendar.css?v=<%= VERSION %>" />
<div class="platform-calendar-details-container">
<c:if test="${!empty title}">
  <div class="platform-calendar-title text-center">
    <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
  </div>
</c:if>
  <%-- Date Formatting --%>
  <c:set var="startDateTime" scope="request">${date:format(calendarEvent.startDate, "MMMM d, yyyy")}</c:set>
  <c:set var="endDateTime" scope="request">${date:format(calendarEvent.endDate, "MMMM d, yyyy")}</c:set>
  <c:set var="startDate" scope="request">${date:format(calendarEvent.startDate, "MMMM d")}</c:set>
  <c:set var="endDate" scope="request">${date:format(calendarEvent.endDate, "MMMM d")}</c:set>
  <c:set var="startYear" scope="request">${date:format(calendarEvent.startDate, "yyyy")}</c:set>
  <c:set var="endYear" scope="request">${date:format(calendarEvent.endDate, "yyyy")}</c:set>
  <c:set var="startTime" scope="request">${date:format(calendarEvent.startDate, "h:mm a")}</c:set>
  <c:set var="endTime" scope="request">${date:format(calendarEvent.endDate, "h:mm a")}</c:set>
  <c:set var="thisMonth" scope="request">${date:format(calendarEvent.startDate, "MMMM yyyy")}</c:set>
  <c:set var="thisDay" scope="request">${date:format(calendarEvent.startDate, "MMMM d, yyyy")}</c:set>
  <%-- Show the month header--%>
  <div class="platform-calendar-month text-center"><h2><c:out value="${thisDay}" /></h2></div>
  <%-- Show the day --%>
  <div class="platform-calendar-month-separator">
    <span class="platform-calendar-month-separator-label"><c:out value="${thisDay}" /></span>
  </div>
  <div class="platform-calendar-event-block">
    <h1><c:out value="${calendarEvent.title}" /></h1>
    <c:choose>
      <c:when test="${calendarEvent.allDay}">
        <%-- A single-day all-day event still has a date worth showing; only the range
             is conditional, which the inner test below handles. --%>
        <p class="platform-calendar-event-date">
          <i class="fa fa-calendar-o fa-fw"></i>
          ${date:format(calendarEvent.startDate, "MMMM d, yyyy")}
          <c:if test="${startDateTime ne endDateTime}">
            -
            ${date:format(calendarEvent.endDate, "MMMM d, yyyy")}
          </c:if>
        </p>
      </c:when>
      <c:otherwise>
        <p class="platform-calendar-event-date">
          <c:choose>
            <c:when test="${startDateTime eq endDateTime}">
              <i class="fa fa-clock-o fa-fw"></i>
              ${date:format(calendarEvent.startDate, "h:mm a")}
              <c:if test="${startTime ne endTime}">
                - ${date:format(calendarEvent.endDate, "h:mm a")}
              </c:if>
            </c:when>
            <c:otherwise>
              <i class="fa fa-calendar-o fa-fw"></i>
              ${date:format(calendarEvent.startDate, "MMMM d, h:mm a")}
              -
              <c:choose>
                <c:when test="${startYear ne endYear}">
                  ${date:format(calendarEvent.endDate, "MMMM d, yyyy h:mm a")}
                </c:when>
                <c:otherwise>
                  ${date:format(calendarEvent.endDate, "MMMM d, h:mm a")}
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
    <c:if test="${!empty calendarEvent.tagsList}">
      <div class="cell auto">
        <c:forEach items="${calendarEvent.tagsList}" var="tag">
          <span class="label secondary"><c:out value="${tag}"/></span>
        </c:forEach>
      </div>
    </c:if>
    <div class="add-to-calendar" style="margin-left: 24px">
      <span class="icon">far fa-calendar-plus</span>
      <span class="timezone"><c:out value="${timezone}"/></span>
      <c:choose>
        <c:when test="${calendarEvent.allDay}">
          <span class="allday">true</span>
          <span class="start">${date:format(calendarEvent.startDate, "MM/dd/yyyy")}</span>
          <span class="end">${date:format(calendarEvent.endDate, "MM/dd/yyyy")}</span>
          <span class="outlookStart">${date:format(calendarEvent.startDate, "yyyy-MM-dd")}</span>
          <span class="outlookEnd">${date:format(date:adjustDays(calendarEvent.endDate, 1), "yyyy-MM-dd")}</span>
        </c:when>
        <c:otherwise>
          <span class="start">${date:format(calendarEvent.startDate, "MM/dd/yyyy hh:mm a")}</span>
          <span class="end">${date:format(calendarEvent.endDate, "MM/dd/yyyy hh:mm a")}</span>
          <span class="outlookStart">${date:format(calendarEvent.startDate, "yyyy-MM-dd'T'HH:mm:00XXX")}</span>
          <span class="outlookEnd">${date:format(calendarEvent.endDate, "yyyy-MM-dd'T'HH:mm:00XXX")}</span>
        </c:otherwise>
      </c:choose>
      <span class="title"><c:out value="${calendarEvent.title}" /></span>
      <c:if test="${!empty calendarEvent.summary}">
        <span class="description"><c:out value="${calendarEvent.summary}" /><c:if test="${!empty calendarEvent.detailsUrl}">

<c:out value="${calendarEvent.detailsUrl}" /></c:if><c:if test="${!empty calendarEvent.signUpUrl}">

<c:out value="${calendarEvent.signUpUrl}" /></c:if></span>
      </c:if>
      <c:if test="${!empty calendarEvent.location}">
        <span class="location"><c:out value="${calendarEvent.location}" /></span>
      </c:if>
    </div>
    <c:if test="${!empty calendarEvent.summary}">
      <p class="platform-calendar-event-summary"><c:out value="${calendarEvent.summary}" /></p>
    </c:if>
    <c:if test="${!empty calendarEvent.detailsUrl || !empty calendarEvent.signUpUrl || !empty calendarEvent.videoUrl}">
      <p class="platform-calendar-event-buttons">
        <i class="fa fa-fw"></i>
        <c:if test="${!empty calendarEvent.videoUrl}">
          <c:choose>
            <c:when test="${fn:startsWith(calendarEvent.videoUrl, 'http://') || fn:startsWith(calendarEvent.videoUrl, 'https://')}">
              <a class="button primary" target="_blank" href="<c:out value="${calendarEvent.videoUrl}" />">Join Meeting</a>
            </c:when>
            <c:otherwise>
              <a class="button primary" href="<c:out value="${ctx}${calendarEvent.videoUrl}" />">Join Meeting</a>
            </c:otherwise>
          </c:choose>
        </c:if>
        <c:if test="${!empty calendarEvent.detailsUrl}">
          <c:choose>
            <c:when test="${fn:startsWith(calendarEvent.detailsUrl, 'http://') || fn:startsWith(calendarEvent.detailsUrl, 'https://')}">
              <a class="button primary" target="_blank" href="<c:out value="${calendarEvent.detailsUrl}" />">Learn More</a>
            </c:when>
            <c:otherwise>
              <a class="button primary" href="<c:out value="${ctx}${calendarEvent.detailsUrl}" />">View Details</a>
            </c:otherwise>
          </c:choose>
        </c:if>
        <c:if test="${!empty calendarEvent.signUpUrl}">
          <c:choose>
            <c:when test="${fn:startsWith(calendarEvent.signUpUrl, 'http://') || fn:startsWith(calendarEvent.signUpUrl, 'https://')}">
              <a class="button primary" target="_blank" href="<c:out value="${calendarEvent.signUpUrl}" />">Sign Up Page</a>
            </c:when>
            <c:otherwise>
              <a class="button primary" href="<c:out value="${ctx}${calendarEvent.signUpUrl}" />">Sign Up Page</a>
            </c:otherwise>
          </c:choose>
        </c:if>
      </p>
    </c:if>
    <c:choose>
      <c:when test="${!empty returnPage}">
        <p class="platform-calendar-event-return">
          <i class="fa fa-fw"></i> <a href="#" data-js-call="goBack" data-js-arg1="<c:out value="${returnPage}"/>"><i class="${font:fal()} fa-arrow-left"></i> Return to previous page</a>
        </p>
      </c:when>
      <c:otherwise>
        <p class="platform-calendar-event-return">
          <i class="fa fa-fw"></i> <a href="${ctx}/calendar"><i class="${font:fal()} fa-arrow-left"></i> View the calendar</a>
        </p>
      </c:otherwise>
    </c:choose>
  </div>
</div>
<script nonce="${cspNonce}">
  function goBack() {
    window.history.back();
  }
</script>
