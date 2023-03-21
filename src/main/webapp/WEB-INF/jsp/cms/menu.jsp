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
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="pagePath" class="java.lang.String" scope="request"/>
<jsp:useBean id="linkList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="useHighlight" class="java.lang.String" scope="request"/>
<jsp:useBean id="wrap" class="java.lang.String" scope="request"/>
<jsp:useBean id="cartItemList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="cartEntryList" class="java.util.ArrayList" scope="request"/>
<div class="platform-menu-container" style="position: relative;">
<c:if test="${!empty title}">
  <span class="menu-title padding-left-15"><c:out value="${title}" /></span>
</c:if>
<%-- not ready
<style>
    #menu-item-cart {
        padding-right: 1rem;
    }
    #menu-item-cart::after {
        content: unset;
    }
    #menu-item-cart-panel {
        max-width: 90vw;
        width: 33rem;
        background-color: white;
    }
</style>
--%>
<c:if test="${showEditor eq 'true' && !empty uniqueId}">
  <c:choose>
    <c:when test="${!empty linkList}">
      <div class="platform-toc-editor"><a class="hollow button small secondary" href="${ctx}/table-of-contents-editor?uniqueId=${uniqueId}&returnPage=${returnPage}"><i class="${font:fas()} fa-edit"></i></a></div>
    </c:when>
    <c:otherwise>
      <a class="button tiny expanded radius secondary" href="${ctx}/table-of-contents-editor?uniqueId=${uniqueId}&returnPage=${returnPage}"><i class="${font:fas()} fa-edit"></i> Add Links</a>
    </c:otherwise>
  </c:choose>
</c:if>
<ul <c:if test="${!empty menuId}"> id="${menuId}" </c:if>class="dropdown menu<c:if test="${!empty menuClass}"> <c:out value="${menuClass}" /></c:if>" data-dropdown-menu>
<c:set var="currentContainer" scope="request" value="---"/>
<c:set var="linkOpen" scope="request" value="false"/>
<c:forEach items="${linkList}" var="link" varStatus="status">
  <li<c:if test="${useHighlight eq 'true' && link['link'] eq pagePath}"> class="active"</c:if>>
  <c:choose>
    <c:when test="${!empty link['type'] && link['type'] eq 'cart'}">
      <c:choose>
        <c:when test="${empty userSession.cart || userSession.cart.totalItems eq 0}">
          <a id="menu-item-cart" href="${ctx}/cart" title="Your Shopping Cart is Empty"><i class="${font:fal()} fa-bag-shopping"></i></a>
        </c:when>
        <c:otherwise>
          <a id="menu-item-cart" href="${ctx}/cart" title="Your Shopping Cart"><i class="${font:fal()} fa-fw fa-bag-shopping" aria-describedby="cartCount"></i><span class="platform-layered-badge badge" id="cartCount"><fmt:formatNumber value="${userSession.cart.totalQty}" /></span></a>
        </c:otherwise>
      </c:choose>
      <%-- not ready
      <c:if test="${!empty cartItemList}">
        <ul class="menu vertical padding-0 margin-0">
          <li>
            <div id="menu-item-cart-panel" class="callout box no-gap">
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
                        <p class="no-gap"><a href="${product.productUrl}"><c:out value="${product.nameWithCaption}"/></a></p>
                        <c:forEach items="${productSku.nativeAttributes}" var="thisAttribute" varStatus="attributeStatus">
                          <c:if test="${!empty thisAttribute.value}">
                            <p class="item-option no-gap"><c:out value="${thisAttribute.value}"/></p>
                          </c:if>
                        </c:forEach>
                        <c:if test="${cartEntry.status eq '400'}">
                          <p><strong>This item will ship when it becomes available</strong></p>
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

                      <!-- @todo an addition-->
                      <div class="small-2 show-for-medium cell">
                        <div class="item-total text-right">
                          <p id="item-${cartItem.id}-total">
                            <fmt:formatNumber type="currency" currencyCode="USD" value="${thisQuantity * productSku.price}"/>
                          </p>
                        </div>
                      </div>

                  </div>
                </div>
              </c:forEach>
              <a href="${ctx}/cart">View your cart</a>
            </div>
          </li>
        </ul>
      </c:if>
--%>
    </c:when>
    <c:when test="${!empty link['divider'] && link['divider'] eq 'true'}">
      <hr style="margin: .01rem auto;" />
    </c:when>
    <c:when test="${!empty link['icon'] && link['icon-only'] eq 'true'}">
      <a title="<c:out value="${link['name']}"/>" href="${ctx}${link['link']}"<c:if test="${fn:startsWith(link['link'], 'http://') || fn:startsWith(link['link'], 'https://')}"> target="_blank"</c:if>><i class="fa-fw <c:out value="${link['icon']}" />"></i></a>
    </c:when>
    <c:otherwise>
      <a class="<c:if test="${wrap eq 'true' }">text-no-wrap</c:if><c:if test="${!empty link['class']}"> <c:out value="${link['class']}" /></c:if>" href="${ctx}${link['link']}"<c:if test="${fn:startsWith(link['link'], 'http://') || fn:startsWith(link['link'], 'https://')}"> target="_blank"</c:if>><c:if test="${!empty link['icon']}"><i class="fa-fw <c:out value="${link['icon']}" />"></i> </c:if><c:out value="${link['name']}"/></a>
    </c:otherwise>
  </c:choose>
  <c:if test="${linkOpen eq 'true' && (empty link['container'] || link['container'] ne currentContainer)}">
    </ul>
    <c:set var="linkOpen" scope="request" value="false"/>
  </c:if>
  <c:if test="${!empty link['container'] && link['container'] ne currentContainer}">
    <ul class="menu vertical">
    <c:set var="linkOpen" scope="request" value="true"/>
  </c:if>
  <c:if test="${linkOpen eq 'true' && status.last}">
    </ul>
  </c:if>
  </li>
  <c:set var="currentContainer" scope="request" value="${link['container']}"/>
</c:forEach>
</ul>
</div>
