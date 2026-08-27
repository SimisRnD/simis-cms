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
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="order" class="com.simisinc.platform.domain.model.ecommerce.Order" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${testMode eq 'true'}"><span class="label warning">TEST MODE</span></c:if>
<button type="button" class="button primary expanded" onclick="openShipConfirm()" id="shipOrderButton">Send Order to Shipping</button>

<div class="reveal small" id="shipConfirmReveal" data-reveal data-close-on-click="false" role="dialog" aria-modal="true" aria-labelledby="shipConfirmTitle">
  <button class="close-button" data-close aria-label="Close modal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
  <h4 id="shipConfirmTitle"><i class="fa fa-truck"></i> Confirm Shipment</h4>
  <p>Ship this order to the carrier? This will notify the customer and update the order status.</p>
  <p class="subheader"><i class="fa fa-info-circle"></i> This action cannot be undone.</p>
  <form method="post" id="shipForm">
    <%-- Required by controller --%>
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <%-- The form --%>
    <input type="hidden" name="uniqueId" value="${order.uniqueId}"/>
    <div class="button-group">
      <button type="button" class="button secondary" data-close>Cancel</button>
      <button type="submit" class="button primary expanded" id="shipSubmitBtn">
        <span class="btn-text">Confirm Shipment</span>
      </button>
    </div>
  </form>
</div>

<script nonce="${cspNonce}">
  function openShipConfirm() {
    if (document.getElementById("shipOrderButton").disabled === true) {
      return;
    }
    new Foundation.Reveal(document.getElementById('shipConfirmReveal')).open();
  }

  document.getElementById('shipForm').addEventListener('submit', function(e) {
    var button = document.getElementById('shipOrderButton');
    var submitBtn = document.getElementById('shipSubmitBtn');
    var btnText = submitBtn.querySelector('.btn-text');
    button.disabled = true;
    submitBtn.disabled = true;
    btnText.innerHTML = '<i class="fa fa-spinner fa-spin"></i> Shipping...';
  });
</script>
