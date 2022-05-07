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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="shippingRate" class="com.simisinc.platform.domain.model.ecommerce.ShippingRate" scope="request"/>
<jsp:useBean id="shippingCountryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="shippingMethodList" class="java.util.ArrayList" scope="request"/>
<c:choose>
  <c:when test="${shippingRate.id eq -1}"><h4>New Shipping Rate</h4></c:when>
  <c:otherwise>
    <h4>Update Shipping Rate</h4>
  </c:otherwise>
</c:choose>
<form method="post" autocomplete="off">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${shippingRate.id}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <div class="grid-x grid-margin-x">
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
    <div class="small-12 medium-6 large-4 cell">
      <label>Region/State <span class="required">*</span>
        <select id="region" name="region">
          <option value=""></option>
          <option value="*"<c:if test="${shippingRate.region eq '*'}"> selected</c:if>>*</option>
          <option value="AL"<c:if test="${shippingRate.region eq 'AL'}"> selected</c:if>>Alabama (AL)</option>
          <option value="AK"<c:if test="${shippingRate.region eq 'AK'}"> selected</c:if>>Alaska (AK)</option>
          <option value="AZ"<c:if test="${shippingRate.region eq 'AZ'}"> selected</c:if>>Arizona (AZ)</option>
          <option value="AR"<c:if test="${shippingRate.region eq 'AR'}"> selected</c:if>>Arkansas (AR)</option>
          <option value="CA"<c:if test="${shippingRate.region eq 'CA'}"> selected</c:if>>California (CA)</option>
          <option value="CO"<c:if test="${shippingRate.region eq 'CO'}"> selected</c:if>>Colorado (CO)</option>
          <option value="CT"<c:if test="${shippingRate.region eq 'CT'}"> selected</c:if>>Connecticut (CT)</option>
          <option value="DE"<c:if test="${shippingRate.region eq 'DE'}"> selected</c:if>>Delaware (DE)</option>
          <option value="DC"<c:if test="${shippingRate.region eq 'DC'}"> selected</c:if>>District Of Columbia (DC)</option>
          <option value="FL"<c:if test="${shippingRate.region eq 'FL'}"> selected</c:if>>Florida (FL)</option>
          <option value="GA"<c:if test="${shippingRate.region eq 'GA'}"> selected</c:if>>Georgia (GA)</option>
          <option value="HI"<c:if test="${shippingRate.region eq 'HI'}"> selected</c:if>>Hawaii (HI)</option>
          <option value="ID"<c:if test="${shippingRate.region eq 'ID'}"> selected</c:if>>Idaho (ID)</option>
          <option value="IL"<c:if test="${shippingRate.region eq 'IL'}"> selected</c:if>>Illinois (IL)</option>
          <option value="IN"<c:if test="${shippingRate.region eq 'IN'}"> selected</c:if>>Indiana (IN)</option>
          <option value="IA"<c:if test="${shippingRate.region eq 'IA'}"> selected</c:if>>Iowa (IA)</option>
          <option value="KS"<c:if test="${shippingRate.region eq 'KS'}"> selected</c:if>>Kansas (KS)</option>
          <option value="KY"<c:if test="${shippingRate.region eq 'KY'}"> selected</c:if>>Kentucky (KY)</option>
          <option value="LA"<c:if test="${shippingRate.region eq 'LA'}"> selected</c:if>>Louisiana (LA)</option>
          <option value="ME"<c:if test="${shippingRate.region eq 'ME'}"> selected</c:if>>Maine (ME)</option>
          <option value="MD"<c:if test="${shippingRate.region eq 'MD'}"> selected</c:if>>Maryland (MD)</option>
          <option value="MA"<c:if test="${shippingRate.region eq 'MA'}"> selected</c:if>>Massachusetts (MA)</option>
          <option value="MI"<c:if test="${shippingRate.region eq 'MI'}"> selected</c:if>>Michigan (MI)</option>
          <option value="MN"<c:if test="${shippingRate.region eq 'MN'}"> selected</c:if>>Minnesota (MN)</option>
          <option value="MS"<c:if test="${shippingRate.region eq 'MS'}"> selected</c:if>>Mississippi (MS)</option>
          <option value="MO"<c:if test="${shippingRate.region eq 'MO'}"> selected</c:if>>Missouri (MO)</option>
          <option value="MT"<c:if test="${shippingRate.region eq 'MT'}"> selected</c:if>>Montana (MT)</option>
          <option value="NE"<c:if test="${shippingRate.region eq 'NE'}"> selected</c:if>>Nebraska (NE)</option>
          <option value="NV"<c:if test="${shippingRate.region eq 'NV'}"> selected</c:if>>Nevada (NV)</option>
          <option value="NH"<c:if test="${shippingRate.region eq 'NH'}"> selected</c:if>>New Hampshire (NH)</option>
          <option value="NJ"<c:if test="${shippingRate.region eq 'NJ'}"> selected</c:if>>New Jersey (NJ)</option>
          <option value="NM"<c:if test="${shippingRate.region eq 'NM'}"> selected</c:if>>New Mexico (NM)</option>
          <option value="NY"<c:if test="${shippingRate.region eq 'NY'}"> selected</c:if>>New York (NY)</option>
          <option value="NC"<c:if test="${shippingRate.region eq 'NC'}"> selected</c:if>>North Carolina (NC)</option>
          <option value="ND"<c:if test="${shippingRate.region eq 'ND'}"> selected</c:if>>North Dakota (ND)</option>
          <option value="OH"<c:if test="${shippingRate.region eq 'OH'}"> selected</c:if>>Ohio (OH)</option>
          <option value="OK"<c:if test="${shippingRate.region eq 'OK'}"> selected</c:if>>Oklahoma (OK)</option>
          <option value="OR"<c:if test="${shippingRate.region eq 'OR'}"> selected</c:if>>Oregon (OR)</option>
          <option value="PA"<c:if test="${shippingRate.region eq 'PA'}"> selected</c:if>>Pennsylvania (PA)</option>
          <option value="RI"<c:if test="${shippingRate.region eq 'RI'}"> selected</c:if>>Rhode Island (RI)</option>
          <option value="SC"<c:if test="${shippingRate.region eq 'SC'}"> selected</c:if>>South Carolina (SC)</option>
          <option value="SD"<c:if test="${shippingRate.region eq 'SD'}"> selected</c:if>>South Dakota (SD)</option>
          <option value="TN"<c:if test="${shippingRate.region eq 'TN'}"> selected</c:if>>Tennessee (TN)</option>
          <option value="TX"<c:if test="${shippingRate.region eq 'TX'}"> selected</c:if>>Texas (TX)</option>
          <option value="UT"<c:if test="${shippingRate.region eq 'UT'}"> selected</c:if>>Utah (UT)</option>
          <option value="VT"<c:if test="${shippingRate.region eq 'VT'}"> selected</c:if>>Vermont (VT)</option>
          <option value="VA"<c:if test="${shippingRate.region eq 'VA'}"> selected</c:if>>Virginia (VA)</option>
          <option value="WA"<c:if test="${shippingRate.region eq 'WA'}"> selected</c:if>>Washington (WA)</option>
          <option value="WV"<c:if test="${shippingRate.region eq 'WV'}"> selected</c:if>>West Virginia (WV)</option>
          <option value="WI"<c:if test="${shippingRate.region eq 'WI'}"> selected</c:if>>Wisconsin (WI)</option>
          <option value="WY"<c:if test="${shippingRate.region eq 'WY'}"> selected</c:if>>Wyoming (WY)</option>
          <option value="AS"<c:if test="${shippingRate.region eq 'AS'}"> selected</c:if>>American Samoa (AS)</option>
          <option value="GU"<c:if test="${shippingRate.region eq 'GU'}"> selected</c:if>>Guam (GU)</option>
          <option value="MP"<c:if test="${shippingRate.region eq 'MP'}"> selected</c:if>>Northern Mariana Islands (MP)</option>
          <option value="PR"<c:if test="${shippingRate.region eq 'PR'}"> selected</c:if>>Puerto Rico (PR)</option>
          <option value="UM"<c:if test="${shippingRate.region eq 'UM'}"> selected</c:if>>United States Minor Outlying Islands (UM)</option>
          <option value="VI"<c:if test="${shippingRate.region eq 'VI'}"> selected</c:if>>Virgin Islands (VI)</option>
          <option value="AA"<c:if test="${shippingRate.region eq 'AA'}"> selected</c:if>>Armed Forces Americas (AA)</option>
          <option value="AP"<c:if test="${shippingRate.region eq 'AP'}"> selected</c:if>>Armed Forces Pacific (AP)</option>
          <option value="AE"<c:if test="${shippingRate.region eq 'AE'}"> selected</c:if>>Armed Forces Others (AE)</option>
        </select>
      </label>
    </div>
    <div class="small-12 medium-6 large-4 cell">
      <label>Zip/Postal Code <span class="required">*</span>
        <input type="text" placeholder="Zip/Postal Code" name="postalCode" value="<c:out value="${shippingRate.postalCode}"/>">
      </label>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-6 medium-4 large-3 cell">
      <label>Method <span class="required">*</span>
        <select id="shippingMethodId" name="shippingMethodId">
          <option value=""></option>
          <c:forEach items="${shippingMethodList}" var="shippingMethod">
            <option value="${shippingMethod.id}"<c:if test="${shippingRate.shippingMethodId eq shippingMethod.id}"> selected</c:if>><c:out value="${shippingMethod.title}" /></option>
          </c:forEach>
        </select>
      </label>
    </div>
    <div class="small-6 medium-4 large-2 cell">
      <label for="shippingFee">Shipping Fee
        <input type="text" id="shippingFee" name="shippingFee" placeholder="Amount" value="<fmt:formatNumber type="currency" currencySymbol="" value="${shippingRate.shippingFee}" />" required/>
      </label>
    </div>
    <div class="small-6 medium-4 large-2 cell">
      <label for="handlingFee">Handling Fee
        <input type="text" id="handlingFee" name="handlingFee" placeholder="Amount" value="<fmt:formatNumber type="currency" currencySymbol="" value="${shippingRate.handlingFee}" />" required/>
      </label>
    </div>
    <div class="small-6 medium-4 large-2 cell">
      <label for="minSubTotal">Min Subtotal
        <input type="text" id="minSubTotal" name="minSubTotal" placeholder="Amount" value="<fmt:formatNumber type="currency" currencySymbol="" value="${shippingRate.minSubTotal}" />" required/>
      </label>
    </div>
    <div class="small-6 medium-4 large-2 cell">
      <label for="minWeightOz">Min Weight (oz)
        <input type="text" id="minWeightOz" name="minWeightOz" placeholder="Number of total ounces" value="<c:out value="${shippingRate.minWeightOz}" />"/>
      </label>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-8 large-6 cell">
      <label for="displayText">Description to display at checkout
        <input type="text" id="displayText" name="displayText" maxlength="255" placeholder="" value="<c:out value="${shippingRate.displayText}" />"/>
      </label>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-10 large-8 cell">
      <label>Exclude SKUs
        <input type="text" id="excludeSkus" name="excludeSkus" placeholder="SKUs" value="<c:out value="${shippingRate.excludeSkus}"/>">
      </label>
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