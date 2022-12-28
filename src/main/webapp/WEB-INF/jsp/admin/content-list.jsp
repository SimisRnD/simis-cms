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
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="html" uri="/WEB-INF/tlds/html-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="contentList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<table class="unstriped">
  <thead>
    <tr>
      <th>
        Unique Id
      </th>
      <th>
        Sample
      </th>
      <th width="100" class="text-center">
        # of characters
      </th>
      <th width="200" class="text-center">
        Last Modified
      </th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${contentList}" var="content">
    <tr>
      <td>
        <a href="${ctx}/content-editor?uniqueId=${content.uniqueId}&returnPage=/admin/content-list"><c:out value="${content.uniqueId}" /></a>
      </td>
      <td><span class="subheader"><c:out value="${text:trim(html:text(content.content), 50, true)}" /></span></td>
      <td class="text-center"><fmt:formatNumber value="${fn:length(content.content)}" /></td>
      <td class="text-center"><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${content.modified}" /></td>
    </tr>
    </c:forEach>
    <c:if test="${empty contentList}">
      <tr>
        <td colspan="4">No content records were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
