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
<jsp:useBean id="searchCustomerNumber" class="java.lang.String" scope="request"/>
<jsp:useBean id="searchOrderNumber" class="java.lang.String" scope="request"/>
<jsp:useBean id="searchEmail" class="java.lang.String" scope="request"/>
<jsp:useBean id="searchPhone" class="java.lang.String" scope="request"/>
<jsp:useBean id="searchName" class="java.lang.String" scope="request"/>
<form id="searchForm" method="post" autocomplete="off">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <label>Customer Number
    <input type="text" placeholder="Search by customer number..." id="customerNumber" name="customerNumber" value="<c:if test="${!empty searchCustomerNumber}"><c:out value="${searchCustomerNumber}" /></c:if>" autocomplete="off">
  </label>
  <label>Order Number
    <input type="text" placeholder="Search by order number..." id="orderNumber" name="orderNumber" value="<c:if test="${!empty searchOrderNumber}"><c:out value="${searchOrderNumber}" /></c:if>" autocomplete="off">
  </label>
  <label>Email
    <input type="text" placeholder="Search by email..." id="email" name="email" value="<c:if test="${!empty searchEmail}"><c:out value="${searchEmail}" /></c:if>" autocomplete="off">
  </label>
  <label>Phone Number
    <input type="text" placeholder="Search by phone..." id="phone" name="phone" value="<c:if test="${!empty searchPhone}"><c:out value="${searchPhone}" /></c:if>" autocomplete="off">
  </label>
  <label>Name
    <input type="text" placeholder="Search by name..." id="name" name="name" value="<c:if test="${!empty searchName}"><c:out value="${searchName}" /></c:if>" autocomplete="off">
  </label>
  <div class="button-container">
    <input type="submit" class="button radius primary expanded" value="Search"/>
    <input id="resetButton" type="reset" class="button radius secondary expanded" value="Reset"/>
  </div>
</form>
<script>
  $(document).ready(function () {
    $('#resetButton').click(function (event) {
      event.preventDefault();
      $('#searchForm input[type="text"]').val('');
    });
  });
</script>