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
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<%@ taglib prefix="user" uri="/WEB-INF/user-functions.tld" %>
<%@ taglib prefix="date" uri="/WEB-INF/date-functions.tld" %>
<jsp:useBean id="contentHtml" class="java.lang.String" scope="request"/>
<jsp:useBean id="wiki" class="com.simisinc.platform.domain.model.cms.Wiki" scope="request"/>
<jsp:useBean id="wikiPage" class="com.simisinc.platform.domain.model.cms.WikiPage" scope="request"/>
<h4><c:out value="${wikiPage.title}" /></h4>
<hr />
<div class="platform-content-container">
  <c:if test="${showEditor eq 'true' && !empty wikiPage.uniqueId}">
    <div class="platform-content-editor"><a class="hollow button small secondary" href="${ctx}/wiki-editor?wikiUniqueId=${wiki.uniqueId}&pageUniqueId=${wikiPage.uniqueId}&returnPage=${returnPage}"><i class="${font:fas()} fa-edit"></i></a></div>
  </c:if>
  <div class="platform-content">
    <em>This wiki page has not been created.</em>
  </div>
</div>
