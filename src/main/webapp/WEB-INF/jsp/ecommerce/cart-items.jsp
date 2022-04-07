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
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<%@ taglib prefix="product" uri="/WEB-INF/product-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="cart" class="com.simisinc.platform.domain.model.ecommerce.Cart" scope="request"/>
<jsp:useBean id="cartItemList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="cartEntryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="priceChangeList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="noLongerAvailableList" class="java.util.ArrayList" scope="request"/>
<link rel="stylesheet" href="${ctx}/css/platform-ecommerce.css?v=<%= VERSION %>"/>
<script type="text/javascript" src="${ctx}/javascript/jquery-format-currency-1.4.0/jquery.formatCurrency-1.4.0.min.js"></script>
<span class="help-text float-right">
  <a href="${ctx}/cart">Edit</a>
</span>
<div class="checkout-items">
  <c:forEach items="${cartEntryList}" var="cartEntry" varStatus="cartEntryStatus">
    <c:set var="cartItem" scope="request" value="${cartEntry.cartItem}"/>
    <c:set var="product" scope="request" value="${cartEntry.product}"/>
    <c:set var="productSku" scope="request" value="${cartEntry.productSku}"/>
    <fmt:parseNumber var="thisQuantity" type="number" value="${cartItem.quantity}"/>
    <div class="checkout-summary-item">
      <div class="grid-x grid-margin-x">
        <div class="small-5 medium-3 cell">
          <c:if test="${!empty product.imageUrl}"><img src="<c:out value="${product.imageUrl}"/>"/></c:if>
        </div>
        <div class="small-7 medium-9 cell">
          <div id="item-${cartEntryStatus}-name">
            <p class="no-gap"><span class="checkout-stage-value"><c:out value="${product.nameWithCaption}"/></span></p>
            <c:forEach items="${productSku.nativeAttributes}" var="thisAttribute" varStatus="attributeStatus">
              <c:if test="${!empty thisAttribute.value}">
                <p class="checkout-stage-subtext no-gap"><c:out value="${thisAttribute.value}"/></p>
              </c:if>
            </c:forEach>
            <p class="checkout-stage-subtext no-gap">Quantity: ${thisQuantity}</p>
            <c:choose>
              <c:when test="${!empty productSku.barcode}">
                <p class="checkout-stage-subtext no-gap">Item #<c:out value="${productSku.barcode}" /></p>
              </c:when>
              <c:otherwise>
                <p class="checkout-stage-subtext no-gap">Item #<c:out value="${productSku.sku}" /></p>
              </c:otherwise>
            </c:choose>
            <p class="item-price"><fmt:formatNumber type="currency" currencyCode="USD" value="${productSku.price}"/></p>
          </div>
        </div>
      </div>
    </div>
  </c:forEach>
</div>
