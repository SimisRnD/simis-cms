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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="calendarEventList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="showMonthName" class="java.lang.String" scope="request"/>
<jsp:useBean id="activeFilters" class="java.util.ArrayList" scope="request"/>
<%@include file="../page_messages.jspf" %>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<c:if test="${!empty activeFilters}">
  <div class="margin-bottom-10">
    <c:forEach items="${activeFilters}" var="activeFilter">
      <span class="label secondary" style="margin-right:5px">
        <c:out value="${activeFilter.facetLabel}"/>: <c:out value="${activeFilter.valueLabel}"/>
        <%-- clearUrl is server-built from the request path + UrlCommand.encodeUri()'d params, so it cannot carry HTML metacharacters --%>
        <a href="${activeFilter.clearUrl}" style="color:inherit" title="Remove this filter"><i class="fa fa-times"></i></a>
      </span>
    </c:forEach>
  </div>
</c:if>
<div class="grid-x grid-margin-x">
  <c:if test="${!empty calendarFacets}">
    <div class="cell medium-3">
      <h6><c:out value="${calendarFacetLabel}"/></h6>
      <ul class="no-bullet" style="text-indent: -11px; margin-left: 21px !important;">
        <c:forEach items="${calendarFacets}" var="facet">
          <li>
            <%-- facet.url is server-built from the request path + UrlCommand.encodeUri()'d params, so it cannot carry HTML metacharacters --%>
            <a href="${facet.url}">
              <c:choose>
                <c:when test="${facet.selected}"><i class="fa fa-circle-check"></i></c:when>
                <c:otherwise><i class="fa fa-circle-o"></i></c:otherwise>
              </c:choose>
              <c:out value="${facet.label}"/>
            </a>&nbsp;<small class="subheader"><fmt:formatNumber value="${facet.count}"/></small>
          </li>
        </c:forEach>
      </ul>
    </div>
  </c:if>
  <div class="cell ${!empty calendarFacets ? 'medium-9' : 'medium-12'}">
    <c:choose>
      <c:when test="${empty calendarEventList}">
        <c:choose>
          <c:when test="${!empty activeFilters}">
            <p>No calendar events match the current filters.</p>
            <ul class="no-bullet">
              <c:forEach items="${activeFilters}" var="activeFilter">
                <li><a href="${activeFilter.clearUrl}">Remove "<c:out value="${activeFilter.valueLabel}"/>"</a></li>
              </c:forEach>
            </ul>
          </c:when>
          <c:otherwise>
            <p>No calendar events were found</p>
          </c:otherwise>
        </c:choose>
      </c:when>
      <c:otherwise>
    <div class="platform-calendar-list-container">
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
              <h3><a href="${ctx}<c:out value="${calendarLink}"/>"><c:out value="${calendarEvent.title}" /></a></h3>
            </c:when>
            <c:otherwise>
              <h3><a href="${ctx}/calendar-event/${calendarEvent.uniqueId}?returnPage=${widgetContext.uri}"><c:out value="${calendarEvent.title}" /></a></h3>
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
              <%-- A single-day all-day event still has a date worth showing; only the range
                   is conditional, which the inner test below handles. --%>
              <p class="platform-calendar-event-date">
                <i class="fa fa-calendar-o fa-fw"></i>
                <fmt:formatDate pattern="MMMM d, yyyy" value="${calendarEvent.startDate}" />
                <c:if test="${startDateTime ne endDateTime}">
                  -
                  <fmt:formatDate pattern="MMMM d, yyyy" value="${calendarEvent.endDate}" />
                </c:if>
              </p>
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
      </c:otherwise>
    </c:choose>
  </div>
</div>
