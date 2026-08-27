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
<jsp:useBean id="useCaptcha" class="java.lang.String" scope="request"/>
<jsp:useBean id="onlineMailingLists" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="countryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="email" class="com.simisinc.platform.domain.model.mailinglists.Email" scope="request"/>
<c:if test="${useCaptcha eq 'true' && !empty googleSiteKey}">
<script src='https://www.google.com/recaptcha/api.js' nonce="${cspNonce}"></script>
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
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-6 cell">
      <label>First Name <span class="required">*</span>
        <input type="text" name="firstName" placeholder="First name" autocomplete="given-name" value="<c:out value="${email.firstName}" />" required>
      </label>
    </div>
    <div class="small-12 medium-6 cell">
      <label>Last Name <span class="required">*</span>
        <input type="text" name="lastName" placeholder="Last name" autocomplete="family-name" value="<c:out value="${email.lastName}" />" required>
      </label>
    </div>
    <div class="small-12 medium-6 cell">
      <label>Email <span class="required">*</span>
        <input type="text" name="email" placeholder="name@email.com" autocomplete="email" value="<c:out value="${email.email}" />" required>
      </label>
    </div>
    <div class="small-12 medium-6 cell">
      <label>Title
        <input type="text" name="title" placeholder="Job title" autocomplete="organization-title" value="<c:out value="${email.title}" />">
      </label>
    </div>
    <div class="small-12 medium-6 cell">
      <label>Company Name
        <input type="text" name="organization" placeholder="Company name" autocomplete="organization" value="<c:out value="${email.organization}" />">
      </label>
    </div>
    <div class="small-12 medium-6 cell">
      <label>Business Phone
        <input type="text" name="phone" placeholder="Business phone" autocomplete="tel" value="<c:out value="${email.phone}" />">
      </label>
    </div>
    <div class="small-12 cell">
      <label>Country
        <select name="country">
          <option value="">Choose</option>
          <c:forEach items="${countryList}" var="listedCountry">
            <option value="<c:out value="${listedCountry.title}" />"<c:if test="${email.country eq listedCountry.title}"> selected</c:if>><c:out value="${listedCountry.title}" /></option>
          </c:forEach>
        </select>
      </label>
    </div>
  </div>
  <%-- Issue #598: let a visitor choose which public list(s) to join, same as the inline form --
       omitted entirely (no checkboxes, no hidden input) when nothing is marked show_online, so a
       default/fresh install's single-list signup behaves exactly as it did before this existed. --%>
  <c:if test="${fn:length(onlineMailingLists) > 1}">
    <p><strong>Email Preferences</strong></p>
    <p class="help-text">Please select your communication preferences.</p>
    <c:forEach items="${onlineMailingLists}" var="list">
      <label class="inline-list-checkbox">
        <input type="checkbox" name="mailingListId" value="${list.id}" checked>
        <c:out value="${list.title}" />
      </label>
    </c:forEach>
  </c:if>
  <c:if test="${fn:length(onlineMailingLists) == 1}">
    <input type="hidden" name="mailingListId" value="${onlineMailingLists[0].id}">
  </c:if>
  <div class="grid-x">
    <div class="small-12 cell">
      <c:choose>
        <c:when test="${useCaptcha eq 'true' && !empty googleSiteKey}">
          <button class="g-recaptcha button radius large success expanded"
              data-sitekey="<c:out value="${googleSiteKey}" />"
              data-callback="onSubmit">
            <c:out value="${buttonName}" />
          </button>
        </c:when>
        <c:when test="${useCaptcha eq 'true' && !empty turnstileSiteKey}">
          <div class="cf-turnstile" data-sitekey="<c:out value="${turnstileSiteKey}" />"></div>
          <input type="submit" class="button radius large success expanded" value="<c:out value="${buttonName}" />"/>
        </c:when>
        <c:when test="${useCaptcha eq 'true'}">
          <p class="help-text">Please enter the text value you see in the image:</p>
          <img src="/assets/captcha" class="margin-bottom-10" alt="captcha" height="40" decoding="async" /><br />
          <input type="text" name="captcha" value="" required/>
          <input type="submit" class="button radius large success expanded" value="<c:out value="${buttonName}" />"/>
        </c:when>
        <c:otherwise>
          <input type="submit" class="button radius large success expanded" value="<c:out value="${buttonName}" />"/>
        </c:otherwise>
      </c:choose>
      <p class="help-text">By subscribing, you agree to our <a href="/legal/privacy">Privacy Policy</a> and <a href="/legal/terms">Terms of Use</a>.</p>
    </div>
  </div>
</form>
