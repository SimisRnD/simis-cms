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
<%-- Form body--%>
<%@include file="../page_messages.jspf" %>
<div>
  <p>The shipping address you entered could not be found by the United States Postal Service. Please verify the address to continue.</p>
</div>
<%-- The form --%>
<div class="grid-x grid-margin-x">
  <div class="small-12 cell">
    <p>
      <c:out value="${address.street}"/><br/>
      <c:if test="${!empty address.addressLine2}">
        <c:out value="${address.addressLine2}"/>
        <br/>
      </c:if>
      <c:out value="${address.city}"/>,
      <c:out value="${address.state}"/>
      <c:out value="${address.postalCode}"/>
      <c:if test="${address.country ne 'United States'}">
        <br/>
        <c:out value="${address.country}"/>
      </c:if>
    </p>
    <form method="post">
      <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
      <input type="hidden" name="token" value="${userSession.formToken}"/>
      <input type="hidden" name="firstName" value="<c:out value="${address.firstName}" />">
      <input type="hidden" name="lastName" value="<c:out value="${address.lastName}" />">
      <input type="hidden" name="street" value="<c:out value="${address.street}" />">
      <input type="hidden" name="addressLine2" value="<c:out value="${address.addressLine2}" />">
      <input type="hidden" name="city" value="<c:out value="${address.city}" />">
      <input type="hidden" name="state" value="<c:out value="${address.state}" />">
      <input type="hidden" name="postalCode" value="<c:out value="${address.postalCode}" />">
      <input type="hidden" name="country" value="<c:out value="${address.country}" />">
      <button class="button primary" name="button" value="edit">Edit this address</button>
      <button class="button secondary" name="button" value="original">Use this address</button>
    </form>
  </div>
</div>
