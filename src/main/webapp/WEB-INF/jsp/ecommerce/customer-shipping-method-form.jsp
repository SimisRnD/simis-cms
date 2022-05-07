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
<jsp:useBean id="cart" class="com.simisinc.platform.domain.model.ecommerce.Cart" scope="request"/>
<jsp:useBean id="shippingRateList" class="java.util.ArrayList" scope="request"/>
<%@include file="../page_messages.jspf" %>
<c:if test="${empty shippingRateList}">
  <p>Delivery options for this address are currently unavailable.</p>
</c:if>
<%-- Form body--%>
<form method="post" onsubmit="return checkForm${widgetContext.uniqueId}()">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- The form --%>
  <div class="grid-x">
    <div class="small-12 cell">
      <fieldset>
        <c:forEach items="${shippingRateList}" var="shippingRate" varStatus="status">
          <c:set var="isShippingRate" scope="request" value="false"/>
          <c:choose>
            <c:when test="${status.first and cart.shippingRateId eq -1}"><c:set var="isShippingRate" scope="request" value="true"/></c:when>
            <c:when test="${shippingRate.id eq cart.shippingRateId}"><c:set var="isShippingRate" scope="request" value="true"/></c:when>
          </c:choose>
          <input type="radio" name="shippingRateId" id="shippingRate${status.index}" value="${shippingRate.id}"<c:if test="${isShippingRate eq 'true'}"> checked</c:if> required/>
          <label for="shippingRate${status.index}"><c:out value="${shippingRate.description}"/>
          &ndash;
          <c:choose>
            <c:when test="${shippingRate.total le 0}">
              Free
            </c:when>
            <c:otherwise>
              <fmt:formatNumber type="currency" currencyCode="USD" value="${shippingRate.total}"/>
            </c:otherwise>
          </c:choose>
          <c:if test="${!empty shippingRate.displayText}">
            <p class="checkout-stage-text" style="padding-left: 26px;">
              <c:out value="${shippingRate.displayText}" />
            </p>
          </c:if>
        </label>
          <c:if test="${!status.last}"><br/></c:if>
        </c:forEach>
      </fieldset>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 cell">
      <button class="button primary" name="button" value="save">Save &amp; Continue</button>
    </div>
  </div>
</form>
