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
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="isDraft" class="java.lang.String" scope="request"/>
<jsp:useBean id="display" class="java.lang.String" scope="request"/>
<jsp:useBean id="carouselClass" class="java.lang.String" scope="request"/>
<jsp:useBean id="carouselTitle" class="java.lang.String" scope="request"/>
<jsp:useBean id="carouselSize" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<div class="platform-content-container">
  <c:if test="${showEditor eq 'true' && !empty uniqueId}">
    <div class="platform-content-editor">
      <c:if test="${isDraft eq 'true'}">
        <a class="hollow button small warning" href="${widgetContext.uri}?action=publish&widget=${widgetContext.uniqueId}&token=${userSession.formToken}" onclick="return confirm('Publish this content?');">DRAFT</a>
      </c:if>
      <a class="hollow button small secondary" href="${ctx}/content-editor?uniqueId=${uniqueId}&returnPage=${returnPage}"><i class="${font:fas()} fa-edit"></i></a>
    </div>
  </c:if>
  <div class="orbit<c:if test="${!empty carouselClass}"> <c:out value="${carouselClass}" /></c:if>" role="region"<c:if test="${!empty carouselTitle}"> aria-label="<c:out value="${carouselTitle}" />"</c:if> data-orbit<c:if test="${!empty dataOptions}"> <c:out value="${dataOptions}" /></c:if>>
    <div class="orbit-wrapper">
      <c:if test="${showControls eq 'true' && fn:length(cardList) gt 1}">
      <div class="orbit-controls">
        <c:if test="${showLeftControl eq 'true'}">
          <button class="orbit-previous"><span class="show-for-sr">Previous Slide</span>&#9664;&#xFE0E;</button>
        </c:if>
        <c:if test="${showRightControl eq 'true'}">
          <button class="orbit-next"><span class="show-for-sr">Next Slide</span>&#9654;&#xFE0E;</button>
        </c:if>
      </div>
      </c:if>
      <ul class="orbit-container">
        <c:forEach items="${cardList}" var="card" varStatus="cardStatus">
          <li class="<c:if test="${cardStatus.first}">is-active </c:if>orbit-slide">
            <figure class="orbit-figure">
              <c:choose>
                <c:when test="${display eq 'images'}"><img class="orbit-image" ${card} /></c:when>
                <c:when test="${carouselSize eq 'large'}"><img class="orbit-image" src="${ctx}/images/widgets/image-640-480.png" alt="background image" /></c:when>
                <c:when test="${carouselSize eq 'medium'}"><img class="orbit-image" src="${ctx}/images/widgets/image-1952-850.png" alt="background image" /></c:when>
                <c:when test="${carouselSize eq 'tiny'}"><img class="orbit-image" src="${ctx}/images/widgets/image-2034-690.png" alt="background image" /></c:when>
                <c:otherwise><img class="orbit-image" src="${ctx}/images/widgets/image-640-240.png" alt="background image"></c:otherwise>
              </c:choose>
              <c:if test="${display eq 'text'}">
              <figcaption class="orbit-caption">
                ${card}
              </figcaption>
              </c:if>
            </figure>
          </li>
        </c:forEach>
      </ul>
    </div>
    <c:if test="${showBullets eq 'true' && fn:length(cardList) gt 1}">
    <nav class="orbit-bullets">
      <c:forEach items="${cardList}" var="card" varStatus="cardStatus">
        <button data-slide="${cardStatus.index}"<c:if test="${cardStatus.first}"> class="is-active"</c:if>><span class="show-for-sr">Slide ${cardStatus.count}</span><c:if test="${cardStatus.first}"><span class="show-for-sr">Current Slide</span></c:if></button>
      </c:forEach>
    </nav>
    </c:if>
  </div>
</div>
