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
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<c:if test="${!empty title}">
  <h3><c:out value="${title}"/></h3>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:choose>
  <%-- Two-factor authentication is ON --%>
  <c:when test="${mfaEnabled eq 'true'}">
    <p><i class="fa fa-lock"></i> Two-factor authentication is <strong>on</strong>. You'll be asked for a code from your authenticator app each time you sign in.</p>
    <form method="post">
      <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
      <input type="hidden" name="token" value="${userSession.formToken}"/>
      <input type="hidden" name="action" value="disable"/>
      <input type="submit" class="button alert radius" value="Turn off two-factor authentication"
             onclick="return confirm('Turn off two-factor authentication for your account?');"/>
    </form>
  </c:when>
  <%-- Enrollment in progress: scan/enter the secret, then confirm a code --%>
  <c:when test="${mfaEnrolling eq 'true'}">
    <p>Scan this code with an authenticator app (Google Authenticator, Authy, 1Password, and others), or enter the setup key by hand.</p>
    <div class="mfa-qrcode" data-otpauth="<c:out value='${otpauthUri}'/>"></div>
    <p>Setup key: <code><c:out value="${mfaSecret}"/></code></p>
    <p class="show-for-small-only">
      <a href="<c:out value='${otpauthUri}'/>">Tap here to add it to an app on this device</a>
    </p>
    <form method="post">
      <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
      <input type="hidden" name="token" value="${userSession.formToken}"/>
      <input type="hidden" name="action" value="confirm"/>
      <label>Enter the 6-digit code from your app to finish
        <input name="code" type="text" inputmode="numeric" pattern="[0-9]*" autocomplete="one-time-code"
               placeholder="123456" autofocus required>
      </label>
      <p><input type="submit" class="button primary radius" value="Turn on two-factor authentication"/></p>
    </form>
    <form method="post">
      <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
      <input type="hidden" name="token" value="${userSession.formToken}"/>
      <input type="hidden" name="action" value="cancel"/>
      <input type="submit" class="button secondary radius" value="Cancel"/>
    </form>
  </c:when>
  <%-- Two-factor authentication is OFF --%>
  <c:otherwise>
    <p>Two-factor authentication adds a second step when you sign in: a rotating code from an authenticator app on your phone. It's currently <strong>off</strong>.</p>
    <form method="post">
      <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
      <input type="hidden" name="token" value="${userSession.formToken}"/>
      <input type="hidden" name="action" value="start"/>
      <input type="submit" class="button primary radius" value="Set up two-factor authentication"/>
    </form>
  </c:otherwise>
</c:choose>
<script src="${ctx}/javascript/mfa-qrcode.js"></script>
