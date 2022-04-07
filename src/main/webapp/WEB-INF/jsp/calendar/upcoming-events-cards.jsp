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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="calendarEventList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="cardClass" class="java.lang.String" scope="request"/>
<jsp:useBean id="smallCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="mediumCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="largeCardCount" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<div class="platform-content-container">
  <div class="platform-content">
    <div class="grid-x grid-margin-x text-center align-center align-stretch small-up-<c:out value="${smallCardCount}" /> medium-up-<c:out value="${mediumCardCount}" /> large-up-<c:out value="${largeCardCount}" />">
      <c:forEach items="${calendarEventList}" var="calendarEvent" varStatus="status">
        <div class="cell">
          <div class="card<c:if test="${!empty cardClass}"> <c:out value="${cardClass}" /></c:if>">
            <c:if test="${!empty titles && fn:length(titles) > status.index }">
              <div class="event-designation">
                <h4><c:out value="${titles[status.index]}" /></h4>
              </div>
            </c:if>
            <div class="card-section event-date">
              <h2><fmt:formatDate pattern="MMMM d" value="${calendarEvent.startDate}" /></h2>
            </div>
            <div class="event-title">
              <c:choose>
                <c:when test="${!empty calendarLink}">
                  <h3><a href="${ctx}${calendarLink}"><c:out value="${calendarEvent.title}" />&nbsp;<i class="fa fa-caret-right"></i></a></h3>
                </c:when>
                <c:otherwise>
                  <h3><a href="${ctx}/calendar-event/${calendarEvent.uniqueId}?returnPage=${widgetContext.uri}"><c:out value="${calendarEvent.title}" />&nbsp;<i class="fa fa-caret-right"></i></a></h3>
                </c:otherwise>
              </c:choose>
            </div>
          </div>
        </div>
      </c:forEach>
    </div>
  </div>
</div>