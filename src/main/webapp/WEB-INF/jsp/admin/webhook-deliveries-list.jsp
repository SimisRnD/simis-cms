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
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="webhookDeliveryList" class="java.util.ArrayList" scope="request"/>
<h2 class="h4">Delivery Log</h2>
<%@include file="../page_messages.jspf" %>
<c:choose>
  <c:when test="${empty webhookSubscription}">
    <p>The webhook subscription was not found.</p>
  </c:when>
  <c:otherwise>
    <p class="help-text">Deliveries for <code><c:out value="${webhookSubscription.url}" /></code>. Test sends are never recorded here -- only genuine deliveries triggered by a real event.</p>
    <p class="help-text"><strong>Pending</strong> is waiting on its first attempt. <strong>Failed (retrying)</strong> means every attempt so far got no successful response, but retries remain -- automatic, roughly 10 minutes, 1 hour, 4 hours, then 24 hours after the first attempt. <strong>Exhausted</strong> means all 5 attempts failed and this delivery will not be retried again; check the Response Code/Snippet columns for why (a non-2xx status, a timeout, or an unreachable/blocked URL), fix the receiving endpoint, and use Send a Test Delivery on the subscription to confirm it's fixed -- there is no way to manually re-trigger the original exhausted delivery itself.</p>
    <a class="button small radius secondary float-left" href="${ctx}/admin/webhook-subscription?webhookSubscriptionId=${webhookSubscription.id}">Back to Subscription</a>
    <table class="unstriped">
      <thead>
        <tr>
          <th>Created</th>
          <th>Event Type</th>
          <th>Status</th>
          <th>Attempts</th>
          <th>Response Code</th>
          <th>Response Snippet</th>
        </tr>
      </thead>
      <tbody>
        <c:forEach items="${webhookDeliveryList}" var="webhookDelivery">
          <tr>
            <td><fmt:formatDate value="${webhookDelivery.created}" pattern="yyyy-MM-dd HH:mm:ss"/></td>
            <td><c:out value="${webhookDelivery.eventType}" /></td>
            <td>
              <c:choose>
                <c:when test="${webhookDelivery.status eq 'delivered'}"><span class="label success">Delivered</span></c:when>
                <c:when test="${webhookDelivery.status eq 'exhausted'}"><span class="label alert">Exhausted</span></c:when>
                <c:when test="${webhookDelivery.status eq 'failed'}"><span class="label warning">Failed (retrying)</span></c:when>
                <c:otherwise><span class="label">Pending</span></c:otherwise>
              </c:choose>
            </td>
            <td><c:out value="${webhookDelivery.attemptCount}" /></td>
            <td><c:out value="${webhookDelivery.responseCode}" /></td>
            <td>
              <%-- The response body is the receiving server's own, externally-influenced content --
                   c:out escapes it, and the repository already truncates it to 1000 characters. --%>
              <small><c:out value="${webhookDelivery.responseSnippet}" /></small>
            </td>
          </tr>
        </c:forEach>
        <c:if test="${empty webhookDeliveryList}">
          <tr>
            <td colspan="6">No deliveries yet</td>
          </tr>
        </c:if>
      </tbody>
    </table>
  </c:otherwise>
</c:choose>
