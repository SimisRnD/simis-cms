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
<jsp:useBean id="trackingNumber" class="com.simisinc.platform.domain.model.ecommerce.TrackingNumber" scope="request"/>
<jsp:useBean id="order" class="com.simisinc.platform.domain.model.ecommerce.Order" scope="request"/>
<jsp:useBean id="shippingCarrierList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="orderEntryList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${testMode eq 'true'}"><span class="label warning">TEST MODE</span></c:if>
<button class="button primary expanded" data-open="trackingFormReveal">Add a Tracking Number</button>
<div class="reveal small" id="trackingFormReveal" data-reveal data-close-on-click="false" data-animation-in="slide-in-down fast">
  <button class="close-button" data-close aria-label="Close modal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
  <h4>Add a Tracking Number</h4>
  <form id="trackingForm" method="post" autocomplete="off">
    <%-- Required by controller --%>
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <%-- The form --%>
    <input type="hidden" name="uniqueId" value="${order.uniqueId}"/>
    <%-- Form --%>
    <div class="grid-x grid-margin-x">
      <div class="small-12 medium-6 cell">
        <label>Shipping Carrier <span class="required">*</span>
          <select id="shippingCarrierId" name="shippingCarrierId">
            <option value="-1">Select</option>
            <c:forEach items="${shippingCarrierList}" var="shippingCarrier">
              <option value="${shippingCarrier.id}"<c:if test="${trackingNumber.shippingCarrierId eq shippingCarrier.id}"> selected</c:if>><c:out value="${shippingCarrier.code}" /> (<c:out value="${shippingCarrier.title}" />)</option>
            </c:forEach>
          </select>
        </label>
      </div>
      <div class="small-12 medium-6 cell">
        <label>Tracking Number(s) <span class="required">*</span> (comma-separated)
          <input class="input-group-field" name="trackingNumber" type="text" value="" autocomplete="off" required>
        </label>
      </div>
    </div>
    <div class="small-1 cell">
      <input type="checkbox" id="sendCustomerEmail" name="sendCustomerEmail" value="true" checked />
      <label for="sendCustomerEmail">Send an email to the customer</label>
    </div>
    <c:if test="${!empty orderEntryList}">
      <h5 class="margin-bottom-25">Choose Products with this Tracking Number:</h5>
      <c:forEach items="${orderEntryList}" var="orderEntry" varStatus="orderEntryStatus">
        <c:set var="orderItem" scope="request" value="${orderEntry.orderItem}"/>
        <c:set var="product" scope="request" value="${orderEntry.product}"/>
        <c:set var="productSku" scope="request" value="${orderEntry.productSku}"/>
        <fmt:parseNumber var="thisQuantity" type="number" value="${orderItem.quantity}"/>
        <div class="grid-x grid-margin-x margin-bottom-20">
          <div class="small-1 cell">
            <input type="checkbox" id="orderItemTrackingNumber${orderEntryStatus.index}" name="orderItemTrackingNumber${orderEntryStatus.index}" value="${orderItem.id}" />
            <label for="orderItemTrackingNumber${orderEntryStatus.index}"></label>
          </div>
          <div class="small-3 medium-3 large-2 cell">
            <c:if test="${!empty product.imageUrl}"><img src="<c:out value="${product.imageUrl}"/>"/></c:if>
          </div>
          <div class="small-8 medium-7 large-9 cell medium-text-left">
              <p class="no-gap">
                <c:out value="${product.nameWithCaption}"/>
                <c:if test="${orderItem.shipped}"><strong>(shipped)</strong></c:if>
              </p>
              <c:forEach items="${productSku.nativeAttributes}" var="thisAttribute" varStatus="attributeStatus">
                <c:if test="${!empty thisAttribute.value}">
                  <p class="no-gap"><c:out value="${thisAttribute.value}"/></p>
                </c:if>
              </c:forEach>
              <p class="no-gap">Quantity: ${thisQuantity}</p>
          </div>
        </div>
      </c:forEach>
    </c:if>
    <div class="button-container">
      <input type="submit" class="button radius expanded" value="Add Tracking Number" />
    </div>
  </form>
</div>