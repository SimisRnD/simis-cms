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
<%@ page import="java.util.Date" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/tlds/group-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="user" class="com.simisinc.platform.domain.model.User" scope="request"/>
<%@include file="../page_messages.jspf" %>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<fieldset class="fieldset">
Profile Location: <c:out value="${user.city}" /><c:if test="${!empty user.city && !empty user.state}">,</c:if> <c:out value="${user.state}" />
<c:out value="${user.country}" />
</fieldset>

<fieldset class="fieldset">
Organization: <c:out value="${user.organization}" /><br />
Title: <c:out value="${user.title}" /><br />
Department: <c:out value="${user.department}" />
</fieldset>
<fieldset class="fieldset">
  Current Time: <fmt:formatDate pattern="h:mm a z" value="<%= new Date() %>" /><br />
  Time Zone:
  <c:choose>
    <c:when test="${!empty user.timeZone}">
      <c:out value="${user.timeZone}" />
    </c:when>
    <c:when test="${!empty userSession.geoIP.timezone}">
      <c:out value="${userSession.geoIP.timezone}" />
    </c:when>
    <c:otherwise>
      System default
    </c:otherwise>
  </c:choose>
  <br />
  Postal Code:
  <c:choose>
    <c:when test="${!empty user.postalCode}">
      <c:out value="${user.postalCode}" />
    </c:when>
    <c:when test="${!empty userSession.geoIP.timezone}">
      <c:out value="${userSession.geoIP.postalCode}" />
    </c:when>
    <c:otherwise>
      Not set
    </c:otherwise>
  </c:choose>
</fieldset>