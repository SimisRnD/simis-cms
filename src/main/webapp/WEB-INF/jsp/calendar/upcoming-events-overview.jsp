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
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="html" uri="/WEB-INF/html-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<%@ taglib prefix="date" uri="/WEB-INF/date-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="calendarEventList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="showEventLink" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  <hr />
</c:if>
<c:choose>
  <c:when test="${!empty calendarEventList}">
    <c:forEach items="${calendarEventList}" var="calendarEvent" varStatus="status">
      <c:choose>
        <c:when test="${showEventLink eq 'true'}">
          <h5><a href="${ctx}/calendar-event/${calendarEvent.uniqueId}?returnPage=${widgetContext.uri}"><c:out value="${calendarEvent.title}" /></a></h5>
        </c:when>
        <c:otherwise>
          <h5><c:out value="${calendarEvent.title}" /></h5>
        </c:otherwise>
      </c:choose>
      <small>
        <c:out value="${date:formatMonthDayYear(calendarEvent.startDate)}"/>
      </small>
      <c:if test="${showEventLink eq 'true'}">
        <p><a href="${ctx}/calendar-event/${calendarEvent.uniqueId}?returnPage=${widgetContext.uri}" class="read-more">See details</a></p>
      </c:if>
      <c:if test="${!status.last}">
        <hr/>
      </c:if>
    </c:forEach>
  </c:when>
  <c:otherwise>
    <p class="subheader">
      No upcoming events were found
    </p>
  </c:otherwise>
</c:choose>
