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
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<%@ taglib prefix="product" uri="/WEB-INF/product-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="customer" class="com.simisinc.platform.domain.model.ecommerce.Customer" scope="request"/>
<jsp:useBean id="userFirstName" class="java.lang.String" scope="request"/>
<jsp:useBean id="userLastName" class="java.lang.String" scope="request"/>
<jsp:useBean id="userEmail" class="java.lang.String" scope="request"/>
<jsp:useBean id="allowRegistrations" class="java.lang.String" scope="request"/>
<jsp:useBean id="subscribeToNewsletter" class="java.lang.String" scope="request"/>
<%-- Form body--%>
<script>
  function checkForm${widgetContext.uniqueId}() {
    return true;
  }
</script>
<%@include file="../page_messages.jspf" %>
<p>Your email address will be used to send you order updates.</p>
<form method="post" onsubmit="return checkForm${widgetContext.uniqueId}()" autocomplete="on">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- The form --%>
  <div class="grid-x grid-margin-x">
    <div class="small-6 cell">
      <label for="firstName">First Name <span class="required">*</span>
        <input type="text" id="firstName" name="firstName" placeholder="Your First Name" value="<c:out value="${userFirstName}" />" autocomplete="given-name" required/>
      </label>
    </div>
    <div class="small-6 cell">
      <label for="lastName">Last Name <span class="required">*</span>
        <input type="text" id="lastName" name="lastName" placeholder="Your Last Name" value="<c:out value="${userLastName}" />" autocomplete="family-name" required/>
      </label>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-6 cell">
      <label>Email Address <span class="required">*</span>
        <input name="email" type="email" placeholder="Email Address" value="<c:out value="${userEmail}"/>" autocomplete="email" required>
      </label>
      <c:if test="${allowRegistrations eq 'true'}">
        <label>Password
          <input name="password" type="password" placeholder="Password" autocomplete="off">
        </label>
        <p class="help-text" id="passwordHelpText">Passwords must be at least 6 characters</p>
      </c:if>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 cell">
      <p class="checkout-stage-info">Receive product updates, launches, events, and more from us?</p>
      <div class="switch">
        <input class="switch-input" id="newsletter-yes-no" type="checkbox" name="newsletter" value="true"<c:if test="${subscribeToNewsletter eq 'true'}"> checked</c:if>>
        <label class="switch-paddle" for="newsletter-yes-no">
          <span class="switch-active" aria-hidden="true">Yes</span>
          <span class="switch-inactive" aria-hidden="true">No</span>          
        </label>
      </div>
    </div>
  </div>

  <div class="grid-x grid-margin-x">
    <c:choose>
      <c:when test="${allowRegistrations eq 'true'}">
        <div class="small-12 medium-6 cell">
          <button class="button primary" name="button" value="createAccount">Create your account</button>
        </div>
        <div class="small-12 medium-6 cell">
          <button class="button primary" name="button" value="guest">Continue as guest</button>
        </div>
      </c:when>
      <c:otherwise>
        <div class="small-12 medium-6 cell">
          <button class="button primary" name="button" value="guest">Save &amp; Continue</button>
        </div>
      </c:otherwise>
    </c:choose>
  </div>
</form>
