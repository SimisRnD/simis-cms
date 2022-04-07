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
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="isDraft" class="java.lang.String" scope="request"/>
<jsp:useBean id="card1" class="java.lang.String" scope="request"/>
<jsp:useBean id="card2" class="java.lang.String" scope="request"/>
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
  <c:if test="${!empty card1}">
    <c:if test="${!empty card2 && useIcon eq 'true'}">
      <div class="float-right">
        <button class="reveal-button" data-toggle="modal${widgetContext.uniqueId}"><i class="${font:fal()} fa-plus-circle"></i></button>
      </div>
    </c:if>
    <button id="reveal-button${widgetContext.uniqueId}" class="reveal-button-text" data-toggle="modal${widgetContext.uniqueId}"><div class="button-reveal-content">${card1}</div></button>
    <c:if test="${!empty card2}">
      <div class="reveal<c:if test="${!empty size}"> <c:out value="${size}" /></c:if>" id="modal${widgetContext.uniqueId}"
           data-reveal
           data-reset-on-close="true"
           <c:choose>
             <c:when test="${animate eq 'up'}">
               data-animation-in="slide-in-up fast"
               data-animation-out="slide-out-down fast"
             </c:when>
             <c:when test="${animate eq 'down'}">
               data-animation-in="slide-in-down fast"
               data-animation-out="slide-out-up fast"
             </c:when>
             <c:when test="${animate eq 'right'}">
               data-animation-in="slide-in-right fast"
               data-animation-out="slide-out-right fast"
             </c:when>
             <c:when test="${animate eq 'fade'}">
               data-animation-in="fade-in"
               data-animation-out="fade-out"
             </c:when>
             <c:otherwise>
                data-animation-in="slide-in-left fast"
                data-animation-out="slide-out-left fast"
             </c:otherwise>
           </c:choose>
           <c:if test="${attach eq 'left'}">
             data-h-offset="0"
           </c:if>
           data-multiple-opened="true"
           <c:if test="${!empty revealClass}">
           data-additional-overlay-classes="<c:out value="${revealClass}" />"
           </c:if>
           data-close-on-click="true">
          ${card2}
        <button class="close-button" data-close aria-label="Close reveal" type="button">
          <span aria-hidden="true"><i class="${font:fal()} fa-circle-xmark"></i></span>
        </button>
      </div>
    </c:if>
  </c:if>
</div>
<script>
  // Attach to the modal display event; auto-play first modal video
  document.addEventListener("DOMContentLoaded", function () {
    $('#modal${widgetContext.uniqueId}').on('open.zf.reveal', function () {
      let revealButtonElement = document.getElementById("reveal-button${widgetContext.uniqueId}");
      let revealContainerElement = document.getElementById("modal${widgetContext.uniqueId}");
      if (revealButtonElement && revealContainerElement) {
        let videoElements = revealContainerElement.getElementsByTagName('video')
        if (videoElements && videoElements.length > 0) {
          let thisVideoElement = videoElements.item(0);
          thisVideoElement.play();
        }
      }
    })
  });
</script>
