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
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="webPageList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<table class="unstriped">
  <thead>
    <tr>
      <th width="60"></th>
      <th>Status</th>
      <th>Link</th>
      <th>Title, Keywords, Description</th>
      <th>Modified</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${webPageList}" var="webPage">
    <tr>
      <td>
        <a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webPageId=${group.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(webPage.link)}" />?');"><i class="fa fa-remove"></i></a>
        <a href="${ctx}/admin/web-page?webPageId=${group.id}"><i class="fa fa-edit"></i></a>
      </td>
      <td>
        <c:out value="${webPage.link}" />
        <c:if test="${!empty webPage.redirectUrl}">
          <br /><i class="fa fa-long-arrow-right"></i> <c:out value="${webPage.redirectUrl}" />
        </c:if>
      </td>
      <td>
        <c:out value="${webPage.title}" />
        <c:if test="${!empty webPage.keywords}">
          <br /><small class="subheader"><c:out value="${webPage.keywords}" /></small>
        </c:if>
        <c:if test="${!empty webPage.description}">
          <br /><small class="subheader"><c:out value="${webPage.description}" /></small>
        </c:if>
      </td>
      <td class="text-center"><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPage.modified}" /></td>
    </tr>
    </c:forEach>
    <c:if test="${empty groupList}">
      <tr>
        <td colspan="5">No web pages were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
