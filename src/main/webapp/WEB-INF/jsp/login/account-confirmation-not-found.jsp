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
<jsp:useBean id="user" class="com.simisinc.platform.domain.model.User" scope="request"/>
<h4>
  An error occurred
</h4>
<p>
  This account may have already been validated, or the request expired.
  If you have trouble logging into your account then you can request another
  confirmation email.
</p>
<p>
  <c:choose>
    <c:when test="${userSession.loggedIn}">
      <a href="${ctx}/my-page" class="button success radius">Visit Your Account <i class="fa fa-angle-right"></i></a><br />
    </c:when>
    <c:otherwise>
      <a href="${ctx}/" class="button success radius">Visit Home Page <i class="fa fa-angle-right"></i></a><br />
    </c:otherwise>
  </c:choose>
  <a href="${ctx}/forgot-password" class="button primary radius">Request Password Reset <i class="fa fa-angle-right"></i></a>
</p>
