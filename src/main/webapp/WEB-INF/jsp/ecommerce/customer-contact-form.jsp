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
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<%@ taglib prefix="product" uri="/WEB-INF/tlds/product-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="customer" class="com.simisinc.platform.domain.model.ecommerce.Customer" scope="request"/>
<%-- Required by controller --%>
<input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
<input type="hidden" name="token" value="${userSession.formToken}"/>
<%-- Form body--%>
<script>
  function checkForm${widgetContext.uniqueId}() {
    if (document.getElementById("shippingCountry").value.trim() === "") {
      alert("Please choose a Country");
      return false;
    }
    if (document.getElementById("shippingState").value.trim() === "") {
      alert("Please choose a State or Province");
      return false;
    }
    return true;
  }
</script>
<%@include file="../page_messages.jspf" %>
<form method="post" onsubmit="return checkForm${widgetContext.uniqueId}()" autocomplete="on">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- The form --%>
  <%--   <span class="required">*</span>Shipping is limited to United States addresses only--%>
  <div class="grid-x">
    <div class="small-12 cell">
      <fieldset>
        <h4>Contact information</h4>
        <div class="grid-x grid-margin-x">
          <div class="small-6 cell">
            <label for="email">Email <span class="required">*</span>
              <input type="text" id="email" name="email" placeholder="Email" value="<c:out value="${customer.email}" />" autocomplete="email" required/>
            </label>
          </div>
          <div class="small-6 cell">
            <label for="phoneNumber">Phone Number <span class="required">*</span>
              <input type="text" id="phoneNumber" name="phoneNumber" placeholder="Phone Number" value="<c:out value="${customer.phoneNumber}" />" autocomplete="tel" required/>
            </label>
          </div>
        </div>
      </fieldset>
      <fieldset>
        <h4>Shipping info</h4>
        <fieldset>
          <label for="country">Country <span class="required">*</span>
            <select id="country" name="shippingCountry">
              <option value="">Choose</option>
              <option value="United States"<c:if test="${customer.shippingCountry eq 'United States'}"> selected</c:if>>United States</option>
              <c:if test="${!empty customer.shippingCountry && customer.shippingCountry ne 'United States'}">
                <option value="<c:out value="${customer.shippingCountry}" />" selected><c:out value="${customer.shippingCountry}"/></option>
              </c:if>
            </select>
          </label>
        </fieldset>
        <div class="grid-x grid-margin-x">
          <div class="small-6 cell">
            <label for="firstName">First Name <span class="required">*</span>
              <input type="text" id="firstName" name="shippingFirstName" placeholder="First Name" value="<c:out value="${customer.shippingAddress.firstName}" />" autocomplete="given-name" required/>
            </label>
          </div>
          <div class="small-6 cell">
            <label for="lastName">Last Name <span class="required">*</span>
              <input type="text" id="lastName" name="shippingLastName" placeholder="Last Name" value="<c:out value="${customer.shippingAddress.lastName}" />" autocomplete="family-name" required/>
            </label>
          </div>
        </div>
        <label for="organization">Company
          <input type="text" id="organization" name="shippingOrganization" placeholder="Company (optional)" value="<c:out value="${customer.shippingAddress.organization}" />" autocomplete="organization"/>
        </label>
      </fieldset>
      <fieldset>
        <label for="street">Address <span class="required">*</span>
          <input type="text" id="street" name="shippingStreet" placeholder="Address" value="<c:out value="${customer.shippingAddress.street}" />" autocomplete="address-line1" required/>
