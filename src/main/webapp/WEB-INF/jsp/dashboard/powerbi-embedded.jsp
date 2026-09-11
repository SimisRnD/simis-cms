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
<%@ taglib prefix="css" uri="/WEB-INF/tlds/style-functions.tld" %>
<jsp:useBean id="embedUrl" class="java.lang.String" scope="request"/>
<jsp:useBean id="height" class="java.lang.String" scope="request"/>
<c:set var="scHook" value="${css:register(pageContext.request, 'min-height: ' += height)}"/>
<iframe src="<c:out value="${embedUrl}"/>" frameborder="0" width="100%"<c:if test="${!empty scHook}"> data-sc-style="${scHook}"</c:if> allowFullScreen="true"></iframe>
