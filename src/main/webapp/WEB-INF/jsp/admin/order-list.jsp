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
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="order" uri="/WEB-INF/tlds/order-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="orderList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<%--<button class="button small primary radius"><i class="fa fa-plus"></i> New Order</button>--%>
<form method="post" action="${ctx}/admin/orders">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form --%>
  <input type="hidden" name="command" value="downloadCSVFile" />
  <button class="button small secondary radius float-left"><i class="fa fa-download"></i> Download CSV File</button>
</form>
<form method="post" action="${ctx}/admin/orders">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form --%>
  <input type="hidden" name="command" value="downloadTaxJarCSVFile" />
  <button class="button small secondary radius float-left margin-left-10"><i class="fa fa-download"></i> Download TaxJar CSV File</button>
</form>
<table class="unstriped stack">
  <thead>
    <tr>
<%--      <th>Date</th>--%>
      <th width="200" nowrap>Order #</th>
      <th width="100" class="text-center">Amount</th>
      <th width="75" class="text-center">Items</th>
      <th>Location</th>
      <th>Status</th>
      <th>Date</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${orderList}" var="order">
    <tr>
<%--      <td><fmt:formatDate pattern="yyyy-MM-dd" value="${order.created}" /></td>--%>
      <td nowrap>
        <a href="${ctx}/admin/order-details?order-number=<c:out value="${order.uniqueId}" />"><c:out value="${order.uniqueId}" /></a>
        <c:if test="${!order.live}"><span class="label warning">TEST MODE</span></c:if>
      </td>
      <td nowrap class="text-center"><fmt:formatNumber type="currency" currencyCode="USD" value="${order.totalAmount}"/></td>
      <td class="text-center"><fmt:formatNumber value="${order.totalItems}" /></td>
    <%--      <td>--%>
<%--        <a href="${ctx}/admin/order-details?orderId=${order.id}"><c:out value="${order.product}" /></a>--%>
<%--      </td>--%>
<%--      <td>--%>
<%--        <c:out value="${order.customer}" />--%>
<%--      </td>--%>
      <td>
        <c:out value="${order.shippingAddress.city}" />
        <c:out value="${order.shippingAddress.state}" />
      </td>
      <td nowrap><c:out value="${order:currentStatus(order.statusId)}"/></td>
      <td nowrap><fmt:formatDate pattern="yyyy-MM-dd" value="${order.created}" /></td>
    </tr>
    </c:forEach>
    <c:if test="${empty orderList}">
      <tr>
        <td colspan="6">No orders were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%-- Paging Control --%>
<%@include file="../paging_control.jspf" %>