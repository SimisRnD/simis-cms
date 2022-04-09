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
<jsp:useBean id="group" class="com.simisinc.platform.domain.model.Group" scope="request"/>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${group.id}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <label>Name <span class="required">*</span>
    <input type="text" placeholder="Give it a name..." name="name" aria-describedby="uniqueNameHelpText" value="<c:out value="${group.name}"/>" required>
  </label>
    <p class="help-text" id="uniqueNameHelpText">The name must be unique</p>
  <label>Description
    <input type="text" placeholder="Describe it..." name="description" value="<c:out value="${group.description}"/>">
  </label>
  <label>Unique Id <span class="required">*</span>
    <input type="text" placeholder="Internal Reference Id..." name="uniqueId" aria-describedby="uniqueIdHelpText" value="<c:out value="${group.uniqueId}"/>">
  </label>
  <p class="help-text" id="uniqueIdHelpText">Leave blank to auto-generate; this value does not usually change! No spaces, use lowercase, a-z, 0-9, dashes</p>
  <p><input type="submit" class="button radius success expanded" value="Save"/></p>
</form>