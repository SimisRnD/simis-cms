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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="address" class="com.simisinc.platform.domain.model.ecommerce.Address" scope="request"/>
<span class="help-text float-right">
  <a href="${ctx}/checkout">Edit</a>
</span>
<p class="checkout-stage-text">
  <c:out value="${address.firstName}" /> <c:out value="${address.lastName}" /><br />
  <c:out value="${address.street}" /><br />
  <c:if test="${!empty address.addressLine2}">
    <c:out value="${address.addressLine2}" /><br />
  </c:if>
  <c:out value="${address.city}" />,
  <c:out value="${address.state}" />
  <c:out value="${address.postalCode}" />
  <c:if test="${address.country ne 'United States'}">
    <br />
    <c:out value="${address.country}"/>
  </c:if>
</p>
