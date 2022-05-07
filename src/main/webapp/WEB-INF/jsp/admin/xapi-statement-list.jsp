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
<%@ taglib prefix="date" uri="/WEB-INF/date-functions.tld" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="markdown" uri="/WEB-INF/markdown-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="xapiStatementList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<table class="unstriped">
  <thead>
    <tr>
      <th>When</th>
      <th>Statement</th>
      <th>Date</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${xapiStatementList}" var="statement">
    <tr>
      <td nowrap>
        <c:out value="${date:relative(statement.created)}" />
      </td>
      <td class="no-gap-all">
        ${markdown:html(statement.messageSnapshot)}
      </td>
      <td nowrap>
        <fmt:formatDate pattern="yyyy-MM-dd" value="${statement.created}" />
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty xapiStatementList}">
      <tr>
        <td colspan="3">No records were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
