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
<%@ taglib prefix="product" uri="/WEB-INF/tlds/product-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="productList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="productImageMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="cardClass" class="java.lang.String" scope="request"/>
<jsp:useBean id="smallCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="mediumCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="largeCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="buttonLabel" class="java.lang.String" scope="request"/>
<jsp:useBean id="buttonClass" class="java.lang.String" scope="request"/>
<div class="grid-x grid-margin-x align-center small-up-<c:out value="${smallCardCount}" /> medium-up-<c:out value="${mediumCardCount}" /> large-up-<c:out value="${largeCardCount}" />">
  <c:forEach items="${productList}" var="product">
    <div class="cell">
      <div class="card<c:if test="${!empty cardClass}"> <c:out value="${cardClass}" /></c:if>">
        <div class="card-image">
          <c:choose>
            <c:when test="${!empty productImageMap[product.uniqueId]}">
              <a href="${ctx}${product.productUrl}"><img alt="product image" src="<c:out value="${productImageMap[product.uniqueId]}"/>" /></a>
            </c:when>
            <c:when test="${!empty product.imageUrl}">
              <a href="${ctx}${product.productUrl}"><img alt="product image" src="<c:out value="${product.imageUrl}"/>" /></a>
            </c:when>
            <c:otherwise>
              <a href="${ctx}${product.productUrl}"><img alt="product image placeholder" src="https://placehold.it/500x300"></a>
            </c:otherwise>
          </c:choose>
        </div>
        <div class="card-section">
          <h3 class="product-name">
            <span class="name-value"><c:out value="${product.name}" /></span>
            <c:if test="${!empty product.caption}"><span class="caption-value"><c:out value="${product.caption}" /></span></c:if>
          </h3>
          <c:choose>
            <c:when test="${product.startingFromPrice gt 0}">
              <p class="product-info">
                From <fmt:formatNumber type="currency" currencyCode="USD" value="${product.startingFromPrice}"/>
                <c:if test="${product.skuCount gt 1}">/ ${product.skuCount} options</c:if>
              </p>
            </c:when>
            <c:when test="${product.price gt 0}">
              <p class="product-info">
                <fmt:formatNumber type="currency" currencyCode="USD" value="${product.price}"/>
                <c:if test="${product.skuCount gt 1}">/ ${product.skuCount} options</c:if>
              </p>
            </c:when>
          </c:choose>
          <%--  <a href="${ctx}${productPageUrl}${product.productUrl}"><c:out value="${product.nameWithCaption}" /></a>--%>
          <a class="<c:out value="${buttonClass}" />" href="${ctx}${product.productUrl}"><c:out value="${buttonLabel}" /></a>
        </div>
      </div>
    </div>
  </c:forEach>
</div>