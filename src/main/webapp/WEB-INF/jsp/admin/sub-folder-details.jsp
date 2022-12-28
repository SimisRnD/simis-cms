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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/tlds/group-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="subFolder" class="com.simisinc.platform.domain.model.cms.SubFolder" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<h3>
  <i class="fa fa-folder-open"></i> <c:out value="${subFolder.name}" />
  <c:if test="${(userSession.hasRole('admin') || userSession.hasRole('content-manager'))}">
    <small>
      <a href="${ctx}/admin/sub-folder?subFolderId=${subFolder.id}&returnPage=${widgetContext.uri}/admin/sub-folder-details%3FsubFolderId=${subFolder.id}%26folderId=${subFolder.folderId}"><i class="fa fa-edit"></i></a>
      <a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&subFolderId=${subFolder.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(subFolder.name)}" />?');"><i class="fa fa-remove"></i></a>
    </small>
  </c:if>
</h3>
<p>
  <c:if test="${!empty subFolder.summary}">
    <small class="subheader"><c:out value="${subFolder.summary}" /></small><br />
  </c:if>
</p>