<%--        </label>--%>
<%--        <label for="addressLine2">Address Line 2--%>
          <input type="text" id="addressLine2" name="shippingAddressLine2" placeholder="Apartment, Suite, etc. (optional)" value="<c:out value="${customer.shippingAddress.addressLine2}" />" autocomplete="address-line2"/>
        </label>
        <label for="city">City <span class="required">*</span>
          <input type="text" id="city" name="shippingCity" placeholder="City" value="<c:out value="${customer.shippingAddress.city}" />" autocomplete="address-level2" required/>
        </label>
        <div class="grid-x grid-margin-x">
          <div class="small-6 cell">
            <label for="state">State/Province <span class="required">*</span>
              <%--<input type="text" id="state" name="shippingState" value="<c:out value="${customer.state}" />" />--%>
              <select id="state" name="shippingState">
                <option value="">Choose</option>
                <option value="AL"<c:if test="${customer.shippingAddress.state eq 'AL'}"> selected</c:if>>Alabama (AL)</option>
                <option value="AK"<c:if test="${customer.shippingAddress.state eq 'AK'}"> selected</c:if>>Alaska (AK)</option>
                <option value="AZ"<c:if test="${customer.shippingAddress.state eq 'AZ'}"> selected</c:if>>Arizona (AZ)</option>
                <option value="AR"<c:if test="${customer.shippingAddress.state eq 'AR'}"> selected</c:if>>Arkansas (AR)</option>
                <option value="CA"<c:if test="${customer.shippingAddress.state eq 'CA'}"> selected</c:if>>California (CA)</option>
                <option value="CO"<c:if test="${customer.shippingAddress.state eq 'CO'}"> selected</c:if>>Colorado (CO)</option>
                <option value="CT"<c:if test="${customer.shippingAddress.state eq 'CT'}"> selected</c:if>>Connecticut (CT)</option>
                <option value="DE"<c:if test="${customer.shippingAddress.state eq 'DE'}"> selected</c:if>>Delaware (DE)</option>
                <option value="DC"<c:if test="${customer.shippingAddress.state eq 'DC'}"> selected</c:if>>District Of Columbia (DC)</option>
                <option value="FL"<c:if test="${customer.shippingAddress.state eq 'FL'}"> selected</c:if>>Florida (FL)</option>
                <option value="GA"<c:if test="${customer.shippingAddress.state eq 'GA'}"> selected</c:if>>Georgia (GA)</option>
                <option value="HI"<c:if test="${customer.shippingAddress.state eq 'HI'}"> selected</c:if>>Hawaii (HI)</option>
                <option value="ID"<c:if test="${customer.shippingAddress.state eq 'ID'}"> selected</c:if>>Idaho (ID)</option>
                <option value="IL"<c:if test="${customer.shippingAddress.state eq 'IL'}"> selected</c:if>>Illinois (IL)</option>
                <option value="IN"<c:if test="${customer.shippingAddress.state eq 'IN'}"> selected</c:if>>Indiana (IN)</option>
                <option value="IA"<c:if test="${customer.shippingAddress.state eq 'IA'}"> selected</c:if>>Iowa (IA)</option>
                <option value="KS"<c:if test="${customer.shippingAddress.state eq 'KS'}"> selected</c:if>>Kansas (KS)</option>
                <option value="KY"<c:if test="${customer.shippingAddress.state eq 'KY'}"> selected</c:if>>Kentucky (KY)</option>
                <option value="LA"<c:if test="${customer.shippingAddress.state eq 'LA'}"> selected</c:if>>Louisiana (LA)</option>
                <option value="ME"<c:if test="${customer.shippingAddress.state eq 'ME'}"> selected</c:if>>Maine (ME)</option>
                <option value="MD"<c:if test="${customer.shippingAddress.state eq 'MD'}"> selected</c:if>>Maryland (MD)</option>
                <option value="MA"<c:if test="${customer.shippingAddress.state eq 'MA'}"> selected</c:if>>Massachusetts (MA)</option>
                <option value="MI"<c:if test="${customer.shippingAddress.state eq 'MI'}"> selected</c:if>>Michigan (MI)</option>
                <option value="MN"<c:if test="${customer.shippingAddress.state eq 'MN'}"> selected</c:if>>Minnesota (MN)</option>
                <option value="MS"<c:if test="${customer.shippingAddress.state eq 'MS'}"> selected</c:if>>Mississippi (MS)</option>
                <option value="MO"<c:if test="${customer.shippingAddress.state eq 'MO'}"> selected</c:if>>Missouri (MO)</option>
                <option value="MT"<c:if test="${customer.shippingAddress.state eq 'MT'}"> selected</c:if>>Montana (MT)</option>
                <option value="NE"<c:if test="${customer.shippingAddress.state eq 'NE'}"> selected</c:if>>Nebraska (NE)</option>
                <option value="NV"<c:if test="${customer.shippingAddress.state eq 'NV'}"> selected</c:if>>Nevada (NV)</option>
                <option value="NH"<c:if test="${customer.shippingAddress.state eq 'NH'}"> selected</c:if>>New Hampshire (NH)</option>
                <option value="NJ"<c:if test="${customer.shippingAddress.state eq 'NJ'}"> selected</c:if>>New Jersey (NJ)</option>
                <option value="NM"<c:if test="${customer.shippingAddress.state eq 'NM'}"> selected</c:if>>New Mexico (NM)</option>
                <option value="NY"<c:if test="${customer.shippingAddress.state eq 'NY'}"> selected</c:if>>New York (NY)</option>
                <option value="NC"<c:if test="${customer.shippingAddress.state eq 'NC'}"> selected</c:if>>North Carolina (NC)</option>
                <option value="ND"<c:if test="${customer.shippingAddress.state eq 'ND'}"> selected</c:if>>North Dakota (ND)</option>
                <option value="OH"<c:if test="${customer.shippingAddress.state eq 'OH'}"> selected</c:if>>Ohio (OH)</option>
                <option value="OK"<c:if test="${customer.shippingAddress.state eq 'OK'}"> selected</c:if>>Oklahoma (OK)</option>
                <option value="OR"<c:if test="${customer.shippingAddress.state eq 'OR'}"> selected</c:if>>Oregon (OR)</option>
                <option value="PA"<c:if test="${customer.shippingAddress.state eq 'PA'}"> selected</c:if>>Pennsylvania (PA)</option>
                <option value="RI"<c:if test="${customer.shippingAddress.state eq 'RI'}"> selected</c:if>>Rhode Island (RI)</option>
                <option value="SC"<c:if test="${customer.shippingAddress.state eq 'SC'}"> selected</c:if>>South Carolina (SC)</option>
                <option value="SD"<c:if test="${customer.shippingAddress.state eq 'SD'}"> selected</c:if>>South Dakota (SD)</option>
                <option value="TN"<c:if test="${customer.shippingAddress.state eq 'TN'}"> selected</c:if>>Tennessee (TN)</option>
                <option value="TX"<c:if test="${customer.shippingAddress.state eq 'TX'}"> selected</c:if>>Texas (TX)</option>
                <option value="UT"<c:if test="${customer.shippingAddress.state eq 'UT'}"> selected</c:if>>Utah (UT)</option>
                <option value="VT"<c:if test="${customer.shippingAddress.state eq 'VT'}"> selected</c:if>>Vermont (VT)</option>
                <option value="VA"<c:if test="${customer.shippingAddress.state eq 'VA'}"> selected</c:if>>Virginia (VA)</option>
                <option value="WA"<c:if test="${customer.shippingAddress.state eq 'WA'}"> selected</c:if>>Washington (WA)</option>
                <option value="WV"<c:if test="${customer.shippingAddress.state eq 'WV'}"> selected</c:if>>West Virginia (WV)</option>
                <option value="WI"<c:if test="${customer.shippingAddress.state eq 'WI'}"> selected</c:if>>Wisconsin (WI)</option>
                <option value="WY"<c:if test="${customer.shippingAddress.state eq 'WY'}"> selected</c:if>>Wyoming (WY)</option>
                <option value="AS"<c:if test="${customer.shippingAddress.state eq 'AS'}"> selected</c:if>>American Samoa (AS)</option>
                <option value="GU"<c:if test="${customer.shippingAddress.state eq 'GU'}"> selected</c:if>>Guam (GU)</option>
                <option value="MP"<c:if test="${customer.shippingAddress.state eq 'MP'}"> selected</c:if>>Northern Mariana Islands (MP)</option>
                <option value="PR"<c:if test="${customer.shippingAddress.state eq 'PR'}"> selected</c:if>>Puerto Rico (PR)</option>
                <option value="UM"<c:if test="${customer.shippingAddress.state eq 'UM'}"> selected</c:if>>United States Minor Outlying Islands (UM)</option>
                <option value="VI"<c:if test="${customer.shippingAddress.state eq 'VI'}"> selected</c:if>>Virgin Islands (VI)</option>
                <option value="AA"<c:if test="${customer.shippingAddress.state eq 'AA'}"> selected</c:if>>Armed Forces Americas (AA)</option>
                <option value="AP"<c:if test="${customer.shippingAddress.state eq 'AP'}"> selected</c:if>>Armed Forces Pacific (AP)</option>
                <option value="AE"<c:if test="${customer.shippingAddress.state eq 'AE'}"> selected</c:if>>Armed Forces Others (AE)</option>
              </select>
            </label>
          </div>
          <div class="small-6 cell">
            <label for="postalCode">Postal Code <span class="required">*</span>
              <input type="text" id="postalCode" name="shippingPostalCode" placeholder="Postal Code" value="<c:out value="${customer.shippingAddress.postalCode}" />" autocomplete="postal-code" required/>
            </label>
          </div>
        </div>
      </fieldset>
      <%--
      <fieldset>
        <h4>Shipping</h4>
        <input type="radio" name="shippingType" id="typeStandard" value="standard"<c:if test="${1 eq 1}"> checked</c:if> required/><label for="typeStandard">Free Shipping</label>
        <input type="radio" name="shippingType" id="typeNextDay" value="nextDay"<c:if test="${1 eq 2}"> checked</c:if> required/><label for="typeNextDay">Next Day Delivery</label>
      </fieldset>
      --%>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <%--    <div class="small-6 cell">--%>
    <%--      <a href="${ctx}/cart"><i class="fa fa-angle-left"></i> Return to cart</a>--%>
    <%--    </div>--%>
    <div class="small-12 cell text-right">
<%--      <button class="button primary" name="button" value="shipping">Continue to payment information <i class="fa fa-angle-double-right"></i></button>--%>
      <button class="button primary" name="button" value="shipping">Continue to delivery options <i class="fa fa-angle-double-right"></i></button>
    </div>
  </div>
</form>
