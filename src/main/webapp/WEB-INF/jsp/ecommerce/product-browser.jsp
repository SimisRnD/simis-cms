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
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<%@ taglib prefix="product" uri="/WEB-INF/tlds/product-functions.tld" %>
<%@ taglib prefix="image" uri="/WEB-INF/tlds/image-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="productList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="productImageMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="cardClass" class="java.lang.String" scope="request"/>
<jsp:useBean id="cardImageClass" class="java.lang.String" scope="request"/>
<jsp:useBean id="smallCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="mediumCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="largeCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="buttonLabel" class="java.lang.String" scope="request"/>
<jsp:useBean id="buttonClass" class="java.lang.String" scope="request"/>
<div class="grid-x grid-margin-x align-center small-up-<c:out value="${smallCardCount}" /> medium-up-<c:out value="${mediumCardCount}" /> large-up-<c:out value="${largeCardCount}" />">
  <c:forEach items="${productList}" var="product" varStatus="status">
    <div class="cell">
      <div class="card<c:if test="${!empty cardClass}"> <c:out value="${cardClass}" /></c:if>">
        <div class="card-image<c:if test="${!empty cardImageClass}"> <c:out value="${cardImageClass}" /></c:if>">
          <c:choose>
            <c:when test="${!empty productImageMap[product.uniqueId]}">
              <c:set var="productImageSrcset" value="${image:srcset(productImageMap[product.uniqueId])}"/>
              <a href="${ctx}<c:out value="${product.productUrl}"/>"><img alt="product image" src="<c:out value="${productImageMap[product.uniqueId]}"/>"
                <c:if test="${not empty productImageSrcset}"> srcset="<c:out value="${productImageSrcset}"/>" sizes="(min-width: 1024px) 25vw, (min-width: 640px) 33vw, 50vw"</c:if>
                decoding="async" loading="lazy" /></a>
            </c:when>
            <c:when test="${!empty product.imageUrl && !empty cardImageClass}">
              <%-- cardImageClass forces a fixed-aspect CSS crop (object-fit: cover) -- request the
                   focal-point-aware square variant directly and skip the width-breakpoint srcset
                   entirely (issue #411 PR3). A srcset's candidates must all be the same crop, only
                   the resolution may vary; mixing in a differently-cropped square variant would let
                   the browser silently shift what's visible depending on which candidate a given
                   viewport resolves to. StreamImageWidget falls back to the original when no square
                   variant exists yet, so this degrades gracefully for any image without one. --%>
              <a href="${ctx}<c:out value="${product.productUrl}"/>"><img alt="product image" src="<c:out value="${product.imageUrl}"/>?variant=square"
                decoding="async" loading="lazy" /></a>
            </c:when>
            <c:when test="${!empty product.imageUrl}">
              <c:set var="productImageSrcset" value="${image:srcsetBatch(product.imageUrl, imageVariantsByImageId, imageWidthsByImageId)}"/>
              <a href="${ctx}<c:out value="${product.productUrl}"/>"><img alt="product image" src="<c:out value="${product.imageUrl}"/>"
                <c:if test="${not empty productImageSrcset}"> srcset="<c:out value="${productImageSrcset}"/>" sizes="(min-width: 1024px) 25vw, (min-width: 640px) 33vw, 50vw"</c:if>
                decoding="async" loading="lazy" /></a>
            </c:when>
            <c:otherwise>
              <a href="${ctx}<c:out value="${product.productUrl}"/>"><img alt="product image placeholder" src="https://placehold.it/500x300" loading="lazy" decoding="async"></a>
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
          <a class="<c:out value="${buttonClass}" />" href="${ctx}<c:out value="${product.productUrl}"/>"><c:out value="${buttonLabel}" /></a>
        </div>
      </div>
    </div>
  </c:forEach>
</div>