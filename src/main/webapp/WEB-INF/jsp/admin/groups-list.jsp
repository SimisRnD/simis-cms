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
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="groupList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<table class="unstriped">
  <thead>
    <tr>
      <th>Name</th>
      <th width="100" class="text-center"># of users</th>
      <th width="60">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${groupList}" var="group">
    <tr>
      <td>
        <c:out value="${group.name}" />
        <c:if test="${!empty group.description}">
          <br /><small class="subheader"><c:out value="${group.description}" /></small>
        </c:if>
      </td>
      <td class="text-center"><fmt:formatNumber value="${group.userCount}" /></td>
      <td>
        <a href="${ctx}/admin/group?groupId=${group.id}"><i class="${font:fas()} fa-edit"></i></a>
        <a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&groupId=${group.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(group.name)}" />?');"><i class="fa fa-remove"></i></a>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty groupList}">
      <tr>
        <td colspan="3">No groups were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
