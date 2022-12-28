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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/tlds/number-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="shippingRateList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="shippingMethodList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<a class="button small radius primary" href="${ctx}/admin/shipping-rate?returnPage=/admin/shipping-rates">Add a Shipping Rate <i class="fa fa-arrow-circle-right"></i></a>
<%@include file="../page_messages.jspf" %>
<%--<a class="button radius" href="${ctx}/admin/datasets/new"><i class="fa fa-cloud-upload"></i> Upload a New Dataset</a>--%>
<table class="unstriped">
  <thead>
    <tr>
      <th>Country</th>
      <th>Region</th>
      <th>Postal Code</th>
      <th>Shipping Method</th>
      <th>Shipping Fee</th>
      <th>Handling Fee</th>
      <th>Min Sub Total</th>
      <th>Min Weight</th>
      <th width="80">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${shippingRateList}" var="shippingRate">
    <tr>
      <td><c:out value="${shippingRate.countryCode}" /></td>
      <td><c:out value="${shippingRate.region}" /></td>
      <td><c:out value="${shippingRate.postalCode}" /></td>
      <td>
        <c:out value="${shippingRate.shippingCode}" />
        <c:if test="${!empty shippingRate.displayText}">
          <br />
          <small><c:out value="${shippingRate.displayText}" /></small>
        </c:if>
      </td>
      <td>
        <fmt:formatNumber type="currency" currencyCode="USD" value="${shippingRate.shippingFee}" />
      </td>
      <td>
        <fmt:formatNumber type="currency" currencyCode="USD" value="${shippingRate.handlingFee}" />
      </td>
      <td>
        <fmt:formatNumber type="currency" currencyCode="USD" value="${shippingRate.minSubTotal}" />
      </td>
      <td>
        <fmt:formatNumber value="${shippingRate.minWeightOz}" />
      </td>
      <td>
        <a href="${ctx}/admin/shipping-rate?shippingRateId=${shippingRate.id}&returnPage=/admin/shipping-rates"><i class="fa fa-edit"></i></a>
        <a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&shippingRateId=${shippingRate.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(shippingRate.shippingCode)}" />?');"><i class="fa fa-remove"></i></a>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty shippingRateList}">
      <tr>
        <td colspan="9">No shipping rates were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
<p>
<c:forEach items="${shippingMethodList}" var="shippingMethod" varStatus="status">
  <strong><c:out value="${shippingMethod.code}" /></strong><c:if test="${!empty shippingMethod.boxzookaCode}"> (<c:out value="${shippingMethod.boxzookaCode}" />)</c:if><c:if test="${!status.last}">;</c:if>
</c:forEach>
</p>
