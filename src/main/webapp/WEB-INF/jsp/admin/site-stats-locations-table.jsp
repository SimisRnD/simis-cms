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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="sessionList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="showContinent" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<table class="unstriped">
  <thead>
    <tr>
      <c:if test="${showContinent ne 'false'}">
        <th width="100">Continent</th>
      </c:if>
      <th width="100">Country</th>
      <th width="100">State</th>
      <th width="100">City</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${sessionList}" var="session">
    <tr>
      <c:if test="${showContinent ne 'false'}">
        <td><c:out value="${session.continent}" /></td>
      </c:if>
      <td><c:out value="${session.country}" /></td>
      <td><c:out value="${session.state}" /></td>
      <td><c:out value="${session.city}" /></td>
    </tr>
    </c:forEach>
    <c:if test="${empty sessionList}">
      <tr>
        <c:choose>
          <c:when test="${showContinent ne 'false'}">
            <td colspan="4">Data was not found</td>
          </c:when>
          <c:otherwise>
            <td colspan="3">Data was not found</td>
          </c:otherwise>
        </c:choose>
      </tr>
    </c:if>
  </tbody>
</table>
