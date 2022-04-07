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
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="isDraft" class="java.lang.String" scope="request"/>
<jsp:useBean id="display" class="java.lang.String" scope="request"/>
<jsp:useBean id="carouselClass" class="java.lang.String" scope="request"/>
<jsp:useBean id="carouselTitle" class="java.lang.String" scope="request"/>
<jsp:useBean id="carouselSize" class="java.lang.String" scope="request"/>
<jsp:useBean id="cardClass" class="java.lang.String" scope="request"/>
<c:if test="${showControls eq 'true' && fn:length(cardList) gt 1}">
<style>
  #swiper${widgetContext.uniqueId} { width: calc(100% - 80px); }
</style>
</c:if>
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
  <div class="swiper-outer-container<c:if test="${!empty carouselClass}"> <c:out value="${carouselClass}" /></c:if>">
    <div id="swiper${widgetContext.uniqueId}" class="swiper-container">
      <div class="swiper-wrapper">
        <c:forEach items="${cardList}" var="card" varStatus="cardStatus">
          <div class="swiper-slide">
            <div class="card<c:if test="${!empty cardClass}"> <c:out value="${cardClass}" /></c:if>">
              ${card}
            </div>
          </div>
        </c:forEach>
      </div>
    </div>
<c:if test="${showControls eq 'true' && fn:length(cardList) gt 1}">
  <c:if test="${showLeftControl eq 'true'}">
    <div id="swiper-button-prev${widgetContext.uniqueId}" class="swiper-button-prev"></div>
  </c:if>
  <c:if test="${showRightControl eq 'true'}">
    <div id="swiper-button-next${widgetContext.uniqueId}" class="swiper-button-next"></div>
  </c:if>
</c:if>
  </div>
</div>
<script>
    var swiper${widgetContext.uniqueId} = new Swiper("#swiper${widgetContext.uniqueId}", {
        slidesPerView: <c:out value="${smallCardCount}" />,
        centerInsufficientSlides: true,
        spaceBetween: 15,
        breakpoints: {
            640: {
                slidesPerView: <c:out value="${mediumCardCount}" />,
                spaceBetween: 15
            },
            1024: {
                slidesPerView: <c:out value="${largeCardCount}" />,
                spaceBetween: 15
            }
        }
        // loop: true,
        // autoplay: { delay: 5000, stopOnLastSlide: true, disableOnInteraction: true },
        <c:if test="${showControls eq 'true' && fn:length(cardList) gt 1}">
        ,navigation: {
            <c:if test="${showLeftControl eq 'true'}">
            nextEl: '#swiper-button-next${widgetContext.uniqueId}'
            </c:if>
            <c:if test="${showLeftControl eq 'true' && showRightControl eq 'true'}">,</c:if>
            <c:if test="${showRightControl eq 'true'}">
            prevEl: '#swiper-button-prev${widgetContext.uniqueId}'
            </c:if>
        }
        </c:if>
    });
</script>
