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
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="item" class="com.simisinc.platform.domain.model.items.Item" scope="request"/>
<jsp:useBean id="buttonName" class="java.lang.String" scope="request"/>
<c:choose>
  <c:when test="${!empty editUrl}">
    <a class="button radius" href="${editUrl}<c:if test="${!empty returnPage}">?returnPage=${returnPage}</c:if>"><i class="${font:fal()} fa-edit"></i> <c:out value="${buttonName}" /></a>
  </c:when>
  <c:otherwise>
    <a class="button radius" href="${ctx}/edit/<c:out value="${item.uniqueId}" /><c:if test="${!empty returnPage}">?returnPage=${returnPage}</c:if>"><i class="${font:fal()} fa-edit"></i> <c:out value="${buttonName}" /></a>
  </c:otherwise>
</c:choose>
