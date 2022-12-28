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
<%@ page import="static com.simisinc.platform.ApplicationInfo.VERSION" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<%@ taglib prefix="product" uri="/WEB-INF/tlds/product-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="cart" class="com.simisinc.platform.domain.model.ecommerce.Cart" scope="request"/>
<jsp:useBean id="cartItemList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="cartEntryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="priceChangeList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="noLongerAvailableList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="pricingRule" class="com.simisinc.platform.domain.model.ecommerce.PricingRule" scope="request"/>
<link rel="stylesheet" href="${ctx}/css/platform-ecommerce.css?v=<%= VERSION %>" />
<%-- Required by controller --%>
<input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
<input type="hidden" name="token" value="${userSession.formToken}"/>
<%-- Form body--%>
<div class="checkout-summary-title">
  <h3><c:out value="${title}" /></h3>
<%--
  <div class="checkout-summary-header">
    <div class="grid-x grid-margin-x">
      <div class="small-9 cell">
        <fmt:formatNumber value="${cart.totalItems}" /> Item<c:if test="${cart.totalItems gt 1}">s</c:if>
      </div>
      <div class="small-3 cell text-right help-text">
        <a href="${ctx}/cart">Edit</a>
      </div>
    </div>
  </div>
  <c:forEach items="${cartEntryList}" var="cartEntry">
    <c:set var="cartItem" scope="request" value="${cartEntry.cartItem}"/>
    <c:set var="product" scope="request" value="${cartEntry.product}"/>
    <c:set var="productSku" scope="request" value="${cartEntry.productSku}"/>
    <div class="checkout-summary-item">
      <div class="grid-x grid-margin-x">
        <div class="small-3 cell">
          <c:if test="${!empty product.imageUrl}"><img src="<c:out value="${product.imageUrl}"/>" /></c:if>
        </div>
        <div class="small-6 cell">
          <div class="item-name">
            <c:out value="${product.nameWithCaption}" /><br />
            <small>Qty: <fmt:formatNumber value="${cartItem.quantity}" /></small>
          </div>
        </div>
        <div class="small-3 cell">
          <div class="item-total text-right">
            <p id="item-${cartItem.id}-total">
              <fmt:formatNumber type="currency" currencyCode="USD" value="${cartItem.quantity * productSku.price}" />
            </p>
          </div>
        </div>
      </div>
    </div>
  </c:forEach>
--%>
  <hr />
  <div class="checkout-summary-details">
    <div class="grid-x">
      <div class="small-6">
          <small>Subtotal</small>
      </div>
      <div class="small-6 text-right">
        <small><fmt:formatNumber type="currency" currencyCode="USD" value="${runningTotal}" /></small>
      </div>
    </div>
    <%-- Display this discount next to the subtotal --%>
    <c:if test="${pricingRule.subtotalPercent > 0 || (pricingRule.buyXItems > 0 && pricingRule.getYItemsFree > 0)}">
      <div class="grid-x">
        <div class="small-8">
          <small>Discount (<c:out value="${pricingRule.promoCode}" />) <c:out value="${pricingRule.name}" /></small>
        </div>
        <div class="small-4 text-right">
            <small>-<fmt:formatNumber type="currency" currencyCode="USD" value="${discount}" /></small>
        </div>
      </div>
    </c:if>
    <%-- Display the shipping --%>
    <div class="grid-x">
      <div class="small-6">
        <small>Shipping</small>
      </div>
      <div class="small-6 text-right">
        <c:choose>
          <c:when test="${empty cart.shippingAndHandlingFee}">
            <small>TBD</small>
          </c:when>
          <c:when test="${cart.shippingAndHandlingFee le 0}">
            <small>Free</small>
          </c:when>
          <c:otherwise>
            <small><fmt:formatNumber type="currency" currencyCode="USD" value="${cart.shippingAndHandlingFee}" /></small>
          </c:otherwise>
        </c:choose>
      </div>
    </div>
    <div class="grid-x">
      <div class="small-6">
        <c:choose>
          <c:when test="${empty cart.taxAmount}">
            <small>Sales Tax</small>
          </c:when>
          <c:when test="${cart.taxAmount le 0}">
            <small>Sales Tax</small>
          </c:when>
          <c:otherwise>
            <small>Estimated Sales Tax</small>
          </c:otherwise>
        </c:choose>
      </div>
      <div class="small-6 text-right">
        <c:choose>
          <c:when test="${empty cart.taxAmount}">
            <small>TBD</small>
          </c:when>
          <c:when test="${cart.taxAmount le 0}">
            <small>None</small>
          </c:when>
          <c:otherwise>
            <small><fmt:formatNumber type="currency" currencyCode="USD" value="${cart.taxAmount}" /></small>
          </c:otherwise>
        </c:choose>
      </div>
    </div>
    <%-- Display this discount before the total --%>
    <c:if test="${pricingRule.subtractAmount > 0.0}">
      <div class="grid-x">
        <div class="small-6">
          <small>Discount (<c:out value="${pricingRule.promoCode}" />) <c:out value="${pricingRule.name}" /></small>
        </div>
        <div class="small-6 text-right">
          <small>-<fmt:formatNumber type="currency" currencyCode="USD" value="${discount}" /></small>
        </div>
      </div>
    </c:if>
    <%-- Show the total --%>
    <hr />
    <div class="grid-x checkout-summary-total">
      <div class="small-6">
        <c:choose>
          <c:when test="${cart.shippingRateId gt -1}">
            Order Total
          </c:when>
          <c:otherwise>
            Estimated Total
          </c:otherwise>
        </c:choose>
      </div>
      <div class="small-6 text-right">
        <span id="cart-subtotal"><fmt:formatNumber type="currency" currencyCode="USD" value="${grandTotal}" /></span>
        <c:if test="${!empty userSession.geoIP && userSession.geoIP.country ne 'United States'}">
          <br /><span class="subheader currency"><small>USD</small></span>
        </c:if>
      </div>
    </div>
  </div>
</div>
