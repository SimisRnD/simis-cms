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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="oAuthProvider" class="java.lang.String" scope="request"/>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}" />
  <input type="hidden" name="token" value="${userSession.formToken}" />
  <%-- Form Content --%>
  <div class="dialog-header">
    <c:if test="${!empty title}">
      <h1><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h1>
    </c:if>
    <c:if test="${'true' eq sitePropertyMap['site.registrations']}">
      <p><small>New here? <a href="${ctx}/register">Create an account</a></small></p>
    </c:if>
  </div>
  <%@include file="../page_messages.jspf" %>
  <div class="grid-x grid-margin-x">
    <div class="small-12 cell">
      <label>Email
        <input name="email" type="text" placeholder="Email" required>
      </label>
      <label>Password
        <input name="password" type="password" placeholder="Password" autocomplete="off" required>
      </label>
      <p class="help-text text-right">
        <a href="${ctx}/forgot-password">Forgot password</a>
      </p>
      <p><input type="submit" class="button primary radius expanded" value="Sign In"></input></p>
      <c:if test="${!empty oAuthProvider}">
        <p><a href="${ctx}/" class="button secondary radius expanded">Login with <c:out value="${oAuthProvider}" /></a></p>
      </c:if>
      <input id="stay-logged-in" name="stayLoggedIn" value="on" type="checkbox" checked><label for="stay-logged-in">Stay logged in</label>
    </div>
  </div>
</form>