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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<style>
  .mfa-recovery-codes { columns: 2; column-gap: 1.5rem; margin: .6rem 0 1rem; }
  .mfa-recovery-codes li { break-inside: avoid; margin-bottom: .35rem; }
  .mfa-recovery-codes li code { display: inline-block; }
  .mfa-copy { margin: 0 0 0 .5rem; }
  .mfa-recovery-actions { display: flex; gap: .5rem; margin-top: .3rem; }
  .mfa-recovery-actions .button { margin: 0; }
</style>
<c:if test="${!empty title}">
  <h3><c:out value="${title}"/></h3>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:choose>
  <%-- Two-factor authentication is ON --%>
  <c:when test="${mfaEnabled eq 'true'}">
    <p><i class="fa fa-lock"></i> Two-factor authentication is <strong>on</strong>. You'll be asked for a code from your authenticator app each time you sign in.</p>
    <c:if test="${!empty recoveryCodes}">
      <div class="callout warning radius">
        <h5>Save your recovery codes</h5>
        <p>Each code works once, for signing in when you can't use your authenticator app. Store them somewhere safe &mdash; you won't be able to see them again.</p>
        <ul class="no-bullet mfa-recovery-codes" id="mfa-recovery-codes">
          <c:forEach items="${recoveryCodes}" var="recoveryCode">
            <li><code><c:out value="${recoveryCode}"/></code></li>
          </c:forEach>
        </ul>
        <p class="mfa-recovery-actions">
          <button type="button" class="button tiny secondary radius mfa-copy-codes">Copy all</button>
          <button type="button" class="button tiny secondary radius mfa-download-codes">Download</button>
        </p>
      </div>
    </c:if>
    <p>Recovery codes remaining: <strong><c:out value="${recoveryRemaining}"/></strong></p>
    <form method="post">
      <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
      <input type="hidden" name="token" value="${userSession.formToken}"/>
      <input type="hidden" name="action" value="regenerate"/>
      <input type="submit" class="button secondary radius" value="Generate new recovery codes"
             onclick="return confirm('Replace your recovery codes? Your current codes will stop working.');"/>
    </form>
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
    <p>Setup key: <code id="mfa-setup-key"><c:out value="${mfaSecret}"/></code><button type="button" class="button tiny secondary radius mfa-copy" data-copy-target="mfa-setup-key">Copy</button></p>
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
<script src="${ctx}/javascript/qrcode-generator-1.4.4/qrcode.js"></script>
<script src="${ctx}/javascript/mfa-qrcode.js"></script>
