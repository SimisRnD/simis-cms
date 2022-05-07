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
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<%@ taglib prefix="product" uri="/WEB-INF/product-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="card" class="com.simisinc.platform.domain.model.ecommerce.Card" scope="request"/>
<span class="help-text float-right">
  <a href="${ctx}/checkout/payment-method">Edit</a>
</span>
<p class="checkout-stage-value no-gap">
  <c:choose>
    <c:when test="${!empty card.brand}">
      <c:out value="${card.brand}"/>
    </c:when>
    <c:otherwise>
      Please update your payment method.
    </c:otherwise>
  </c:choose>
</p>
<c:if test="${!empty card.last4}">
  <p class="checkout-stage-subtext">ending in <c:out value="${card.last4}"/></p>
</c:if>