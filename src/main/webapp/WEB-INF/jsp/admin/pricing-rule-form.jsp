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
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="pricingRule" class="com.simisinc.platform.domain.model.ecommerce.PricingRule" scope="request"/>
<c:choose>
  <c:when test="${pricingRule.id eq -1}"><h4>New Pricing Rule</h4></c:when>
  <c:otherwise>
    <h4>Update Pricing Rule</h4>
  </c:otherwise>
</c:choose>
<form method="post" autocomplete="off">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${pricingRule.id}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <div class="grid-x grid-margin-x">

    <%--
    <div class="small-12 medium-6 large-4 cell">
      <label>Country <span class="required">*</span>
        <select id="countryCode" name="countryCode">
          <option value=""></option>
          <option value="*"<c:if test="${shippingRate.countryCode eq '*'}"> selected</c:if>>*</option>
          <c:forEach items="${shippingCountryList}" var="shippingCountry">
            <option value="${shippingCountry.code}"<c:if test="${shippingRate.countryCode eq shippingCountry.code}"> selected</c:if>><c:out value="${shippingCountry.title}" /> (<c:out value="${shippingCountry.code}" />)</option>
          </c:forEach>
        </select>
      </label>
    </div>
    --%>
    <div class="small-12 cell">

      <label>Enabled
        <div class="switch large">
          <input class="switch-input" id="enabled-yes-no" type="checkbox" name="enabled" value="true"<c:if test="${pricingRule.enabled}"> checked</c:if>>
          <label class="switch-paddle" for="enabled-yes-no">
            <span class="switch-active" aria-hidden="true">Yes</span>
            <span class="switch-inactive" aria-hidden="true">No</span>
          </label>
        </div>
      </label>

      <div class="grid-x grid-margin-x">
        <div class="small-12 medium-8 large-7 cell">
          <label>Display Name During Checkout <span class="required">*</span>
            <input type="text" placeholder="Name" name="name" value="<c:out value="${pricingRule.name}"/>" maxlength="255" required>
          </label>
        </div>
        <div class="small-12 medium-3 large-4 cell">
          <label>Promo Code
            <input type="text" placeholder="Promo Code" name="promoCode" value="<c:out value="${pricingRule.promoCode}"/>" maxlength="20">
          </label>
        </div>
      </div>

      <label>Description
        <input type="text" placeholder="Description" name="description" value="<c:out value="${pricingRule.description}"/>">
      </label>

      <label>Valid SKUs
        <input type="text" placeholder="SKUs" name="validSkus" value="<c:out value="${pricingRule.validSkus}"/>">
      </label>

      <div class="grid-x grid-margin-x">
        <div class="small-12 medium-6 large-4 cell">
          <label>Percent Off Subtotal
            <div class="input-group">
              <input class="input-group-field" type="text" name="subtotalPercent" value="<c:if test="${pricingRule.subtotalPercent gt 0.0}"><c:out value="${pricingRule.subtotalPercent}"/></c:if>">
              <span class="input-group-label"><i class="fa fa-percentage"></i></span>
            </div>
          </label>
        </div>

        <div class="small-12 medium-6 large-4 cell">
          <label>Amount To Subtract From Total
            <div class="input-group">
              <span class="input-group-label"><i class="fa fa-dollar"></i></span>
              <input class="input-group-field" type="text" name="subtractAmount" value="<c:if test="${pricingRule.subtractAmount gt 0.0}"><fmt:formatNumber type="currency" currencySymbol="" value="${pricingRule.subtractAmount}" /></c:if>">
            </div>
          </label>
        </div>

        <div class="small-12 medium-6 large-4 cell">
          <label>Allow free shipping
            <div class="switch large">
              <input class="switch-input" id="freeShipping-yes-no" type="checkbox" name="freeShipping" value="true"<c:if test="${pricingRule.freeShipping}"> checked</c:if>>
              <label class="switch-paddle" for="freeShipping-yes-no">
                <span class="switch-active" aria-hidden="true">Yes</span>
                <span class="switch-inactive" aria-hidden="true">No</span>
              </label>
            </div>
          </label>
        </div>
      </div>

      <div class="grid-x grid-margin-x">
        <div class="small-12 medium-6 large-4 cell">
          <label>Buy X items
            <input type="text" placeholder="" name="buyXItems" value="<c:if test="${pricingRule.buyXItems gt 0}"><c:out value="${pricingRule.buyXItems}"/></c:if>">
          </label>
        </div>
        <div class="small-12 medium-6 large-4 cell">
          <label>Get Y free
            <input type="text" placeholder="" name="getYItemsFree" value="<c:if test="${pricingRule.getYItemsFree gt 0}"><c:out value="${pricingRule.getYItemsFree}"/></c:if>">
          </label>
        </div>
      </div>

      <div class="grid-x grid-margin-x">
        <div class="small-12 medium-6 large-4 cell">
          <label>Item limit
            <input type="text" placeholder="" name="itemLimit" value="<c:if test="${pricingRule.itemLimit gt 0}"><c:out value="${pricingRule.itemLimit}"/></c:if>">
          </label>
        </div>
      </div>

      <div class="grid-x grid-margin-x">
        <div class="small-12 medium-6 large-4 cell">
          <label>Minimum Subtotal
            <div class="input-group">
              <span class="input-group-label"><i class="fa fa-dollar"></i></span>
              <input class="input-group-field" type="text" name="minimumSubtotal" value="<c:if test="${pricingRule.minimumSubtotal gt 0}"><fmt:formatNumber type="currency" currencySymbol="" value="${pricingRule.minimumSubtotal}" /></c:if>">
            </div>
          </label>
        </div>

        <div class="small-12 medium-6 large-4 cell">
          <label>Minimum Order Quantity
            <div class="input-group">
              <span class="input-group-label"><i class="fa fa-greater-than-equal"></i></span>
              <input class="input-group-field" type="text" name="minimumOrderQuantity" value="<c:if test="${pricingRule.minimumOrderQuantity gt 0}"><c:out value="${pricingRule.minimumOrderQuantity}"/></c:if>">
            </div>
          </label>
        </div>
      </div>

      <div class="grid-x grid-margin-x">
        <div class="small-12 medium-6 large-4 cell">
          <label>Uses per code
            <input type="text" placeholder="" name="usesPerCode" value="<c:if test="${pricingRule.usesPerCode gt 0}"><c:out value="${pricingRule.usesPerCode}"/></c:if>">
          </label>
        </div>
        <div class="small-12 medium-6 large-4 cell">
          <label>Uses per customer
            <input type="text" placeholder="" name="usesPerCustomer" value="<c:if test="${pricingRule.usesPerCustomer gt 0}"><c:out value="${pricingRule.usesPerCustomer}"/></c:if>">
          </label>
        </div>
      </div>

      <div class="grid-x grid-margin-x">
        <div class="small-12 medium-6 large-4 cell">
          <label>Start Date/Time
            <div class="input-group">
              <span class="input-group-label"><i class="fa fa-calendar"></i></span>
              <input class="input-group-field" type="text" placeholder="" id="fromDate" name="fromDate" value="<fmt:formatDate pattern="MM-dd-yyyy HH:mm" value="${pricingRule.fromDate}" />">
            </div>
          </label>
          <script>
            $(function () {
              $('#fromDate').fdatepicker({
                format: 'mm-dd-yyyy hh:ii',
                disableDblClickSelection: true,
                pickTime: true
              });
            });
          </script>
        </div>
        <div class="small-12 medium-6 large-4 cell">
          <label>End Date/Time
            <div class="input-group">
              <span class="input-group-label"><i class="fa fa-calendar"></i></span>
              <input class="input-group-field" type="text" placeholder="" id="toDate" name="toDate" value="<fmt:formatDate pattern="MM-dd-yyyy HH:mm" value="${pricingRule.toDate}" />">
            </div>
          </label>
          <script>
            $(function () {
              $('#toDate').fdatepicker({
                format: 'mm-dd-yyyy hh:ii',
                disableDblClickSelection: true,
                pickTime: true
              });
            });
          </script>
        </div>
      </div>
    </div>
  </div>


  <div class="grid-x grid-margin-x">
    <div class="small-12 cell">
      <p>
        <input type="submit" class="button radius success" value="Save"/>
        <c:if test="${!empty returnPage}"><a class="button radius secondary" href="${returnPage}">Cancel</a></c:if>
      </p>
    </div>
  </div>
</form>