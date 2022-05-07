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
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="html" uri="/WEB-INF/html-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/number-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="imageList" class="java.util.ArrayList" scope="request"/>
<style>
  .image-browser {
    display: inline-block;
    max-width: 100%;
    height: 175px;
    text-align: center;
    background-image: linear-gradient(45deg, #F6F6F6 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #F6F6F6 75%),
    linear-gradient(45deg, transparent 75%, #F6F6F6 75%),
    linear-gradient(45deg, #F6F6F6 25%, #fff 25%);
    background-size: 10px 10px;
    background-position: 0 0, 0 0, -5px -5px, 5px 5px;
  }
  .image-browser img {
    max-width: 95%;
    max-height: 160px;
    position: relative;
    top: 50%;
    transform: translateY(-50%);
  }
</style>
<div class="grid-container">
  <c:if test="${empty imageList}">
    <p>No images were found.</p>
  </c:if>
  <div class="grid-x grid-margin-x small-up-2 medium-up-3 large-up-5">
    <c:forEach items="${imageList}" var="image" varStatus="status">
      <div class="cell card">
        <div class="image-browser">
          <img src="${ctx}/assets/img/<fmt:formatDate pattern="yyyyMMddHHmmss" value="${image.created}" />-${image.id}/${url:encodeUri(image.filename)}">
        </div>
        <div class="card-section">
          <div>
            <small><c:out value="${image.filename}"/></small><br />
            <small style="color: #999999">${image.width}x${image.height}</small>
            <small style="color: #999999"><c:out value="${number:suffix(image.fileLength)}"/></small><br />
            <small style="color: #999999"><fmt:formatDate pattern="yyyy-MM-dd" value="${image.created}" /></small><br />
            <small><a target="_blank" href="${ctx}/assets/img/<fmt:formatDate pattern="yyyyMMddHHmmss" value="${image.created}" />-${image.id}/${url:encodeUri(image.filename)}">Image Link</a></small>
          </div>
        </div>
      </div>
    </c:forEach>
  </div>
</div>
