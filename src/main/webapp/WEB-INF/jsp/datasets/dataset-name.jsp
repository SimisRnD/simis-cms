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
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="dataset" class="com.simisinc.platform.domain.model.datasets.Dataset" scope="request"/>
<jsp:useBean id="showCount" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<h5>
  <i class="fa fa-table"></i>
  <c:out value="${dataset.name}" />
  <c:if test="${showCount eq 'true'}">
    <c:choose>
      <c:when test="${dataset.rowsProcessed lt dataset.rowCount && dataset.rowsProcessed gt 0}">
        <span class="label round warning" id="rowCount"><fmt:formatNumber value="${dataset.rowCount}" /></span>
        <i class="fa fa-spinner fa-spin fa-fw"></i>
      </c:when>
      <c:when test="${!empty dataset.processed}">
        <span class="label round success" id="rowCount"><fmt:formatNumber value="${dataset.rowCount}" /></span>
      </c:when>
      <c:otherwise>
        <span class="label round" id="rowCount"><fmt:formatNumber value="${dataset.rowCount}" /></span>
      </c:otherwise>
    </c:choose>
  </c:if>
</h5>
