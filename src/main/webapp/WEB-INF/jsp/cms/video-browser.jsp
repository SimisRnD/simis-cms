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
<%@ taglib prefix="g" uri="http://granule.com/tags" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<%@ taglib prefix="html" uri="/WEB-INF/html-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/number-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="fileItemList" class="java.util.ArrayList" scope="request"/>
<%-- Include the formatting for when TinyMCE uses an iFrame to open the browser --%>
<%-- All of Foundation.css would override colors and stuff when using the browser directly --%>
<g:compress>
  <link rel="stylesheet" type="text/css" href="${ctx}/css/${font:fontawesome()}/css/all.min.css" />
  <link rel="stylesheet" type="text/css" href="${ctx}/css/${font:fontawesome()}/css/v4-shims.min.css" />
  <link rel="stylesheet" type="text/css" href="${ctx}/css/foundation-6.6.3/foundation.min.css" />
</g:compress>
<h3>Videos</h3>
<c:if test="${empty fileItemList}">
  No videos were found
</c:if>
<c:forEach items="${fileItemList}" var="file" varStatus="status">
  <i class="fa fa-file-video-o"></i>
  <a href="#" style="text-decoration: underline;" onclick="mySubmit(this.dataset.src)" data-src="${ctx}/assets/view/${file.url}"><c:out value="${file.title}" /></a>
  <small><c:out value="${file.mimeType}" /></small>
  <small><c:out value="${number:suffix(file.fileLength)}"/></small>
  <c:if test="${!status.last}"><br /></c:if>
</c:forEach>
<script>
  <c:choose>
    <c:when test="${!empty inputId}">
    function mySubmit(itemUrl) {
      top.document.getElementById("<c:out value="${inputId}" />").href = itemUrl;
      $('#imageBrowserReveal').foundation('close');
    }
    </c:when>
    <c:otherwise>
    function mySubmit(itemUrl) {
        window.parent.postMessage({
            mceAction: 'FileSelected',
            content: itemUrl
        }, '*');
    }
    </c:otherwise>
  </c:choose>
</script>
