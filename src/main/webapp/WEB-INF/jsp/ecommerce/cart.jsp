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
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<%@ taglib prefix="product" uri="/WEB-INF/product-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="cart" class="com.simisinc.platform.domain.model.ecommerce.Cart" scope="request"/>
<jsp:useBean id="cartEntryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="priceChangeList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="noLongerAvailableList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="cartRuleNotMetList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="card1" class="java.lang.String" scope="request"/>
<jsp:useBean id="card2" class="java.lang.String" scope="request"/>
<jsp:useBean id="pricingRule" class="com.simisinc.platform.domain.model.ecommerce.PricingRule" scope="request"/>
<jsp:useBean id="preventCheckout" class="java.lang.String" scope="request"/>
<link rel="stylesheet" href="${ctx}/css/platform-ecommerce.css?v=<%= VERSION %>"/>
<script type="text/javascript" src="${ctx}/javascript/jquery-formatcurrency-1.6.3/jquery.formatCurrency.min.js"></script>
<script>
  var itemIdList = [<c:forEach items="${cartEntryList}" var="cartEntry" varStatus="status">${cartEntry.cartItem.id}<c:if test="${!status.last}">, </c:if></c:forEach>];

  function updatePrice(itemId, price) {
    var qty = $('#item-' + itemId + '-quantity').find(":selected").text();
    $('#item-' + itemId + '-total').html(qty * price);
    $('#item-' + itemId + '-total').formatCurrency();
    $('#cart-subtotal').html('Update cart for new subtotal');
    $('#update-button').show();
  }

  function removeItem${widgetContext.uniqueId}(itemId) {
    window.location.href = '${widgetContext.uri}?action=removeItem&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&itemId=' + itemId;
  }

  function showPromoCodeEntry(e) {
    $('#promoCodeEntry').show();
    $('#promoCodeInput').focus();
  }
</script>
<c:if test="${!empty title}">
  <div class="checkout-title">
    <c:if test="${!empty title}">
      <h1><c:out value="${title}"/></h1>
    </c:if>
      <%--    <h5 id="cart-item-count"><fmt:formatNumber value="${cart.totalItems}" /> Item<c:if test="${cart.totalItems gt 1}">s</c:if></h5>--%>
  </div>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${!empty noLongerAvailableList}">
  <c:forEach items="${noLongerAvailableList}" var="cartItem">
    <div class="callout warning">
      Please note that <strong><c:out value="${cartItem.productName}"/></strong> is no longer available
      since you placed it in your shopping cart, and has been removed.
    </div>
  </c:forEach>
</c:if>
<c:if test="${!empty cartRuleNotMetList}">
  <c:forEach items="${cartRuleNotMetList}" var="cartEntry">
    <div class="callout warning">
      Please see the note about <strong><c:out value="${cartEntry.cartItem.productName}"/></strong>: <c:out value="${cartEntry.errorMessage}" />
    </div>
  </c:forEach>
