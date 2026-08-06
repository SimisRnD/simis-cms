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
<h4>This confirmation link is no longer valid</h4>
<p>
  It may have already been used, or it may have expired. If you still want to subscribe, please
  sign up again from the site to get a fresh confirmation email.
</p>
<p>
  <a href="${ctx}/" class="button primary radius">Visit Home Page <i class="fa fa-angle-right"></i></a>
  <a href="${ctx}/contact-us" class="button secondary radius">Contact Us <i class="fa fa-angle-right"></i></a>
</p>
