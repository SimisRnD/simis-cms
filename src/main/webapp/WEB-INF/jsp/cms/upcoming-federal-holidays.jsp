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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="holidayList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="showObservedNote" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
</c:if>
<ul class="federal-holiday-list no-bullet">
  <c:forEach items="${holidayList}" var="holiday">
    <li>
      <span class="federal-holiday-date">
        <%-- The observed date leads: it is the day the office is actually shut --%>
        <c:out value="${holiday.observedLabel}"/>
      </span>
      <span class="federal-holiday-name"><c:out value="${holiday.name}"/></span>
      <c:if test="${showObservedNote eq 'true' && holiday.observedOnDifferentDay}">
        <span class="federal-holiday-observed">observed &mdash; falls on <c:out value="${holiday.dayName}"/></span>
      </c:if>
    </li>
  </c:forEach>
</ul>
