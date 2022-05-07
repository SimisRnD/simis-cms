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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="blog" class="com.simisinc.platform.domain.model.cms.Blog" scope="request"/>
<c:choose>
  <c:when test="${blog.id eq -1}"><h4>New Blog</h4></c:when>
  <c:otherwise><h4>Update Blog</h4></c:otherwise>
</c:choose>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${blog.id}"/>
  <c:if test="${!empty returnPage}">
    <input type="hidden" name="returnPage" value="<c:out value="${returnPage}"/>"/>
  </c:if>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <label>Name <span class="required">*</span>
    <input type="text" placeholder="Blog, News, Press Releases..." name="name" value="<c:out value="${blog.name}"/>" required>
  </label>
  <label>Description
    <input type="text" placeholder="Describe it..." name="description" value="<c:out value="${blog.description}"/>">
  </label>
  <input id="enabled" type="checkbox" name="enabled" value="true" <c:if test="${blog.id == -1 || blog.enabled}">checked</c:if>/><label for="enabled">Online?</label>
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
