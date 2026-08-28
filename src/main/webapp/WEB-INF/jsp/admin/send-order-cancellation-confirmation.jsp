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
<form method="post" id="sendOrderCancellationConfirmationForm">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- The form --%>
  <input type="hidden" name="uniqueId" value="${order.uniqueId}"/>
  <button id="sendOrderCancellationConfirmationButton" class="button primary expanded">Send Order Cancellation Confirmation</button>
</form>
<script nonce="${cspNonce}">
  function sendOrderCancellationConfirmation() {
    if (document.getElementById("sendOrderCancellationConfirmationButton").disabled === true) {
      return false;
    }
    if (confirm('Send an order cancellation confirmation e-mail?')) {
      document.getElementById("sendOrderCancellationConfirmationButton").disabled = true;
      return true;
    }
    return false;
  }

  // issue #1188: this guard was an inline onsubmit attribute. CSP (script-src-attr) blocks inline
  // handler attributes -- the nonce authorises this block, not an attribute calling into it -- so
  // the confirm and the double-submit guard were both skipped and the mail went out anyway.
  document.addEventListener('DOMContentLoaded', function () {
    var form = document.getElementById('sendOrderCancellationConfirmationForm');
    if (form) {
      form.addEventListener('submit', function (event) {
        if (!sendOrderCancellationConfirmation()) {
          event.preventDefault();
        }
      });
    }
  });
</script>