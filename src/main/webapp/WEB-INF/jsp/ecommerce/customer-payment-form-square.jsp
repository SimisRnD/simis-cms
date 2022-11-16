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
<jsp:useBean id="customer" class="com.simisinc.platform.domain.model.ecommerce.Customer" scope="request"/>
<jsp:useBean id="payment" class="com.simisinc.platform.domain.model.ecommerce.Payment" scope="request"/>
<jsp:useBean id="squareAppId" class="java.lang.String" scope="request"/>
<jsp:useBean id="squareLocationId" class="java.lang.String" scope="request"/>
<jsp:useBean id="testMode" class="java.lang.String" scope="request"/>
<%-- Square --%>
<c:choose>
  <c:when test="${testMode eq 'true'}">
    <script type="text/javascript" src="https://sandbox.web.squarecdn.com/v1/square.js"></script>
  </c:when>
  <c:otherwise>
    <script type="text/javascript" src="https://web.squarecdn.com/v1/square.js"></script>
  </c:otherwise>
</c:choose>
<%-- Page Scripts --%>
<%@include file="../page_messages.jspf" %>
<script>
    const appId = '<c:out value="${squareAppId}" />';
    const locationId = '<c:out value="${squareLocationId}" />';

    async function initializeCard(payments) {
      const card = await payments.card();
      await card.attach('#card-container');
      return card;
    }

    async function tokenize(paymentMethod) {
      const tokenResult = await paymentMethod.tokenize();
      if (tokenResult.status === 'OK') {
        return tokenResult.token;
      } else {
        let errorMessage = `Tokenization failed with status: ${tokenResult.status}`;
        if (tokenResult.errors) {
          errorMessage += ` and errors: ${JSON.stringify(
            tokenResult.errors
          )}`;
        }

        throw new Error(errorMessage);
      }
    }

    // status is either SUCCESS or FAILURE;
    function displayPaymentResults(status) {
      const statusContainer = document.getElementById(
        'payment-status-container'
      );
      if (status === 'SUCCESS') {
        statusContainer.classList.remove('is-failure');
        statusContainer.classList.add('is-success');
      } else {
        statusContainer.classList.remove('is-success');
        statusContainer.classList.add('is-failure');
      }
      statusContainer.style.visibility = 'visible';
    }

    document.addEventListener('DOMContentLoaded', async function () {
      if (!window.Square) {
        throw new Error('Square.js failed to load properly');
      }

      let payments;
      try {
        payments = window.Square.payments(appId, locationId);
      } catch {
        const statusContainer = document.getElementById(
          'payment-status-container'
        );
        statusContainer.className = 'missing-credentials';
        statusContainer.style.visibility = 'visible';
        return;
      }

      let card;
      try {
        card = await initializeCard(payments);
      } catch (e) {
        console.error('Initializing Card failed', e);
        return;
      }

      // Checkpoint 2.
      async function handlePaymentMethodSubmission(event, paymentMethod) {
        event.preventDefault();

        try {
          // disable the submit button as we await tokenization and make a payment request.
          cardButton.disabled = true;
          const token = await tokenize(paymentMethod);

          // submit the form with the token
          var form = document.getElementById('payment-form');
          var hiddenInput = document.createElement('input');
          hiddenInput.setAttribute('type', 'hidden');
          hiddenInput.setAttribute('name', 'squareNonce');
          hiddenInput.setAttribute('value', token);
          form.appendChild(hiddenInput);
          form.submit();
        } catch (e) {
          cardButton.disabled = false;
          displayPaymentResults('FAILURE');
          console.error(e.message);
        }
      }

      const cardButton = document.getElementById('card-button');
      cardButton.addEventListener('click', async function (event) {
        await handlePaymentMethodSubmission(event, card);
      });
    });
</script>
<style>
  .sq-card-iframe-container {
    margin-top: 16px;
  }
  /*
  .sq-card-wrapper .sq-card-message-no-error, .sq-card-wrapper .sq-card-message-no-error::before {
    color: #ffffff !important;
  }
  .sq-card-wrapper .sq-card-message-no-error::before {
    background-color: #ffffff;
  }
  */
</style>
<form method="post" id="payment-form">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form body--%>
  <div class="grid-x">
    <div class="small-12 cell">
      <div class="form-row">
        <label>
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
          <div id="card-container"></div>
          <button id="card-button" type="button" class="button-credit-card button primary">Verify &amp; Continue</button>
        </div>
      </div>
    </div>
  </div>
</form>
