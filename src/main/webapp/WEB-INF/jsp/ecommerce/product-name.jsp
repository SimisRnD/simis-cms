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
<jsp:useBean id="product" class="com.simisinc.platform.domain.model.ecommerce.Product" scope="request"/>
<jsp:useBean id="showPrice" class="java.lang.String" scope="request"/>
<jsp:useBean id="combineCaption" class="java.lang.String" scope="request"/>
<c:choose>
  <c:when test="${!empty product.caption && combineCaption eq 'true'}">
    <c:choose>
      <c:when test="${showPrice eq 'true'}">
        <h1 style="margin-bottom: 0; line-height: 1.2"><c:out value="${product.name}"/> <c:out value="${product.caption}" /></h1>
      </c:when>
      <c:otherwise>
        <h1><c:out value="${product.name}"/> <c:out value="${product.caption}" /></h1>
      </c:otherwise>
    </c:choose>
  </c:when>
  <c:when test="${!empty product.caption || showPrice eq 'true'}">
    <h1 style="margin-bottom: 0; line-height: 1.2"><c:out value="${product.name}"/></h1>
  </c:when>
  <c:otherwise>
    <h1><c:out value="${product.name}"/></h1>
  </c:otherwise>
</c:choose>
<c:if test="${!empty product.caption && combineCaption ne 'true'}">
  <c:choose>
    <c:when test="${showPrice eq 'true'}">
      <h2 style="margin-bottom: 0; line-height: 1.2"><c:out value="${product.caption}"/></h2>
    </c:when>
    <c:otherwise>
      <h2><c:out value="${product.caption}"/></h2>
    </c:otherwise>
  </c:choose>
</c:if>
<c:if test="${showPrice eq 'true'}">
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
</c:if>
