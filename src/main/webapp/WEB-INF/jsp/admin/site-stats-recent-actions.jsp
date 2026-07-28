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
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="title" class="java.lang.String" scope="request"/>
<jsp:useBean id="link" class="java.lang.String" scope="request"/>
<div class="callout radius" style="height: 100%; margin: 0;">
  <p class="no-gap" style="font-weight: bold;"><c:out value="${title}"/></p>
  <c:choose>
    <c:when test="${empty recentActionsList}">
      <p class="no-gap text-muted">No recent activity</p>
    </c:when>
    <c:otherwise>
      <ul class="no-bullet no-gap">
        <c:forEach items="${recentActionsList}" var="record">
          <li style="padding: 4px 0; border-bottom: 1px solid #eee;">
            <span title="<fmt:formatDate pattern='yyyy-MM-dd HH:mm:ss z' value='${record.occurred}' />"><c:out value="${date:relative(record.occurred)}" /></span>
            &mdash;
            <c:choose>
              <c:when test="${!empty record.actorUsername}"><c:out value="${record.actorUsername}" /></c:when>
              <c:otherwise>System</c:otherwise>
            </c:choose>
            <c:out value="${record.eventType}" />
            <c:if test="${!empty record.targetLabel}">
              (<c:out value="${record.targetLabel}" />)
            </c:if>
          </li>
        </c:forEach>
      </ul>
    </c:otherwise>
  </c:choose>
  <c:if test="${!empty link}">
    <a href="<c:out value="${link}" />">View audit log &raquo;</a>
  </c:if>
</div>
