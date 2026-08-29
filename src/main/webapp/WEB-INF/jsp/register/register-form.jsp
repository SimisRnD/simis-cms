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
<jsp:useBean id="user" class="com.simisinc.platform.domain.model.User" scope="request"/>
<jsp:useBean id="useCaptcha" class="java.lang.String" scope="request"/>
<c:if test="${useCaptcha eq 'true' && !empty googleSiteKey}">
  <%-- enterprise.js for a key issued by Google's current console: those cannot be verified by
     the legacy siteverify endpoint at all, so they take the Enterprise assessment API and its own
     script family. The button markup below is identical either way -- Google's integration panel
     prints the same data-sitekey/data-callback form for both. Issue 1615. --%>
  <c:choose>
    <c:when test="${googleEnterprise eq 'true'}">
      <script src='https://www.google.com/recaptcha/enterprise.js?render=<c:out value="${googleSiteKey}"/>' nonce="${cspNonce}"></script>
    </c:when>
    <c:otherwise>
      <script src='https://www.google.com/recaptcha/api.js' nonce="${cspNonce}"></script>
    </c:otherwise>
  </c:choose>
  <script nonce="${cspNonce}">
    function onSubmit(token) {
      document.getElementById("form${widgetContext.uniqueId}").submit();
    }
  </script>
</c:if>
<c:if test="${useCaptcha eq 'true' && !empty turnstileSiteKey}">
  <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer nonce="${cspNonce}"></script>
</c:if>
<form id="form${widgetContext.uniqueId}" method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}" />
  <input type="hidden" name="token" value="${userSession.formToken}" />
  <%-- Form Content --%>
  <div class="dialog-header">
    <c:if test="${!empty title}">
      <h1><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h1>
    </c:if>
    <%@include file="../page_messages.jspf" %>
    <p><small>Already have an account? <a href="${ctx}/login">Sign in</a></small></p>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 cell">
      <label>First Name
        <input name="firstName" type="text" placeholder="First Name" value="<c:out value="${user.firstName}"/>" required>
      </label>
      <label>Last Name
        <input name="lastName" type="text" placeholder="Last Name" value="<c:out value="${user.lastName}"/>" required>
      </label>
      <label>Email Address
        <input name="email" type="email" placeholder="Email Address" value="<c:out value="${user.email}"/>" required>
      </label>
      <label>Password
        <input name="password" type="password" placeholder="Password" autocomplete="off" required>
      </label>
      <p class="help-text" id="passwordHelpText">Passwords must be at least 6 characters</p>
      <label>Re-Enter Password
        <input name="password2" type="password" placeholder="Re-Enter Password" autocomplete="off" required>
      </label>
      <c:choose>
        <c:when test="${useCaptcha eq 'true' && !empty googleSiteKey}">
          <p>
            <button
                class="g-recaptcha button radius expanded"
                data-sitekey="<c:out value="${googleSiteKey}" />"
                data-callback="onSubmit">
              Create Account
            </button>
          </p>
        </c:when>
        <c:when test="${useCaptcha eq 'true' && !empty turnstileSiteKey}">
          <div class="cf-turnstile" data-sitekey="<c:out value="${turnstileSiteKey}" />"></div>
          <p>
            <input type="submit" class="button radius primary" value="Create Account"/>
          </p>
        </c:when>
        <c:when test="${useCaptcha eq 'true'}">
          <p>
            Please enter the text value you see in the image:<br />
            <img src="/assets/captcha" class="margin-bottom-10" alt="captcha" height="40" decoding="async" />
            <a href="#" data-captcha-refresh class="margin-left-5" title="Get a new image" aria-label="Get a new captcha image"><i class="fa fa-sync-alt"></i></a><br />
            <input type="text" name="captcha" value="" required/>
            <input type="submit" class="button radius primary" value="Create Account"/>
          </p>
        </c:when>
        <c:otherwise>
          <p>
            <input type="submit" class="button primary radius expanded" value="Create Account"/>
          </p>
        </c:otherwise>
      </c:choose>
      <c:if test="${showLegalLinks eq 'true'}">
        <p><small>By signing up, you agree to the <a href="${ctx}/legal/terms" target="_blank">Terms of Use</a> and <a href="${ctx}/legal/privacy" target="_blank">Privacy Policy</a></small></p>
      </c:if>
    </div>
  </div>
</form>