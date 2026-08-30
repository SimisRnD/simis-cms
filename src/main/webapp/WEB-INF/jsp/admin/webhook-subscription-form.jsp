<%--
  ~ Copyright 2026 SimIS Inc.
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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="webhookSubscription" class="com.simisinc.platform.domain.model.webhooks.WebhookSubscription" scope="request"/>
<jsp:useBean id="eventTypeList" class="java.util.ArrayList" scope="request"/>
<c:choose>
  <c:when test="${webhookSubscription.id eq -1}"><h2 class="h4">New Webhook Subscription</h2></c:when>
  <c:otherwise><h2 class="h4">Edit Webhook Subscription</h2></c:otherwise>
</c:choose>
<%@include file="../page_messages.jspf" %>

<c:if test="${!empty generatedSecret}">
  <div class="callout warning radius">
    <h5><c:if test="${secretWasRotated}">New secret</c:if><c:if test="${!secretWasRotated}">Signing secret</c:if></h5>
    <p>Copy this now and store it in the receiving system's webhook configuration -- <strong>it will not be shown again</strong>.<c:if test="${secretWasRotated}"> The previous secret stopped working immediately.</c:if></p>
    <p><code id="webhook-secret"><c:out value="${generatedSecret}" /></code></p>
  </div>
</c:if>

<c:if test="${!empty testSendResult}">
  <div class="callout radius">
    <h5>Test send result</h5>
    <c:choose>
      <c:when test="${testSendResult.requestSent}">
        <p>Response status: <strong><c:out value="${testSendResult.statusCode}" /></strong></p>
        <c:if test="${!empty testSendResult.responseSnippet}">
          <p>Response body:</p>
          <pre style="white-space: pre-wrap; word-break: break-all;"><c:out value="${testSendResult.responseSnippet}" /></pre>
        </c:if>
      </c:when>
      <c:otherwise>
        <p>No response was received -- the URL may be unreachable, timed out, or was blocked by the outbound-request safety check.</p>
      </c:otherwise>
    </c:choose>
    <p class="help-text">Signature sent: <code><c:out value="${testSendResult.signatureHeaderValue}" /></code></p>
    <p class="help-text">This was a synchronous test only -- no delivery-log entry was created and nothing was queued for retry.</p>
  </div>
</c:if>

<form method="post" autocomplete="off">
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <input type="hidden" name="id" value="${webhookSubscription.id}"/>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-10 large-8 cell">
      <label for="url">URL <span class="required">*</span>
        <input type="url" id="url" name="url" maxlength="2000" placeholder="https://example.com/hooks/simis" value="<c:out value="${webhookSubscription.url}" />" <c:if test="${webhookSubscription.id eq -1}">autofocus="autofocus"</c:if> required>
      </label>
      <p class="help-text" id="urlHelpText">This application sends a signed HTTP POST here whenever a subscribed event happens.</p>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-10 large-8 cell">
      <fieldset>
        <legend>Event Types <span class="required">*</span></legend>
        <c:forEach items="${eventTypeList}" var="eventType">
          <label>
            <input type="checkbox" name="eventType" value="${eventType.id}"<c:if test="${webhookSubscription.eventTypeList.contains(eventType.id)}"> checked</c:if>>
            <c:out value="${eventType.label}" /> <small class="help-text"><c:out value="${eventType.id}" /></small>
          </label>
        </c:forEach>
      </fieldset>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-10 large-8 cell">
      <label>Enabled
        <input id="enabled" type="checkbox" name="enabled" value="true" <c:if test="${webhookSubscription.id eq -1 || webhookSubscription.enabled}">checked</c:if>/>
      </label>
      <p class="help-text">Disabled subscriptions are kept but never receive deliveries.</p>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 cell">
      <p>
        <input type="submit" class="button radius success" value="Save"/>
        <a class="button radius secondary" href="${ctx}/admin/webhooks">Cancel</a>
      </p>
    </div>
  </div>
</form>

<c:if test="${webhookSubscription.id ne -1}">
  <hr/>
  <h5>Actions</h5>
  <p>
    <a class="button radius secondary" href="${ctx}/admin/webhook-deliveries?webhookSubscriptionId=${webhookSubscription.id}">View Delivery Log</a>
    <a href="#" class="button radius secondary" data-confirm-post="Rotate the signing secret? The current secret will stop working immediately." data-post-url="${widgetContext.uri}?action=rotateSecret&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webhookSubscriptionId=${webhookSubscription.id}">Rotate Secret</a>
  </p>

  <h5>Send a Test Delivery</h5>
  <c:choose>
    <c:when test="${empty webhookSubscription.eventTypeList}">
      <p class="help-text">Select and save at least one event type to enable test sends.</p>
    </c:when>
    <c:otherwise>
      <p class="help-text">Fires a real, signed HTTP POST with a sample payload to the URL above. This is a synchronous test only: it does not create a delivery-log entry and is never retried.</p>
      <form method="post">
        <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
        <input type="hidden" name="token" value="${userSession.formToken}"/>
        <input type="hidden" name="action" value="testSend"/>
        <input type="hidden" name="webhookSubscriptionId" value="${webhookSubscription.id}"/>
        <label for="testEventType">Simulate event
          <select id="testEventType" name="testEventType">
            <c:forEach items="${webhookSubscription.eventTypeList}" var="subscribedEventType">
              <option value="<c:out value="${subscribedEventType}" />"><c:out value="${subscribedEventType}" /></option>
            </c:forEach>
          </select>
        </label>
        <input type="submit" class="button radius" value="Send Test"/>
      </form>
    </c:otherwise>
  </c:choose>
</c:if>
