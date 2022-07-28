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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="order" class="com.simisinc.platform.domain.model.ecommerce.Order" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${testMode eq 'true'}"><span class="label warning">TEST MODE</span></c:if>
<button class="button alert expanded" data-open="refundFormReveal">Issue a Refund</button>
<div class="reveal small" id="refundFormReveal" data-reveal data-close-on-click="false" data-animation-in="slide-in-down fast">
  <button class="close-button" data-close aria-label="Close modal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
  <h4>Issue a Refund</h4>
  <form id="issueRefundForm" method="post" autocomplete="off">
    <%-- Required by controller --%>
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <%-- The form --%>
    <input type="hidden" name="uniqueId" value="${order.uniqueId}"/>
    <%-- Form --%>
    <label>Amount
      <div class="input-group">
        <span class="input-group-label"><i class="fa fa-dollar"></i></span>
        <input class="input-group-field" name="amountToRefund" type="text" placeholder="0.00" value="" autocomplete="off">
      </div>
    </label>
    <p class="help-text" id="amountToRefund">Order total is <fmt:formatNumber type="currency" currencyCode="USD" value="${order.totalAmount}"/></p>
    <p>To issue a refund, use your login credentials:</p>
    <label>Your Email
      <input name="email" type="text" placeholder="Email" required>
    </label>
    <label>Your Password
      <input name="password" type="password" placeholder="Password" autocomplete="off" required>
    </label>
    <p class="help-text" id="passwordHelp">Your password is required to process the refund</p>
    <div class="button-container">
      <input type="submit" class="button radius expanded" value="Issue Refund" />
    </div>
  </form>
</div>