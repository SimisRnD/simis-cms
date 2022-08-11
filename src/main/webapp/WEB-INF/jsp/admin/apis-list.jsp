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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="apiList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<table class="unstriped">
  <thead>
    <tr>
      <th width="10%" class="text-center">Method</th>
      <th width="45%" class="text-center">Endpoint</th>
      <th width="45%" class="text-center">Class</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${apiList}" var="api">
    <tr>
      <td align="center">
        <c:forEach var="method" items="${fn:split(api.method,',')}">
            <c:choose>
              <c:when test="${fn:trim(method) eq 'get'}">
                <span class="label radius primary">GET</span>
              </c:when>
              <c:when test="${fn:trim(method) eq 'post'}">
                <span class="label radius secondary">POST</span>
              </c:when>
              <c:when test="${fn:trim(method) eq 'put'}">
                <span class="label radius secondary">PUT</span>
              </c:when>
              <c:when test="${fn:trim(method) eq 'delete'}">
                <span class="label radius alert">DELETE</span>
              </c:when>
            </c:choose>
        </c:forEach>
      </td>
      <td>
        /api/<c:out value="${api.endpointValue}" />
      </td>
      <td>
        <c:out value="${api.serviceClass}" />
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty apiList}">
      <tr>
        <td colspan="3">No APIs were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
