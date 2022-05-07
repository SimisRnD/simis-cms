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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="subFolder" class="com.simisinc.platform.domain.model.cms.SubFolder" scope="request"/>
<jsp:useBean id="groupList" class="java.util.ArrayList" scope="request"/>
<c:choose>
  <c:when test="${subFolder.id eq -1}"><h4>New Sub-Folder</h4></c:when>
  <c:otherwise><h4>Update Sub-Folder</h4></c:otherwise>
</c:choose>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${subFolder.id}"/>
  <input type="hidden" name="folderId" value="${subFolder.folderId}"/>
  <c:if test="${!empty returnPage}">
    <input type="hidden" name="returnPage" value="<c:out value="${returnPage}"/>" />
  </c:if>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <label>Sub-Folder Name <span class="required">*</span>
    <input type="text" placeholder="Give it a name..." name="name" value="<c:out value="${subFolder.name}"/>" required>
  </label>
  <label>Description
    <input type="text" placeholder="Describe it..." name="summary" value="<c:out value="${subFolder.summary}"/>">
  </label>
  <label>Start Date <span class="required">*</span>
    <div class="input-group">
      <span class="input-group-label"><i class="fa fa-calendar"></i></span>
      <input class="input-group-field" type="text" placeholder="mm-dd-yyyy time" id="startDate${widgetContext.uniqueId}" name="startDate" value="<fmt:formatDate pattern="MM-dd-yyyy HH:mm" value="${subFolder.startDate}" />">
    </div>
  </label>
  <script>
    $(function () {
      $('#startDate').fdatepicker({
        format: 'mm-dd-yyyy hh:ii',
        disableDblClickSelection: true,
        pickTime: true
      });
    });
  </script>
  <c:choose>
    <c:when test="${!empty returnPage}">
      <p>
        <input type="submit" class="button radius success" value="Save"/>
        <a href="${returnPage}" class="button radius secondary">Cancel</a>
      </p>
    </c:when>
    <c:otherwise>
      <p><input type="submit" class="button radius success expanded" value="Save"/></p>
    </c:otherwise>
  </c:choose>
</form>