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
<jsp:useBean id="oAuthProvider" class="java.lang.String" scope="request"/>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}" />
  <input type="hidden" name="token" value="${userSession.formToken}" />
  <%-- Form Content --%>
  <div class="dialog-header">
    <c:if test="${!empty title}">
      <h1><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h1>
    </c:if>
    <c:if test="${'true' eq sitePropertyMap['site.registrations']}">
      <p><small>New here? <a href="${ctx}/register">Create an account</a></small></p>
    </c:if>
  </div>
  <%@include file="../page_messages.jspf" %>
  <div class="grid-x grid-margin-x">
    <div class="small-12 cell">
      <c:choose>
        <c:when test="${mfaRequired eq 'true'}">
          <p>Enter the 6-digit code from your authenticator app.</p>
          <label>Authentication code
            <input name="code" type="text" inputmode="numeric" pattern="[0-9]*" autocomplete="one-time-code" placeholder="123456" autofocus required>
          </label>
          <p class="login-stay-signed-in">
            <input id="stay-logged-in" name="stayLoggedIn" value="on" type="checkbox" checked><label for="stay-logged-in">Stay logged in</label>
          </p>
          <p><input type="submit" class="button primary radius expanded" value="Verify"></input></p>
        </c:when>
        <c:otherwise>
          <%-- autocomplete="username" / "current-password" are the standard sign-in pair. The
               password field previously carried autocomplete="off", which is worth calling out
               because it looks like the cautious choice and is the opposite: it discourages
               password managers, and someone who cannot store a password picks one they can
               remember. NIST SP 800-63B asks verifiers to FACILITATE password manager use for
               exactly that reason, and WCAG 2.1 SC 1.3.5 (Identify Input Purpose) wants a field's
               purpose declared. The value was inherited from the initial code drop rather than
               chosen: the MFA branch above already uses one-time-code correctly, and the codebase
               uses email, new-password, given-name and cc-number elsewhere. --%>
          <label>Email
            <input name="email" type="text" placeholder="Email" autocomplete="username" required>
          </label>
          <%-- The input sits outside the label rather than inside it: a button nested in a label has
               its clicks forwarded to the labelled control, which would steal focus from the toggle. --%>
          <label for="login-password">Password</label>
          <div class="password-field">
            <input id="login-password" name="password" type="password" placeholder="Password" autocomplete="current-password" required>
            <button type="button" class="secret-reveal-toggle" data-reveal-secret hidden
                    aria-pressed="false" aria-label="Show the value while typing"
                    title="Show the value while typing"><i class="fa fa-eye" aria-hidden="true"></i></button>
          </div>
          <p class="help-text text-right">
            <a href="${ctx}/forgot-password">Forgot your password?</a>
          </p>
          <%-- "Stay logged in" sits BEFORE the submit button. It changes what submitting does, so it
               has to be readable before the visitor commits. Below a full-width primary action, with
               nothing after it, it was both out of order and easy to miss entirely. --%>
          <p class="login-stay-signed-in">
            <input id="stay-logged-in" name="stayLoggedIn" value="on" type="checkbox" checked><label for="stay-logged-in">Stay logged in</label>
          </p>
          <p><input type="submit" class="button primary radius expanded" value="Sign In"></input></p>
          <c:if test="${!empty oAuthProvider}">
            <p><a href="${ctx}/" class="button secondary radius expanded">Login with <c:out value="${oAuthProvider}" /></a></p>
          </c:if>
        </c:otherwise>
      </c:choose>
    </div>
  </div>
</form>