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
<jsp:useBean id="productCategory" class="com.simisinc.platform.domain.model.ecommerce.ProductCategory" scope="request"/>
<c:choose>
  <c:when test="${productCategory.id eq -1}"><h4>New Category</h4></c:when>
  <c:otherwise>
    <h4>Update Category</h4>
  </c:otherwise>
</c:choose>
<form method="post" autocomplete="off">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${productCategory.id}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-10 large-8 cell">
      <label for="name">Name <span class="required">*</span>
        <input type="text" id="name" name="name" maxlength="255" placeholder="" value="<c:out value="${productCategory.name}" />" <c:if test="${productCategory.id eq -1}">autofocus="autofocus"</c:if> required>
      </label>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-10 large-8 cell">
      <label for="uniqueId">Unique Id <span class="required">*</span>
        <input type="text" id="uniqueId" name="uniqueId" maxlength="255" placeholder="" value="<c:out value="${productCategory.uniqueId}" />"/>
      </label>
      <p class="help-text" id="uniqueIdHelpText">Leave blank to auto-generate; this value does not usually change! No spaces, use lowercase, a-z, 0-9, dashes</p>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-10 large-8 cell">
      <label for="description">Description
        <input type="text" id="description" name="description" maxlength="255" placeholder="" value="<c:out value="${productCategory.description}" />"/>
      </label>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-10 large-8 cell">
      <label for="description">Display Order
        <input type="text" id="displayOrder" name="displayOrder" maxlength="4" placeholder="" value="<c:out value="${productCategory.displayOrder}" />"/>
      </label>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-10 large-8 cell">
      <label>Show the product category online?
        <input id="enabled" type="checkbox" name="enabled" value="true" <c:if test="${productCategory.id == -1 || productCategory.enabled}">checked</c:if>/>
      </label>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 cell">
      <p>
        <input type="submit" class="button radius success" value="Save"/>
        <c:if test="${!empty returnPage}"><a class="button radius secondary" href="${returnPage}">Cancel</a></c:if>
      </p>
    </div>
  </div>
</form>