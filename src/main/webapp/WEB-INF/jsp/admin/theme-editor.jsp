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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="theme" class="com.simisinc.platform.domain.model.cms.Theme" scope="request"/>
<jsp:useBean id="themeList" class="java.util.ArrayList" scope="request"/>
<script>
function deleteTheme(themeId) {
  if (!confirm("Are you sure you want to DELETE this theme?")) {
    return;
  }
  window.location.href = '${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&id='+themeId;
}
</script>
<%-- Title and Message block --%>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${!empty themeList}">
  <h5>Restore a theme:</h5>
  <ol>
    <c:forEach items="${themeList}" var="thisTheme">
      <li>
        <a href="${widgetContext.uri}?action=restore&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&id=${thisTheme.id}"><c:out value="${thisTheme.name}"/></a>
        <a href="javascript:deleteTheme(${thisTheme.id})"><i class="fa fa-trash-o"></i></a>
      </li>
    </c:forEach>
  </ol>
</c:if>
<h5>Save this theme:</h5>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form Content --%>
  <label>Name
    <input type="text" placeholder="Name of theme..." name="name" value="<c:out value="${theme.name}"/>">
  </label>
  <p><input type="submit" class="button radius success" value="Save"/></p>
</form>
