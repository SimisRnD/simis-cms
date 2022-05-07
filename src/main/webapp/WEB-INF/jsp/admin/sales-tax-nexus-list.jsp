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
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/number-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="salesTaxNexusAddressList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<a class="button small radius primary" href="${ctx}/admin/sales-tax-nexus-address?returnPage=/admin/sales-tax-nexus">Add an Address <i class="fa fa-arrow-circle-right"></i></a>
<%@include file="../page_messages.jspf" %>
<table class="unstriped">
  <thead>
    <tr>
      <th>Street Address</th>
      <th>City</th>
      <th>State/Region</th>
      <th>Zip/Postal Code</th>
      <th>Country</th>
      <th width="80">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${salesTaxNexusAddressList}" var="address">
    <tr>
      <td><c:out value="${address.street}" /></td>
      <td><c:out value="${address.city}" /></td>
      <td><c:out value="${address.state}" /></td>
      <td><c:out value="${address.postalCode}" /></td>
      <td><c:out value="${address.country}" /></td>
      <td>
        <a href="${ctx}/admin/sales-tax-nexus-address?addressId=${address.id}&returnPage=/admin/sales-tax-nexus"><i class="fa fa-edit"></i></a>
        <a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&addressId=${address.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(address.street)}" />?');"><i class="fa fa-remove"></i></a>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty salesTaxNexusAddressList}">
      <tr>
        <td colspan="6">No addresses were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
