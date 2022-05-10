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
<%@ taglib prefix="date" uri="/WEB-INF/date-functions.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/group-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="user" class="com.simisinc.platform.domain.model.User" scope="request"/>
<jsp:useBean id="showName" class="java.lang.String" scope="request"/>
<jsp:useBean id="showNickname" class="java.lang.String" scope="request"/>
<jsp:useBean id="showJoinDate" class="java.lang.String" scope="request"/>
<%@include file="../page_messages.jspf" %>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<c:if test="${showName eq 'true'}">
<h4><c:out value="${user.fullName}"/></h4>
</c:if>
<c:if test="${showNickname eq 'true'}">
<p class="subheader"><span class="display-field-value"><c:if test="${!empty user.nickname}"><c:out value="${user.nickname}"/></span></p></c:if>
</c:if>
<c:if test="${showJoinDate eq 'true'}">
<p class="subheader"><span class="display-field-value">You joined the site on <c:out value="${date:formatMonthDayYear(user.created)}"/></span></p>
</c:if>