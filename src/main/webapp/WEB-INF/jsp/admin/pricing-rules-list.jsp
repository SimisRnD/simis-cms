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
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/tlds/number-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="pricingRuleList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<a class="button small radius primary" href="${ctx}/admin/pricing-rule?returnPage=/admin/pricing-rules">Add a Pricing Rule <i class="fa fa-arrow-circle-right"></i></a>
<%@include file="../page_messages.jspf" %>
<%--<a class="button radius" href="${ctx}/admin/datasets/new"><i class="fa fa-cloud-upload"></i> Upload a New Dataset</a>--%>
<table class="unstriped">
  <thead>
    <tr>
      <th width="50">Status</th>
      <th width="500">Display Name</th>
      <th width="150">Date Range</th>
      <th width="240">Details</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${pricingRuleList}" var="pricingRule">
    <tr>
      <td nowrap>
        <c:choose>
          <c:when test="${!pricingRule.enabled}">
            <span class="label alert">off</span>
          </c:when>
          <c:when test="${date:isAfterNow(pricingRule.fromDate)}">
            <span class="label success">coming soon</span>
          </c:when>
          <c:when test="${date:isBeforeNow(pricingRule.toDate)}">
            <span class="label warning">expired</span>
          </c:when>
          <c:otherwise>
            <span class="label success">active</span>
          </c:otherwise>
        </c:choose>
      </td>
      <td>
        <a href="${ctx}/admin/pricing-rule?pricingRuleId=${pricingRule.id}&returnPage=/admin/pricing-rules"><c:out value="${pricingRule.name}" /></a>
<%--        <a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&pricingRuleId=${pricingRule.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(pricingRule.promoCode)}" />?');"><i class="fa fa-remove"></i></a>--%>
        <c:if test="${!empty pricingRule.promoCode}">
          <br /><span class="subheader">Promo code: <c:out value="${pricingRule.promoCode}" /></span>
        </c:if>
        <c:if test="${!empty pricingRule.description}">
          <br /><small><c:out value="${pricingRule.description}" /></small>
        </c:if>

      </td>
      <td nowrap>
        <fmt:formatDate pattern="yyyy-MM-dd" value="${pricingRule.fromDate}" /> -<br />
        <fmt:formatDate pattern="yyyy-MM-dd" value="${pricingRule.toDate}" />
      </td>
      <td nowrap>
        <c:if test="${pricingRule.buyXItems gt 0}">Buy ${pricingRule.buyXItems}, Get ${pricingRule.getYItemsFree} free<br /></c:if>
        <c:if test="${pricingRule.subtotalPercent gt 0}">${pricingRule.subtotalPercent}% off<br /></c:if>
        <c:if test="${pricingRule.subtractAmount gt 0}">Amount off: <fmt:formatNumber type="currency" currencyCode="USD" value="${pricingRule.subtractAmount}"/><br /></c:if>
        <c:if test="${pricingRule.minimumOrderQuantity gt 0}">Min Qty: ${pricingRule.minimumOrderQuantity}<br /></c:if>
        <c:if test="${pricingRule.minimumSubtotal gt 0}">Min Subtotal: <fmt:formatNumber type="currency" currencyCode="USD" value="${pricingRule.minimumSubtotal}"/><br /></c:if>
        <c:if test="${pricingRule.itemLimit gt 0}">Item Limit: ${pricingRule.itemLimit}<br /></c:if>
        <c:if test="${!empty pricingRule.validSkus}"><c:out value="${pricingRule.validSkus}" /><br /></c:if>
        <c:if test="${pricingRule.freeShipping}">Free Shipping<br /></c:if>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty pricingRuleList}">
      <tr>
        <td colspan="4">No pricing rules were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
