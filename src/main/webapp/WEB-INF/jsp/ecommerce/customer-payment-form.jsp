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
<jsp:useBean id="customer" class="com.simisinc.platform.domain.model.ecommerce.Customer" scope="request"/>
<jsp:useBean id="cart" class="com.simisinc.platform.domain.model.ecommerce.Cart" scope="request"/>
<jsp:useBean id="payment" class="com.simisinc.platform.domain.model.ecommerce.Payment" scope="request"/>
<%-- Required by controller --%>
<input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
<input type="hidden" name="token" value="${userSession.formToken}"/>
<%-- Page Scripts --%>
<style>
  #creditCardNumber {
    padding-right: 34px;
    background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='32px' height='18px' style='fill:#666666' viewBox='0 0 576 512'%3E%3Cpath d='M527.9 32H48.1C21.5 32 0 53.5 0 80v352c0 26.5 21.5 48 48.1 48h479.8c26.6 0 48.1-21.5 48.1-48V80c0-26.5-21.5-48-48.1-48zm-6 400H54.1c-3.3 0-6-2.7-6-6V86c0-3.3 2.7-6 6-6h467.8c3.3 0 6 2.7 6 6v340c0 3.3-2.7 6-6 6zM192 364v8c0 6.6-5.4 12-12 12h-72c-6.6 0-12-5.4-12-12v-8c0-6.6 5.4-12 12-12h72c6.6 0 12 5.4 12 12zm192 0v8c0 6.6-5.4 12-12 12H236c-6.6 0-12-5.4-12-12v-8c0-6.6 5.4-12 12-12h136c6.6 0 12 5.4 12 12zm-124-44h-56c-6.6 0-12-5.4-12-12v-40c0-6.6 5.4-12 12-12h56c6.6 0 12 5.4 12 12v40c0 6.6-5.4 12-12 12zm28-12v-40c0-6.6 5.4-12 12-12h56c6.6 0 12 5.4 12 12v40c0 6.6-5.4 12-12 12h-56c-6.6 0-12-5.4-12-12zm-192 0v-40c0-6.6 5.4-12 12-12h56c6.6 0 12 5.4 12 12v40c0 6.6-5.4 12-12 12h-56c-6.6 0-12-5.4-12-12zm384-40v40c0 6.6-5.4 12-12 12h-72c-6.6 0-12-5.4-12-12v-40c0-6.6 5.4-12 12-12h72c6.6 0 12 5.4 12 12zm0-132v48c0 13.3-10.7 24-24 24h-80c-13.3 0-24-10.7-24-24v-48c0-13.3 10.7-24 24-24h80c13.3 0 24 10.7 24 24z'/%3E%3C/svg%3E");
    background-repeat: no-repeat;
    background-position: right center;
  }
</style>
<script src="${ctx}/javascript/payform-1.4.0/payform.js"></script>
<script>
  function checkForm${widgetContext.uniqueId}() {

    var ccInput = document.getElementById('creditCardNumber');
    var mmYYInput = document.getElementById('creditCardMMYY');
    var cvcInput = document.getElementById('creditCardCVC');
    var zipInput = document.getElementById('creditCardZip');

    var creditCardNumberHelpText = document.getElementById('creditCardNumberHelpText');
    if (!payform.validateCardNumber(ccInput.value)) {
      creditCardNumberHelpText.innerText = 'Please check the card number';
      return false;
    } else {
      creditCardNumberHelpText.innerText = '';
    }

    var creditCardMMYYHelpText = document.getElementById('creditCardMMYYHelpText');
    if (!payform.validateCardExpiry(payform.parseCardExpiry(mmYYInput.value))) {
      creditCardMMYYHelpText.innerText = 'The card\'s expiration date is incomplete or in the past';
      return false;
    } else {
      creditCardMMYYHelpText.innerText = '';
    }

    var creditCardCVCHelpText = document.getElementById('creditCardCVCHelpText');
    if (!payform.validateCardCVC(cvcInput.value)) {
      creditCardCVCHelpText.innerText = 'The card\'s security code is incomplete';
      return false;
    } else {
      creditCardCVCHelpText.innerText = '';
    }

    var creditCardZipHelpText = document.getElementById('creditCardZipHelpText');
    if (zipInput.value.length !== 5) {
      creditCardZipHelpText.innerText = 'The postal code is incomplete';
      return false;
    } else {
      creditCardZipHelpText.innerText = '';
    }

    return true;
  }
