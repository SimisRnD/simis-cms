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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="app" class="com.simisinc.platform.domain.model.App" scope="request"/>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${app.id}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <c:if test="${app.id ne -1}">
    <label>Client ID
      <input type="text" class="no-gap" readonly value="<c:out value="${app.publicKey}"/>" aria-describedby="appClientIdHelpText" onclick="this.select();">
    </label>
    <p class="help-text" id="appClientIdHelpText">Send this as the <code>X-API-Key</code> request header (or a <code>key</code> query parameter) when calling this application's REST API. It identifies which app is calling, but isn't itself proof of identity -- most endpoints also require an authenticated user via Basic or Bearer auth on top of it. Not sensitive on its own; safe to share with anyone building against this app's API, or to embed directly in client-side scripts. There is deliberately no "Client Secret" shown anywhere for this App -- it isn't a credential to look for or rely on.</p>
  </c:if>
  <%-- Form Content --%>
  <label>Name <span class="required">*</span>
    <input type="text" placeholder="Give it a name..." name="name" value="<c:out value="${app.name}"/>" required>
  </label>
  <label>Description
    <input type="text" placeholder="Describe it..." name="summary" value="<c:out value="${app.summary}"/>">
  </label>
  <label>Enabled?
    <div class="switch large">
      <input class="switch-input" id="app-enabled-yes-no" type="checkbox" name="enabled" value="true" aria-describedby="appEnabledHelpText"<c:if test="${app.enabled}"> checked</c:if>>
      <label class="switch-paddle" for="app-enabled-yes-no">
        <span class="switch-active" aria-hidden="true">Yes</span>
        <span class="switch-inactive" aria-hidden="true">No</span>
      </label>
    </div>
  </label>
  <p class="help-text" id="appEnabledHelpText">Turn this off to immediately stop this App from authenticating API requests, without deleting its history or its Client ID. If a Client ID has been compromised, disabling it here takes effect right away.</p>
  <div class="button-container">
    <input type="submit" class="button radius success expanded" value="Save"/>
  </div>
</form>
<c:if test="${app.id ne -1}">
  <p class="help-text">To permanently remove this App instead, use the Delete action on the <a href="${ctx}/admin/apps">Apps list</a>. Prefer Delete over Enabled=No once a leaked credential has been confirmed and you've verified nothing else still needs its audit history -- Delete cannot be undone.</p>
  <p class="help-text"><strong>Best practice:</strong> after saving a Client ID to a new integration, confirm it's actually being used by checking the <strong>Devices</strong> count on the <a href="${ctx}/admin/apps">Apps list</a> once the integration has had a chance to make its first call -- a count that stays at zero usually means the key never made it into the client's configuration, rather than a problem on this end.</p>
</c:if>