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
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}" />
  <input type="hidden" name="token" value="${userSession.formToken}" />
  <input type="hidden" name="returnUrl" value="${fn:escapeXml(returnUrl)}" />
  <%-- Form Content --%>
  <div class="dialog-header">
    <h1><i class="fa fa-lock"></i> Confirm your identity</h1>
    <p>This action requires you to re-verify your identity.</p>
  </div>
  <%@include file="../page_messages.jspf" %>
  <div class="grid-x grid-margin-x">
    <div class="small-12 cell">
      <c:choose>
        <c:when test="${mfaEnabled eq 'true'}">
          <p>Enter your password <strong>or</strong> the 6-digit code from your authenticator app.</p>
          <label>Password
            <input name="password" type="password" placeholder="Password" autocomplete="current-password" autofocus>
          </label>
          <label>Authenticator code
            <input name="code" type="text" inputmode="numeric" pattern="[0-9]*" autocomplete="one-time-code" placeholder="123456">
          </label>
        </c:when>
        <c:otherwise>
          <p>Enter your password to continue.</p>
          <label>Password
            <input name="password" type="password" placeholder="Password" autocomplete="current-password" autofocus required>
          </label>
        </c:otherwise>
      </c:choose>
      <p><input type="submit" class="button primary radius expanded" value="Verify"></p>
    </div>
  </div>
</form>
