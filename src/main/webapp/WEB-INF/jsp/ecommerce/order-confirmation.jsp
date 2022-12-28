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
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<%@ taglib prefix="order" uri="/WEB-INF/tlds/order-functions.tld" %>
<%@ taglib prefix="product" uri="/WEB-INF/tlds/product-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="title" class="java.lang.String" scope="request"/>
<jsp:useBean id="calloutHtml" class="java.lang.String" scope="request"/>
<jsp:useBean id="introHtml" class="java.lang.String" scope="request"/>
<jsp:useBean id="order" class="com.simisinc.platform.domain.model.ecommerce.Order" scope="request"/>
<jsp:useBean id="orderEntryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="shippingMethod" class="com.simisinc.platform.domain.model.ecommerce.ShippingMethod" scope="request"/>
<jsp:useBean id="trackingNumberList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<c:if test="${!empty calloutHtml}">
  <div class="callout">
      ${calloutHtml}
  </div>
</c:if>
<c:if test="${!empty introHtml}">
  <div class="platform-content">
      ${introHtml}
  </div>
</c:if>
<c:choose>
  <c:when test="${!empty order.paymentDate}">
    <fmt:formatDate pattern="MM-dd-yyyy hh:mm a" value="${order.paymentDate}" />
  </c:when>
  <c:otherwise>
    <fmt:formatDate pattern="MM-dd-yyyy hh:mm a" value="${order.created}" />
  </c:otherwise>
</c:choose>
<c:if test="${userSession.hasRole('admin') || userSession.hasRole('ecommerce-manager')}">
  <c:if test="${!order.live}"><span class="label warning">TEST MODE</span></c:if>
  <span class="label"><c:out value="${order:currentStatus(order.statusId)}"/></span>
</c:if>
<c:choose>
  <c:when test="${!empty trackingNumberList}">
    <br />
    Tracking number<c:if test="${fn:length(trackingNumberList) > 1}">s</c:if>:
    <c:forEach items="${trackingNumberList}" var="thisTrackingNumber" varStatus="trackingNumberStatus">
      <c:set var="trackingLink" scope="request" value="${order:trackingNumberLink(thisTrackingNumber)}"/>
      <a href="${trackingLink}" target="_blank"><c:out value="${thisTrackingNumber.trackingNumber}" /></a><c:if test="${!trackingNumberStatus.last}">, </c:if>
    </c:forEach>
  </c:when>
  <c:when test="${!empty order.trackingNumbers}">
    <br />
    Tracking number(s):
    <c:set var="trackingLink" scope="request" value="${order:trackingNumberLinkMap(order.trackingNumbers, shippingMethod)}"/>
    <c:if test="${!empty trackingLink}">
      <c:forEach items="${trackingLink}" var="thisTrackingNumber" varStatus="trackingNumberStatus">
        <a href="${thisTrackingNumber.value}" target="_blank"><c:out value="${thisTrackingNumber.key}" /></a><c:if test="${!trackingNumberStatus.last}">, </c:if>
      </c:forEach>
    </c:if>
  </c:when>
