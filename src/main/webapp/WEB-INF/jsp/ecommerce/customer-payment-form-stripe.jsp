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
<jsp:useBean id="customer" class="com.simisinc.platform.domain.model.ecommerce.Customer" scope="request"/>
<jsp:useBean id="cart" class="com.simisinc.platform.domain.model.ecommerce.Cart" scope="request"/>
<jsp:useBean id="payment" class="com.simisinc.platform.domain.model.ecommerce.Payment" scope="request"/>
<jsp:useBean id="stripeKey" class="java.lang.String" scope="request"/>
<jsp:useBean id="testMode" class="java.lang.String" scope="request"/>
<%-- Stripe --%>
<script src="https://js.stripe.com/v3/"></script>
<%-- Page Scripts --%>
<%@include file="../page_messages.jspf" %>
<form method="post" id="payment-form">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form body--%>
  <div class="grid-x">
    <div class="small-12 cell">
      <div class="form-row">
        <label for="card-element">
          <c:choose>
            <c:when test="${cart.grandTotal gt 0}">
              Pay with your Credit Card via Stripe <span class="required">*</span>
            </c:when>
            <c:otherwise>
              We validate your credit card information for all orders and gifts. We do not store or bill your credit card number.
            </c:otherwise>
          </c:choose>
          <c:if test="${testMode eq 'true'}"><span class="label warning">TEST MODE</span></c:if>
        </label>
        <div class="platform-ecommerce-card" id="card-element">
        </div>
        <div id="card-errors" role="alert"></div>
      </div>
    </div>
  </div>
  <div class="button-container">
    <button class="button primary" name="button" value="save">Save &amp; Continue</button>
  </div>
</form>
<script>
  var stripe = Stripe('<c:out value="${stripeKey}" />');
  var elements = stripe.elements();
  var style = {
    base: {
      fontSize: '16px',
      color: '#394546'
    }
  };

  var card = elements.create('card', {style: style});
  card.mount('#card-element');

  card.addEventListener('change', function (event) {
    var displayError = document.getElementById('card-errors');
    if (event.error) {
      displayError.textContent = event.error.message;
    } else {
      displayError.textContent = '';
    }
  });

  function stripeTokenHandler(token) {
    var form = document.getElementById('payment-form');
    var hiddenInput = document.createElement('input');
    hiddenInput.setAttribute('type', 'hidden');
    hiddenInput.setAttribute('name', 'stripeToken');
    hiddenInput.setAttribute('value', token.id);
    form.appendChild(hiddenInput);
    form.submit();
  }

  var form = document.getElementById('payment-form');
  form.addEventListener('submit', function (event) {
    event.preventDefault();
    stripe.createToken(card).then(function (result) {
      if (result.error) {
        var errorElement = document.getElementById('card-errors');
        errorElement.textContent = result.error.message;
      } else {
        // Send the token to your server.
        stripeTokenHandler(result.token);
      }
    });
  });
</script>