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
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/tlds/number-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="productCategoryList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<a class="button small radius primary float-left" href="${ctx}/admin/product-category?returnPage=/admin/product-categories">Add a Category <i class="fa fa-arrow-circle-right"></i></a>
<table class="unstriped">
  <thead>
    <tr>
      <th width="10%">Order</th>
      <th>Name</th>
      <th>Unique Id</th>
      <th>Description</th>
      <th>Status</th>
      <th width="80">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${productCategoryList}" var="productCategory" varStatus="status">
    <tr>
      <td>
        <input type="hidden" name="productCategoryId${status.count}" value="${productCategory.id}" />
<%--        <input type="text" name="displayOrder${status.count}" value="${status.count}" />--%>
        <c:out value="${productCategory.displayOrder}" />
      </td>
      <td>
        <a href="${ctx}/admin/product-category?productCategoryId=${productCategory.id}&returnPage=/admin/product-categories"><c:out value="${productCategory.name}" /></a>
      </td>
      <td>
        <small><c:out value="${productCategory.uniqueId}" /></small>
      </td>
      <td><c:out value="${productCategory.description}" /></td>
      <td>
        <c:choose>
          <c:when test="${!empty productCategory.enabled}">
            <span class="label success">Enabled</span>
          </c:when>
          <c:otherwise>
            <span class="label warning">Hidden</span>
          </c:otherwise>
        </c:choose>
      </td>
      <td>
        <a href="${ctx}/admin/product-category?productCategoryId=${productCategory.id}&returnPage=/admin/product-categories"><i class="fa fa-edit"></i></a>
        <a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&productCategoryId=${productCategory.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(productCategory.name)}" />?');"><i class="fa fa-remove"></i></a>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty productCategoryList}">
      <tr>
        <td colspan="6">No product categories were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
