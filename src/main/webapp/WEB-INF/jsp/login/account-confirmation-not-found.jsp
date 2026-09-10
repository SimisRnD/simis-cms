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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="user" class="com.simisinc.platform.domain.model.User" scope="request"/>
<h2 class="h4">
  This link is no longer valid
</h2>
<%-- #1836: only "expired" is knowable from the data. An account holds a single token, so a link
     replaced by a newer one leaves nothing to distinguish it from one that never existed -- the
     fallback names the realistic causes instead of guessing a single wrong one. --%>
<c:choose>
  <c:when test="${notFoundReason eq 'expired'}">
    <p>
      This link has expired. Links are only good for a limited time, so please
      request a new one and use it soon after it arrives.
    </p>
  </c:when>
  <c:otherwise>
    <p>
      This link has either already been used, or it was replaced by a newer one.
      Only the most recent link sent to you will work, so please check for a more
      recent email before requesting another.
    </p>
  </c:otherwise>
</c:choose>
<p>
  If you still cannot get in, request a new link below.
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
