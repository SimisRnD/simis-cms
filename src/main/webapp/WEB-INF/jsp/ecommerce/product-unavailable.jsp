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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="product" class="com.simisinc.platform.domain.model.ecommerce.Product" scope="request"/>
<jsp:useBean id="productSku" class="com.simisinc.platform.domain.model.ecommerce.ProductSku" scope="request"/>
<jsp:useBean id="unavailableText" class="java.lang.String" scope="request"/>
<jsp:useBean id="showPrice" class="java.lang.String" scope="request"/>
<link rel="stylesheet" href="${ctx}/css/platform-ecommerce.css?v=<%= VERSION %>" />
<%-- Required by controller --%>
<input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
<input type="hidden" name="token" value="${userSession.formToken}"/>
<%-- Title and Message block --%>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<%-- Form Content --%>
<c:choose>
  <c:when test="${showPrice eq 'true' && !empty productSku.price && productSku.price gt 0}">
    <input type="submit" class="button expanded secondary" value="<c:out value="${unavailableText}" /> &bull; <fmt:formatNumber type="currency" currencyCode="USD" value="${productSku.price}" />"/>
  </c:when>
  <c:otherwise>
    <input type="submit" class="button expanded secondary" value="<c:out value="${unavailableText}" />"/>
  </c:otherwise>
</c:choose>