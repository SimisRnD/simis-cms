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
<jsp:useBean id="firstName" class="java.lang.String" scope="request"/>
<jsp:useBean id="lastName" class="java.lang.String" scope="request"/>
<jsp:useBean id="email" class="java.lang.String" scope="request"/>
<jsp:useBean id="createAccount" class="java.lang.String" scope="request"/>
<jsp:useBean id="subscribeToNewsletter" class="java.lang.String" scope="request"/>
<span class="help-text float-right">
  <a href="${ctx}/checkout/order-updates">Edit</a>
</span>
<p class="checkout-stage-text no-gap">Checking out as:</p>
<p class="checkout-stage-value">
  <c:out value="${firstName}"/> <c:out value="${lastName}"/>
  <c:if test="${!empty email}"><br /><c:out value="${email}"/></c:if>
</p>
<c:if test="${createAccount eq 'true'}">
  <p class="checkout-stage-subtext">A user account will be created</p>
</c:if>
<c:if test="${subscribeToNewsletter eq 'true'}">
  <p class="checkout-stage-subtext">You will be subscribed to the newsletter</p>
</c:if>
