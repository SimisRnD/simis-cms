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
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="customerList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<%--<button class="button small primary radius"><i class="fa fa-plus"></i> New Customer</button>--%>
<table class="unstriped stack">
  <thead>
    <tr>
      <th width="200" nowrap>Customer #</th>
      <th>Name</th>
      <th>Email</th>
      <th>Location</th>
<%--      <th width="60">Orders</th>--%>
      <th width="120">Created</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${customerList}" var="customer">
    <tr>
      <td><c:out value="${customer.uniqueId}" /></td>
      <td>
<%--        <a href="${ctx}/admin/customer-details?customerId=${customer.id}"><c:out value="${customer.fullName}" /></a>--%>
        <c:out value="${customer.firstName}" />
        <c:out value="${customer.lastName}" />
        <c:if test="${!empty customer.organization}">
          <br /><small class="subheader"><c:out value="${customer.organization}" /></small>
        </c:if>
      </td>
      <td>
        <c:out value="${text:trim(customer.email, 30, true)}"/>
      </td>
      <td>
        <c:out value="${customer.shippingAddress.city}" />
        <c:out value="${customer.shippingAddress.state}" />
      </td>
<%--      <td class="text-center">--%>
<%--        Order Count--%>
<%--      </td>--%>
      <td><fmt:formatDate pattern="yyyy-MM-dd" value="${customer.created}" /></td>
    </tr>
    </c:forEach>
    <c:if test="${empty customerList}">
      <tr>
        <td colspan="5">No customers were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%-- Paging Control --%>
<%@include file="../paging_control.jspf" %>