</c:if>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form body--%>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-8 cell checkout-items">
      <hr/>
      <div>
        <c:if test="${!empty priceChangeList}">
          <c:forEach items="${priceChangeList}" var="cartEntry">
            <c:set var="cartItem" scope="request" value="${cartEntry.cartItem}"/>
            <c:set var="product" scope="request" value="${cartEntry.product}"/>
            <c:set var="productSku" scope="request" value="${cartEntry.productSku}"/>
            <div class="callout warning">
              Please note that the price of <strong><c:out value="${product.nameWithCaption}"/></strong>
              <c:choose>
                <c:when test="${productSku.price gt cartItem.eachAmount}">
                  has <strong>increased</strong> from
                </c:when>
                <c:when test="${productSku.price lt cartItem.eachAmount}">
                  has <strong>decreased</strong> from
                </c:when>
                <c:otherwise>
                  has changed from
                </c:otherwise>
              </c:choose>
              <fmt:formatNumber type="currency" currencyCode="USD" value="${cartItem.eachAmount}"/>
              to <fmt:formatNumber type="currency" currencyCode="USD" value="${productSku.price}"/>
              since you placed it in your shopping cart.
              Items in your shopping cart will always reflect the most recent price displayed on their product detail pages.
            </div>
          </c:forEach>
        </c:if>
        <c:forEach items="${cartEntryList}" var="cartEntry" varStatus="cartEntryStatus">
          <c:set var="cartItem" scope="request" value="${cartEntry.cartItem}"/>
          <c:set var="product" scope="request" value="${cartEntry.product}"/>
          <c:set var="productSku" scope="request" value="${cartEntry.productSku}"/>
          <fmt:parseNumber var="thisQuantity" type="number" value="${cartItem.quantity}"/>
          <div class="checkout-summary-item">
            <div class="grid-x grid-margin-x">
              <div class="small-5 medium-2 cell">
                <c:if test="${!empty product.imageUrl}"><img src="<c:out value="${product.imageUrl}"/>"/></c:if>
              </div>
              <div class="small-7 medium-5 cell">
                <div id="item-${cartEntryStatus.index}-name">
                  <p class="no-gap">
                    <c:choose>
                      <c:when test="${!empty product.productUrl}">
                        <a class="item-name" href="${product.productUrl}"><c:out value="${product.nameWithCaption}"/></a>
                      </c:when>
                      <c:otherwise>
                        <a class="item-name" href="${product.uniqueId}"><c:out value="${product.nameWithCaption}"/></a>
                      </c:otherwise>
                    </c:choose>
                  </p>
                  <c:forEach items="${productSku.nativeAttributes}" var="thisAttribute" varStatus="attributeStatus">
                    <c:if test="${!empty thisAttribute.value}">
                      <p class="item-option no-gap"><c:out value="${thisAttribute.value}"/></p>
                    </c:if>
                  </c:forEach>
                  <c:if test="${cartEntry.status eq '400'}">
                    <p><strong>This item will ship when it becomes available</strong></p>
                  </c:if>
                  <c:if test="${!empty product.excludeUsStates}">
                    <p><small>Some state shipping restrictions apply</small></p>
                  </c:if>
                  <p>
                    <small><a href="javascript:removeItem${widgetContext.uniqueId}(${cartItem.id})">remove</a></small>
                  </p>
                </div>
              </div>
              <div class="small-5 medium-3 cell">
                <div class="item-quantity">
                  <p class="title">
                    <select class="quantity" id="item-${cartItem.id}-quantity" name="item-${cartItem.id}-quantity" onchange="updatePrice(${cartItem.id},${productSku.price})">
                      <c:forEach var="i" begin="1" end="20">
                        <option value="${i}"<c:if test="${thisQuantity eq i}"> selected</c:if>>${i}</option>
                      </c:forEach>
                    </select>
                    <c:if test="${cartItem.quantityFree gt 0}">
                      <em>You get <fmt:formatNumber value="${cartItem.quantityFree}" /> free</em>
                    </c:if>
                  </p>
                </div>
              </div>
              <div class="small-5 medium-2 cell text-right">
                <p class="item-price"><fmt:formatNumber type="currency" currencyCode="USD" value="${productSku.price}"/></p>
              </div>
                <%--
                <div class="small-2 show-for-medium cell">
                  <div class="item-total text-right">
                    <p id="item-${cartItem.id}-total">
                      <fmt:formatNumber type="currency" currencyCode="USD" value="${thisQuantity * productSku.price}"/>
                    </p>
                  </div>
                </div>
                --%>
            </div>
          </div>
          <hr/>
        </c:forEach>
        <%-- Content Items --%>
        <c:if test="${!empty card1 || !empty card2}">
          <div class="grid-x grid-margin-x">
            <c:if test="${!empty card1}">
              <div class="small-6 cell cart-content-1">
                <div class="platform-content">${card1}</div>
              </div>
            </c:if>
            <c:if test="${!empty card2}">
              <div class="small-6 cell cart-content-2">
                <div class="platform-content">${card2}</div>
              </div>
            </c:if>
          </div>
        </c:if>
      </div>
    </div>
    <%-- Summary Column --%>
    <div class="small-12 medium-4 cell callout box checkout-summary-callout margin-bottom-50">
      <div class="checkout-summary-title">
        <h3>Summary</h3>
        <hr/>
        <div class="checkout-summary-details">
          <div class="grid-x">
            <div class="small-6">
              <small>Subtotal</small>
            </div>
            <div class="small-6 text-right">
              <small><fmt:formatNumber type="currency" currencyCode="USD" value="${runningTotal}"/></small>
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
          <%-- Shipping --%>
          <div class="grid-x">
            <div class="small-6">
              <small>Shipping</small>
            </div>
            <div class="small-6 text-right">
              <small>TBD</small>
            </div>
          </div>
          <div class="grid-x">
            <div class="small-6">
              <small>Sales Tax</small>
            </div>
            <div class="small-6 text-right">
              <small>TBD</small>
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
          <%-- Estimated Total --%>
          <hr/>
          <div class="grid-x checkout-summary-total margin-bottom-25">
            <div class="small-6">
              Estimated Total
            </div>
            <div class="small-6 text-right">
              <fmt:formatNumber type="currency" currencyCode="USD" value="${grandTotal}"/>
              <c:if test="${!empty userSession.geoIP && userSession.geoIP.country ne 'United States'}">
                <br /><span class="subheader currency"><small>USD</small></span>
              </c:if>
            </div>
          </div>
          <%-- Promo Code --%>
          <hr/>
          <div>
            <div class="float-right">
              <a class="accordion-button" href="javascript:showPromoCodeEntry()"><i class="${font:fal()} fa-plus-circle"></i></a>
            </div>
            <a class="accordion-button" href="javascript:showPromoCodeEntry()">Promo Code</a>
          </div>
          <div id="promoCodeEntry" style="display:none" class="margin-top-10">
            <div class="grid-x">
              <div class="small-7">
                <input id="promoCodeInput" type="text" name="promoCode" value="<c:out value="${cart.promoCode}" />" placeholder="Promo Code" class="no-gap" />
              </div>
              <div class="small-5">
                <button class="button primary expanded no-gap">Apply</button>
              </div>
            </div>
          </div>
          <c:if test="${!empty cart.promoCode}">
            <p>
            <c:choose>
              <c:when test="${!empty pricingRule.name}">
                <strong><c:out value="${pricingRule.promoCode}" />:</strong>
                <c:out value="${pricingRule.name}" />
              </c:when>
              <c:otherwise>
                <strong>Invalid promo code</strong>
              </c:otherwise>
            </c:choose>
            </p>
          </c:if>
          <hr/>
          <%-- Gift Card --%>
          <%--          <hr/>--%>
          <%--          <div class="float-right">--%>
          <%--            <button class="accordion-button"><i class="${font:fal()} fa-plus-circle"></i></button>--%>
          <%--          </div>--%>
          <%--          <button class="accordion-button">Gift Card</button>--%>
        </div>
        <div class="text-center">
          <span id="cart-subtotal"></span>
        </div>
        <c:if test="${!empty cartEntryList}">
          <div class="cart-buttons text-center">
            <c:choose>
              <c:when test="${!empty priceChangeList || !empty noLongerAvailableList}">
                <button id="update-button" class="button secondary expanded">Update Cart</button>
              </c:when>
              <c:otherwise>
                <button id="update-button" style="display:none" class="button secondary expanded">Update Cart</button>
              </c:otherwise>
            </c:choose>
            <button class="button primary expanded" name="button" value="checkout"<c:if test="${preventCheckout eq 'true'}"> disabled="true"</c:if>>Check Out</button>
<%--            <p>&mdash; or check out with &mdash;</p>--%>
<%--            <button class="button primary expanded" style="padding:0;background-color:#000000" name="button" value="checkout"><i class="fab fa-3x fa-apple-pay"></i></button>--%>
<%--            <button class="button primary expanded" style="padding:8px;background-color:#009CDE;font-size:larger;text-transform:none" name="button" value="checkout"><i class="fab fa-paypal"></i> PayPal</button>--%>
          </div>
          <div class="cart-disclaimer">
            <p>
              By checking out, I agree to the
              <a href="${ctx}/legal/terms">Terms of Use</a>
              and acknowledge that I have read the
              <a href="${ctx}/legal/privacy">Privacy Policy</a>.
              Shipping is calculated at checkout.
            </p>
          </div>
        </c:if>
      </div>
    </div>
  </div>
</form>
