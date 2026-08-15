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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="webhookSubscriptionList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<p class="help-text">Webhook subscriptions deliver a signed HTTP POST to an external URL when a selected event happens -- content changes (page/blog publish), calendar events, form/order submissions, and user/mailing-list lifecycle events. The signing secret is shown once, right after it's created or rotated -- store it in the receiving system's webhook configuration; the receiving system uses it to verify the <code>X-Simis-Signature: sha256=&lt;hex&gt;</code> header (HMAC-SHA256 of the raw request body) actually came from this site. A failed delivery retries automatically for up to about a day before it's given up on -- see a subscription's Delivery Log for the history of a specific one.</p>
<a class="button small radius primary float-left" href="${ctx}/admin/webhook-subscription">Add a Webhook <i class="fa fa-arrow-circle-right"></i></a>
<table class="unstriped">
  <thead>
    <tr>
      <th>URL</th>
      <th>Event Types</th>
      <th>Status</th>
      <th width="220">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${webhookSubscriptionList}" var="webhookSubscription">
      <tr>
        <td>
          <a href="${ctx}/admin/webhook-subscription?webhookSubscriptionId=${webhookSubscription.id}"><c:out value="${webhookSubscription.url}" /></a>
        </td>
        <td>
          <small>
            <c:forEach items="${webhookSubscription.eventTypeList}" var="eventType" varStatus="eventTypeStatus">
              <c:if test="${!eventTypeStatus.first}">, </c:if><c:out value="${eventType}" />
            </c:forEach>
          </small>
        </td>
        <td>
          <c:choose>
            <c:when test="${webhookSubscription.enabled}">
              <span class="label success">Enabled</span>
            </c:when>
            <c:otherwise>
              <span class="label warning">Disabled</span>
            </c:otherwise>
          </c:choose>
        </td>
        <td>
          <a href="${ctx}/admin/webhook-subscription?webhookSubscriptionId=${webhookSubscription.id}" title="Edit"><i class="fa fa-edit"></i></a>
          <a href="${ctx}/admin/webhook-deliveries?webhookSubscriptionId=${webhookSubscription.id}" title="Delivery log"><i class="fa fa-list"></i></a>
          <c:choose>
            <c:when test="${webhookSubscription.enabled}">
              <a href="#" title="Disable" data-confirm-post="Disable this webhook subscription?" data-post-url="${widgetContext.uri}?action=toggleEnabled&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webhookSubscriptionId=${webhookSubscription.id}"><i class="fa fa-toggle-on"></i></a>
            </c:when>
            <c:otherwise>
              <a href="#" title="Enable" data-confirm-post="Enable this webhook subscription?" data-post-url="${widgetContext.uri}?action=toggleEnabled&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webhookSubscriptionId=${webhookSubscription.id}"><i class="fa fa-toggle-off"></i></a>
            </c:otherwise>
          </c:choose>
          <a href="#" title="Delete" data-confirm-post="Are you sure you want to delete the webhook for <c:out value="${webhookSubscription.url}" />? This cannot be undone." data-post-url="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webhookSubscriptionId=${webhookSubscription.id}"><i class="fa fa-remove"></i></a>
        </td>
      </tr>
    </c:forEach>
    <c:if test="${empty webhookSubscriptionList}">
      <tr>
        <td colspan="4">No webhook subscriptions were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
