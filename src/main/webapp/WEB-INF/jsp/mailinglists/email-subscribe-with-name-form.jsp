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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="formFieldList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="useCaptcha" class="java.lang.String" scope="request"/>
<c:if test="${useCaptcha eq 'true' && !empty googleSiteKey}">
<script src='https://www.google.com/recaptcha/api.js'></script>
<script>
  function onSubmit(token) {
    document.getElementById("form${widgetContext.uniqueId}").submit();
  }
</script>
</c:if>
<form id="form${widgetContext.uniqueId}" method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <div class="grid-container">
    <div class="grid-x grid-margin-x align-bottom">
      <div class="small-12 medium-5 cell">
        <label>Your Name
          <input class="input-group-field" type="text" name="firstName" placeholder="Your name" required>
        </label>
      </div>
      <div class="small-12 medium-5 cell">
        <label>Your Email
          <input class="input-group-field" type="text" name="email" placeholder="Your email" required>
        </label>
      </div>
      <div class="small-12 medium-2 cell">
        <c:choose>
          <c:when test="${useCaptcha eq 'true' && !empty googleSiteKey}">
            <button class="g-recaptcha button radius large success expanded"
                data-sitekey="<c:out value="${googleSiteKey}" />"
                data-callback="onSubmit">
              <c:out value="${buttonName}" />
            </button>
          </c:when>
          <c:when test="${useCaptcha eq 'true'}">
            Please enter the text value you see in the image:<br />
            <img src="/assets/captcha" class="margin-bottom-10" /><br />
            <input type="text" name="captcha" value="" required/>
            <input type="submit" class="button radius success" value="<c:out value="${buttonName}" />"/>
          </c:when>
          <c:otherwise>
            <input type="submit" class="button no-gap" value="<c:out value="${buttonName}" />"/>
          </c:otherwise>
        </c:choose>
      </div>
    </div>
  </div>
</form>