</script>
<%@include file="../page_messages.jspf" %>
<%-- Form body--%>
<form method="post" onsubmit="return checkForm${widgetContext.uniqueId}()" autocomplete="on">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- The form --%>
  <div class="grid-x">
    <div class="small-12 cell">
      <fieldset>
        <%-- cc-name for name on card --%>
        <%-- Try inputmode for mobile... <input type=text inputmode=numeric autocomplete=cc-number> --%>
        <div class="grid-x grid-margin-x">
          <div class="small-12 medium-5 cell">
            <label for="creditCardNumber">Card number <span class="required">*</span>
                <input type="tel" id="creditCardNumber" name="creditCardNumber" placeholder="Card number" value="" class="cc-number" autocomplete="cc-number" required/>
            </label>
            <p class="help-text" id="creditCardNumberHelpText"></p>
          </div>
          <div class="small-6 medium-3 cell">
            <label for="creditCardMMYY">Expiration <span class="required">*</span>
              <input type="tel" id="creditCardMMYY" name="creditCardMMYY" placeholder="MM / YY" value="" class="cc-exp" autocomplete="cc-exp" required/>
            </label>
            <p class="help-text" id="creditCardMMYYHelpText"></p>
          </div>
          <div class="small-6 medium-3 cell">
            <label for="creditCardCVC">CVV/CVC <span class="required">*</span>
              <input type="tel" id="creditCardCVC" name="creditCardCVC" placeholder="Code" value="<c:out value="${payment.creditCardCVC}" />" autocomplete="off" required/>
            </label>
            <p class="help-text" id="creditCardCVCHelpText"></p>
          </div>
          <%--
          <div class="small-4 cell">
            <label for="creditCardZip">ZIP <span class="required">*</span>
              <input type="tel" id="creditCardZip" name="creditCardZip" placeholder="ZIP" value="<c:out value="${payment.creditCardZip}" />" required/>
            </label>
            <p class="help-text" id="creditCardZipHelpText"></p>
          </div>
          --%>
          <div class="small-12 cell">
            <input type="radio" name="billingAddressType" id="billingAddressSame" value="same"<c:if test="${1 eq 1}"> checked</c:if> required/><label for="billingAddressSame">Billing address is the same as my shipping address</label>
            <p class="margin-left-25 no-gap">
              <c:out value="${customer.shippingAddress.firstName}" />
              <c:out value="${customer.shippingAddress.lastName}" /><br />
              <c:out value="${customer.shippingAddress.street}" />,
              <c:if test="${!empty customer.shippingAddress.addressLine2}">
                <c:out value="${customer.shippingAddress.addressLine2}" />, </c:if>
              <c:out value="${customer.shippingAddress.city}" />,
              <c:out value="${customer.shippingAddress.state}" />
              <c:out value="${customer.shippingAddress.postalCode}" />
              <c:if test="${customer.shippingAddress.country ne 'United States' && customer.shippingAddress.country ne 'US'}">
                <c:out value="${customer.shippingAddress.country}"/>
              </c:if>
            </p>
            <input type="radio" name="billingAddressType" id="billingAddressDifferent" value="different"<c:if test="${1 eq 2}"> checked</c:if> required/><label for="billingAddressDifferent">Use a different address for billing</label>


          </div>
        </div>
      </fieldset>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 cell">
      <button class="button primary" name="button" value="save">Save &amp; Continue</button>
    </div>
  </div>
