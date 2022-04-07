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
<jsp:useBean id="cart" class="com.simisinc.platform.domain.model.ecommerce.Cart" scope="request"/>
<jsp:useBean id="customer" class="com.simisinc.platform.domain.model.ecommerce.Customer" scope="request"/>
<jsp:useBean id="payment" class="com.simisinc.platform.domain.model.ecommerce.Payment" scope="request"/>
<jsp:useBean id="squareAppId" class="java.lang.String" scope="request"/>
<jsp:useBean id="testMode" class="java.lang.String" scope="request"/>
<%-- Square --%>
<c:choose>
  <c:when test="${testMode eq 'true'}">
    <script type="text/javascript" src="https://js.squareupsandbox.com/v2/paymentform"></script>
  </c:when>
  <c:otherwise>
    <script type="text/javascript" src="https://js.squareup.com/v2/paymentform"></script>
  </c:otherwise>
</c:choose>
<%-- Page Scripts --%>
<%@include file="../page_messages.jspf" %>
<style>
  .third {
    float: left;
    width: calc((100% - 32px) / 3);
    padding: 0;
    margin: 0 16px 16px 0;
  }
  .third:last-of-type {
    margin-right: 0;
  }
  .sq-input {
    box-sizing: border-box;
    border: 1px solid #E0E2E3;
    background-color: white;
    border-radius: 3px;
    display: inline-block;
    -webkit-transition: border-color .2s ease-in-out;
    -moz-transition: border-color .2s ease-in-out;
    -ms-transition: border-color .2s ease-in-out;
    transition: border-color .2s ease-in-out;
  }
  .sq-input--focus {
    border: 1px solid #4A90E2;
  }
  .sq-input--error {
    border: 1px solid #E02F2F;
  }
  #sq-card-number {
    /*margin-bottom: 16px;*/
  }
</style>
<form method="post" id="payment-form">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form body--%>
  <div class="grid-x">
    <div class="small-12 cell">
      <div class="form-row">
        <label for="sq-card-number">
          <c:choose>
            <c:when test="${cart.grandTotal gt 0}">
              Pay with your Credit Card via Square <span class="required">*</span>
            </c:when>
            <c:otherwise>
              We validate your credit card information for all orders and gifts. We do not store or bill your credit card number.
            </c:otherwise>
          </c:choose>
          <c:if test="${testMode eq 'true'}"><span class="label warning">TEST MODE</span></c:if>
        </label>
        <div id="form-container">
<%--          <div id="sq-card"></div>--%>
          <div id="sq-card-number"></div>
          <div class="third" id="sq-expiration-date"></div>
          <div class="third" id="sq-cvv"></div>
          <div class="third" id="sq-postal-code"></div>
          <div id="card-errors" class="input-error" role="alert"></div>
          <button id="sq-creditcard" class="button-credit-card button primary" onclick="onGetCardNonce(event)">Save &amp; Continue</button>
        </div>
      </div>
    </div>
  </div>
</form>
<script type="text/javascript">
  // Create and initialize a payment form object
  const paymentForm = new SqPaymentForm({
    applicationId: "<c:out value="${squareAppId}" />",
    inputClass: 'sq-input',
    autoBuild: false,
    inputStyles: [{
      fontSize: '16px',
      lineHeight: '24px',
      padding: '16px'
      // placeholderColor: '#666666',
      // backgroundColor: '#a0a0a0'
    }],
    // Initialize the credit card placeholders
<%-- in beta
    card: {
      elementId: 'sq-card'
    },
--%>
    cardNumber: {
      elementId: 'sq-card-number',
      placeholder: 'Card Number'
    },
    cvv: {
      elementId: 'sq-cvv',
      placeholder: 'CVV'
    },
    expirationDate: {
      elementId: 'sq-expiration-date',
      placeholder: 'MM/YY'
    },
    postalCode: {
      elementId: 'sq-postal-code',
      placeholder: 'Postal'
    },
    callbacks: {
      /*
      * Triggered when: SqPaymentForm completes a card nonce request
      */
      cardNonceResponseReceived: function (errors, nonce, cardData) {
        if (errors) {
          var errorMsg = '';
          errors.forEach(function (error) {
            errorMsg += ('  ' + error.message);
          });
          var errorElement = document.getElementById('card-errors');
          errorElement.textContent = errorMsg;
          return;
        }
        var form = document.getElementById('payment-form');
        var hiddenInput = document.createElement('input');
        hiddenInput.setAttribute('type', 'hidden');
        hiddenInput.setAttribute('name', 'squareNonce');
        hiddenInput.setAttribute('value', nonce);
        form.appendChild(hiddenInput);
        form.submit();
      }
    }
  });
  paymentForm.build();

  function onGetCardNonce(event) {
    event.preventDefault();
    paymentForm.requestCardNonce();
  }
</script>