</c:choose>
<div class="grid-x grid-margin-x">
  <div class="small-12 medium-8 cell checkout-items">
    <hr/>
    <div class="grid-x grid-margin-x">
      <c:if test="${!empty order.shippingAddress}">
        <div class="small-12 medium-6 cell">
          <h5 class="order-section-title">Shipped To:</h5>
          <p>
            <c:out value="${order.shippingAddress.firstName}"/> <c:out value="${order.shippingAddress.lastName}"/><br/>
            <c:out value="${order.shippingAddress.street}"/><br/>
            <c:if test="${!empty order.shippingAddress.addressLine2}">
              <c:out value="${order.shippingAddress.addressLine2}"/><br/>
            </c:if>
            <c:out value="${order.shippingAddress.city}"/>,
            <c:out value="${order.shippingAddress.state}"/>
            <c:out value="${order.shippingAddress.postalCode}"/>
            <c:if test="${order.shippingAddress.country ne 'United States'}">
              <br/>
              <c:out value="${order.shippingAddress.country}"/>
            </c:if>
          </p>
        </div>
      </c:if>
      <div class="small-12 medium-6 cell">
        <c:if test="${!empty order.paymentBrand}">
          <h5 class="order-section-title">Billed To:</h5>
          <p class="no-gap">
            <c:out value="${fn:toUpperCase(order.paymentBrand)}"/>
            <c:if test="${!empty order.paymentLast4}">
              ending in <c:out value="${order.paymentLast4}"/>
            </c:if>
          </p>
        </c:if>
        <c:if test="${!empty shippingMethod.title}">
          <p><c:out value="${shippingMethod.title}"/></p>
        </c:if>
      </div>
    </div>
    <p>
      <c:if test="${!empty order.firstName}"><c:out value="${order.firstName}"/></c:if>
      <c:if test="${!empty order.lastName}"><c:out value="${order.lastName}"/></c:if>
      <c:if test="${!empty order.email}">
        <c:if test="${!empty order.firstName || !empty order.lastName}"><br /></c:if>
        <c:out value="${order.email}"/>
      </c:if>
    </p>
    <c:if test="${!empty orderEntryList}">
      <hr/>
      <h5 class="order-section-title">Products</h5>
      <div class="checkout-items">
        <c:forEach items="${orderEntryList}" var="orderEntry" varStatus="orderEntryStatus">
          <c:set var="orderItem" scope="request" value="${orderEntry.orderItem}"/>
          <c:set var="product" scope="request" value="${orderEntry.product}"/>
          <c:set var="productSku" scope="request" value="${orderEntry.productSku}"/>
          <fmt:parseNumber var="thisQuantity" type="number" value="${orderItem.quantity}"/>
          <div class="checkout-summary-item">
            <div class="grid-x grid-margin-x">
              <div class="small-5 medium-3 cell">
                <c:if test="${!empty product.imageUrl}"><img src="<c:out value="${product.imageUrl}"/>"/></c:if>
              </div>
              <div class="small-7 medium-9 cell">
                <div id="item-${orderEntryStatus}-name">
                  <p class="no-gap">
                    <span class="checkout-stage-value"><c:out value="${product.nameWithCaption}"/></span>
                    <c:if test="${orderItem.shipped}"><strong>(shipped)</strong></c:if>
                  </p>
                  <c:forEach items="${productSku.nativeAttributes}" var="thisAttribute" varStatus="attributeStatus">
                    <c:if test="${!empty thisAttribute.value}">
                      <p class="checkout-stage-subtext no-gap"><c:out value="${thisAttribute.value}"/></p>
                    </c:if>
                  </c:forEach>
                  <p class="checkout-stage-subtext no-gap">Quantity: ${thisQuantity}</p>
                  <p class="item-price no-gap">
                    <fmt:formatNumber type="currency" currencyCode="USD" value="${orderItem.totalAmount}"/>
                  </p>
                  <p>
                    <c:choose>
                      <c:when test="${!empty productSku.barcode}">
                        Item #<c:out value="${productSku.barcode}" />
                      </c:when>
                      <c:otherwise>
                        Item #<c:out value="${productSku.sku}" />
                      </c:otherwise>
                    </c:choose>
                  </p>
                </div>
              </div>
            </div>
          </div>
        </c:forEach>
      </div>
    </c:if>
  </div>
  <%-- Order Total Column --%>
  <div class="small-12 medium-4 cell margin-bottom-50">
    <div class="checkout-summary-title callout box checkout-summary-callout">
      <h3>Summary</h3>
      <hr/>
      <div class="checkout-summary-details">
        <div class="grid-x">
          <div class="small-6">
            <c:choose>
              <c:when test="${order.totalItems eq 1}">
                <small>Subtotal (1 item)</small>
              </c:when>
              <c:otherwise>
                <small>Subtotal (${order.totalItems} items)</small>
              </c:otherwise>
            </c:choose>
          </div>
          <div class="small-6 text-right">
            <small><fmt:formatNumber type="currency" currencyCode="USD" value="${order.subtotalAmount}"/></small>
          </div>
        </div>
        <c:if test="${order.discountAmount gt 0}">
          <div class="grid-x">
            <div class="small-6">
              <small>Discount</small>
            </div>
            <div class="small-6 text-right">
              <small>-<fmt:formatNumber type="currency" currencyCode="USD" value="${order.discountAmount}"/></small>
            </div>
          </div>
        </c:if>
        <c:if test="${order.shippingFee gt 0}">
          <div class="grid-x">
            <div class="small-6">
              <small>Shipping</small>
            </div>
            <div class="small-6 text-right">
              <small><fmt:formatNumber type="currency" currencyCode="USD" value="${order.shippingFee}"/></small>
            </div>
          </div>
        </c:if>
        <c:if test="${order.taxAmount gt 0}">
          <div class="grid-x">
            <div class="small-6">
              <small>Sales Tax</small>
            </div>
            <div class="small-6 text-right">
              <small><fmt:formatNumber type="currency" currencyCode="USD" value="${order.taxAmount}"/></small>
            </div>
          </div>
        </c:if>
        <%-- Total --%>
        <hr/>
        <div class="grid-x checkout-summary-total margin-bottom-25">
          <div class="small-6">
            Total
          </div>
          <div class="small-6 text-right">
            <fmt:formatNumber type="currency" currencyCode="USD" value="${order.totalAmount}"/>
            <c:if test="${!empty order.currency && order.currency ne 'usd'}">
              <br/><span class="subheader currency"><small>USD</small></span>
            </c:if>
          </div>
        </div>
        <c:if test="${order.refunded}">
          <div class="grid-x checkout-summary-total margin-bottom-25">
            <div class="small-6">
              Refunded
            </div>
            <div class="small-6 text-right">
              -<fmt:formatNumber type="currency" currencyCode="USD" value="${order.totalRefunded}"/>
            </div>
          </div>
        </c:if>
      </div>
    </div>
    <div class="hide-for-print">
      <c:if test="${order.refunded}">
        <p>
          Refunded <fmt:formatNumber type="currency" currencyCode="USD" value="${order.totalRefunded}"/><br />
          <fmt:formatDate pattern="MM-dd-yyyy hh:mm a" value="${order.refundedDate}" />
        </p>
      </c:if>
      <c:if test="${order.canceled}">
        <p>
          Canceled<br />
          <fmt:formatDate pattern="MM-dd-yyyy hh:mm a" value="${order.canceledDate}" />
        </p>
      </c:if>
      <c:if test="${order.shipped}">
        <p>
          Marked as shipped<br />
          <c:if test="${!empty order.shippedDate}">
            <fmt:formatDate pattern="MM-dd-yyyy hh:mm a" value="${order.shippedDate}" /><br />
          </c:if>
        </p>
      </c:if>
    </div>
  </div>
</div>