</form>
<%-- Format input for card number entry --%>
<script>
  var ccInput = document.getElementById('creditCardNumber');
  payform.cardNumberInput(ccInput);
  var mmYYInput = document.getElementById('creditCardMMYY');
  payform.expiryInput(mmYYInput);
  var cvcInput = document.getElementById('creditCardCVC');
  payform.cvcInput(cvcInput);
  var zipInput = document.getElementById('creditCardZip');
  payform.numericInput(zipInput);

  function updateType(e) {
    var cardType = payform.parseCardType(e.target.value);
    if (cardType) {
      if (cardType === 'amex') {
        ccInput.style.backgroundImage = 'url("data:image/svg+xml,%3Csvg xmlns=\'http://www.w3.org/2000/svg\' width=\'32px\' height=\'18px\' style=\'fill:#222222\' viewBox=\'0 0 576 512\'%3E%3Cpath d=\'M325.1 167.8c0-16.4-14.1-18.4-27.4-18.4l-39.1-.3v69.3H275v-25.1h18c18.4 0 14.5 10.3 14.8 25.1h16.6v-13.5c0-9.2-1.5-15.1-11-18.4 7.4-3 11.8-10.7 11.7-18.7zm-29.4 11.3H275v-15.3h21c5.1 0 10.7 1 10.7 7.4 0 6.6-5.3 7.9-11 7.9zM279 268.6h-52.7l-21 22.8-20.5-22.8h-66.5l-.1 69.3h65.4l21.3-23 20.4 23h32.2l.1-23.3c18.9 0 49.3 4.6 49.3-23.3 0-17.3-12.3-22.7-27.9-22.7zm-103.8 54.7h-40.6v-13.8h36.3v-14.1h-36.3v-12.5h41.7l17.9 20.2zm65.8 8.2l-25.3-28.1L241 276zm37.8-31h-21.2v-17.6h21.5c5.6 0 10.2 2.3 10.2 8.4 0 6.4-4.6 9.2-10.5 9.2zm-31.6-136.7v-14.6h-55.5v69.3h55.5v-14.3h-38.9v-13.8h37.8v-14.1h-37.8v-12.5zM576 255.4h-.2zm-194.6 31.9c0-16.4-14.1-18.7-27.1-18.7h-39.4l-.1 69.3h16.6l.1-25.3h17.6c11 0 14.8 2 14.8 13.8l-.1 11.5h16.6l.1-13.8c0-8.9-1.8-15.1-11-18.4 7.7-3.1 11.8-10.8 11.9-18.4zm-29.2 11.2h-20.7v-15.6h21c5.1 0 10.7 1 10.7 7.4 0 6.9-5.4 8.2-11 8.2zm-172.8-80v-69.3h-27.6l-19.7 47-21.7-47H83.3v65.7l-28.1-65.7H30.7L1 218.5h17.9l6.4-15.3h34.5l6.4 15.3H100v-54.2l24 54.2h14.6l24-54.2v54.2zM31.2 188.8l11.2-27.6 11.5 27.6zm477.4 158.9v-4.5c-10.8 5.6-3.9 4.5-156.7 4.5 0-25.2.1-23.9 0-25.2-1.7-.1-3.2-.1-9.4-.1 0 17.9-.1 6.8-.1 25.3h-39.6c0-12.1.1-15.3.1-29.2-10 6-22.8 6.4-34.3 6.2 0 14.7-.1 8.3-.1 23h-48.9c-5.1-5.7-2.7-3.1-15.4-17.4-3.2 3.5-12.8 13.9-16.1 17.4h-82v-92.3h83.1c5 5.6 2.8 3.1 15.5 17.2 3.2-3.5 12.2-13.4 15.7-17.2h58c9.8 0 18 1.9 24.3 5.6v-5.6c54.3 0 64.3-1.4 75.7 5.1v-5.1h78.2v5.2c11.4-6.9 19.6-5.2 64.9-5.2v5c10.3-5.9 16.6-5.2 54.3-5V80c0-26.5-21.5-48-48-48h-480c-26.5 0-48 21.5-48 48v109.8c9.4-21.9 19.7-46 23.1-53.9h39.7c4.3 10.1 1.6 3.7 9 21.1v-21.1h46c2.9 6.2 11.1 24 13.9 30 5.8-13.6 10.1-23.9 12.6-30h103c0-.1 11.5 0 11.6 0 43.7.2 53.6-.8 64.4 5.3v-5.3H363v9.3c7.6-6.1 17.9-9.3 30.7-9.3h27.6c0 .5 1.9.3 2.3.3H456c4.2 9.8 2.6 6 8.8 20.6v-20.6h43.3c4.9 8-1-1.8 11.2 18.4v-18.4h39.9v92h-41.6c-5.4-9-1.4-2.2-13.2-21.9v21.9h-52.8c-6.4-14.8-.1-.3-6.6-15.3h-19c-4.2 10-2.2 5.2-6.4 15.3h-26.8c-12.3 0-22.3-3-29.7-8.9v8.9h-66.5c-.3-13.9-.1-24.8-.1-24.8-1.8-.3-3.4-.2-9.8-.2v25.1H151.2v-11.4c-2.5 5.6-2.7 5.9-5.1 11.4h-29.5c-4-8.9-2.9-6.4-5.1-11.4v11.4H58.6c-4.2-10.1-2.2-5.3-6.4-15.3H33c-4.2 10-2.2 5.2-6.4 15.3H0V432c0 26.5 21.5 48 48 48h480.1c26.5 0 48-21.5 48-48v-90.4c-12.7 8.3-32.7 6.1-67.5 6.1zm36.3-64.5H575v-14.6h-32.9c-12.8 0-23.8 6.6-23.8 20.7 0 33 42.7 12.8 42.7 27.4 0 5.1-4.3 6.4-8.4 6.4h-32l-.1 14.8h32c8.4 0 17.6-1.8 22.5-8.9v-25.8c-10.5-13.8-39.3-1.3-39.3-13.5 0-5.8 4.6-6.5 9.2-6.5zm-57 39.8h-32.2l-.1 14.8h32.2c14.8 0 26.2-5.6 26.2-22 0-33.2-42.9-11.2-42.9-26.3 0-5.6 4.9-6.4 9.2-6.4h30.4v-14.6h-33.2c-12.8 0-23.5 6.6-23.5 20.7 0 33 42.7 12.5 42.7 27.4-.1 5.4-4.7 6.4-8.8 6.4zm-42.2-40.1v-14.3h-55.2l-.1 69.3h55.2l.1-14.3-38.6-.3v-13.8H445v-14.1h-37.8v-12.5zm-56.3-108.1c-.3.2-1.4 2.2-1.4 7.6 0 6 .9 7.7 1.1 7.9.2.1 1.1.5 3.4.5l7.3-16.9c-1.1 0-2.1-.1-3.1-.1-5.6 0-7 .7-7.3 1zm20.4-10.5h-.1zm-16.2-15.2c-23.5 0-34 12-34 35.3 0 22.2 10.2 34 33 34h19.2l6.4-15.3h34.3l6.6 15.3h33.7v-51.9l31.2 51.9h23.6v-69h-16.9v48.1l-29.1-48.1h-25.3v65.4l-27.9-65.4h-24.8l-23.5 54.5h-7.4c-13.3 0-16.1-8.1-16.1-19.9 0-23.8 15.7-20 33.1-19.7v-15.2zm42.1 12.1l11.2 27.6h-22.8zm-101.1-12v69.3h16.9v-69.3z\'/%3E%3C/svg%3E")';
      } else if (cardType === 'visa') {
        ccInput.style.backgroundImage = 'url("data:image/svg+xml,%3Csvg xmlns=\'http://www.w3.org/2000/svg\' width=\'32px\' height=\'18px\' style=\'fill:#222222\' viewBox=\'0 0 576 512\'%3E%3Cpath d=\'M470.1 231.3s7.6 37.2 9.3 45H446c3.3-8.9 16-43.5 16-43.5-.2.3 3.3-9.1 5.3-14.9l2.8 13.4zM576 80v352c0 26.5-21.5 48-48 48H48c-26.5 0-48-21.5-48-48V80c0-26.5 21.5-48 48-48h480c26.5 0 48 21.5 48 48zM152.5 331.2L215.7 176h-42.5l-39.3 106-4.3-21.5-14-71.4c-2.3-9.9-9.4-12.7-18.2-13.1H32.7l-.7 3.1c15.8 4 29.9 9.8 42.2 17.1l35.8 135h42.5zm94.4.2L272.1 176h-40.2l-25.1 155.4h40.1zm139.9-50.8c.2-17.7-10.6-31.2-33.7-42.3-14.1-7.1-22.7-11.9-22.7-19.2.2-6.6 7.3-13.4 23.1-13.4 13.1-.3 22.7 2.8 29.9 5.9l3.6 1.7 5.5-33.6c-7.9-3.1-20.5-6.6-36-6.6-39.7 0-67.6 21.2-67.8 51.4-.3 22.3 20 34.7 35.2 42.2 15.5 7.6 20.8 12.6 20.8 19.3-.2 10.4-12.6 15.2-24.1 15.2-16 0-24.6-2.5-37.7-8.3l-5.3-2.5-5.6 34.9c9.4 4.3 26.8 8.1 44.8 8.3 42.2.1 69.7-20.8 70-53zM528 331.4L495.6 176h-31.1c-9.6 0-16.9 2.8-21 12.9l-59.7 142.5H426s6.9-19.2 8.4-23.3H486c1.2 5.5 4.8 23.3 4.8 23.3H528z\'/%3E%3C/svg%3E")';
      } else if (cardType === 'mastercard') {
        ccInput.style.backgroundImage = 'url("data:image/svg+xml,%3Csvg xmlns=\'http://www.w3.org/2000/svg\' width=\'32px\' height=\'18px\' style=\'fill:#222222\' viewBox=\'0 0 576 512\'%3E%3Cpath d=\'M482.9 410.3c0 6.8-4.6 11.7-11.2 11.7-6.8 0-11.2-5.2-11.2-11.7 0-6.5 4.4-11.7 11.2-11.7 6.6 0 11.2 5.2 11.2 11.7zm-310.8-11.7c-7.1 0-11.2 5.2-11.2 11.7 0 6.5 4.1 11.7 11.2 11.7 6.5 0 10.9-4.9 10.9-11.7-.1-6.5-4.4-11.7-10.9-11.7zm117.5-.3c-5.4 0-8.7 3.5-9.5 8.7h19.1c-.9-5.7-4.4-8.7-9.6-8.7zm107.8.3c-6.8 0-10.9 5.2-10.9 11.7 0 6.5 4.1 11.7 10.9 11.7 6.8 0 11.2-4.9 11.2-11.7 0-6.5-4.4-11.7-11.2-11.7zm105.9 26.1c0 .3.3.5.3 1.1 0 .3-.3.5-.3 1.1-.3.3-.3.5-.5.8-.3.3-.5.5-1.1.5-.3.3-.5.3-1.1.3-.3 0-.5 0-1.1-.3-.3 0-.5-.3-.8-.5-.3-.3-.5-.5-.5-.8-.3-.5-.3-.8-.3-1.1 0-.5 0-.8.3-1.1 0-.5.3-.8.5-1.1.3-.3.5-.3.8-.5.5-.3.8-.3 1.1-.3.5 0 .8 0 1.1.3.5.3.8.3 1.1.5s.2.6.5 1.1zm-2.2 1.4c.5 0 .5-.3.8-.3.3-.3.3-.5.3-.8 0-.3 0-.5-.3-.8-.3 0-.5-.3-1.1-.3h-1.6v3.5h.8V426h.3l1.1 1.4h.8l-1.1-1.3zM576 81v352c0 26.5-21.5 48-48 48H48c-26.5 0-48-21.5-48-48V81c0-26.5 21.5-48 48-48h480c26.5 0 48 21.5 48 48zM64 220.6c0 76.5 62.1 138.5 138.5 138.5 27.2 0 53.9-8.2 76.5-23.1-72.9-59.3-72.4-171.2 0-230.5-22.6-15-49.3-23.1-76.5-23.1-76.4-.1-138.5 62-138.5 138.2zm224 108.8c70.5-55 70.2-162.2 0-217.5-70.2 55.3-70.5 162.6 0 217.5zm-142.3 76.3c0-8.7-5.7-14.4-14.7-14.7-4.6 0-9.5 1.4-12.8 6.5-2.4-4.1-6.5-6.5-12.2-6.5-3.8 0-7.6 1.4-10.6 5.4V392h-8.2v36.7h8.2c0-18.9-2.5-30.2 9-30.2 10.2 0 8.2 10.2 8.2 30.2h7.9c0-18.3-2.5-30.2 9-30.2 10.2 0 8.2 10 8.2 30.2h8.2v-23zm44.9-13.7h-7.9v4.4c-2.7-3.3-6.5-5.4-11.7-5.4-10.3 0-18.2 8.2-18.2 19.3 0 11.2 7.9 19.3 18.2 19.3 5.2 0 9-1.9 11.7-5.4v4.6h7.9V392zm40.5 25.6c0-15-22.9-8.2-22.9-15.2 0-5.7 11.9-4.8 18.5-1.1l3.3-6.5c-9.4-6.1-30.2-6-30.2 8.2 0 14.3 22.9 8.3 22.9 15 0 6.3-13.5 5.8-20.7.8l-3.5 6.3c11.2 7.6 32.6 6 32.6-7.5zm35.4 9.3l-2.2-6.8c-3.8 2.1-12.2 4.4-12.2-4.1v-16.6h13.1V392h-13.1v-11.2h-8.2V392h-7.6v7.3h7.6V416c0 17.6 17.3 14.4 22.6 10.9zm13.3-13.4h27.5c0-16.2-7.4-22.6-17.4-22.6-10.6 0-18.2 7.9-18.2 19.3 0 20.5 22.6 23.9 33.8 14.2l-3.8-6c-7.8 6.4-19.6 5.8-21.9-4.9zm59.1-21.5c-4.6-2-11.6-1.8-15.2 4.4V392h-8.2v36.7h8.2V408c0-11.6 9.5-10.1 12.8-8.4l2.4-7.6zm10.6 18.3c0-11.4 11.6-15.1 20.7-8.4l3.8-6.5c-11.6-9.1-32.7-4.1-32.7 15 0 19.8 22.4 23.8 32.7 15l-3.8-6.5c-9.2 6.5-20.7 2.6-20.7-8.6zm66.7-18.3H408v4.4c-8.3-11-29.9-4.8-29.9 13.9 0 19.2 22.4 24.7 29.9 13.9v4.6h8.2V392zm33.7 0c-2.4-1.2-11-2.9-15.2 4.4V392h-7.9v36.7h7.9V408c0-11 9-10.3 12.8-8.4l2.4-7.6zm40.3-14.9h-7.9v19.3c-8.2-10.9-29.9-5.1-29.9 13.9 0 19.4 22.5 24.6 29.9 13.9v4.6h7.9v-51.7zm7.6-75.1v4.6h.8V302h1.9v-.8h-4.6v.8h1.9zm6.6 123.8c0-.5 0-1.1-.3-1.6-.3-.3-.5-.8-.8-1.1-.3-.3-.8-.5-1.1-.8-.5 0-1.1-.3-1.6-.3-.3 0-.8.3-1.4.3-.5.3-.8.5-1.1.8-.5.3-.8.8-.8 1.1-.3.5-.3 1.1-.3 1.6 0 .3 0 .8.3 1.4 0 .3.3.8.8 1.1.3.3.5.5 1.1.8.5.3 1.1.3 1.4.3.5 0 1.1 0 1.6-.3.3-.3.8-.5 1.1-.8.3-.3.5-.8.8-1.1.3-.6.3-1.1.3-1.4zm3.2-124.7h-1.4l-1.6 3.5-1.6-3.5h-1.4v5.4h.8v-4.1l1.6 3.5h1.1l1.4-3.5v4.1h1.1v-5.4zm4.4-80.5c0-76.2-62.1-138.3-138.5-138.3-27.2 0-53.9 8.2-76.5 23.1 72.1 59.3 73.2 171.5 0 230.5 22.6 15 49.5 23.1 76.5 23.1 76.4.1 138.5-61.9 138.5-138.4z\'/%3E%3C/svg%3E")';
      } else if (cardType === 'discover') {
        ccInput.style.backgroundImage = 'url("data:image/svg+xml,%3Csvg xmlns=\'http://www.w3.org/2000/svg\' width=\'32px\' height=\'18px\' style=\'fill:#222222\' viewBox=\'0 0 576 512\'%3E%3Cpath d=\'M520.4 196.1c0-7.9-5.5-12.1-15.6-12.1h-4.9v24.9h4.7c10.3 0 15.8-4.4 15.8-12.8zM528 32H48C21.5 32 0 53.5 0 80v352c0 26.5 21.5 48 48 48h480c26.5 0 48-21.5 48-48V80c0-26.5-21.5-48-48-48zm-44.1 138.9c22.6 0 52.9-4.1 52.9 24.4 0 12.6-6.6 20.7-18.7 23.2l25.8 34.4h-19.6l-22.2-32.8h-2.2v32.8h-16zm-55.9.1h45.3v14H444v18.2h28.3V217H444v22.2h29.3V253H428zm-68.7 0l21.9 55.2 22.2-55.2h17.5l-35.5 84.2h-8.6l-35-84.2zm-55.9-3c24.7 0 44.6 20 44.6 44.6 0 24.7-20 44.6-44.6 44.6-24.7 0-44.6-20-44.6-44.6 0-24.7 20-44.6 44.6-44.6zm-49.3 6.1v19c-20.1-20.1-46.8-4.7-46.8 19 0 25 27.5 38.5 46.8 19.2v19c-29.7 14.3-63.3-5.7-63.3-38.2 0-31.2 33.1-53 63.3-38zm-97.2 66.3c11.4 0 22.4-15.3-3.3-24.4-15-5.5-20.2-11.4-20.2-22.7 0-23.2 30.6-31.4 49.7-14.3l-8.4 10.8c-10.4-11.6-24.9-6.2-24.9 2.5 0 4.4 2.7 6.9 12.3 10.3 18.2 6.6 23.6 12.5 23.6 25.6 0 29.5-38.8 37.4-56.6 11.3l10.3-9.9c3.7 7.1 9.9 10.8 17.5 10.8zM55.4 253H32v-82h23.4c26.1 0 44.1 17 44.1 41.1 0 18.5-13.2 40.9-44.1 40.9zm67.5 0h-16v-82h16zM544 433c0 8.2-6.8 15-15 15H128c189.6-35.6 382.7-139.2 416-160zM74.1 191.6c-5.2-4.9-11.6-6.6-21.9-6.6H48v54.2h4.2c10.3 0 17-2 21.9-6.4 5.7-5.2 8.9-12.8 8.9-20.7s-3.2-15.5-8.9-20.5z\'/%3E%3C/svg%3E")';
      } else if (cardType === 'dinersclub') {
        ccInput.style.backgroundImage = 'url("data:image/svg+xml,%3Csvg xmlns=\'http://www.w3.org/2000/svg\' width=\'32px\' height=\'18px\' style=\'fill:#222222\' viewBox=\'0 0 576 512\'%3E%3Cpath d=\'M239.7 79.9c-96.9 0-175.8 78.6-175.8 175.8 0 96.9 78.9 175.8 175.8 175.8 97.2 0 175.8-78.9 175.8-175.8 0-97.2-78.6-175.8-175.8-175.8zm-39.9 279.6c-41.7-15.9-71.4-56.4-71.4-103.8s29.7-87.9 71.4-104.1v207.9zm79.8.3V151.6c41.7 16.2 71.4 56.7 71.4 104.1s-29.7 87.9-71.4 104.1zM528 32H48C21.5 32 0 53.5 0 80v352c0 26.5 21.5 48 48 48h480c26.5 0 48-21.5 48-48V80c0-26.5-21.5-48-48-48zM329.7 448h-90.3c-106.2 0-193.8-85.5-193.8-190.2C45.6 143.2 133.2 64 239.4 64h90.3c105 0 200.7 79.2 200.7 193.8 0 104.7-95.7 190.2-200.7 190.2z\'/%3E%3C/svg%3E")';
      }
    } else {
      ccInput.style.backgroundImage = 'url("data:image/svg+xml,%3Csvg xmlns=\'http://www.w3.org/2000/svg\' width=\'32px\' height=\'18px\' style=\'fill:#666666\' viewBox=\'0 0 576 512\'%3E%3Cpath d=\'M527.9 32H48.1C21.5 32 0 53.5 0 80v352c0 26.5 21.5 48 48.1 48h479.8c26.6 0 48.1-21.5 48.1-48V80c0-26.5-21.5-48-48.1-48zm-6 400H54.1c-3.3 0-6-2.7-6-6V86c0-3.3 2.7-6 6-6h467.8c3.3 0 6 2.7 6 6v340c0 3.3-2.7 6-6 6zM192 364v8c0 6.6-5.4 12-12 12h-72c-6.6 0-12-5.4-12-12v-8c0-6.6 5.4-12 12-12h72c6.6 0 12 5.4 12 12zm192 0v8c0 6.6-5.4 12-12 12H236c-6.6 0-12-5.4-12-12v-8c0-6.6 5.4-12 12-12h136c6.6 0 12 5.4 12 12zm-124-44h-56c-6.6 0-12-5.4-12-12v-40c0-6.6 5.4-12 12-12h56c6.6 0 12 5.4 12 12v40c0 6.6-5.4 12-12 12zm28-12v-40c0-6.6 5.4-12 12-12h56c6.6 0 12 5.4 12 12v40c0 6.6-5.4 12-12 12h-56c-6.6 0-12-5.4-12-12zm-192 0v-40c0-6.6 5.4-12 12-12h56c6.6 0 12 5.4 12 12v40c0 6.6-5.4 12-12 12h-56c-6.6 0-12-5.4-12-12zm384-40v40c0 6.6-5.4 12-12 12h-72c-6.6 0-12-5.4-12-12v-40c0-6.6 5.4-12 12-12h72c6.6 0 12 5.4 12 12zm0-132v48c0 13.3-10.7 24-24 24h-80c-13.3 0-24-10.7-24-24v-48c0-13.3 10.7-24 24-24h80c13.3 0 24 10.7 24 24z\'/%3E%3C/svg%3E")';
    }
  }
  ccInput.addEventListener('input',   updateType);
</script>