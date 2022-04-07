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
<jsp:useBean id="product" class="com.simisinc.platform.domain.model.ecommerce.Product" scope="request"/>
<jsp:useBean id="productSkuList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="buttonName" class="java.lang.String" scope="request"/>
<jsp:useBean id="showPrice" class="java.lang.String" scope="request"/>
<link rel="stylesheet" href="${ctx}/css/platform-ecommerce.css?v=<%= VERSION %>" />
<script type="text/javascript" src="${ctx}/javascript/jquery-format-currency-1.4.0/jquery.formatCurrency-1.4.0.min.js"></script>
<script>
  function updatePrice(price) {
    var qty = $('#quantity').find(":selected").text();
    $("#price").html(qty * price);
    $('#price').formatCurrency();
  }
  function checkInventory(sku) {

  }
</script>
<form id="form${widgetContext.uniqueId}" method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <div class="product-details-add-to-cart">
    <hr>
    <%-- Render all the options --%>
    <p>
      <c:forEach items="${attributeList}" var="attribute" varStatus="attributeStatus">
        <c:if test="${!empty attributeMap[attribute.name] && fn:length(attributeMap[attribute.name]) > 1}">
          <c:out value="${attribute.value}" />:<br />
          <c:forEach items="${attributeMap[attribute.name]}" var="skuAttribute" varStatus="status">
            <input type="radio" name="${attribute.name}" value="<c:out value="${skuAttribute}" />" id="radio${attribute.name}-${status.index}">
            <label for="radio${attribute.name}-${status.index}">
              <%-- If the first attribute, show the SKU price --%>
              <c:set var="productSkuPrice" value="0" scope="request" />
              <c:set var="productStrikePrice" value="0" scope="request" />
              <c:if test="${attributeStatus.first}">
                <c:forEach items="${productSkuList}" var="thisProduct">
                  <c:forEach items="${thisProduct.attributes}" var="thisAttribute">
                    <c:if test="${skuAttribute eq thisAttribute.value}">
                      <c:set var="productSkuPrice" value="${thisProduct.price}" scope="request" />
                      <c:set var="productStrikePrice" value="${thisProduct.strikePrice}" scope="request" />
                    </c:if>
                  </c:forEach>
                </c:forEach>
              </c:if>
              <c:out value="${skuAttribute}" /><c:if test="${productSkuPrice ne 0}">,
                <c:if test="${productStrikePrice > productSkuPrice}"><span class="text-strike"><fmt:formatNumber type="currency" currencySymbol="$" value="${productStrikePrice}" /></span></c:if>
                <span class="product-price-add-to-cart"><fmt:formatNumber type="currency" currencySymbol="$" value="${productSkuPrice}" /></span></c:if>
            </label>
          </c:forEach>
          <c:if test="${!attributeStatus.last}"><br /></c:if>
        </c:if>
      </c:forEach>
    </p>
    <div class="product-call-to-action">
      <%--<input class="quantity" name="quantity" type="number" value="1" min="1" max="1000" required>--%>
      <select class="quantity" id="quantity" name="quantity" onchange="updatePrice()">
        <option value="1">1</option>
        <option value="2">2</option>
        <option value="3">3</option>
        <option value="4">4</option>
        <option value="5">5</option>
        <option value="6">6</option>
        <option value="7">7</option>
        <option value="8">8</option>
        <option value="9">9</option>
        <option value="10">10</option>
        <option value="11">11</option>
        <option value="12">12</option>
        <option value="13">13</option>
        <option value="14">14</option>
        <option value="15">15</option>
        <option value="16">16</option>
        <option value="17">17</option>
        <option value="18">18</option>
        <option value="19">19</option>
        <option value="20">20</option>
      </select>
      <input type="submit" class="button call-to-action" value="<c:out value="${buttonName}" />"/>
    </div>
<%--
    <p>
      <c:choose>
        <c:when test="${product.isService || product.isVirtual || product.isDownload}">
          <span class="in-stock">Available</span>
        </c:when>
        <c:when test="${productSku.inventoryQty > 0}">
          <span id="inventory" class="in-stock">In Stock</span>
        </c:when>
        <c:otherwise>
          <span class="out-of-stock">Out of Stock</span>
        </c:otherwise>
      </c:choose>
      <c:if test="${product.isGood && productSku.inventoryLow > 0 && productSku.inventoryQty > 0 && productSku.inventoryQty <= productSku.inventoryLow}">
        <span class="dim-text">(Only <fmt:formatNumber value="${productSku.inventoryQty}" /> left in Stock)</span>
      </c:if>
    </p>
--%>
  </div>
</form>