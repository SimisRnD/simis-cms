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
<%@ taglib prefix="date" uri="/WEB-INF/date-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="appList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<table class="unstriped">
  <thead>
    <tr>
      <th>Name</th>
      <th width="100" class="text-center">Devices</th>
      <th width="100" class="text-center">Enabled?</th>
      <th width="200">Created</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${appList}" var="app">
    <tr>
      <td>
        <c:out value="${app.name}" />
        <c:if test="${!empty app.summary}">
          <br /><small class="subheader"><c:out value="${app.summary}" /></small>
        </c:if>
        <c:if test="${!empty app.publicKey && !date:isMinutesOld(app.created, 10)}">
          <br /><small>New Public Key: <c:out value="${app.publicKey}" /></small>
        </c:if>
      </td>
      <td class="text-center"><fmt:formatNumber value="0" /></td>
      <td class="text-center">
        <c:choose>
          <c:when test="${app.enabled}"><span class="label success">Yes</span></c:when>
          <c:otherwise><span class="label warning">No</span></c:otherwise>
        </c:choose>
      </td>
      <td class="text-center"><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${app.created}" /></td>
    </tr>
    </c:forEach>
    <c:if test="${empty appList}">
      <tr>
        <td colspan="4">No apps were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
